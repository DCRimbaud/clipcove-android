/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Owns the resumable download state machine, concurrency limits, retries, persistence, and file cleanup.
 */
package com.douyin.data.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * App-private download manager:
 * - Streams through OkHttp and resumes with Range requests using a .part file.
 * - Pause/resume/cancel: pause preserves the part file, while resume continues with Range.
 * - Retries only IO/5xx failures with exponential backoff and jitter; 4xx responses are terminal because signed URLs may be expired.
 * - Reports a three-second sliding-window transfer speed.
 * - Limits global concurrency to three and exposes task state through StateFlow.
 */
class AndroidDownloadRepository : DownloadRepository {

    private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAX_PARALLEL)
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    override val tasks: StateFlow<List<DownloadTask>> = _tasks

    /** Active OkHttp calls, cancelled to interrupt blocking reads during pause or removal. */
    private val runningCalls = ConcurrentHashMap<Long, Call>()
    private val speedMeters = ConcurrentHashMap<Long, SpeedMeter>()
    /** Live task objects owned by download workers; UI snapshots are resolved by id before mutation. */
    private val liveTasks = ConcurrentHashMap<Long, DownloadTask>()
    private val nextId = java.util.concurrent.atomic.AtomicLong(1)

    override fun init(context: android.content.Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            loadPersistedTasks()
        }
    }

    // Task persistence

    private fun persistFile(): File =
        File(appContext!!.filesDir, "downloads" + File.separator + "tasks.json")

    /** Persists the small task snapshot after each state change. */
    private fun persist() {
        val tasks = _tasks.value
        if (tasks.isEmpty()) {
            persistFile().delete()
            return
        }
        runCatching {
            val arr = org.json.JSONArray()
            tasks.forEach { arr.put(it.toJson()) }
            persistFile().parentFile?.mkdirs()
            persistFile().writeText(arr.toString(), Charsets.UTF_8)
        }
    }

    /** Restores persisted tasks; unfinished work becomes paused and completed files are validated before reuse. */
    private fun loadPersistedTasks() {
        val f = persistFile()
        if (!f.exists()) return
        val restored = runCatching {
            val arr = org.json.JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).map { DownloadTask.fromJson(arr.getJSONObject(it)) }
        }.getOrElse {
            Log.w("DYAPP", "load tasks.json fail: ${it.message}")
            return
        }
        if (restored.isEmpty()) return
        val dir = File(appContext!!.filesDir, "downloads")
        val keep = restored.filter { t ->
            when (t.status) {
                DownloadStatus.DONE -> {
                    // Completed tasks remain restorable only while their file still exists.
                    val f0 = if (t.filePath.isNotEmpty()) File(t.filePath) else File(dir, t.filename)
                    f0.exists() && f0.length() > 0
                }
                DownloadStatus.PAUSED, DownloadStatus.FAILED -> true
                else -> false
            }
        }
        if (keep.isEmpty()) return
        // Unfinished tasks lost their worker with the process, so restore them as paused for an explicit user decision.
        // Completed tasks stay DONE after file validation and remain actionable from the list.
        keep.forEach { t ->
            when (t.status) {
                DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                    t.paused = true
                    t.status = DownloadStatus.PAUSED
                    liveTasks[t.id] = t
                    // Resume accounting uses the persisted part-file length.
                    val part = File(dir, t.filename + ".part")
                    if (part.exists()) {
                        t.downloadedBytes = part.length()
                    }
                }
                DownloadStatus.DONE -> {
                    // Restore completed tasks into the live map so later deletion can clean the backing file.
                    t.paused = false
                    t.speedBps = 0
                    liveTasks[t.id] = t
                }
                else -> {}
            }
        }
        synchronized(this) {
            _tasks.value = keep + _tasks.value
        }
        nextId.set((keep.maxOfOrNull { it.id } ?: 0L) + 1)
    }

    override fun enqueue(title: String, filename: String, url: String): DownloadTask {
        val task = DownloadTask(nextId.getAndIncrement(), title, filename, url)
        liveTasks[task.id] = task
        synchronized(this) {
            // Store snapshots separately from worker objects; otherwise copied updates can compare equal and suppress StateFlow emissions.
            // Snapshot identity is also required for deterministic list equality.
            _tasks.value = listOf(task.copy()) + _tasks.value
        }
        persist()
        scope.launch { downloadLoop(task) }
        return task
    }

    override fun pause(task: DownloadTask) {
        liveTasks[task.id]?.let { t ->
            t.paused = true
            runningCalls[t.id]?.cancel()
            speedMeters.remove(t.id)
            t.status = DownloadStatus.PAUSED
            update(t)
        }
    }

    override fun resume(task: DownloadTask) {
        relaunch(task, DownloadStatus.PAUSED)
    }

    override fun retry(task: DownloadTask) {
        relaunch(task, DownloadStatus.FAILED)
    }

    override fun pauseAll() {
        _tasks.value
            .filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING }
            .forEach(::pause)
    }

    override fun resumeAll() {
        _tasks.value
            .filter { it.status == DownloadStatus.PAUSED }
            .forEach(::resume)
    }

    override fun retryFailed() {
        _tasks.value
            .filter { it.status == DownloadStatus.FAILED }
            .forEach(::retry)
    }

    /**
     * Re-enters a paused or failed task into the state machine.
     *
     * Failed workers are normally removed from `liveTasks`, so a defensive copy
     * is registered before launching a new worker. The expected source status
     * makes repeated taps idempotent and prevents duplicate workers.
     */
    private fun relaunch(task: DownloadTask, expectedStatus: DownloadStatus) {
        val live = liveTasks[task.id]
        val owned = live ?: task.copy().also { liveTasks[it.id] = it }
        if (owned.status != expectedStatus) return

        owned.cancelled = false
        owned.paused = false
        owned.speedBps = 0
        owned.retryCount = 0
        owned.status = DownloadStatus.QUEUED
        update(owned)
        persist()
        scope.launch { downloadLoop(owned) }
    }

    override fun remove(task: DownloadTask) {
        val t = liveTasks.remove(task.id)
        if (t == null) {
            // Tasks missing from the live map still clean their known files by persisted metadata.
            cleanupFiles(task)
            synchronized(this) {
                _tasks.value = _tasks.value.filter { it.id != task.id }
            }
            persist()
            return
        }
        t.cancelled = true
        runningCalls[t.id]?.cancel()
        runningCalls.remove(t.id)
        speedMeters.remove(t.id)
        cleanupFiles(t)
        synchronized(this) {
            _tasks.value = _tasks.value.filter { it.id != t.id }
        }
        persist()
    }

    // Download state machine

    private suspend fun downloadLoop(task: DownloadTask) {
        if (task.cancelled) return
        var attempt = 0
        try {
            while (true) {
                if (task.cancelled) { cleanupFiles(task); return }
                if (task.paused) { task.status = DownloadStatus.PAUSED; update(task); return }
                task.status = DownloadStatus.DOWNLOADING
                task.retryCount = attempt
                update(task)
                try {
                    semaphore.withPermit { streamDownload(task) }
                    if (task.cancelled) { cleanupFiles(task); return }
                    if (task.paused) { task.status = DownloadStatus.PAUSED; update(task); return }
                    finishTask(task)
                    return
                } catch (e: Exception) {
                    if (task.cancelled) { cleanupFiles(task); return }
                    if (task.paused) { task.status = DownloadStatus.PAUSED; update(task); return }
                    Log.w("DYAPP", "download retryable fail ${task.filename}: ${e.message}")
                    if (!retryable(e) || attempt >= MAX_RETRIES) {
                        task.status = DownloadStatus.FAILED
                        update(task)
                        return
                    }
                    attempt++
                    val delayMs = backoffMillis(attempt)
                    task.status = DownloadStatus.QUEUED
                    task.retryCount = attempt
                    update(task)
                    delay(delayMs)
                }
            }
        } finally {
            // Retain paused workers for resume and queued workers that were relaunched from a finishing worker.
            if (task.status != DownloadStatus.PAUSED &&
                task.status != DownloadStatus.QUEUED &&
                liveTasks[task.id] === task
            ) {
                liveTasks.remove(task.id)
            }
            persist()
        }
    }

    /** Performs one streaming attempt into the part file; exceptions are classified by the retry loop. */
    private fun streamDownload(task: DownloadTask) {
        val dir = File(appContext!!.filesDir, "downloads").apply { mkdirs() }
        val partFile = File(dir, task.filename + ".part")
        val startBytes = if (partFile.exists()) partFile.length() else 0L
        val req = Request.Builder()
            .url(task.url)
            .header("User-Agent", UA)
            .header("Referer", refererFor(task.url))
            .header("Range", "bytes=$startBytes-")
            .build()
        val call = client.newCall(req)
        runningCalls[task.id] = call
        try {
            call.execute().use { resp ->
                val code = resp.code
                if (code == 416) {
                    // A 416 response means the persisted file is already complete when its length matches the expected size.
                    return
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val total = parseTotal(resp, startBytes)
                task.totalBytes = total
                if (code != 206) {
                    // If the server ignores Range and returns 200, reset the part file before copying.
                    partFile.delete()
                }
                task.downloadedBytes = if (code == 206) startBytes else 0
                val meter = speedMeters.getOrPut(task.id) { SpeedMeter() }
                if (code == 206 && startBytes > 0) {
                    java.io.RandomAccessFile(partFile, "rw").use { raf ->
                        raf.seek(startBytes)
                        copyStream(resp, task, { b, n -> raf.write(b, 0, n) }, total, meter)
                    }
                } else {
                    partFile.outputStream().use { out ->
                        copyStream(resp, task, { b, n -> out.write(b, 0, n) }, total, meter)
                    }
                }
            }
        } finally {
            runningCalls.remove(task.id)
        }
    }

    private fun copyStream(
        resp: okhttp3.Response,
        task: DownloadTask,
        write: (ByteArray, Int) -> Unit,
        total: Long,
        meter: SpeedMeter
    ) {
        val body = resp.body ?: throw IOException("empty body")
        val buf = ByteArray(64 * 1024)
        body.byteStream().use { input ->
            while (true) {
                if (task.cancelled || Thread.currentThread().isInterrupted) throw IOException("cancelled")
                if (task.paused) throw IOException("paused")
                val n = input.read(buf)
                if (n < 0) break
                write(buf, n)
                task.downloadedBytes += n
                meter.addSample(task.downloadedBytes)
                task.speedBps = meter.speedBps()
                if (total > 0) {
                    val pct = ((task.downloadedBytes * 100) / total).toInt()
                    if (pct != task.progress) {
                        task.progress = pct
                        update(task)
                    }
                }
            }
        }
    }

    private fun finishTask(task: DownloadTask) {
        val dir = File(appContext!!.filesDir, "downloads")
        val partFile = File(dir, task.filename + ".part")
        val outFile = File(dir, task.filename)
        if (partFile.exists() && !outFile.exists()) {
            partFile.renameTo(outFile)
        }
        task.filePath = outFile.absolutePath
        task.progress = 100
        task.speedBps = 0
        task.status = DownloadStatus.DONE
        update(task)
    }

    private fun cleanupFiles(task: DownloadTask) {
        runCatching {
            val dir = File(appContext?.filesDir ?: return, "downloads")
            if (task.filePath.isNotEmpty()) File(task.filePath).delete()
            File(dir, task.filename).delete()
            File(dir, task.filename + ".part").delete()
        }
    }

    private fun update(task: DownloadTask) {
        synchronized(this) {
            val before = _tasks.value
            _tasks.value = _tasks.value.map {
                if (it.id == task.id) {
                    task.copy(
                        status = task.status,
                        progress = task.progress,
                        speedBps = task.speedBps,
                        downloadedBytes = task.downloadedBytes,
                        totalBytes = task.totalBytes,
                        filePath = task.filePath,
                        savedToPublic = task.savedToPublic,
                        cancelled = task.cancelled,
                        retryCount = task.retryCount,
                        paused = task.paused
                    )
                } else it
            }
            val after = _tasks.value
            Log.d("DYAPP", "update task=${task.id} st=${task.status} p=${task.progress} emit=${before != after} listSize=${after.size}")
        }
    }

    // Utilities

    /** Parses total size from Content-Range or Content-Length. */
    private fun parseTotal(resp: okhttp3.Response, fallback: Long): Long {
        val cr = resp.header("Content-Range")
        if (cr != null) {
            val slash = cr.lastIndexOf('/')
            if (slash > 0) {
                return cr.substring(slash + 1).toLongOrNull() ?: fallback
            }
        }
        return resp.header("Content-Length")?.toLongOrNull() ?: fallback
    }

    /** Only IO, network, and 5xx failures are retryable; signed 4xx failures are terminal. */
    private fun retryable(e: Exception): Boolean {
        if (e is IOException) return true
        val msg = e.message ?: ""
        if (msg.contains("HTTP 5") || msg.contains("HTTP 4")) {
            return msg.contains("HTTP 5")
        }
        return false
    }

    /** Exponential backoff with jitter, capped at thirty seconds. */
    private fun backoffMillis(attempt: Int): Long {
        val exp = (1000L shl (attempt - 1)).coerceAtMost(30_000L)
        return exp / 2 + Random.nextLong(exp / 2, exp + 1)
    }

    /** Selects the platform Referer required by the target CDN hostname. */
    private fun refererFor(url: String): String {
        return when {
            url.contains("xhscdn.com") -> "https://www.xiaohongshu.com/"
            else -> "https://www.douyin.com/"
        }
    }

    /** Releases the accumulated speed samples for one task. */
    fun release() {
        scope.cancel()
    }

    private companion object {
        const val MAX_PARALLEL = 3
        const val MAX_RETRIES = 3
        val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }
}

/** Three-second sliding-window speed meter based on timestamped byte samples. */
private class SpeedMeter(private val windowMs: Long = 3000) {
    private val samples = ArrayDeque<Pair<Long, Long>>()
    private var lastBytes = 0L
    private var lastTime = System.currentTimeMillis()

    /** Records a cumulative byte sample after a successful stream read. */
    fun addSample(bytes: Long) {
        val now = System.currentTimeMillis()
        lastBytes = bytes
        lastTime = now
        samples.addLast(now to bytes)
        while (samples.size > 2 && now - samples.first().first > windowMs) {
            samples.removeFirst()
        }
    }

    /** Returns the three-second sliding-window average in bytes per second. */
    fun speedBps(): Long {
        if (samples.size < 2) return 0
        val (t0, b0) = samples.first()
        val dt = lastTime - t0
        if (dt <= 0) return 0
        return ((lastBytes - b0) * 1000 / dt).coerceAtLeast(0)
    }
}