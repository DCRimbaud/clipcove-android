package com.douyin.feature.settings

import com.douyin.data.favorites.FavoriteItem
import com.douyin.data.history.HistoryItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    @Test
    fun `round trips versioned backup with source urls`() {
        val json = BackupCodec.encode(
            history = listOf(HistoryItem("h1", "History", "cover", 10, "https://xhs.link/h")),
            favorites = listOf(FavoriteItem("f1", "Favorite", "cover", 20, "https://xhs.link/f")),
            exportedAt = 100,
            appVersion = "1.1.0",
        )

        val root = JSONObject(json)
        assertEquals(1, root.getInt("schemaVersion"))
        assertEquals("1.1.0", root.getString("appVersion"))

        val payload = BackupCodec.decode(json)
        assertEquals(1, payload.history.size)
        assertEquals("https://xhs.link/h", payload.history.single().sourceUrl)
        assertEquals(1, payload.favorites.size)
        assertEquals("https://xhs.link/f", payload.favorites.single().sourceUrl)
    }

    @Test
    fun `imports legacy flat array export`() {
        val legacy = JSONArray()
            .put(
                JSONObject()
                    .put("type", "history")
                    .put("awemeId", "h1")
                    .put("title", "Old history")
                    .put("time", 10)
            )
            .put(
                JSONObject()
                    .put("type", "favorite")
                    .put("awemeId", "f1")
                    .put("title", "Old favorite")
                    .put("cover", "https://cover")
                    .put("time", 20)
            )
            .toString()

        val payload = BackupCodec.decode(legacy)

        assertEquals(1, payload.history.size)
        assertEquals("h1", payload.history.single().awemeId)
        assertEquals(1, payload.favorites.size)
        assertEquals("f1", payload.favorites.single().awemeId)
    }

    @Test
    fun `rejects a newer schema version`() {
        val error = runCatching {
            BackupCodec.decode("""{"schemaVersion":99,"history":[],"favorites":[]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("newer"))
    }
}
