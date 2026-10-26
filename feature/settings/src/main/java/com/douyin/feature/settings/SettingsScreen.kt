/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Renders preferences, transfer controls, data management, update state, and confirmation dialogs.
 */
package com.douyin.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.douyin.core.designsystem.DyAmber
import com.douyin.core.designsystem.DyBorder
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyCyan
import com.douyin.core.designsystem.DyGreen
import com.douyin.core.designsystem.DyIconTile
import com.douyin.core.designsystem.DyInput
import com.douyin.core.designsystem.DyMotion
import com.douyin.core.designsystem.DyPageHeader
import com.douyin.core.designsystem.DyPanel
import com.douyin.core.designsystem.DyPill
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DySectionHeader
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.core.designsystem.dyPressable
import com.douyin.core.uiresources.R as SharedR
import com.douyin.data.settings.AppLanguage
import com.douyin.data.update.AppRelease
import java.io.File

@Composable
/**
 * Renders preferences, LAN transfer, local-data controls, and update state.
 *
 * Toast messages are consumed after display. Destructive operations use explicit
 * confirmation dialogs and remain delegated to SettingsViewModel.
 */
fun SettingsScreen(viewModel: SettingsViewModel) {
    val autoSave by viewModel.autoSave.collectAsStateWithLifecycle()
    val autoParseSharedLinks by viewModel.autoParseSharedLinks.collectAsStateWithLifecycle()
    val autoUpdateMirrors by viewModel.autoUpdateMirrors.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val wifiRunning by viewModel.wifiRunning.collectAsStateWithLifecycle()
    val wifiUrl by viewModel.wifiUrl.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val updateRelease = updateState.pendingRelease()
    val updateProgress by animateFloatAsState(
        targetValue = (updateState as? UpdateUiState.Downloading)?.progress?.div(100f) ?: 0f,
        animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
        label = "settingsUpdateProgress",
    )
    val updateTint by animateColorAsState(
        targetValue = when (updateState) {
            UpdateUiState.UpToDate,
            is UpdateUiState.InstallReady -> DyGreen
            is UpdateUiState.PermissionRequired -> DyAmber
            is UpdateUiState.Failed -> DyRed
            is UpdateUiState.Available,
            is UpdateUiState.Downloading -> DyAmber
            else -> DyCyan
        },
        animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
        label = "settingsUpdateTint",
    )
    val updateIcon = when (updateState) {
        UpdateUiState.UpToDate,
        is UpdateUiState.InstallReady -> Icons.Filled.CheckCircle
        is UpdateUiState.Available,
        is UpdateUiState.Downloading -> Icons.Filled.Download
        else -> Icons.Filled.SystemUpdate
    }
    val context = LocalContext.current
    var showChangelog by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val importBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                viewModel.reportFileReadError()
            } else {
                viewModel.importData(text)
            }
        }
    }

    LaunchedEffect(message) {
        message?.let {
            val text = context.getString(it.resourceId, *it.args.toTypedArray())
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DyPageHeader(
            title = stringResource(SharedR.string.nav_settings),
            subtitle = stringResource(SharedR.string.settings_subtitle),
            eyebrow = stringResource(SharedR.string.settings_eyebrow),
            trailing = { DyPill("v${viewModel.versionName}", DyCyan) },
        )

        SettingsSection(title = stringResource(SharedR.string.settings_general)) {
            ActionRow(
                icon = Icons.Filled.Language,
                tint = DyCyan,
                title = stringResource(SharedR.string.language_title),
                description = stringResource(
                    SharedR.string.settings_language_current,
                    languageLabel(appLanguage),
                ),
                onClick = { showLanguageDialog = true },
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            SettingRow(
                icon = Icons.Filled.Download,
                tint = DyGreen,
                title = stringResource(SharedR.string.settings_auto_save_title),
                description = stringResource(SharedR.string.settings_auto_save_description),
                trailing = {
                    Switch(
                        checked = autoSave,
                        onCheckedChange = viewModel::setAutoSave,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = DyRed,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = DyInput,
                            uncheckedThumbColor = DyTextSecondary,
                        ),
                    )
                },
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            SettingRow(
                icon = Icons.Filled.ContentPaste,
                tint = DyCyan,
                title = stringResource(SharedR.string.settings_auto_parse_title),
                description = stringResource(SharedR.string.settings_auto_parse_description),
                trailing = {
                    Switch(
                        checked = autoParseSharedLinks,
                        onCheckedChange = viewModel::setAutoParseSharedLinks,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = DyRed,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = DyInput,
                            uncheckedThumbColor = DyTextSecondary,
                        ),
                    )
                },
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            SettingRow(
                icon = Icons.Filled.SystemUpdate,
                tint = DyGreen,
                title = stringResource(SharedR.string.settings_update_mirrors_title),
                description = stringResource(SharedR.string.settings_update_mirrors_description),
                trailing = {
                    Switch(
                        checked = autoUpdateMirrors,
                        onCheckedChange = viewModel::setAutoUpdateMirrors,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = DyRed,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = DyInput,
                            uncheckedThumbColor = DyTextSecondary,
                        ),
                    )
                },
            )
        }

        SettingsSection(title = stringResource(SharedR.string.settings_lan_section)) {
            SettingRow(
                icon = Icons.Filled.Wifi,
                tint = DyCyan,
                title = stringResource(SharedR.string.settings_wifi_title),
                description = if (wifiRunning) {
                    stringResource(SharedR.string.settings_wifi_description_on)
                } else {
                    stringResource(SharedR.string.settings_wifi_description_off)
                },
                trailing = {
                    Button(
                        onClick = viewModel::toggleWifi,
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (wifiRunning) DyInput else DyRed,
                            contentColor = if (wifiRunning) DyText else Color.White,
                        ),
                        border = if (wifiRunning) BorderStroke(1.dp, DyBorder) else null,
                    ) {
                        Text(
                            if (wifiRunning) {
                                stringResource(SharedR.string.action_stop)
                            } else {
                                stringResource(SharedR.string.action_start)
                            },
                            fontSize = 13.sp,
                        )
                    }
                },
            )
            AnimatedVisibility(
                visible = wifiRunning && wifiUrl.isNotBlank(),
                enter = fadeIn(tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing)) +
                    expandVertically(
                        animationSpec = tween(
                            DyMotion.StandardMs,
                            easing = DyMotion.EmphasizedEasing,
                        )
                    ),
                exit = fadeOut(tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing)) +
                    shrinkVertically(
                        animationSpec = tween(
                            DyMotion.QuickMs,
                            easing = DyMotion.StandardEasing,
                        )
                    ),
            ) {
                Column {
                    HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(SharedR.string.settings_wifi_access),
                            style = MaterialTheme.typography.bodySmall,
                            color = DyTextSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(wifiUrl, style = MaterialTheme.typography.labelLarge, color = DyCyan, maxLines = 1)
                    }
                }
            }
        }

        SettingsSection(title = stringResource(SharedR.string.settings_data_section)) {
            ActionRow(
                icon = Icons.Filled.Save,
                tint = DyCyan,
                title = stringResource(SharedR.string.settings_export_backup_title),
                description = stringResource(SharedR.string.settings_export_backup_description),
                onClick = { exportBackup(context, viewModel) },
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.UploadFile,
                tint = DyGreen,
                title = stringResource(SharedR.string.settings_restore_backup_title),
                description = stringResource(SharedR.string.settings_restore_backup_description),
                onClick = {
                    importBackup.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                },
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.History,
                tint = DyAmber,
                title = stringResource(SharedR.string.settings_clear_history_title),
                description = stringResource(SharedR.string.settings_clear_history_description),
                onClick = viewModel::clearHistory,
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.Delete,
                tint = DyRed,
                title = stringResource(SharedR.string.settings_clear_downloads_title),
                description = stringResource(SharedR.string.settings_clear_downloads_description),
                onClick = viewModel::clearDownloads,
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.DeleteForever,
                tint = DyRed,
                title = stringResource(SharedR.string.settings_clear_all_title),
                description = stringResource(SharedR.string.settings_clear_all_description),
                onClick = { showClearAllConfirm = true },
            )
        }

        SettingsSection(title = stringResource(SharedR.string.settings_updates_section)) {
            ActionRow(
                icon = Icons.Filled.BugReport,
                tint = DyAmber,
                title = stringResource(SharedR.string.settings_export_diagnostics_title),
                description = stringResource(SharedR.string.settings_export_diagnostics_description),
                onClick = { exportDiagnostics(context, viewModel) },
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.Delete,
                tint = DyTextSecondary,
                title = stringResource(SharedR.string.settings_clear_diagnostics_title),
                description = stringResource(SharedR.string.settings_clear_diagnostics_description),
                onClick = viewModel::clearDiagnostics,
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = updateIcon,
                tint = updateTint,
                title = updateActionTitle(updateState),
                description = updateActionDescription(updateState, autoUpdateMirrors),
                onClick = {
                    if (updateState is UpdateUiState.Downloading) return@ActionRow
                    when {
                        updateRelease?.hasInstallableAssets() == true ->
                            viewModel.downloadAndInstallUpdate()
                        updateRelease != null -> openReleasePage(context, updateRelease.htmlUrl)
                        else -> viewModel.checkForUpdate()
                    }
                },
            )
            AnimatedVisibility(
                visible = updateState is UpdateUiState.Downloading,
                enter = fadeIn(tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing)) +
                    expandVertically(
                        animationSpec = tween(
                            DyMotion.QuickMs,
                            easing = DyMotion.StandardEasing,
                        )
                    ),
                exit = fadeOut(tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing)) +
                    shrinkVertically(
                        animationSpec = tween(
                            DyMotion.QuickMs,
                            easing = DyMotion.StandardEasing,
                        )
                    ),
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 66.dp, end = 14.dp, bottom = 12.dp)) {
                    LinearProgressIndicator(
                        progress = { updateProgress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = updateTint,
                        trackColor = DyInput,
                    )
                }
            }
            if (updateRelease != null) {
                HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
                ActionRow(
                    icon = Icons.Filled.Description,
                    tint = DyCyan,
                    title = stringResource(SharedR.string.settings_view_release_title),
                    description = stringResource(SharedR.string.settings_view_release_description),
                    onClick = { openReleasePage(context, updateRelease.htmlUrl) },
                )
            }
        }

        SettingsSection(title = stringResource(SharedR.string.settings_about_title)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DyIconTile(Icons.Filled.Settings, tint = DyRed, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text("ClipCove", style = MaterialTheme.typography.titleLarge, color = DyText)
                    Text(
                        stringResource(
                            SharedR.string.settings_version_line,
                            viewModel.versionName,
                            viewModel.versionCode,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = DyTextSecondary,
                    )
                }
                DyPill(stringResource(SharedR.string.settings_on_device_badge), DyGreen)
            }
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            Text(
                stringResource(SharedR.string.settings_about_description),
                style = MaterialTheme.typography.bodySmall,
                color = DyTextSecondary,
                modifier = Modifier.padding(14.dp),
                lineHeight = 19.sp,
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.Description,
                tint = DyCyan,
                title = stringResource(SharedR.string.settings_changelog_title),
                description = stringResource(SharedR.string.settings_changelog_description),
                onClick = { showChangelog = true },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = DyTextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                stringResource(SharedR.string.settings_all_local),
                style = MaterialTheme.typography.bodySmall,
                color = DyTextSecondary,
            )
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            containerColor = DyCard,
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = DyRed) },
            title = {
                Text(
                    stringResource(SharedR.string.settings_clear_all_dialog_title),
                    color = DyText,
                )
            },
            text = {
                Text(
                    stringResource(SharedR.string.settings_clear_all_dialog_description),
                    color = DyTextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllConfirm = false
                        viewModel.clearAllLocalData()
                    },
                ) {
                    Text(stringResource(SharedR.string.settings_clear_all_confirm), color = DyRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(SharedR.string.action_cancel), color = DyTextSecondary)
                }
            },
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = DyCard,
            icon = { Icon(Icons.Filled.Language, contentDescription = null, tint = DyCyan) },
            title = {
                Text(
                    stringResource(SharedR.string.language_dialog_title),
                    color = DyText,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppLanguage.entries.forEach { option ->
                        val selected = option == appLanguage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .dyPressable(pressedScale = 0.985f) {
                                    viewModel.setAppLanguage(option)
                                    showLanguageDialog = false
                                    (context as? Activity)?.recreate()
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    viewModel.setAppLanguage(option)
                                    showLanguageDialog = false
                                    (context as? Activity)?.recreate()
                                },
                            )
                            Text(languageLabel(option), color = DyText)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(SharedR.string.action_close), color = DyRed)
                }
            },
        )
    }
    if (showChangelog) {
        val languageCode = context.resources.configuration.locales[0].language
        val changelogAsset = if (languageCode == "zh") "changelog_zh.md" else "changelog.md"
        val loadFailed = stringResource(SharedR.string.settings_changelog_load_failed)
        val changelog = remember(changelogAsset, loadFailed) {
            try {
                context.assets.open(changelogAsset).bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                loadFailed
            }
        }
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            containerColor = DyCard,
            title = {
                Text(
                    stringResource(SharedR.string.settings_changelog_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = DyText,
                )
            },
            text = {
                Text(
                    changelog,
                    style = MaterialTheme.typography.bodySmall,
                    color = DyTextSecondary,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) {
                    Text(stringResource(SharedR.string.action_close), color = DyRed)
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DySectionHeader(title = title)
        DyPanel(contentPadding = 0.dp, content = content)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DyIconTile(icon = icon, tint = tint, size = 42.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = DyText)
            Spacer(Modifier.height(3.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = DyTextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dyPressable(pressedScale = 0.99f, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DyIconTile(icon = icon, tint = tint, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = DyText)
            Text(description, style = MaterialTheme.typography.bodySmall, color = DyTextSecondary)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DyTextSecondary)
    }
}

private fun UpdateUiState.pendingRelease(): AppRelease? = when (this) {
    is UpdateUiState.Available -> release
    is UpdateUiState.Downloading -> release
    is UpdateUiState.PermissionRequired -> release
    is UpdateUiState.InstallReady -> release
    is UpdateUiState.Failed -> release
    else -> null
}

private fun AppRelease.hasInstallableAssets(): Boolean =
    assets.any { it.name.endsWith(".apk", ignoreCase = true) } &&
        assets.any { it.name.endsWith(".sha256", ignoreCase = true) }

private fun AppRelease.apkSizeBytes(): Long =
    assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.sizeBytes ?: 0L

@Composable
private fun updateActionTitle(state: UpdateUiState): String = when (state) {
    UpdateUiState.Idle -> stringResource(SharedR.string.update_title_idle)
    UpdateUiState.Checking -> stringResource(SharedR.string.update_title_checking)
    UpdateUiState.UpToDate -> stringResource(SharedR.string.update_title_current)
    is UpdateUiState.Available -> stringResource(
        SharedR.string.update_title_available,
        state.release.tagName,
    )
    is UpdateUiState.Downloading -> if (state.progress > 0) {
        stringResource(SharedR.string.update_title_downloading_progress, state.progress)
    } else {
        stringResource(SharedR.string.update_title_downloading_probe)
    }
    is UpdateUiState.PermissionRequired -> stringResource(SharedR.string.update_title_permission)
    is UpdateUiState.InstallReady -> stringResource(SharedR.string.update_title_installer)
    is UpdateUiState.Failed -> if (state.release != null) {
        stringResource(SharedR.string.update_title_failed_retry)
    } else {
        stringResource(SharedR.string.update_title_failed_recheck)
    }
}

@Composable
private fun updateActionDescription(state: UpdateUiState, autoUpdateMirrors: Boolean): String = when (state) {
    UpdateUiState.Idle -> stringResource(SharedR.string.update_description_idle)
    UpdateUiState.Checking -> stringResource(SharedR.string.update_description_checking)
    UpdateUiState.UpToDate -> stringResource(SharedR.string.update_description_current)
    is UpdateUiState.Available -> {
        val size = state.release.apkSizeBytes()
        val sizeHint = if (size > 0) {
            stringResource(SharedR.string.update_available_size, formatUpdateSize(size))
        } else {
            ""
        }
        val routeHint = if (autoUpdateMirrors) {
            stringResource(SharedR.string.update_available_mirror_hint)
        } else {
            ""
        }
        stringResource(SharedR.string.update_description_available, sizeHint, routeHint)
    }
    is UpdateUiState.Downloading -> if (state.progress > 0) {
        stringResource(SharedR.string.update_description_downloading)
    } else {
        stringResource(SharedR.string.update_description_probing)
    }
    is UpdateUiState.PermissionRequired -> stringResource(SharedR.string.update_description_permission)
    is UpdateUiState.InstallReady -> stringResource(SharedR.string.update_description_installer)
    is UpdateUiState.Failed -> if (state.release != null) {
        stringResource(SharedR.string.update_description_failed_retry)
    } else {
        stringResource(SharedR.string.update_description_failed_recheck)
    }
}

private fun formatUpdateSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun openReleasePage(context: Context, url: String) {
    val allowedPrefix = "https://github.com/DCRimbaud/clipcove-android/releases/"
    val safeUrl = url.takeIf { it.startsWith(allowedPrefix) }
        ?: "https://github.com/DCRimbaud/clipcove-android/releases"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)))
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(SharedR.string.settings_open_browser_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

/** Export backup: MediaStore on Android 10+, FileProvider on older releases. */
private fun exportBackup(context: Context, viewModel: SettingsViewModel) {
    try {
        val filename = "clipcove_backup_${System.currentTimeMillis()}.json"
        exportTextFile(
            context = context,
            content = viewModel.exportData(),
            filename = filename,
            mimeType = "application/json",
            chooserTitle = context.getString(SharedR.string.settings_share_backup),
        )
        Toast.makeText(
            context,
            context.getString(SharedR.string.settings_backup_exported, filename),
            Toast.LENGTH_LONG,
        ).show()
    } catch (error: Exception) {
        val message = if (error is SecurityException) {
            SharedR.string.settings_storage_permission_export
        } else {
            SharedR.string.settings_export_failed
        }
        Toast.makeText(context, context.getString(message), Toast.LENGTH_LONG).show()
    }
}

private fun exportDiagnostics(context: Context, viewModel: SettingsViewModel) {
    try {
        val filename = "clipcove_diagnostics_${System.currentTimeMillis()}.txt"
        exportTextFile(
            context = context,
            content = viewModel.exportDiagnostics(),
            filename = filename,
            mimeType = "text/plain",
            chooserTitle = context.getString(SharedR.string.settings_share_diagnostics),
        )
        Toast.makeText(
            context,
            context.getString(SharedR.string.settings_diagnostics_exported, filename),
            Toast.LENGTH_LONG,
        ).show()
    } catch (error: Exception) {
        val message = if (error is SecurityException) {
            SharedR.string.settings_storage_permission_export
        } else {
            SharedR.string.settings_export_failed
        }
        Toast.makeText(context, context.getString(message), Toast.LENGTH_LONG).show()
    }
}

@Suppress("DEPRECATION")
private fun exportTextFile(
    context: Context,
    content: String,
    filename: String,
    mimeType: String,
    chooserTitle: String,
) {
    val uri = if (Build.VERSION.SDK_INT >= 29) {
        val values = android.content.ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val contentUri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: throw IllegalStateException()
        context.contentResolver.openOutputStream(contentUri)?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException()
        contentUri
    } else {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            (context as? android.app.Activity)?.requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_STORAGE,
            )
            throw SecurityException()
        }
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            filename,
        )
        file.writeText(content, Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val share = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, chooserTitle))
}

private const val REQUEST_WRITE_STORAGE = 202





/** Returns the localized label for an app-language choice. */
@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(SharedR.string.language_system)
    AppLanguage.ENGLISH -> stringResource(SharedR.string.language_english)
    AppLanguage.CHINESE -> stringResource(SharedR.string.language_chinese)
}