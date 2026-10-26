/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Encodes and decodes versioned backup payloads with legacy compatibility.
 */
package com.douyin.feature.settings

import com.douyin.data.favorites.FavoriteItem
import com.douyin.data.history.HistoryItem
import org.json.JSONArray
import org.json.JSONObject

/** Decoded backup contents plus metadata used for validation and diagnostics. */
internal data class BackupPayload(
    val history: List<HistoryItem>,
    val favorites: List<FavoriteItem>,
)

/** Versioned JSON backup codec with read compatibility for legacy flat arrays. */
internal object BackupCodec {
    const val SCHEMA_VERSION = 1
    private const val APP_NAME = "ClipCove"

    /** Serializes history and favorites into the current backup schema. */
    fun encode(
        history: List<HistoryItem>,
        favorites: List<FavoriteItem>,
        exportedAt: Long = System.currentTimeMillis(),
        appVersion: String = "",
    ): String {
        val historyArray = JSONArray()
        history.forEach { item ->
            historyArray.put(
                JSONObject()
                    .put("awemeId", item.awemeId)
                    .put("title", item.title)
                    .put("cover", item.cover)
                    .put("time", item.time)
                    .put("sourceUrl", item.sourceUrl)
            )
        }

        val favoritesArray = JSONArray()
        favorites.forEach { item ->
            val json = JSONObject()
                .put("awemeId", item.awemeId)
                .put("title", item.title)
                .put("cover", item.cover)
                .put("time", item.time)
                .put("sourceUrl", item.sourceUrl)
            item.platform?.let { platform -> json.put("platform", platform.name) }
            favoritesArray.put(json)
        }

        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("app", APP_NAME)
            .put("appVersion", appVersion)
            .put("exportedAt", exportedAt)
            .put("history", historyArray)
            .put("favorites", favoritesArray)
            .toString(2)
    }

    /**
     * Parses the current schema and the legacy flat-array schema.
     *
     * @throws IllegalArgumentException when JSON or required fields are invalid.
     */
    fun decode(raw: String): BackupPayload {
        val clean = raw.removePrefix("\uFEFF").trim()
        if (clean.isBlank()) throw IllegalArgumentException("Backup file is empty")
        return try {
            when (clean.first()) {
                '[' -> decodeLegacyArray(JSONArray(clean))
                '{' -> decodeVersionedObject(JSONObject(clean))
                else -> throw IllegalArgumentException("Unsupported backup format")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Backup file is not valid JSON", e)
        }
    }

    private fun decodeVersionedObject(root: JSONObject): BackupPayload {
        val version = root.optInt("schemaVersion", SCHEMA_VERSION)
        if (version > SCHEMA_VERSION) {
            throw IllegalArgumentException("Backup schema $version is newer than this app supports")
        }
        if (version < 1) throw IllegalArgumentException("Unsupported backup schema: $version")
        return BackupPayload(
            history = root.optJSONArray("history").toHistoryItems(),
            favorites = root.optJSONArray("favorites").toFavoriteItems(),
        )
    }

    private fun decodeLegacyArray(array: JSONArray): BackupPayload {
        val history = mutableListOf<HistoryItem>()
        val favorites = mutableListOf<FavoriteItem>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            when (item.optString("type").lowercase()) {
                "history" -> item.toHistoryItem()?.let(history::add)
                "favorite", "favourite" -> item.toFavoriteItem()?.let(favorites::add)
            }
        }
        return BackupPayload(history = history, favorites = favorites)
    }

    private fun JSONArray?.toHistoryItems(): List<HistoryItem> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.toHistoryItem()?.let(::add)
            }
        }
    }

    private fun JSONArray?.toFavoriteItems(): List<FavoriteItem> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.toFavoriteItem()?.let(::add)
            }
        }
    }

    private fun JSONObject.toHistoryItem(): HistoryItem? {
        val id = optString("awemeId").ifBlank { optString("id") }.trim()
        if (id.isBlank()) return null
        return HistoryItem(
            awemeId = id,
            title = optString("title"),
            cover = optString("cover"),
            time = optLong("time").coerceAtLeast(0),
            sourceUrl = optString("sourceUrl"),
        )
    }

    private fun JSONObject.toFavoriteItem(): FavoriteItem? {
        val id = optString("awemeId").ifBlank { optString("id") }.trim()
        if (id.isBlank()) return null
        return FavoriteItem(
            awemeId = id,
            title = optString("title"),
            cover = optString("cover"),
            time = optLong("time").coerceAtLeast(0),
            sourceUrl = optString("sourceUrl"),
            platform = optString("platform")
                .takeIf { it.isNotBlank() }
                ?.let { value -> runCatching { com.douyin.core.model.MediaPlatform.valueOf(value) }.getOrNull() },
        )
    }
}