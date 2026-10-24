package com.douyin.feature.downloads

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.douyin.core.designsystem.DyBorder
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyCyan
import com.douyin.core.designsystem.DyEmptyState
import com.douyin.core.designsystem.DyGreen
import com.douyin.core.designsystem.DyIconTile
import com.douyin.core.designsystem.DyInput
import com.douyin.core.designsystem.DyPageHeader
import com.douyin.core.designsystem.DyPanel
import com.douyin.core.designsystem.DyPill
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.data.download.DownloadRepository
import com.douyin.data.download.DownloadStatus
import com.douyin.data.download.DownloadTask
import java.io.File

@Composable
fun DownloadsScreen(repository: DownloadRepository) {
    val tasks by repository.tasks.collectAsStateWithLifecycle()
    val activeCount = tasks.count {
        it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.QUEUED ||
            it.status == DownloadStatus.PAUSED
    }
    val completedCount = tasks.count { it.status == DownloadStatus.DONE }

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 14.dp),
    ) {
        DyPageHeader(
            title = "下载管理",
            subtitle = "文件只保存在本机，可暂停、续传和另存。",
            eyebrow = "DOWNLOADS",
            trailing = { DyPill(text = "${tasks.size} 项", color = DyCyan) },
        )
        Spacer(Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            DyEmptyState(
                icon = Icons.Filled.Download,
                title = "还没有下载任务",
                description = "解析作品后点击下载，视频、图片和原声会集中显示在这里。",
            )
        } else {
            DyPanel(contentPadding = 14.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryMetric("进行中", activeCount.toString(), DyCyan, Modifier.weight(1f))
                    SummaryMetric("已完成", completedCount.toString(), DyGreen, Modifier.weight(1f))
                    SummaryMetric("全部", tasks.size.toString(), DyText, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadCard(task = task, repository = repository)
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, color = DyTextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DownloadCard(task: DownloadTask, repository: DownloadRepository) {
    val context = LocalContext.current
    val statusColor = task.status.statusColor()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DyCard,
        border = BorderStroke(1.dp, DyBorder.copy(alpha = 0.68f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                DyIconTile(icon = Icons.Filled.Download, tint = statusColor, size = 42.dp)
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, color = DyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(task.filename, style = MaterialTheme.typography.bodySmall, color = DyTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DyPill(task.status.label(), statusColor)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                color = statusColor,
                trackColor = DyInput,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(downloadDetail(task), style = MaterialTheme.typography.bodySmall, color = DyTextSecondary, modifier = Modifier.weight(1f))
                if (task.totalBytes > 0) {
                    Text(formatSize(task.totalBytes), style = MaterialTheme.typography.bodySmall, color = DyTextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            DownloadActions(task = task, repository = repository, context = context)
        }
    }
}

@Composable
private fun DownloadActions(task: DownloadTask, repository: DownloadRepository, context: Context) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        when (task.status) {
            DownloadStatus.DOWNLOADING -> {
                TaskAction("暂停", DyCyan, Modifier.weight(1f)) { repository.pause(task) }
                TaskAction("取消", DyRed, Modifier.weight(1f)) { repository.remove(task) }
            }
            DownloadStatus.PAUSED -> {
                TaskAction("继续", DyGreen, Modifier.weight(1f)) { repository.resume(task) }
                TaskAction("删除", DyRed, Modifier.weight(1f)) { repository.remove(task) }
            }
            DownloadStatus.FAILED -> {
                TaskAction("重试", DyCyan, Modifier.weight(1f)) { repository.resume(task) }
                TaskAction("删除", DyRed, Modifier.weight(1f)) { repository.remove(task) }
            }
            DownloadStatus.QUEUED -> {
                TaskAction("取消", DyRed, Modifier.weight(1f)) { repository.remove(task) }
            }
            DownloadStatus.DONE -> {
                TaskAction("打开", DyText, Modifier.weight(1f)) { openDownloaded(context, task) }
                TaskAction("分享", DyText, Modifier.weight(1f)) { shareDownloaded(context, task) }
                TaskAction("另存", DyText, Modifier.weight(1f)) { saveToDownloads(context, task) }
                TaskAction("删除", DyRed, Modifier.weight(1f)) { repository.remove(task) }
            }
        }
    }
}

@Composable
private fun TaskAction(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun downloadDetail(task: DownloadTask): String = when (task.status) {
    DownloadStatus.QUEUED -> "等待下载"
    DownloadStatus.DOWNLOADING -> "${task.progress}% · ${formatSpeed(task.speedBps)}"
    DownloadStatus.PAUSED -> "${task.progress}% · 支持断点续传"
    DownloadStatus.DONE -> "已完成"
    DownloadStatus.FAILED -> "下载失败，可点击重试"
}

private fun DownloadStatus.label(): String = when (this) {
    DownloadStatus.QUEUED -> "排队中"
    DownloadStatus.DOWNLOADING -> "下载中"
    DownloadStatus.PAUSED -> "已暂停"
    DownloadStatus.DONE -> "已完成"
    DownloadStatus.FAILED -> "失败"
}

private fun DownloadStatus.statusColor(): Color = when (this) {
    DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.DOWNLOADING -> DyCyan
    DownloadStatus.DONE -> DyGreen
    DownloadStatus.FAILED -> DyRed
}

private fun openDownloaded(context: Context, task: DownloadTask) {
    try {
        val uri = queryDownloadUri(context, task.filename) ?: throw RuntimeException("文件不存在，请先另存")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, guessMime(task.filename))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开"))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开：${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareDownloaded(context: Context, task: DownloadTask) {
    try {
        val uri = queryDownloadUri(context, task.filename) ?: throw RuntimeException("文件不存在，请先另存")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = guessMime(task.filename)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享"))
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Suppress("DEPRECATION")
private fun saveToDownloads(context: Context, task: DownloadTask) {
    if (Build.VERSION.SDK_INT < 29 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        (context as? android.app.Activity)?.requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_WRITE_STORAGE,
        )
        Toast.makeText(context, "请允许存储权限后再次点击另存", Toast.LENGTH_LONG).show()
        return
    }
    Thread {
        try {
            val src = File(task.filePath)
            if (!src.exists()) throw RuntimeException("文件不存在")
            if (Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, task.filename)
                    put(MediaStore.Downloads.MIME_TYPE, guessMime(task.filename))
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw RuntimeException("保存失败")
                val out = context.contentResolver.openOutputStream(uri) ?: throw RuntimeException("写入失败")
                out.use { output -> src.inputStream().use { it.copyTo(output) } }
            } else {
                val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), task.filename)
                src.copyTo(dest, overwrite = true)
            }
            runOnUiThread(context) { Toast.makeText(context, "已保存到手机「下载」目录", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) {
            runOnUiThread(context) { Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_LONG).show() }
        }
    }.start()
}

private fun runOnUiThread(context: Context, block: () -> Unit) {
    (context as? android.app.Activity)?.runOnUiThread { block() } ?: block()
}

private fun queryDownloadUri(context: Context, filename: String): Uri? {
    if (Build.VERSION.SDK_INT < 29) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    return queryMediaStoreDownload(context, filename)
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun queryMediaStoreDownload(context: Context, filename: String): Uri? {
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
    context.contentResolver.query(collection, null, selection, arrayOf(filename), "date_added DESC")?.use { cursor ->
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
            return Uri.withAppendedPath(collection, id.toString())
        }
    }
    return null
}

private fun guessMime(filename: String): String = when (filename.substringAfterLast('.', "mp4")) {
    "mp3" -> "audio/mpeg"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    else -> "video/mp4"
}

private const val REQUEST_WRITE_STORAGE = 201

private fun formatSpeed(bps: Long): String {
    if (bps <= 0) return "--"
    return if (bps >= 1024 * 1024) {
        "%.1f MB/s".format(bps / 1024.0 / 1024.0)
    } else if (bps >= 1024) {
        "%.0f KB/s".format(bps / 1024.0)
    } else {
        "$bps B/s"
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "--"
    return if (bytes >= 1024L * 1024L * 1024L) {
        "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    } else if (bytes >= 1024L * 1024L) {
        "%.1f MB".format(bytes / 1024.0 / 1024.0)
    } else if (bytes >= 1024L) {
        "%.0f KB".format(bytes / 1024.0)
    } else {
        "$bytes B"
    }
}
