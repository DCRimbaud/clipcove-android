/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Protects Douyin mapping invariants for media, metadata, URLs, and structured failures.
 */
package com.douyin.data.douyin

import com.douyin.core.model.MediaPlatform
import com.douyin.core.model.ParseFailureType
import com.douyin.core.model.ParseResult
import com.douyin.core.model.VideoSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protects Douyin media, metadata, URL, and failure mapping invariants. */
class DouyinMediaMapperTest {

    /** Ensures video metadata and source candidates map into the normalized media contract. */
    @Test
    fun `maps video result to typed media`() {
        val result = DouyinMediaMapper.fromPythonJson(
            """
            {
              "aweme_id":"7345492945006595379",
              "title":"测试视频",
              "description":"完整作品说明",
              "author":"作者",
              "author_id":"user-1",
              "author_handle":"clipcove",
              "author_avatar":"https://example.com/avatar.jpg",
              "hashtags":["测试","视频"],
              "location":"上海",
              "share_url":"https://www.douyin.com/video/7345492945006595379",
              "cover":"https://example.com/cover.jpg",
              "create_time":1700000000,
              "duration":12500,
              "stats":{"digg_count":10,"comment_count":2,"collect_count":3,"share_count":4,"play_count":1000,"recommend_count":20},
              "play_url":"https://example.com/video.mp4",
              "play_urls":[{"label":"bitrate_0","url":"https://example.com/video.mp4","bitrate":2048,"width":1080,"height":1920,"size":4096}],
              "music":"https://example.com/music.mp3",
              "music_title":"原声",
              "music_author":"音乐人",
              "music_duration":44,
              "music_cover":"https://example.com/music.jpg",
              "images":[]
            }
            """.trimIndent()
        )

        assertTrue(result is ParseResult.Success)
        val media = (result as ParseResult.Success).media
        assertEquals(MediaPlatform.DOUYIN, media.platform)
        assertEquals("7345492945006595379", media.id)
        assertEquals("https://example.com/video.mp4", media.videoUrl)
        assertEquals(1, media.videoCandidates.size)
        assertEquals(VideoSourceKind.TRANSCODED, media.videoCandidates.single().sourceKind)
        assertFalse(media.videoCandidates.single().watermarkRisk)
        assertEquals(10L, media.stats.diggCount)
        assertEquals(1_000L, media.stats.playCount)
        assertEquals("完整作品说明", media.description)
        assertEquals("clipcove", media.authorHandle)
        assertEquals(listOf("测试", "视频"), media.hashtags)
        assertEquals("上海", media.location)
        assertEquals("https://www.douyin.com/video/7345492945006595379", media.shareUrl)
        assertEquals("音乐人", media.musicAuthor)
        assertEquals(44_000L, media.musicDurationMs)
        assertEquals(1080, media.videoCandidates.single().width)
        assertEquals(1920, media.videoCandidates.single().height)
        assertEquals(4096L, media.videoCandidates.single().sizeBytes)
        assertTrue(media.hasVideo)
    }

    /** Ensures legacy play URLs remain downloadable through a typed candidate. */
    @Test
    fun `creates a typed candidate when only play url is present`() {
        val result = DouyinMediaMapper.fromPythonJson(
            """
            {
              "aweme_id":"7345492945006595379",
              "title":"Fallback",
              "play_url":"https://example.com/fallback.mp4",
              "images":[]
            }
            """.trimIndent()
        )

        val media = (result as ParseResult.Success).media
        assertEquals("https://example.com/fallback.mp4", media.videoUrl)
        assertEquals(1, media.videoCandidates.size)
        assertEquals(VideoSourceKind.TRANSCODED, media.videoCandidates.single().sourceKind)
    }

    /** Ensures platform media URLs never remain cleartext. */
    @Test
    fun `upgrades insecure platform URLs to HTTPS`() {
        val result = DouyinMediaMapper.fromPythonJson(
            """
            {
              "aweme_id":"7345492945006595379",
              "title":"HTTPS",
              "cover":"http://example.com/cover.jpg",
              "play_url":"http://example.com/video.mp4",
              "play_urls":[{"label":"best","url":"http://example.com/video.mp4"}],
              "music":"http://example.com/music.mp3",
              "images":[{"url":"http://example.com/image.jpg"}]
            }
            """.trimIndent()
        )

        val media = (result as ParseResult.Success).media
        assertEquals("https://example.com/cover.jpg", media.coverUrl)
        assertEquals("https://example.com/video.mp4", media.videoUrl)
        assertEquals("https://example.com/video.mp4", media.videoCandidates.single().url)
        assertEquals("https://example.com/music.mp3", media.musicUrl)
        assertEquals("https://example.com/image.jpg", media.images.single().url)
    }

    /** Ensures gallery images use an accessible URL and preserve the Live Photo stream. */
    @Test
    fun `maps image note and live photo`() {
        val result = DouyinMediaMapper.fromPythonJson(
            """
            {
              "aweme_id":"7616399587141737704",
              "title":"图文",
              "author":"作者",
              "create_time":1700000000,
              "images":[{
                "url":"https://example.com/a.jpg",
                "original_url":"https://example.com/a-original.jpg",
                "gif_url":"https://example.com/a.mp4",
                "width":1080,
                "height":1440
              }]
            }
            """.trimIndent()
        )

        val media = (result as ParseResult.Success).media
        assertEquals(1, media.images.size)
        assertEquals("https://example.com/a.jpg", media.images.single().preferredUrl)
        assertEquals("https://example.com/a.mp4", media.images.single().livePhotoUrl)
        assertTrue(media.hasImages)
    }

    /** Ensures structured risk-control payloads become typed non-retryable failures. */
    @Test
    fun `maps structured risk control error`() {
        val result = DouyinMediaMapper.fromPythonJson(
            """{"error":{"code":"RISK_CONTROL","message":"触发访问风控","debug_detail":"403"}}"""
        )

        val failure = (result as ParseResult.Failure).error
        assertEquals(ParseFailureType.RISK_CONTROL, failure.type)
        assertEquals("触发访问风控", failure.message)
        assertEquals(false, failure.isRetryable)
    }

    /** Ensures legacy error strings still classify as invalid-link failures. */
    @Test
    fun `supports legacy string errors`() {
        val result = DouyinMediaMapper.fromPythonJson("""{"error":"链接无效: 未找到链接"}""")

        val failure = (result as ParseResult.Failure).error
        assertEquals(ParseFailureType.INVALID_LINK, failure.type)
    }

    /** Ensures malformed parser output becomes a retryable response-change failure. */
    @Test
    fun `rejects malformed result`() {
        val result = DouyinMediaMapper.fromPythonJson("not-json")

        val failure = (result as ParseResult.Failure).error
        assertEquals(ParseFailureType.RESPONSE_CHANGED, failure.type)
        assertTrue(failure.isRetryable)
    }
}