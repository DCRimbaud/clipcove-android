package com.douyin.data.xhs

import com.douyin.core.model.MediaPlatform
import com.douyin.core.model.ParseFailureType
import com.douyin.core.model.ParseResult
import com.douyin.core.model.VideoSourceKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XhsSsrParserTest {

    @Test
    fun `maps video note and human readable counters`() {
        val note = JSONObject()
            .put("noteId", NOTE_ID)
            .put("title", "视频笔记")
            .put("time", 1_789_951_505_000L)
            .put("user", JSONObject().put("nickname", "作者").put("authorID", "author-1"))
            .put(
                "interactInfo",
                JSONObject()
                    .put("likedCount", "1.2万")
                    .put("commentCount", "8")
                    .put("collectedCount", 30)
                    .put("shareCount", "2")
            )
            .put(
                "video",
                JSONObject().put(
                    "media",
                    JSONObject().put(
                        "stream",
                        JSONObject().put(
                            "h264",
                            JSONArray().put(
                                JSONObject()
                                    .put("masterUrl", "https://example.com/video.mp4")
                                    .put("streamDesc", "X264_MP4")
                                    .put("avgBitrate", 1_338_971)
                                    .put("duration", 10_520)
                            )
                        )
                    )
                )
            )

        val result = XhsSsrParser.parse(html(stateFor(note)), sourceUrl())

        val media = (result as ParseResult.Success).media
        assertEquals(MediaPlatform.XIAOHONGSHU, media.platform)
        assertEquals(NOTE_ID, media.id)
        assertEquals("视频笔记", media.title)
        assertEquals("https://example.com/video.mp4", media.videoUrl)
        assertEquals(10_520L, media.durationMs)
        assertEquals(12_000L, media.stats.diggCount)
        assertEquals(1_789_951_505L, media.createdAtSeconds)
        assertTrue(media.hasVideo)
    }

    @Test
    fun `maps image note and selects larger image variant`() {
        val note = JSONObject()
            .put("noteId", NOTE_ID)
            .put("title", "图文笔记")
            .put("time", 1_789_951_505_000L)
            .put("user", JSONObject().put("nickname", "作者"))
            .put(
                "imageList",
                JSONArray().put(
                    JSONObject()
                        .put("urlDefault", "https://example.com/small.jpg")
                        .put("originalImageUrl", "https://example.com/original.jpg")
                        .put("width", 720)
                        .put("height", 960)
                        .put(
                            "infoList",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("url", "https://example.com/small.jpg")
                                        .put("width", 720)
                                        .put("height", 960)
                                )
                                .put(
                                    JSONObject()
                                        .put("url", "https://example.com/large.jpg")
                                        .put("width", 1440)
                                        .put("height", 1920)
                                )
                        )
                )
            )

        val result = XhsSsrParser.parse(html(stateFor(note)), sourceUrl())

        val media = (result as ParseResult.Success).media
        assertEquals(1, media.images.size)
        assertEquals("https://example.com/large.jpg", media.images.single().url)
        assertEquals("https://example.com/original.jpg", media.images.single().preferredUrl)
        assertTrue(media.hasImages)
    }

    @Test
    fun `upgrades insecure CDN URLs to HTTPS`() {
        val note = JSONObject()
            .put("noteId", NOTE_ID)
            .put("title", "HTTPS")
            .put("type", "video")
            .put(
                "video",
                JSONObject().put(
                    "media",
                    JSONObject().put(
                        "stream",
                        JSONObject().put(
                            "h264",
                            JSONArray().put(
                                JSONObject()
                                    .put("masterUrl", "http://example.com/video.mp4")
                            )
                        )
                    )
                )
            )
            .put(
                "imageList",
                JSONArray().put(
                    JSONObject()
                        .put("urlDefault", "http://example.com/cover.jpg")
                        .put("originalImageUrl", "http://example.com/original.jpg")
                )
            )

        val media = (XhsSsrParser.parse(html(stateFor(note)), sourceUrl()) as ParseResult.Success).media

        assertEquals("https://example.com/video.mp4", media.videoUrl)
        assertEquals("https://example.com/cover.jpg", media.coverUrl)
        assertEquals("https://example.com/original.jpg", media.images.single().preferredUrl)
    }

    @Test
    fun `prefers origin video over watermarked stream`() {
        val note = JSONObject()
            .put("noteId", NOTE_ID)
            .put("title", "原片测试")
            .put("type", "video")
            .put("user", JSONObject().put("nickname", "作者"))
            .put(
                "video",
                JSONObject()
                    .put("consumer", JSONObject().put("originVideoKey", "spectrum/original.mp4"))
                    .put(
                        "media",
                        JSONObject().put(
                            "stream",
                            JSONObject().put(
                                "h264",
                                JSONArray().put(
                                    JSONObject()
                                        .put("masterUrl", "https://example.com/watermarked.mp4")
                                        .put("avgBitrate", 2_000_000)
                                        .put("duration", 23_000)
                                        .put("width", 720)
                                        .put("height", 1280)
                                        .put("size", 6_162_441)
                                )
                            )
                        )
                    )
            )

        val result = XhsSsrParser.parse(html(stateFor(note)), sourceUrl())

        val media = (result as ParseResult.Success).media
        assertEquals("https://sns-video-bd.xhscdn.com/spectrum/original.mp4", media.videoUrl)
        assertEquals("https://example.com/watermarked.mp4", media.videoCandidates.last().url)
        assertEquals(VideoSourceKind.ORIGINAL, media.videoCandidates.first().sourceKind)
        assertFalse(media.videoCandidates.first().watermarkRisk)
        assertEquals(VideoSourceKind.TRANSCODED, media.videoCandidates.last().sourceKind)
        assertTrue(media.videoCandidates.last().watermarkRisk)
        assertEquals(720, media.videoCandidates.last().width)
        assertEquals(1280, media.videoCandidates.last().height)
        assertEquals(6_162_441L, media.videoCandidates.last().sizeBytes)
    }

    @Test
    fun `maps mobile noteData SSR payload`() {
        val mobileNote = JSONObject()
            .put("noteId", NOTE_ID)
            .put("type", "video")
            .put("title", "")
            .put("desc", "#测试话题[话题]#")
            .put("time", 1_792_861_419_000L)
            .put("user", JSONObject().put("nickName", "移动端作者"))
            .put(
                "video",
                JSONObject().put(
                    "media",
                    JSONObject().put(
                        "stream",
                        JSONObject().put(
                            "h264",
                            JSONArray().put(
                                JSONObject()
                                    .put("masterUrl", "https://example.com/mobile.mp4")
                                    .put("avgBitrate", 2_133_809)
                                    .put("duration", 23_104)
                            )
                        )
                    )
                )
            )
            .put(
                "imageList",
                JSONArray().put(JSONObject().put("url", "https://example.com/mobile-cover.jpg"))
            )
        val state = JSONObject().put(
            "noteData",
            JSONObject().put(
                "data",
                JSONObject().put("noteData", mobileNote)
            )
        )

        val result = XhsSsrParser.parse(html(state), sourceUrl())

        val media = (result as ParseResult.Success).media
        assertEquals(NOTE_ID, media.id)
        assertEquals("移动端作者", media.author)
        assertEquals("https://example.com/mobile.mp4", media.videoUrl)
        assertEquals("https://example.com/mobile-cover.jpg", media.coverUrl)
    }

    @Test
    fun `selects the note matching the share url`() {
        val wrongNote = JSONObject()
            .put("noteId", "aaaaaaaaaaaaaaaaaaaaaaaa")
            .put("title", "错误笔记")
            .put("type", "normal")
            .put("imageList", JSONArray().put(JSONObject().put("urlDefault", "https://example.com/wrong.jpg")))
        val correctNote = JSONObject()
            .put("noteId", NOTE_ID)
            .put("title", "正确笔记")
            .put("type", "normal")
            .put("user", JSONObject().put("nickname", "正确作者"))
            .put("imageList", JSONArray().put(JSONObject().put("urlDefault", "https://example.com/right.jpg")))

        val noteMap = JSONObject()
            .put("aaaaaaaaaaaaaaaaaaaaaaaa", JSONObject().put("note", wrongNote))
            .put(NOTE_ID, JSONObject().put("note", correctNote))
        val state = JSONObject().put(
            "note",
            JSONObject().put("noteDetailMap", noteMap).put("currentNoteId", "aaaaaaaaaaaaaaaaaaaaaaaa")
        )

        val result = XhsSsrParser.parse(html(state), sourceUrl())

        val media = (result as ParseResult.Success).media
        assertEquals(NOTE_ID, media.id)
        assertEquals("正确笔记", media.title)
        assertEquals("https://example.com/right.jpg", media.coverUrl)
    }

    @Test
    fun `supports mediaV2 snake case video stream`() {
        val mediaV2 = JSONObject().put(
            "stream",
            JSONObject().put(
                "h264",
                JSONArray().put(
                    JSONObject()
                        .put("master_url", "https://example.com/media-v2.mp4")
                        .put("stream_desc", "X264_MP4")
                        .put("avg_bitrate", 2_973_769)
                        .put("duration", 23_104)
                )
            )
        )
        val note = JSONObject()
            .put("noteId", NOTE_ID)
            .put("title", "mediaV2 视频")
            .put("type", "video")
            .put("user", JSONObject().put("nickname", "作者"))
            .put(
                "video",
                JSONObject()
                    .put("media", JSONObject().put("stream", JSONObject().put("h264", JSONArray())))
                    .put("mediaV2", mediaV2.toString())
            )

        val result = XhsSsrParser.parse(html(stateFor(note)), sourceUrl())

        val media = (result as ParseResult.Success).media
        assertEquals("https://example.com/media-v2.mp4", media.videoUrl)
        assertEquals(23_104L, media.durationMs)
        assertEquals(2_973_769L, media.videoCandidates.single().bitrate)
    }

    @Test
    fun `rejects a video note without a playable stream`() {
        val note = JSONObject()
            .put("noteId", NOTE_ID)
            .put("title", "无视频流")
            .put("type", "video")
            .put("video", JSONObject().put("media", JSONObject().put("stream", JSONObject())))
            .put("imageList", JSONArray().put(JSONObject().put("urlDefault", "https://example.com/cover.jpg")))

        val result = XhsSsrParser.parse(html(stateFor(note)), sourceUrl())

        val failure = (result as ParseResult.Failure).error
        assertEquals(ParseFailureType.RESPONSE_CHANGED, failure.type)
        assertTrue(failure.message.contains("视频地址"))
    }

    @Test
    fun `reports changed page structure when state is absent`() {
        val result = XhsSsrParser.parse("<html><body>blocked</body></html>", sourceUrl())

        val failure = (result as ParseResult.Failure).error
        assertEquals(ParseFailureType.RESPONSE_CHANGED, failure.type)
        assertTrue(failure.isRetryable)
    }

    private fun stateFor(note: JSONObject): JSONObject =
        JSONObject().put(
            "note",
            JSONObject().put(
                "noteDetailMap",
                JSONObject().put(NOTE_ID, JSONObject().put("note", note))
            )
        )

    private fun html(state: JSONObject): String =
        """<html><script>window.__INITIAL_STATE__=$state</script></html>"""

    private fun sourceUrl(): String = "https://www.xiaohongshu.com/explore/$NOTE_ID?xsec_token=test"

    private companion object {
        const val NOTE_ID = "6ab07e11000000001203e63e"
    }
}
