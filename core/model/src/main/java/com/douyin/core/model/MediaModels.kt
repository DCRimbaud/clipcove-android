package com.douyin.core.model

/** 支持的内容平台。展示文案只属于领域语义，不依赖 Android 资源。 */
enum class MediaPlatform(val displayName: String) {
    DOUYIN("抖音"),
    XIAOHONGSHU("小红书"),
}

data class MediaStats(
    val diggCount: Long = 0,
    val commentCount: Long = 0,
    val collectCount: Long = 0,
    val shareCount: Long = 0,
)

data class VideoCandidate(
    val label: String,
    val url: String,
    val bitrate: Long = 0,
)

data class MediaImage(
    val url: String,
    val originalUrl: String = "",
    val livePhotoUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
) {
    val preferredUrl: String get() = originalUrl.ifBlank { url }
}

/**
 * 平台解析后的统一媒体模型。
 *
 * 时间单位在这里固定：发布时间为 Unix 秒，时长为毫秒。
 * 平台字段差异只允许存在于 data 层的映射器中。
 */
data class MediaInfo(
    val id: String,
    val platform: MediaPlatform,
    val title: String,
    val author: String,
    val coverUrl: String,
    val createdAtSeconds: Long = 0,
    val durationMs: Long = 0,
    val stats: MediaStats = MediaStats(),
    val videoUrl: String = "",
    val videoCandidates: List<VideoCandidate> = emptyList(),
    val musicUrl: String = "",
    val musicTitle: String = "",
    val images: List<MediaImage> = emptyList(),
) {
    val hasVideo: Boolean get() = videoUrl.isNotBlank()
    val hasImages: Boolean get() = images.isNotEmpty()
    val hasMusic: Boolean get() = musicUrl.isNotBlank()
}
