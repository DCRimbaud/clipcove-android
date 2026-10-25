package com.douyin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.core.common.diagnostics.DiagnosticsRepository
import com.douyin.data.download.DownloadRepository
import com.douyin.data.favorites.FavoritesRepository
import com.douyin.data.history.HistoryRepository
import com.douyin.data.settings.SettingsRepository
import com.douyin.data.update.AppRelease
import com.douyin.data.update.ReleaseSelector
import com.douyin.data.update.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val release: AppRelease) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

/** Settings coordinator for preferences, local data, diagnostics, and update checks. */
class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val historyRepo: HistoryRepository,
    private val downloadRepo: DownloadRepository,
    private val favoritesRepo: FavoritesRepository,
    private val diagnosticsRepo: DiagnosticsRepository,
    private val updateRepository: UpdateRepository,
    val wifiTransfer: WifiTransferController,
    val versionName: String,
    val versionCode: Int,
) : ViewModel() {

    val autoSave: StateFlow<Boolean> = settingsRepo.autoSaveToDownloads
    val autoParseSharedLinks: StateFlow<Boolean> = settingsRepo.autoParseSharedLinks

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState

    val wifiRunning: StateFlow<Boolean> = wifiTransfer.running
    val wifiUrl: StateFlow<String> = wifiTransfer.url

    fun setAutoSave(value: Boolean) {
        settingsRepo.setAutoSave(value)
        _message.value = if (value) "下载完成后将自动保存到「下载」目录" else "已关闭自动保存"
    }

    fun setAutoParseSharedLinks(value: Boolean) {
        settingsRepo.setAutoParseSharedLinks(value)
        _message.value = if (value) "分享链接导入后将自动解析" else "分享链接仅填入输入框"
    }

    fun toggleWifi() {
        val ok = wifiTransfer.toggle()
        _message.value = when {
            ok && wifiTransfer.running.value -> "WiFi 传输已开启"
            ok -> "WiFi 传输已关闭"
            else -> "启动失败，请检查网络"
        }
    }

    fun clearHistory() {
        historyRepo.clear()
        _message.value = "历史记录已清空"
    }

    fun clearDownloads() {
        downloadRepo.tasks.value.toList().forEach(downloadRepo::remove)
        _message.value = "下载列表已清空"
    }

    fun clearDiagnostics() {
        diagnosticsRepo.clear()
        _message.value = "诊断记录已清空"
    }

    fun clearAllLocalData() {
        downloadRepo.tasks.value.toList().forEach(downloadRepo::remove)
        historyRepo.clear()
        favoritesRepo.clear()
        diagnosticsRepo.clear()
        _message.value = "历史、收藏、下载记录和诊断信息已清空"
    }

    /** Export a versioned backup containing history, favorites, and source URLs. */
    fun exportData(): String = BackupCodec.encode(
        history = historyRepo.load(),
        favorites = favoritesRepo.load(),
        appVersion = versionName,
    )

    /**
     * Restore old flat-array backups and the current versioned format.
     * Restore is additive: existing entries are merged by stable media ID.
     */
    fun importData(raw: String) {
        try {
            val payload = BackupCodec.decode(raw)
            payload.history.forEach(historyRepo::add)
            payload.favorites.forEach(favoritesRepo::add)
            val total = payload.history.size + payload.favorites.size
            _message.value = if (total == 0) {
                "备份格式有效，但没有可恢复的数据"
            } else {
                "还原完成：${payload.history.size} 条历史，${payload.favorites.size} 条收藏"
            }
        } catch (e: IllegalArgumentException) {
            _message.value = "还原失败：${e.message ?: "文件格式不支持"}"
        } catch (e: Exception) {
            _message.value = "还原失败：无法读取备份文件"
        }
    }

    fun exportDiagnostics(): String = diagnosticsRepo.exportReport(versionName, versionCode)

    fun checkForUpdate() {
        if (_updateState.value == UpdateUiState.Checking) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            _updateState.value = try {
                val latest = ReleaseSelector.newest(updateRepository.getReleases(), versionName)
                if (latest == null) UpdateUiState.UpToDate else UpdateUiState.Available(latest)
            } catch (e: Exception) {
                UpdateUiState.Failed(e.message ?: "检查更新失败，请稍后重试")
            }
        }
    }

    fun reportFileReadError() {
        _message.value = "无法读取所选文件，请重新选择"
    }

    fun consumeMessage() {
        _message.value = null
    }
}
