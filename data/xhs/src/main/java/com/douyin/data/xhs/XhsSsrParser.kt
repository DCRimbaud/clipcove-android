/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Parses official Xiaohongshu SSR payloads into normalized media contracts without network access.
 */
package com.douyin.data.xhs

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
import java.security.MessageDigest

/** Pure parser that converts official note-page HTML into the normalized media model without network access. */
object XhsSsrParser {

    /**
     * Parses an official Xiaohongshu note page without performing network I/O.
     *
     * @param html Raw SSR page HTML.
     * @param sourceUrl Original share URL used for token-preserving diagnostics.
     * @return Normalized media or a typed failure when the SSR contract is absent.
     */
    fun parse(html: String, sourceUrl: String): ParseResult {
        val state = extractInitialState(html)
            ?: return failure(
                "小红书页面结构已变化，请确认使用最新分享链接",
                "initial_state_missing bytes=${html.length}",
            )

        val expectedId = noteIdFromUrl(sourceUrl)
        val mobileNote = state.optJSONObject("noteData")
            ?.optJSONObject("data")
            ?.optJSONObject("noteData")
        var mobileFailure: ParseResult.Failure? = null
        if (mobileNote != null) {
            val mobileId = mobileNote.optString("noteId")
            val matchesExpected = expectedId == null || mobileId.isBlank() || mobileId == expectedId
            if (matchesExpected) {
                val result = parseNoteObject(
                    detail = mobileNote,
                    fallbackId = mobileId.ifBlank { expectedId.orEmpty() },
                    sourceUrl = sourceUrl,
                )
                if (result is ParseResult.Success) return result
                mobileFailure = result as ParseResult.Failure
            }
        }

        val desktopNote = state.optJSONObject("note")
        val noteMap = desktopNote?.optJSONObject("noteDetailMap")
        if (desktopNote != null && noteMap != null && noteMap.length() > 0) {
            val currentNoteId = desktopNote.optString("currentNoteId").takeIf { it.isNotBlank() }
            val key = pickNoteKey(noteMap, expectedId, currentNoteId)
            if (key != null) {
                val detail = noteMap.optJSONObject(key)?.optJSONObject("note")
                if (detail != null) {
                    val result = parseNoteObject(detail, key, sourceUrl)
                    if (result is ParseResult.Success) return result
                    if (mobileFailure == null) mobileFailure = result as ParseResult.Failure
                }
            }
        }

        if (mobileFailure != null) return mobileFailure
        return failure(
            "小红书页面中没有笔记数据",
            "expectedId=$expectedId topKeys=${state.keys().asSequence().take(24).joinToString(",")}",
        )
    }

    private fun parseNoteObject(
        detail: JSONObject,
        fallbackId: String,
        sourceUrl: String,
    ): ParseResult {
        return try {
            val media = normalize(detail, fallbackId, sourceUrl)
            val isVideoNote = detail.optString("type").equals("video", ignoreCase = true)
            if (isVideoNote && media.videoUrl.isBlank()) {
                failure(
                    "小红书视频地址解析失败，请稍后重试",
                    "video_missing id=${media.id} images=${media.images.size} videoKeys=${detail.optJSONObject("video")?.keys()?.asSequence()?.joinToString(",")}",
                )
            } else if (!media.hasVideo && !media.hasImages) {
                failure("小红书没有返回可下载的媒体", "empty_media id=${media.id} title=${media.title}")
            } else {
                ParseResult.Success(media)
            }
        } catch (e: Exception) {
            ParseResult.Failure(
                ParseFailure(
                    ParseFailureType.RESPONSE_CHANGED,
                    "小红书笔记解析失败，请稍后重试",
                    e.message,
                )
            )
        }
    }

