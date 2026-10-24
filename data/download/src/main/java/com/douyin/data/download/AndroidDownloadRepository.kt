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
 * App 内下载管理器(自研增强版):
 * - OkHttp 流式下载,Range 断点续传(.part 文件,完成后 rename)
 * - 暂停/继续/取消:暂停保留断点文件,恢复时带 Range 续传
 * - 失败自动重试:指数退避 + 随机抖动,仅重试 IO/5xx,4xx 不重试(签名 URL 过期无意义)
 * - 实时速度:3 秒滑动窗口平均
 * - 全局并发上限 3,任务状态通过 StateFlow 暴露,UI 订阅自动刷新
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

    /** 进行中的 OkHttp Call:暂停/取消时 cancel 中断阻塞读 */
    private val runningCalls = ConcurrentHashMap<Long, Call>()
    private val speedMeters = ConcurrentHashMap<Long, SpeedMeter>()
    /** 活跃任务对象(下载线程持引用):UI 拿到的可能是 copy 副本,操作前按 id 找回原对象 */
    private val liveTasks = ConcurrentHashMap<Long, DownloadTask>()
    private val nextId = java.util.concurrent.atomic.AtomicLong(1)

    override fun init(context: android.content.Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            loadPersistedTasks()
        }
    }

    // ===== 任务持久化 =====

    private fun persistFile(): File =
        File(appContext!!.filesDir, "downloads" + File.separator + "tasks.json")

    /** 每次任务变更后持久化(IO 线程,量小) */
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

    /** 启动时恢复任务列表:下载中/排队 → 暂停(等用户点继续,.part 续传);完成态校验文件存在 */
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
                    // 完成态:文件必须还在
                    val f0 = if (t.filePath.isNotEmpty()) File(t.filePath) else File(dir, t.filename)
                    f0.exists() && f0.length() > 0
                }
                DownloadStatus.PAUSED, DownloadStatus.FAILED -> true
                else -> false
            }
        }
        if (keep.isEmpty()) return
        // 未完成任务(下载线程已随进程结束)统一恢复为暂停,交给用户决定继续/删除;
        // 已完成任务保持 DONE(文件校验通过),仅恢复列表供打开/另存/删除
        keep.forEach { t ->
            when (t.status) {
                DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                    t.paused = true
                    t.status = DownloadStatus.PAUSED
                    liveTasks[t.id] = t
                    // 断点续传:.part 文件长度作为已下载量
                    val part = File(dir, t.filename + ".part")
                    if (part.exists()) {
                        t.downloadedBytes = part.length()
                    }
                }
                DownloadStatus.DONE -> {
                    // 完成态:文件校验通过,恢复为完成;放入 liveTasks 以便删除时清理文件
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
            // 列表存快照副本(与下载线程对象分离),否则 update 时 copy 出的
            // 新对象与列表元素字段恒等,List.equals 判定相等,StateFlow 永不 emit
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
        val t = liveTasks[task.id] ?: return
        if (t.status != DownloadStatus.PAUSED) return
        t.paused = false
        t.retryCount = 0
        t.status = DownloadStatus.QUEUED
        update(t)
        scope.launch { downloadLoop(t) }
    }

    override fun remove(task: DownloadTask) {
        val t = liveTasks.remove(task.id)
        if (t == null) {
            // 未在活跃映射(已完成/已恢复):仍按任务信息清理文件(修复残留 bug)
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

    // ===== 下载主循环 =====

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
            // 完成/失败/取消:移出活跃映射;暂停时保留(恢复下载仍要找到原对象)
            if (task.status != DownloadStatus.PAUSED && liveTasks[task.id] === task) {
                liveTasks.remove(task.id)
            }
            persist()
        }
    }

    /** 单次流式下载,写入 .part;抛异常表示失败(由上层决定重试) */
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
                    // 已完整(继续请求超范围):直接完成
                    return
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val total = parseTotal(resp, startBytes)
                task.totalBytes = total
                if (code != 206) {
                    // 服务器忽略 Range(200):从头重写
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

    // ===== 工具 =====

    /** 解析 Content-Range / Content-Length 得到总大小 */
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

    /** 仅 IO/网络/5xx 可重试;4xx(尤其 403/404 签名过期)不重试 */
    private fun retryable(e: Exception): Boolean {
        if (e is IOException) return true
        val msg = e.message ?: ""
        if (msg.contains("HTTP 5") || msg.contains("HTTP 4")) {
            return msg.contains("HTTP 5")
        }
        return false
    }

    /** 指数退避:1s → 2s → 4s → ... 上限 30s,加随机抖动 */
    private fun backoffMillis(attempt: Int): Long {
        val exp = (1000L shl (attempt - 1)).coerceAtMost(30_000L)
        return exp / 2 + Random.nextLong(exp / 2, exp + 1)
    }

    /** 按 CDN 域名选择 Referer(抖音/小红书各自校验) */
    private fun refererFor(url: String): String {
        return when {
            url.contains("xhscdn.com") -> "https://www.xiaohongshu.com/"
            else -> "https://www.douyin.com/"
        }
    }

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

/** 3 秒滑动窗口速度计(采样 (时间戳, 累计字节),窗口内差值 / 窗口时长) */
private class SpeedMeter(private val windowMs: Long = 3000) {
    private val samples = ArrayDeque<Pair<Long, Long>>()
    private var lastBytes = 0L
    private var lastTime = System.currentTimeMillis()

    fun addSample(bytes: Long) {
        val now = System.currentTimeMillis()
        lastBytes = bytes
        lastTime = now
        samples.addLast(now to bytes)
        while (samples.size > 2 && now - samples.first().first > windowMs) {
            samples.removeFirst()
        }
    }

    fun speedBps(): Long {
        if (samples.size < 2) return 0
        val (t0, b0) = samples.first()
        val dt = lastTime - t0
        if (dt <= 0) return 0
        return ((lastBytes - b0) * 1000 / dt).coerceAtLeast(0)
    }
}
