package com.douyin.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyInput
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val autoSave by viewModel.autoSave.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val wifiRunning by viewModel.wifiRunning.collectAsStateWithLifecycle()
    val wifiUrl by viewModel.wifiUrl.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showChangelog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("设置", fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = DyText)
        Spacer(Modifier.height(16.dp))

        // 自动保存开关
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动保存到「下载」目录", fontSize = 15.sp, color = DyText)
                    Text("下载完成后自动存入手机公共下载目录", fontSize = 12.sp, color = DyTextSecondary)
                }
                Switch(
                    checked = autoSave,
                    onCheckedChange = viewModel::setAutoSave,
                    colors = SwitchDefaults.colors(checkedTrackColor = DyRed)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // WiFi 传文件
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WiFi 传文件到电脑", fontSize = 15.sp, color = DyText)
                    Text(
                        if (wifiRunning) "电脑浏览器打开:${wifiUrl}\n(需在同一 WiFi)"
                        else "开启后,电脑浏览器可下载手机里的文件",
                        fontSize = 12.sp, color = DyTextSecondary, lineHeight = 18.sp
                    )
                }
                Button(
                    onClick = viewModel::toggleWifi,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (wifiRunning) DyCard else DyRed,
                        contentColor = DyText
                    ),
                    border = if (wifiRunning) androidx.compose.foundation.BorderStroke(1.dp, DyRed) else null
                ) {
                    Text(if (wifiRunning) "停止" else "开启", fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 数据管理
        SettingCard {
            Text("数据管理", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = DyText)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { exportBackup(context, viewModel) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DyInput)
            ) {
                Text("导出备份(历史+收藏)", fontSize = 14.sp, color = DyText)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::clearHistory,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DyInput)
            ) {
                Text("清空历史记录", fontSize = 14.sp, color = DyText)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::clearDownloads,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DyInput)
            ) {
                Text("清空下载列表", fontSize = 14.sp, color = DyText)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 关于
        SettingCard {
            Text("关于", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = DyText)
            Spacer(Modifier.height(6.dp))
            Text(
                "拾刻 v${viewModel.versionName}\n" +
                    "构建号 ${viewModel.versionCode}\n" +
                    "模块化架构:app / core / data / feature + Koin DI\n\n" +
                    "· 支持平台:抖音 / 小红书\n" +
                    "· 分享口令 / 短链 / 链接直接解析\n" +
                    "· 无水印视频 / 音乐 / 图集 / 动图下载\n" +
                    "· 动图+音乐合成\n" +
                    "· 内置下载管理器 / WiFi 传文件\n\n" +
                    "仅个人学习使用,注意版权合规",
                fontSize = 13.sp, color = DyTextSecondary, lineHeight = 20.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showChangelog = true },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DyInput)
            ) {
                Text("查看更新日志", fontSize = 14.sp, color = DyText)
            }
        }
    }

    // 更新日志对话框
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
            title = { Text("更新日志", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = DyText) },
            text = {
                Text(
                    changelog,
                    fontSize = 12.sp,
                    color = DyTextSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) {
                    Text("关闭", color = DyRed)
                }
            }
        )
    }
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = DyCard) {
        Column(Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}

/** 导出备份:生成 JSON 文件存公共下载目录 + 分享 */
private fun exportBackup(context: android.content.Context, viewModel: SettingsViewModel) {
    try {
        val json = viewModel.exportData()
        val filename = "shike_backup_${System.currentTimeMillis()}.json"
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw RuntimeException("insert failed")
            val out = context.contentResolver.openOutputStream(uri)
                ?: throw RuntimeException("写入失败")
            out.use { o -> o.write(json.toByteArray()) }
            android.widget.Toast.makeText(context, "备份已导出:$filename", android.widget.Toast.LENGTH_LONG).show()
            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(share, "分享备份"))
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "导出失败:${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

