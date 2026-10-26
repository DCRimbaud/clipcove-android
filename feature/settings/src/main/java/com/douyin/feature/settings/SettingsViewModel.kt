/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Coordinates settings persistence, data clearing, backup restore, diagnostics, and update actions.
 */
package com.douyin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.core.common.diagnostics.DiagnosticsRepository
import com.douyin.core.uiresources.R as SharedR
import com.douyin.data.download.DownloadRepository
import com.douyin.data.favorites.FavoritesRepository
import com.douyin.data.history.HistoryRepository
import com.douyin.data.settings.AppLanguage
import com.douyin.data.settings.SettingsRepository
import com.douyin.data.update.AppRelease
import com.douyin.data.update.InstallResult
import com.douyin.data.update.ReleaseSelector
import com.douyin.data.update.UpdateInstaller
import com.douyin.data.update.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Observable state machine for manual GitHub update checks and installation. */
sealed interface UpdateUiState {
    /** No update request has started in this screen session. */
    data object Idle : UpdateUiState
    /** A release metadata request is in flight. */
    data object Checking : UpdateUiState
    /** The installed version is not older than any eligible release. */
    data object UpToDate : UpdateUiState
    /** A newer release is available and can be installed. */
    data class Available(val release: AppRelease) : UpdateUiState
    /** An APK is being downloaded or its routes are being probed. */
    data class Downloading(val release: AppRelease, val progress: Int) : UpdateUiState
    /** Android's unknown-source permission must be granted before downloading. */
    data class PermissionRequired(val release: AppRelease) : UpdateUiState
    /** The verified package was handed to Android's installer. */
    data class InstallReady(val release: AppRelease) : UpdateUiState
    /** The last check or download failed; release is retained when retry is possible. */
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateUiState
}

/** One localizable, one-shot settings notification resource. */
data class SettingsMessage(
    val resourceId: Int,
    val args: List<Any> = emptyList(),
)

