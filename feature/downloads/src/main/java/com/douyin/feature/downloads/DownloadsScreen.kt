package com.douyin.feature.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyCyan
import com.douyin.core.designsystem.DyGreen
import com.douyin.core.designsystem.DyInput
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.data.download.DownloadRepository
import com.douyin.data.download.DownloadStatus
import com.douyin.data.download.DownloadTask
import java.io.File

/** 下载管理页:订阅 DownloadRepository 任务流 */
@Composable
fun DownloadsScreen(repository: DownloadRepository) {
    val tasks by repository.tasks.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("下载管理", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DyText)
        Spacer(Modifier.height(12.dp))
        if (tasks.isEmpty()) {
            Text(
                "暂无下载任务",
                fontSize = 14.sp, color = DyTextSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tasks, key = { it.id }) { task ->
                    DownloadRow(task, repository)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(task: DownloadTask, repository: DownloadRepository) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(14.dp), color = DyCard) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(task.title, fontSize = 14.sp, color = DyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { task.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = DyRed,
                trackColor = DyInput
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (task.status) {
                        DownloadStatus.QUEUED -> "等待中..."
                        DownloadStatus.DOWNLOADING -> "下载中 ${task.progress}% · ${formatSpeed(task.speedBps)} · ${formatSize(task.totalBytes)}"
                        DownloadStatus.PAUSED -> "已暂停 ${task.progress}%(支持断点续传)"
                        DownloadStatus.DONE -> "已完成 ✓ ${formatSize(task.totalBytes)}"
                        DownloadStatus.FAILED -> "失败(点击重试)"
                    },
                    fontSize = 12.sp,
                    color = if (task.status == DownloadStatus.DONE) DyGreen else DyTextSecondary,
                    modifier = Modifier.weight(1f)
                )
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        Text("暂停", fontSize = 12.sp, color = DyCyan,
                            modifier = Modifier.padding(start = 10.dp).clickable { repository.pause(task) })
                        Text("取消", fontSize = 12.sp, color = DyRed,
                            modifier = Modifier.padding(start = 10.dp).clickable { repository.remove(task) })
                    }
                    DownloadStatus.PAUSED -> {
                        Text("继续", fontSize = 12.sp, color = DyCyan,
                            modifier = Modifier.padding(start = 10.dp).clickable { repository.resume(task) })
                        Text("取消", fontSize = 12.sp, color = DyRed,
                            modifier = Modifier.padding(start = 10.dp).clickable { repository.remove(task) })
                    }
                    DownloadStatus.FAILED -> {
                        Text("重试", fontSize = 12.sp, color = DyCyan,
                            modifier = Modifier.padding(start = 10.dp).clickable { repository.resume(task) })
                        Text("删除", fontSize = 12.sp, color = DyRed,
                            modifier = Modifier.padding(start = 10.dp).clickable { repository.remove(task) })
                    }
                    DownloadStatus.QUEUED -> {
                        Text("取消", fontSize = 12.sp, color = DyRed,
                            modifier = Modifier.padding(start = 10.dp).clickable { repository.remove(task) })
                    }
                    DownloadStatus.DONE -> {
                        Text("打开", fontSize = 12.sp, color = DyCyan,
                            modifier = Modifier.padding(start = 10.dp).clickable { openDownloaded(context, task) })
                        Text("分享", fontSize = 12.sp, color = DyCyan,
                            modifier = Modifier.padding(start = 10.dp).clickable { shareDownloaded(context, task) })
                        Text("另存", fontSize = 12.sp, color = DyCyan,
                            modifier = Modifier.padding(start = 10.dp).clickable { saveToDownloads(context, task) })
                        Text("删除", fontSize = 12.sp, color = DyCyan,
                            modifier = Modifier.padding(start = 10.dp).clickable { repository.remove(task) })
                    }
                }
            }
        }
    }
}

private fun openDownloaded(context: Context, task: DownloadTask) {
    try {
        val uri = queryDownloadUri(context, task.filename)
            ?: throw RuntimeException("文件不存在,请先另存")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, guessMime(task.filename))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开"))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开:${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareDownloaded(context: Context, task: DownloadTask) {
    try {
        val uri = queryDownloadUri(context, task.filename)
            ?: throw RuntimeException("文件不存在,请先另存")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = guessMime(task.filename)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享"))
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败:${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Suppress("DEPRECATION")
private fun saveToDownloads(context: Context, task: DownloadTask) {
    // 文件 IO 放后台线程,避免主线程复制大文件 ANR
    Thread {
        try {
            val src = File(task.filePath)
            if (!src.exists()) throw RuntimeException("文件不存在")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, task.filename)
                    put(MediaStore.Downloads.MIME_TYPE, guessMime(task.filename))
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw RuntimeException("保存失败")
                val out = context.contentResolver.openOutputStream(uri)
                    ?: throw RuntimeException("写入失败")
                out.use { o ->
                    src.inputStream().use { it.copyTo(o) }
                }
            } else {
                val dest = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    task.filename
                )
                src.copyTo(dest, overwrite = true)
            }
            runOnUiThread(context) { Toast.makeText(context, "已保存到手机「下载」目录", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) {
            runOnUiThread(context) { Toast.makeText(context, "保存失败:${e.message}", Toast.LENGTH_LONG).show() }
        }
    }.start()
}

private fun runOnUiThread(context: Context, block: () -> Unit) {
    (context as? android.app.Activity)?.runOnUiThread { block() } ?: block()
}

private fun queryDownloadUri(context: Context, filename: String): Uri? {
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
    context.contentResolver.query(collection, null, selection, arrayOf(filename), null)?.use { c ->
        if (c.moveToFirst()) {
            val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
            return Uri.withAppendedPath(collection, id.toString())
        }
    }
    return null
}

private fun guessMime(filename: String): String =
    when (filename.substringAfterLast('.', "mp4")) {
        "mp3" -> "audio/mpeg"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "video/mp4"
    }

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
