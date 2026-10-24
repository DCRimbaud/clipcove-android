package com.douyin.data.download

import org.json.JSONObject

/** 下载状态 */
enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, DONE, FAILED }

/** 下载任务数据模型(跨线程读写,状态字段 volatile 保证可见性) */
data class DownloadTask(
    val id: Long,
    val title: String,
    val filename: String,
    val url: String,
    @Volatile var status: DownloadStatus = DownloadStatus.QUEUED,
    @Volatile var progress: Int = 0,
    /** 实时下载速度(字节/秒),用于 UI 显示 */
    @Volatile var speedBps: Long = 0,
    @Volatile var downloadedBytes: Long = 0,
    @Volatile var totalBytes: Long = 0,
    @Volatile var filePath: String = "",
    /** 是否已保存到公共「下载」目录(避免重复另存) */
    @Volatile var savedToPublic: Boolean = false,
    /** 取消标志:remove 时置位,下载循环检查后立即终止并清理文件 */
    @Volatile var cancelled: Boolean = false,
    /** 当前失败重试次数 */
    @Volatile var retryCount: Int = 0,
    /** 暂停标志:pause 时置位,下载循环检查后退出但保留 .part 断点文件 */
    @Volatile var paused: Boolean = false
) {

    /** 序列化为 JSON(持久化到本地,重启后恢复任务列表) */
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