/** Settings coordinator for preferences, local data, diagnostics, and update checks. */
class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val historyRepo: HistoryRepository,
    private val downloadRepo: DownloadRepository,
    private val favoritesRepo: FavoritesRepository,
    private val diagnosticsRepo: DiagnosticsRepository,
    private val updateRepository: UpdateRepository,
    private val updateInstaller: UpdateInstaller,
    val wifiTransfer: WifiTransferController,
    val versionName: String,
    val versionCode: Int,
) : ViewModel() {

    val autoSave: StateFlow<Boolean> = settingsRepo.autoSaveToDownloads
    val autoParseSharedLinks: StateFlow<Boolean> = settingsRepo.autoParseSharedLinks
    val autoUpdateMirrors: StateFlow<Boolean> = settingsRepo.autoUpdateMirrors
    val appLanguage: StateFlow<AppLanguage> = settingsRepo.appLanguage

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState

    val wifiRunning: StateFlow<Boolean> = wifiTransfer.running
    val wifiUrl: StateFlow<String> = wifiTransfer.url

    /** Persists public-download auto-save and publishes a user-facing confirmation. */
    fun setAutoSave(value: Boolean) {
        settingsRepo.setAutoSave(value)
        publishMessage(
            if (value) {
                SharedR.string.settings_message_auto_save_on
            } else {
                SharedR.string.settings_message_auto_save_off
            },
        )
    }

    /** Persists shared-link auto-parse behavior. */
    fun setAutoParseSharedLinks(value: Boolean) {
        settingsRepo.setAutoParseSharedLinks(value)
        publishMessage(
            if (value) {
                SharedR.string.settings_message_auto_parse_on
            } else {
                SharedR.string.settings_message_auto_parse_off
            },
        )
    }

    /** Persists whether update transports may use approved mirrors. */
    fun setAutoUpdateMirrors(value: Boolean) {
        settingsRepo.setAutoUpdateMirrors(value)
        publishMessage(
            if (value) {
                SharedR.string.settings_message_mirrors_on
            } else {
                SharedR.string.settings_message_mirrors_off
            },
        )
    }

    /** Starts or stops the LAN transfer server and reports the result. */
    fun toggleWifi() {
        val ok = wifiTransfer.toggle()
        publishMessage(
            when {
                ok && wifiTransfer.running.value -> SharedR.string.settings_message_wifi_on
                ok -> SharedR.string.settings_message_wifi_off
                else -> SharedR.string.settings_message_wifi_failed
            },
        )
    }

    /** Clears parse history without deleting favorites or media files. */
    fun clearHistory() {
        historyRepo.clear()
        publishMessage(SharedR.string.settings_message_history_cleared)
    }

    /** Removes every download task and its app-private files. */
    fun clearDownloads() {
        downloadRepo.tasks.value.toList().forEach(downloadRepo::remove)
        publishMessage(SharedR.string.settings_message_downloads_cleared)
    }

    /** Clears sanitized diagnostics only. */
    fun clearDiagnostics() {
        diagnosticsRepo.clear()
        publishMessage(SharedR.string.settings_message_diagnostics_cleared)
    }

    /** Removes downloads, history, favorites, and diagnostics in one confirmed operation. */
    fun clearAllLocalData() {
        downloadRepo.tasks.value.toList().forEach(downloadRepo::remove)
        historyRepo.clear()
        favoritesRepo.clear()
        diagnosticsRepo.clear()
        publishMessage(SharedR.string.settings_message_all_cleared)
    }

    /** Export a versioned backup containing history, favorites, and source URLs. */
    /** Serializes a versioned local backup for sharing or storage. */
    fun exportData(): String = BackupCodec.encode(
        history = historyRepo.load(),
        favorites = favoritesRepo.load(),
        appVersion = versionName,
    )

    /**
     * Restore old flat-array backups and the current versioned format.
     * Restore is additive: existing entries are merged by stable media ID.
     */
    /**
     * Restores history and favorites from a versioned or legacy backup.
     *
     * @param raw Untrusted JSON text selected by the user.
     * @return Nothing; success, validation failure, and repair messages are
     *         published through [message].
     */
    fun importData(raw: String) {
        try {
            val payload = BackupCodec.decode(raw)
            payload.history.forEach(historyRepo::add)
            payload.favorites.forEach(favoritesRepo::add)
            val total = payload.history.size + payload.favorites.size
            if (total == 0) {
                publishMessage(SharedR.string.settings_message_backup_empty)
            } else {
                publishMessage(
                    SharedR.string.settings_message_restore_complete,
                    payload.history.size,
                    payload.favorites.size,
                )
            }
        } catch (_: IllegalArgumentException) {
            publishMessage(SharedR.string.settings_message_restore_failed)
        } catch (_: Exception) {
            publishMessage(SharedR.string.settings_message_backup_read_failed)
        }
    }

    /** Builds a sanitized support report stamped with app/version/device context. */
    fun exportDiagnostics(): String = diagnosticsRepo.exportReport(versionName, versionCode)

    /**
     * Queries GitHub Releases without blocking the UI thread.
     *
     * Re-entry is ignored while a check or download is already active.
     */
    fun checkForUpdate() {
        if (_updateState.value is UpdateUiState.Checking ||
            _updateState.value is UpdateUiState.Downloading
        ) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            _updateState.value = try {
                val latest = ReleaseSelector.newest(
                    updateRepository.getReleases(settingsRepo.autoUpdateMirrors.value),
                    versionName,
                )
                if (latest == null) UpdateUiState.UpToDate else UpdateUiState.Available(latest)
            } catch (e: Exception) {
                UpdateUiState.Failed(e.message ?: "Update check failed")
            }
        }
    }

    /**
     * Downloads, verifies, and opens the currently selected update.
     *
     * Probe progress, permission requirements, installer handoff, and terminal
     * failures are all represented by [UpdateUiState].
     */
    fun downloadAndInstallUpdate() {
        val release = _updateState.value.pendingRelease() ?: return
        if (_updateState.value is UpdateUiState.Downloading) return
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading(release, 0)
            try {
                val result = updateInstaller.downloadAndInstall(
                    release = release,
                    allowMirrors = settingsRepo.autoUpdateMirrors.value,
                ) { progress ->
                    if (progress < 100) {
                        _updateState.value = UpdateUiState.Downloading(release, progress)
                    }
                }
                _updateState.value = when (result) {
                    InstallResult.InstallerOpened -> UpdateUiState.InstallReady(release)
                    InstallResult.PermissionRequired -> UpdateUiState.PermissionRequired(release)
                }
            } catch (e: Exception) {
                _updateState.value = UpdateUiState.Failed(
                    message = e.message ?: "Update download or installation failed",
                    release = release,
                )
            }
        }
    }

    private fun UpdateUiState.pendingRelease(): AppRelease? = when (this) {
        is UpdateUiState.Available -> release
        is UpdateUiState.PermissionRequired -> release
        is UpdateUiState.InstallReady -> release
        is UpdateUiState.Failed -> release
        else -> null
    }

    /** Publishes a recoverable backup-file read failure. */
    fun reportFileReadError() {
        publishMessage(SharedR.string.settings_message_file_read_failed)
    }

    /** Persists the language choice; the hosting activity recreates the UI. */
    fun setAppLanguage(language: AppLanguage) {
        settingsRepo.setAppLanguage(language)
    }

    /** Publishes a localizable one-shot notification. */
    private fun publishMessage(resourceId: Int, vararg args: Any) {
        _message.value = SettingsMessage(resourceId, args.toList())
    }

    /** Clears the one-shot message after the UI displays its toast. */
    fun consumeMessage() {
        _message.value = null
    }
}