    private fun extractInitialState(html: String): JSONObject? {
        val marker = html.indexOf("window.__INITIAL_STATE__")
        if (marker < 0) return null
        val start = html.indexOf('{', marker)
        val scriptEnd = html.indexOf("</script>", marker)
        if (start < 0 || scriptEnd <= start) return null

        val raw = html.substring(start, scriptEnd)
        var depth = 0
        var inString = false
        var escaped = false
        var end = -1
        for (index in raw.indices) {
            val ch = raw[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = index + 1
                        break
                    }
                }
            }
        }
        if (end <= 0) return null

        val segment = raw.substring(0, end)
            .replace(":undefined", ":null")
            .replace("undefined,", "null,")
            .replace("[undefined", "[null")
            .replace("undefined]", "null]")
        return runCatching { JSONObject(segment) }.getOrNull()
    }

    private fun normalize(note: JSONObject, fallbackId: String, sourceUrl: String): MediaInfo {
        val video = note.optJSONObject("video")
        val images = note.optJSONArray("imageList")
        val directStreams = video?.optJSONObject("media")?.optJSONObject("stream")
            ?: video?.optJSONObject("stream")
        var h264Streams = directStreams?.optJSONArray("h264")
        var streamCandidates = h264Streams.toVideoCandidates()
        if (streamCandidates.isEmpty()) {
            h264Streams = mediaV2Streams(video)?.optJSONArray("h264")
            streamCandidates = h264Streams.toVideoCandidates()
        }
        val originalVideoUrl = originVideoUrl(originVideoKey(video))
        val candidates = if (originalVideoUrl.isNotBlank()) {
            listOf(
                VideoCandidate(
                    label = "Original",
                    url = originalVideoUrl,
                    sourceKind = VideoSourceKind.ORIGINAL,
                    watermarkRisk = false,
                )
            ) + streamCandidates
        } else {
            streamCandidates
        }
        val videoUrl = candidates.firstOrNull()?.url.orEmpty()
        val firstImage = images?.optJSONObject(0)
        val firstImageUrl = firstImage?.originalImageUrl().orEmpty()
            .ifBlank { secureUrl(firstImage.bestImageUrl().orEmpty()) }
        val videoCover = secureUrl(
            video?.optJSONObject("cover")
                ?.optJSONArray("urlList")
                ?.optString(0)
                .orEmpty()
        )
        val rawTime = note.optLong("time", 0)
        val interact = note.optJSONObject("interactInfo")
        val user = note.optJSONObject("user")
        val tagList = note.optJSONArray("tagList")
        val music = note.optJSONObject("music")
        val musicPlay = music?.optJSONObject("playUrl")
            ?: music?.optJSONObject("play_url")
        val musicUrl = secureUrl(
            musicPlay?.optString("url")
                ?.ifBlank { musicPlay.optJSONArray("urlList")?.optString(0).orEmpty() }
                .orEmpty()
        )
        val musicCover = secureUrl(
            music?.optJSONObject("cover")?.optString("url")
                ?: music?.optJSONObject("cover")?.optJSONArray("urlList")?.optString(0).orEmpty()
        )
        val musicDuration = music?.optLong("duration", 0L) ?: 0L

        return MediaInfo(
            id = note.optString("noteId").ifBlank {
                deriveId(sourceUrl, note.optJSONObject("user")?.optString("authorID").orEmpty())
            }.ifBlank { fallbackId },
            platform = MediaPlatform.XIAOHONGSHU,
            title = note.optString("title").ifBlank {
                note.optString("desc").lineSequence().firstOrNull().orEmpty()
            }.ifBlank { "(无标题)" },
            author = user?.optString("nickname")
                ?.ifBlank { user?.optString("nickName").orEmpty() }
                .orEmpty(),
            coverUrl = videoCover.ifBlank { firstImageUrl },
            createdAtSeconds = if (rawTime > 1_000_000_000_000L) rawTime / 1000 else rawTime,
            durationMs = h264Streams.optDurationMs(video),
            stats = MediaStats(
                diggCount = interact.optCount("likedCount"),
                commentCount = interact.optCount("commentCount"),
                collectCount = interact.optCount("collectedCount"),
                shareCount = interact.optCount("shareCount"),
                playCount = interact.optCount("viewCount"),
            ),
            description = note.optString("desc"),
            authorId = user?.let {
                it.optString("authorID")
                    .ifBlank { it.optString("userId").ifBlank { it.optString("user_id") } }
            }.orEmpty(),
            authorHandle = user?.let {
                it.optString("redId").ifBlank { it.optString("red_id") }
            }.orEmpty(),
            authorAvatarUrl = secureUrl(user?.optJSONObject("avatar")?.optString("url").orEmpty()),
            hashtags = tagList.toTagNames(),
            location = note.optString("ipLocation")
                .ifBlank { note.optString("location") }
                .ifBlank { note.optJSONObject("poiInfo")?.optString("name").orEmpty() },
            shareUrl = note.optString("shareUrl").ifBlank { sourceUrl },
            videoUrl = videoUrl,
            videoCandidates = candidates,
            musicUrl = musicUrl,
            musicTitle = music?.optString("title").orEmpty(),
            musicAuthor = music?.optString("author").orEmpty(),
            musicDurationMs = if (musicDuration in 1..999) musicDuration * 1000 else musicDuration,
            musicCoverUrl = musicCover,
            images = images.toMediaImages(),
        )
    }

    private fun JSONArray?.toTagNames(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = opt(index)
                val name = when (item) {
                    is JSONObject -> item.optString("name")
                        .ifBlank { item.optString("tagName") }
                        .ifBlank { item.optString("tag_name") }
                    else -> item?.toString().orEmpty()
                }.trim()
                if (name.isNotEmpty() && name !in this) add(name)
            }
        }
    }

    private fun JSONArray?.toVideoCandidates(): List<VideoCandidate> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val backupUrls = item.optJSONArray("backupUrls")
                    ?: item.optJSONArray("backup_urls")
                val url = secureUrl(
                    item.optString("masterUrl")
                        .ifBlank { item.optString("master_url") }
                        .ifBlank { backupUrls?.optString(0).orEmpty() }
                )
                if (url.isBlank()) continue
                val bitrate = sequenceOf(
                    item.optLong("avgBitrate"),
                    item.optLong("avg_bitrate"),
                    item.optLong("videoBitrate"),
                    item.optLong("video_bitrate"),
                ).firstOrNull { it > 0 } ?: 0L
                val width = sequenceOf(
                    item.optInt("width"),
                    item.optInt("videoWidth"),
                    item.optInt("video_width"),
                ).firstOrNull { it > 0 } ?: 0
                val height = sequenceOf(
                    item.optInt("height"),
                    item.optInt("videoHeight"),
                    item.optInt("video_height"),
                ).firstOrNull { it > 0 } ?: 0
                val sizeBytes = sequenceOf(
                    item.optLong("size"),
                    item.optLong("fileSize"),
                    item.optLong("file_size"),
                    item.optLong("videoSize"),
                    item.optLong("video_size"),
                ).firstOrNull { it > 0 } ?: 0L
                add(
                    VideoCandidate(
                        label = item.optString("streamDesc")
                            .ifBlank { item.optString("stream_desc", "h264_$index") },
                        url = url,
                        bitrate = bitrate,
                        width = width,
                        height = height,
                        sizeBytes = sizeBytes,
                        sourceKind = VideoSourceKind.TRANSCODED,
                        watermarkRisk = true,
                    )
                )
            }
        }
    }

    private fun mediaV2Object(video: JSONObject?): JSONObject? {
        if (video == null) return null
        return when (val value = video.opt("mediaV2")) {
            is JSONObject -> value
            is String -> runCatching { JSONObject(value) }.getOrNull()
            else -> null
        }
    }

    private fun mediaV2Streams(video: JSONObject?): JSONObject? =
        mediaV2Object(video)?.optJSONObject("stream")

    private fun originVideoKey(video: JSONObject?): String {
        if (video == null) return ""
        val consumer = video.optJSONObject("consumer")
        val direct = consumer?.optString("originVideoKey").orEmpty()
            .ifBlank { consumer?.optString("origin_video_key").orEmpty() }
            .ifBlank { video.optString("originVideoKey") }
        if (direct.isNotBlank()) return direct.trim()
        val mediaConsumer = mediaV2Object(video)?.optJSONObject("consumer")
        return mediaConsumer?.optString("originVideoKey").orEmpty()
            .ifBlank { mediaConsumer?.optString("origin_video_key").orEmpty() }
            .trim()
    }

    private fun originVideoUrl(key: String): String {
        if (key.isBlank()) return ""
        if (key.startsWith("http://") || key.startsWith("https://")) return key
        return "https://sns-video-bd.xhscdn.com/${key.trimStart('/')}"
    }

    private fun pickNoteKey(
        noteMap: JSONObject,
        expectedId: String?,
        currentNoteId: String?,
    ): String? {
        expectedId?.takeIf { noteMap.has(it) }?.let { return it }
        currentNoteId?.takeIf { noteMap.has(it) }?.let { return it }
        return if (noteMap.length() == 1) noteMap.keys().asSequence().firstOrNull() else null
    }

    private fun noteIdFromUrl(url: String): String? =
        Regex("""(?:explore|discovery/item|item)/([a-f0-9]{24})""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)

    private fun JSONArray?.toMediaImages(): List<MediaImage> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val url = item.bestImageUrl()
                if (url.isBlank()) continue
                add(
                    MediaImage(
                        url = secureUrl(url),
                        originalUrl = item.originalImageUrl(),
                        width = item.optInt("width").coerceAtLeast(0),
                        height = item.optInt("height").coerceAtLeast(0),
                    )
                )
            }
        }
    }

    /**
     * Resolves the clean image source for a gallery item.
     *
     * Xiaohongshu's mobile H5 variants embed a platform watermark. When no
     * explicit original is supplied, the stable `fileId` still maps to the
     * platform's clean image CDN. The format transform keeps `.jpg` filenames
     * accurate even when the stored original is a PNG.
     */
    private fun JSONObject.originalImageUrl(): String {
        val explicit = secureUrl(optString("originalImageUrl"))
        if (explicit.isNotBlank()) return explicit

        val fileId = optString("fileId").trim().trimStart('/')
        if (!CLEAN_IMAGE_FILE_ID.matches(fileId) || fileId.split('/').any { it == ".." }) {
            return ""
        }
        return "https://sns-img-bd.xhscdn.com/$fileId?imageView2/2/format/jpg"
    }

    private fun JSONObject?.bestImageUrl(): String {
        if (this == null) return ""
        var best = optString("urlDefault").ifBlank { optString("url") }
        var bestArea = optInt("width") * optInt("height")
        val infoList = optJSONArray("infoList")
        if (infoList != null) {
            for (index in 0 until infoList.length()) {
                val info = infoList.optJSONObject(index) ?: continue
                val url = info.optString("url")
                if (url.isBlank()) continue
                val area = info.optInt("width") * info.optInt("height")
                if (area > bestArea) {
                    best = url
                    bestArea = area
                }
            }
        }
        return best
    }

    private fun JSONArray?.optDurationMs(video: JSONObject?): Long {
        val first = this?.optJSONObject(0)
        val duration = first?.optLong("duration", first.optLong("videoDuration")) ?: 0L
        if (duration > 0) return duration
        val media = video?.optJSONObject("media")?.optJSONObject("video")
        val fallback = media?.optLong("duration") ?: 0L
        return if (fallback in 1..999) fallback * 1000 else fallback
    }

    private fun JSONObject?.optCount(key: String): Long {
        if (this == null || !has(key) || isNull(key)) return 0
        val value = opt(key) ?: return 0
        if (value is Number) return value.toLong().coerceAtLeast(0)
        val text = value.toString().trim()
        val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*([万亿wW]?)""").find(text)
            ?: return 0
        val amount = match.groupValues[1].toDoubleOrNull() ?: return 0
        val multiplier = when (match.groupValues[2]) {
            "万", "w", "W" -> 10_000.0
            "亿" -> 100_000_000.0
            else -> 1.0
        }
        return (amount * multiplier).toLong().coerceAtLeast(0)
    }

    private fun deriveId(url: String, authorId: String): String {
        Regex("""(?:explore|discovery/item|item)/([a-f0-9]{24})""").find(url)?.let {
            return it.groupValues[1]
        }
        val stable = authorId.ifBlank { url }
        val digest = MessageDigest.getInstance("MD5")
            .digest(stable.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "xhs" + digest.take(16)
    }

    private fun failure(message: String, detail: String? = null): ParseResult.Failure =
        ParseResult.Failure(
            ParseFailure(
                type = ParseFailureType.RESPONSE_CHANGED,
                message = message,
                debugDetail = detail,
            )
        )

    private fun secureUrl(url: String): String =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    private val CLEAN_IMAGE_FILE_ID = Regex("""[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*""")
}