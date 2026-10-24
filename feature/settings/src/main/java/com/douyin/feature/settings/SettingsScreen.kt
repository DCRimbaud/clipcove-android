package com.douyin.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
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
import java.io.File

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val autoSave by viewModel.autoSave.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val wifiRunning by viewModel.wifiRunning.collectAsStateWithLifecycle()
    val wifiUrl by viewModel.wifiUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showChangelog by remember { mutableStateOf(false) }

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
                description = "导出历史与收藏为 JSON 文件",
                onClick = { exportBackup(context, viewModel) },
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
        }

        SettingsSection(title = "关于拾刻") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DyIconTile(Icons.Filled.Settings, tint = DyRed, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text("拾刻", style = MaterialTheme.typography.titleLarge, color = DyText)
                    Text(
                        "v${viewModel.versionName} · build ${viewModel.versionCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DyTextSecondary,
                    )
                }
                DyPill("无水印", DyGreen)
            }
            HorizontalDivider(color = DyBorder.copy(alpha = 0.6f))
            Text(
                "支持抖音与小红书的分享链接解析、视频 / 图集 / 原声下载、本地收藏和局域网传输。仅用于个人学习，请尊重版权与平台规则。",
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
                description = "了解每个版本的变化",
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

/** 导出备份：Android 10+ 使用 MediaStore，旧系统使用公共下载目录 + FileProvider。 */
@Suppress("DEPRECATION")
private fun exportBackup(context: Context, viewModel: SettingsViewModel) {
    try {
        val json = viewModel.exportData()
        val filename = "shike_backup_${System.currentTimeMillis()}.json"
        val uri = if (Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val contentUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw RuntimeException("无法创建备份文件")
            context.contentResolver.openOutputStream(contentUri)?.use { it.write(json.toByteArray()) }
                ?: throw RuntimeException("写入失败")
            contentUri
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                (context as? android.app.Activity)?.requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_STORAGE,
                )
                Toast.makeText(context, "请允许存储权限后再次导出", Toast.LENGTH_LONG).show()
                return
            }
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
            file.writeText(json)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        Toast.makeText(context, "备份已导出：$filename", Toast.LENGTH_LONG).show()
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "分享备份"))
    } catch (e: Exception) {
        Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
    }
}

private const val REQUEST_WRITE_STORAGE = 202
