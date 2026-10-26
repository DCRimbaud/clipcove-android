/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Maps untrusted Douyin JSON into normalized media metadata, image assets, and video candidates.
 */
package com.douyin.data.douyin

import com.douyin.core.model.MediaImage
import com.douyin.core.model.MediaInfo
import com.douyin.core.model.MediaPlatform
import com.douyin.core.model.MediaStats
import com.douyin.core.model.ParseFailure
import com.douyin.core.model.ParseFailureType
import com.douyin.core.model.ParseResult
import com.douyin.core.model.VideoCandidate
import com.douyin.core.model.VideoSourceKind
import org.json.JSONArray
import org.json.JSONObject

/** Maps untrusted Python JSON into the typed media contract and structured failures. */
object DouyinMediaMapper {

    /**
     * Converts the Chaquopy JSON envelope into a typed parse result.
     *
     * @param raw Untrusted JSON returned by the Python resolver.
     * @return A typed success or failure; malformed JSON never escapes this boundary.
     */
    fun fromPythonJson(raw: String): ParseResult {
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return ParseResult.Failure(
                ParseFailure(
                    ParseFailureType.RESPONSE_CHANGED,
                    "抖音解析结果格式异常，请稍后重试",
                    e.message,
                )
            )
        }

        if (!root.isNull("error")) return failure(root.opt("error"))

        val id = root.optString("aweme_id").trim()
        if (id.isEmpty()) {
            return ParseResult.Failure(
                ParseFailure(ParseFailureType.RESPONSE_CHANGED, "抖音解析结果缺少作品 ID")
            )
        }

        val declaredVideoUrl = secureUrl(root.optString("play_url"))
        val candidates = root.optJSONArray("play_urls").toVideoCandidates().toMutableList()
        val videoUrl = declaredVideoUrl
            .ifBlank { candidates.firstOrNull()?.url.orEmpty() }
        if (videoUrl.isNotBlank() && candidates.none { it.url == videoUrl }) {
            candidates.add(
                0,
                VideoCandidate(
                    label = "Platform stream",
                    url = videoUrl,
                    sourceKind = VideoSourceKind.TRANSCODED,
                )
            )
        }
        val images = root.optJSONArray("images").toMediaImages()
        if (videoUrl.isBlank() && images.isEmpty()) {
            return ParseResult.Failure(
                ParseFailure(ParseFailureType.CONTENT_UNAVAILABLE, "该作品没有可下载的媒体内容")
            )
        }

        val stats = root.optJSONObject("stats")
        val media = MediaInfo(
            id = id,
            platform = MediaPlatform.DOUYIN,
            title = root.optString("title").ifBlank { "(无标题)" },
            author = root.optString("author"),
            coverUrl = secureUrl(root.optString("cover")),
            createdAtSeconds = root.optLong("create_time").coerceAtLeast(0),
            durationMs = root.optLong("duration").coerceAtLeast(0),
            stats = MediaStats(
                diggCount = stats?.optLong("digg_count")?.coerceAtLeast(0) ?: 0L,
                commentCount = stats?.optLong("comment_count")?.coerceAtLeast(0) ?: 0L,
                collectCount = stats?.optLong("collect_count")?.coerceAtLeast(0) ?: 0L,
                shareCount = stats?.optLong("share_count")?.coerceAtLeast(0) ?: 0L,
                playCount = stats?.optLong("play_count")?.coerceAtLeast(0) ?: 0L,
                recommendCount = stats?.optLong("recommend_count")?.coerceAtLeast(0) ?: 0L,
            ),
            description = root.optString("description").ifBlank { root.optString("title") },
            authorId = root.optString("author_id"),
            authorHandle = root.optString("author_handle"),
            authorAvatarUrl = secureUrl(root.optString("author_avatar")),
            hashtags = root.optJSONArray("hashtags").toStringList(),
            location = root.optString("location"),
            shareUrl = secureUrl(root.optString("share_url")),
            videoUrl = videoUrl,
            videoCandidates = candidates,
            musicUrl = secureUrl(root.optString("music")),
            musicTitle = root.optString("music_title"),
            musicAuthor = root.optString("music_author"),
            musicDurationMs = root.optLong("music_duration")
                .coerceAtLeast(0)
                .let { if (it in 1..999) it * 1000 else it },
            musicCoverUrl = secureUrl(root.optString("music_cover")),
            images = images,
        )
        return ParseResult.Success(media)
    }

    private fun failure(node: Any?): ParseResult.Failure {
        val objectNode = node as? JSONObject
        val legacyMessage = (node as? String).orEmpty()
        val message = objectNode?.optString("message")
            ?.ifBlank { null }
            ?: legacyMessage.ifBlank { "抖音解析失败，请稍后重试" }
        val code = objectNode?.optString("code").orEmpty()
        val detail = objectNode?.optString("debug_detail")?.takeIf { it.isNotBlank() }
        return ParseResult.Failure(
            ParseFailure(
                type = classifyFailure(code, message),
                message = message,
                debugDetail = detail,
            )
        )
    }

    private fun classifyFailure(code: String, message: String): ParseFailureType {
        val normalized = code.trim().uppercase()
        if (normalized.isNotEmpty()) {
            runCatching { ParseFailureType.valueOf(normalized) }.getOrNull()?.let { return it }
        }
        return when {
            message.contains("链接无效") || message.contains("未在输入中找到") ->
                ParseFailureType.INVALID_LINK
            message.contains("风控", true) || message.contains("Argus", true) ||
                message.contains("Uifid", true) || message.contains("403") ->
                ParseFailureType.RISK_CONTROL
            message.contains("超时") || message.contains("网络") || message.contains("连接") ->
                ParseFailureType.NETWORK
            message.contains("不存在") || message.contains("删除") || message.contains("不可见") ||
                message.contains("权限") -> ParseFailureType.CONTENT_UNAVAILABLE
            message.contains("JSON", true) || message.contains("格式") || message.contains("接口返回") ->
                ParseFailureType.RESPONSE_CHANGED
            else -> ParseFailureType.UNKNOWN
        }
    }

    private fun JSONArray?.toVideoCandidates(): List<VideoCandidate> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val url = secureUrl(item.optString("url"))
                if (url.isBlank()) continue
                add(
                    VideoCandidate(
                        label = item.optString("label", "candidate_$index"),
                        url = url,
                        bitrate = item.optLong("bitrate").coerceAtLeast(0),
                        width = item.optInt("width").coerceAtLeast(0),
                        height = item.optInt("height").coerceAtLeast(0),
                        sizeBytes = sequenceOf(
                            item.optLong("size"),
                            item.optLong("file_size"),
                            item.optLong("data_size"),
                        ).firstOrNull { it > 0 } ?: 0L,
                        sourceKind = VideoSourceKind.TRANSCODED,
                    )
                )
            }
        }
    }

    private fun JSONArray?.toMediaImages(): List<MediaImage> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val url = secureUrl(item.optString("url"))
                if (url.isBlank()) continue
                add(
                    MediaImage(
                        url = url,
                        // Douyin's original_url is synthesized by stripping signed CDN transforms;
                        // the resulting path returns HTTP 403, so keep the accessible direct URL.
                        originalUrl = "",
                        livePhotoUrl = secureUrl(item.optString("gif_url")),
                        width = item.optInt("width").coerceAtLeast(0),
                        height = item.optInt("height").coerceAtLeast(0),
                    )
                )
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotEmpty() && value !in this) add(value)
            }
        }
    }

    private fun secureUrl(url: String): String =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
}