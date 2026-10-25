package com.douyin.feature.settings

import android.Manifest
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.douyin.core.designsystem.DyPageHeader
import com.douyin.core.designsystem.DyPanel
import com.douyin.core.designsystem.DyPill
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DySectionHeader
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.data.update.AppRelease
import java.io.File

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val autoSave by viewModel.autoSave.collectAsStateWithLifecycle()
    val autoParseSharedLinks by viewModel.autoParseSharedLinks.collectAsStateWithLifecycle()
    val autoUpdateMirrors by viewModel.autoUpdateMirrors.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val wifiRunning by viewModel.wifiRunning.collectAsStateWithLifecycle()
    val wifiUrl by viewModel.wifiUrl.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val updateRelease = updateState.pendingRelease()
    val context = LocalContext.current
    var showChangelog by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
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
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
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
            title = "设置",
            subtitle = "管理保存、传输和本地数据。",
            eyebrow = "PREFERENCES",
            trailing = { DyPill("v${viewModel.versionName}", DyCyan) },
        )

        SettingsSection(title = "常规") {
            SettingRow(
                icon = Icons.Filled.Download,
                tint = DyGreen,
                title = "自动保存到下载目录",
                description = "下载完成后同步保存到手机公共「下载」目录。",
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
                title = "分享链接自动解析",
                description = "从其他 App 分享链接到 ClipCove 后立即开始解析。",
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
                title = "更新加速线路",
                description = "GitHub 直连失败时自动尝试公共加速线路；安装包仍会校验签名和 SHA-256。",
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

        SettingsSection(title = "局域网传文件") {
            SettingRow(
                icon = Icons.Filled.Wifi,
                tint = DyCyan,
                title = "WiFi 传文件到电脑",
                description = if (wifiRunning) "保持同一 WiFi，在电脑浏览器访问下方地址。" else "开启后，电脑可直接浏览并下载手机内文件。",
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
                        Text(if (wifiRunning) "停止" else "开启", fontSize = 13.sp)
                    }
                },
            )
            if (wifiRunning && wifiUrl.isNotBlank()) {
                HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("访问地址", style = MaterialTheme.typography.bodySmall, color = DyTextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(wifiUrl, style = MaterialTheme.typography.labelLarge, color = DyCyan, maxLines = 1)
                }
            }
        }

        SettingsSection(title = "数据管理") {
            ActionRow(
                icon = Icons.Filled.Save,
                tint = DyCyan,
                title = "导出备份",
                description = "包含历史、收藏和原始链接的 JSON 文件",
                onClick = { exportBackup(context, viewModel) },
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.UploadFile,
                tint = DyGreen,
                title = "从备份还原",
                description = "支持当前版本和旧版备份，重复条目会自动合并",
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
                title = "清空历史记录",
                description = "不会删除已经下载的文件",
                onClick = viewModel::clearHistory,
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.Delete,
                tint = DyRed,
                title = "清空下载列表",
                description = "同时清理应用内的下载文件",
                onClick = viewModel::clearDownloads,
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.DeleteForever,
                tint = DyRed,
                title = "清空全部本地数据",
                description = "删除历史、收藏、下载记录和诊断信息，需再次确认",
                onClick = { showClearAllConfirm = true },
            )
        }

        SettingsSection(title = "诊断与更新") {
            ActionRow(
                icon = Icons.Filled.BugReport,
                tint = DyAmber,
                title = "导出诊断报告",
                description = "仅包含最多 50 条脱敏解析失败记录，不含分享链接",
                onClick = { exportDiagnostics(context, viewModel) },
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.Delete,
                tint = DyTextSecondary,
                title = "清空诊断记录",
                description = "不会影响历史、收藏或已下载文件",
                onClick = viewModel::clearDiagnostics,
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.SystemUpdate,
                tint = DyCyan,
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
            if (updateRelease != null) {
                HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
                ActionRow(
                    icon = Icons.Filled.Description,
                    tint = DyCyan,
                    title = "查看发布说明",
                    description = "在 GitHub 查看更新内容或手动下载",
                    onClick = { openReleasePage(context, updateRelease.htmlUrl) },
                )
            }
        }

        SettingsSection(title = "关于 ClipCove") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DyIconTile(Icons.Filled.Settings, tint = DyRed, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text("ClipCove", style = MaterialTheme.typography.titleLarge, color = DyText)
                    Text(
                        "v${viewModel.versionName} · build ${viewModel.versionCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DyTextSecondary,
                    )
                }
                DyPill("本地处理", DyGreen)
            }
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            Text(
                "支持抖音与小红书的链接解析、原片优先、画质来源提示、图集 / 原声下载、本地收藏和局域网传输。仅用于个人学习，请尊重版权与平台规则。",
                style = MaterialTheme.typography.bodySmall,
                color = DyTextSecondary,
                modifier = Modifier.padding(14.dp),
                lineHeight = 19.sp,
            )
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            ActionRow(
                icon = Icons.Filled.Description,
                tint = DyCyan,
                title = "查看更新日志",
                description = "只看当前版本的简要变化",
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
            Text("所有解析都在本机完成", style = MaterialTheme.typography.bodySmall, color = DyTextSecondary)
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            containerColor = DyCard,
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = DyRed) },
            title = { Text("清空全部本地数据？", color = DyText) },
            text = {
                Text(
                    "这会删除解析历史、收藏、应用内下载任务与文件、以及诊断记录。已经保存到系统「下载」目录的文件不会被删除。此操作无法撤销。",
                    color = DyTextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllConfirm = false
                        viewModel.clearAllLocalData()
                    },
                ) { Text("确认清空", color = DyRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("取消", color = DyTextSecondary)
                }
            },
        )
    }

    if (showChangelog) {
        val changelog = remember {
            try {
                context.assets.open("changelog.md").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                "更新日志加载失败"
            }
        }
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            containerColor = DyCard,
            title = { Text("更新日志", style = MaterialTheme.typography.titleLarge, color = DyText) },
            text = {
                Text(
                    changelog,
                    style = MaterialTheme.typography.bodySmall,
                    color = DyTextSecondary,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) { Text("关闭", color = DyRed) }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
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

private fun updateActionTitle(state: UpdateUiState): String = when (state) {
    UpdateUiState.Idle -> "检查更新"
    UpdateUiState.Checking -> "正在检查更新"
    UpdateUiState.UpToDate -> "已是最新版本"
    is UpdateUiState.Available -> "发现新版本 ${state.release.tagName}"
    is UpdateUiState.Downloading -> "正在下载更新 ${state.progress}%"
    is UpdateUiState.PermissionRequired -> "需要安装权限"
    is UpdateUiState.InstallReady -> "安装器已打开"
    is UpdateUiState.Failed -> if (state.release != null) "更新失败，点按重试" else "重新检查更新"
}

private fun updateActionDescription(state: UpdateUiState, autoUpdateMirrors: Boolean): String = when (state) {
    UpdateUiState.Idle -> "手动查询 GitHub Releases，不会在后台自动访问"
    UpdateUiState.Checking -> "正在连接 GitHub，请稍候…"
    UpdateUiState.UpToDate -> "当前版本已是 GitHub 上可用的最新版本"
    is UpdateUiState.Available -> {
        val size = state.release.apkSizeBytes()
        val sizeHint = if (size > 0) "（约 ${formatUpdateSize(size)}）" else ""
        val routeHint = if (autoUpdateMirrors) "GitHub 直连失败时会自动切换加速线路。" else ""
        "点按自动下载并校验 APK$sizeHint，随后打开系统安装器。建议连接 WiFi。$routeHint"
    }
    is UpdateUiState.Downloading -> "正在应用内下载更新，请保持网络连接。"
    is UpdateUiState.PermissionRequired -> "请在系统页面允许 ClipCove 安装未知应用，然后返回本页再次点按。"
    is UpdateUiState.InstallReady -> "请按系统安装页提示完成升级；页面关闭后可再次点按。"
    is UpdateUiState.Failed -> {
        val retry = if (state.release != null) "点按重试。" else "点按重新检查。"
        "${state.message.take(120)}。$retry"
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
        Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
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
            chooserTitle = "分享备份",
        )
        Toast.makeText(context, "备份已导出：$filename", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
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
            chooserTitle = "分享诊断报告",
        )
        Toast.makeText(context, "诊断报告已导出：$filename", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
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
        ) ?: throw RuntimeException("无法创建导出文件")
        context.contentResolver.openOutputStream(contentUri)?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw RuntimeException("写入导出文件失败")
        contentUri
    } else {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            (context as? android.app.Activity)?.requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_STORAGE,
            )
            throw RuntimeException("请允许存储权限后再次导出")
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
