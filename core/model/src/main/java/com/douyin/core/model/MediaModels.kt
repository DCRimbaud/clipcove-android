/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Platform-neutral media contracts shared by parsers, persistence, and UI presentation.
 */
package com.douyin.core.model

/** Supported content platforms. UI display names are resolved from Android resources. */
enum class MediaPlatform {
    DOUYIN,
    XIAOHONGSHU,
}

/**
 * Normalized interaction counters. Missing platform counters remain zero.
 *
 * @property diggCount Likes or equivalent positive reactions.
 * @property commentCount Comment count.
 * @property collectCount Saves/favorites count.
 * @property shareCount Share count.
 * @property playCount Views/plays when the platform exposes them.
 * @property recommendCount Recommendations when the platform exposes them.
 */
data class MediaStats(
    val diggCount: Long = 0,
    val commentCount: Long = 0,
    val collectCount: Long = 0,
    val shareCount: Long = 0,
    val playCount: Long = 0,
    val recommendCount: Long = 0,
)

/** Identifies whether a video candidate is original, transcoded, or unknown. */
enum class VideoSourceKind {
    ORIGINAL,
    TRANSCODED,
    OTHER,
}

/**
 * One selectable video stream and its quality/risk metadata.
 *
 * @property label Display label derived from the platform or quality tier.
 * @property url Signed or public media URL.
 * @property bitrate Declared bitrate in bits per second, or zero.
 * @property width Declared width in pixels, or zero.
 * @property height Declared height in pixels, or zero.
 * @property sizeBytes Declared file size, or zero when unknown.
 * @property sourceKind Original/transcoded classification.
 * @property watermarkRisk Whether the stream is known or likely to contain a watermark.
 */
data class VideoCandidate(
    val label: String,
    val url: String,
    val bitrate: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val sourceKind: VideoSourceKind = VideoSourceKind.OTHER,
    val watermarkRisk: Boolean = false,
) {
    /** Human-readable resolution summary; empty when dimensions are unavailable. */
    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${height}p · ${width}x${height}" else ""
}

/**
 * One gallery image with the best accessible URL and optional Live Photo stream.
 *
 * @property url Platform-rendered accessible image URL.
 * @property originalUrl Optional original image URL when the platform provides a valid one.
 * @property livePhotoUrl Optional video stream backing a Live Photo.
 * @property width Image width in pixels, or zero.
 * @property height Image height in pixels, or zero.
 */
data class MediaImage(
    val url: String,
    val originalUrl: String = "",
    val livePhotoUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
) {
    /** Uses the original variant only when it is present and non-blank. */
    val preferredUrl: String get() = originalUrl.ifBlank { url }
}

/**
 * Normalized media model produced by every platform parser.
 *
 * Time units are fixed here: publication time is Unix seconds and duration is milliseconds.
 * Platform-specific fields must remain in data-layer mappers.
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
    val description: String = "",
    val authorId: String = "",
    val authorHandle: String = "",
    val authorAvatarUrl: String = "",
    val hashtags: List<String> = emptyList(),
    val location: String = "",
    val shareUrl: String = "",
    val videoUrl: String = "",
    val videoCandidates: List<VideoCandidate> = emptyList(),
    val musicUrl: String = "",
    val musicTitle: String = "",
    val musicAuthor: String = "",
    val musicDurationMs: Long = 0,
    val musicCoverUrl: String = "",
    val images: List<MediaImage> = emptyList(),
) {
    /** True when a primary video URL is available. */
    val hasVideo: Boolean get() = videoUrl.isNotBlank()
    /** True when at least one gallery image is available. */
    val hasImages: Boolean get() = images.isNotEmpty()
    /** True when a soundtrack URL is available. */
    val hasMusic: Boolean get() = musicUrl.isNotBlank()

    /** Prefers an original stream, then falls back to the first declared candidate. */
    val preferredVideoCandidate: VideoCandidate?
        get() = videoCandidates.firstOrNull { it.sourceKind == VideoSourceKind.ORIGINAL }
            ?: videoCandidates.firstOrNull()
}