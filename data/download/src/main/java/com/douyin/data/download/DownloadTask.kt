/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines the thread-safe persisted download-task state and JSON serialization.
 */
package com.douyin.data.download

import org.json.JSONObject

/** Persisted download lifecycle state. */
enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, DONE, FAILED }

/** Download task shared across threads; volatile mutable fields publish state changes safely. */
data class DownloadTask(
    val id: Long,
    val title: String,
    val filename: String,
    val url: String,
    @Volatile var status: DownloadStatus = DownloadStatus.QUEUED,
    @Volatile var progress: Int = 0,
    /** Current transfer speed in bytes per second for UI display. */
    @Volatile var speedBps: Long = 0,
    @Volatile var downloadedBytes: Long = 0,
    @Volatile var totalBytes: Long = 0,
    @Volatile var filePath: String = "",
    /** Whether the completed file was already copied to the public Downloads collection. */
    @Volatile var savedToPublic: Boolean = false,
    /** Cancellation flag set by removal so the worker stops and cleans its files. */
    @Volatile var cancelled: Boolean = false,
    /** Number of retries already consumed by the current task. */
    @Volatile var retryCount: Int = 0,
    /** Pause flag checked by the worker; the part file is retained for resume. */
    @Volatile var paused: Boolean = false
) {

    /** Serializes the task for app-private persistence and restart recovery. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("filename", filename)
        put("url", url)
        put("status", status.name)
        put("progress", progress)
        put("speedBps", speedBps)
        put("downloadedBytes", downloadedBytes)
        put("totalBytes", totalBytes)
        put("filePath", filePath)
        put("savedToPublic", savedToPublic)
        put("cancelled", cancelled)
        put("retryCount", retryCount)
        put("paused", paused)
    }

    companion object {
        /**
     * Rehydrates a persisted task snapshot.
     *
     * Missing fields use safe defaults so a partially written task file cannot
     * crash startup. File validity is checked by the repository after parsing.
     */
    fun fromJson(o: JSONObject): DownloadTask = DownloadTask(
            id = o.optLong("id"),
            title = o.optString("title"),
            filename = o.optString("filename"),
            url = o.optString("url"),
            status = runCatching { DownloadStatus.valueOf(o.optString("status", "PAUSED")) }
                .getOrDefault(DownloadStatus.PAUSED),
            progress = o.optInt("progress"),
            speedBps = o.optLong("speedBps"),
            downloadedBytes = o.optLong("downloadedBytes"),
            totalBytes = o.optLong("totalBytes"),
            filePath = o.optString("filePath"),
            savedToPublic = o.optBoolean("savedToPublic"),
            cancelled = o.optBoolean("cancelled"),
            retryCount = o.optInt("retryCount"),
            paused = o.optBoolean("paused")
        )
    }
}