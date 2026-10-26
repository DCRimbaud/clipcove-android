/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Runs FFmpeg media merging outside the UI thread and reports task outcomes.
 */
package com.douyin.data.merger

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** ffmpeg-kit implementation that merges in a background coroutine and copies the result to public Downloads. */
class FfmpegVideoMerger : VideoMerger {

    // Serialize merges because the implementation uses fixed temporary filenames.
    private val mutex = Mutex()

    override fun merge(
        context: Context,
        videoUrl: String,
        audioUrl: String,
        filename: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            mutex.withLock {
                try {
                    val dir = File(context.filesDir, "merge").apply { mkdirs() }
                    val vf = File(dir, "v.mp4")
                    val af = File(dir, "a.mp3")
                    val of = File(dir, "out.mp4")
                    downloadFile(videoUrl, vf)
                    downloadFile(audioUrl, af)
                    val rc = FFmpegKit.execute(
                        "-y -i ${vf.absolutePath} -i ${af.absolutePath} " +
                            "-map 0:v -map 1:a -c:v copy -c:a aac -shortest ${of.absolutePath}"
                    )
                    val success = ReturnCode.isSuccess(rc.returnCode)
                    if (success) {
                        try {
                            saveToDownloads(context, of, filename)
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { onResult(false, "合成成功但保存失败:${e.message}") }
                            return@withLock
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onResult(success, if (success) "合成成功" else "合成失败")
                    }
                } catch (e: Exception) {
                    Log.e("DYAPP", "merge fail", e)
                    withContext(Dispatchers.Main) { onResult(false, "合成失败:${e.message}") }
                }
            }
        }
    }

    private fun downloadFile(url: String, file: File) {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        )
        conn.setRequestProperty(
            "Referer",
            if (url.contains("xhscdn.com")) "https://www.xiaohongshu.com/" else "https://www.douyin.com/"
        )
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.connect()
        conn.getInputStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToDownloads(context: Context, file: File, filename: String) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw RuntimeException("MediaStore insert failed")
            val out = context.contentResolver.openOutputStream(uri)
                ?: throw RuntimeException("openOutputStream failed")
            out.use { o ->
                file.inputStream().use { input -> input.copyTo(o) }
            }
        } else {
            val dest = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                filename
            )
            file.copyTo(dest, overwrite = true)
        }
    }
}