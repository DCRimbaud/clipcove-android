package com.douyin.data.xhs

import com.douyin.core.model.MediaPlatform
import com.douyin.core.model.ParseFailureType
import com.douyin.core.model.ParseResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
