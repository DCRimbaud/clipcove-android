/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Mirrors completed app-private downloads into the public Downloads collection and owns retry/deduplication policy.
 */
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
 * Auto-save service: observes completed downloads and mirrors them when enabled.
 * Files are written through MediaStore on Android 10+ and require no runtime permission there.
 *
 * Thread safety: ConcurrentHashMap deduplicates completed tasks so repeated flow emissions cannot copy twice.
 * Blocking file I/O runs in child jobs and is not interrupted by task-flow restarts.
 */
class AutoSaveService(
    private val context: Context,
    private val downloadRepo: DownloadRepository,
    private val settingsRepo: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val saved = ConcurrentHashMap.newKeySet<Long>()
    /** Maximum copy attempts before a permanently failing task is abandoned. */
    private val failCount = ConcurrentHashMap<Long, Int>()
    private var collectJob: Job? = null
    @Volatile
    private var autoSaveEnabled = true
    private val maxRetries = 3

    /** Starts settings and task observers once; repeated calls are ignored. */
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
                    // Run blocking I/O outside the task-flow collector.
                    scope.launch saveTask@{
                        try {
                            if (!autoSaveEnabled) {
                                saved.remove(task.id)
                                return@saveTask
                            }
                            saveToPublic(task)
                            task.savedToPublic = true
                        } catch (e: Exception) {
                            Log.e("DYAPP", "auto save fail: ${task.filename}", e)
                            saved.remove(task.id)
                            val count = (failCount[task.id] ?: 0) + 1
                            failCount[task.id] = count
                            if (count >= maxRetries) {
                                // Mark the task as handled after the retry budget is exhausted.
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