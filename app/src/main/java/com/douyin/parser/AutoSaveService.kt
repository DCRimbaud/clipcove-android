package com.douyin.parser

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.douyin.data.download.DownloadRepository
import com.douyin.data.download.DownloadStatus
import com.douyin.data.download.DownloadTask
import com.douyin.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 自动另存服务:订阅下载任务流,完成后若开启「自动保存到下载」,
 * 将文件保存到公共 Downloads 目录(MediaStore,无需权限)。
 *
 * 线程安全:已保存任务用 ConcurrentHashMap 去重,防止任务流重复触发产生重复文件;
 * 阻塞 IO 在独立 Job 中执行,不被 collect 重启打断。
 */
class AutoSaveService(
    private val context: Context,
    private val downloadRepo: DownloadRepository,
    private val settingsRepo: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val saved = ConcurrentHashMap.newKeySet<Long>()
    /** 失败重试次数上限(防止永久性失败无限重试) */
    private val failCount = ConcurrentHashMap<Long, Int>()
    private var collectJob: Job? = null
    @Volatile
    private var autoSaveEnabled = true
    private val maxRetries = 3

    fun start() {
        if (collectJob != null) return
        collectJob = scope.launch {
            settingsRepo.autoSaveToDownloads.collectLatest { enabled ->
                autoSaveEnabled = enabled
            }
        }
        scope.launch {
            downloadRepo.tasks.collectLatest { tasks ->
                val toSave = tasks.filter {
                    it.status == DownloadStatus.DONE && !it.savedToPublic && saved.add(it.id)
                }
                toSave.forEach { task ->
                    // 阻塞 IO 在独立 Job,不阻塞 collect 流
                    scope.launch {
                        try {
                            if (!autoSaveEnabled) {
                                saved.remove(task.id)
                                return@launch
                            }
                            saveToPublic(task)
                            task.savedToPublic = true
                        } catch (e: Exception) {
                            Log.e("DYAPP", "auto save fail: ${task.filename}", e)
                            saved.remove(task.id)
                            val count = (failCount[task.id] ?: 0) + 1
                            failCount[task.id] = count
                            if (count >= maxRetries) {
                                // 放弃:标记已处理,避免无限重试
                                saved.add(task.id)
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToPublic(task: DownloadTask) {
        if (!autoSaveEnabled) return
        val src = File(task.filePath)
        if (!src.exists()) throw RuntimeException("file not exists")
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, task.filename)
                put(MediaStore.Downloads.MIME_TYPE, guessMime(task.filename))
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw RuntimeException("insert failed")
            val out = context.contentResolver.openOutputStream(uri)
                ?: throw RuntimeException("openOutputStream failed")
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
    }

    private fun guessMime(filename: String): String =
        when (filename.substringAfterLast('.', "mp4")) {
            "mp3" -> "audio/mpeg"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "video/mp4"
        }
}
