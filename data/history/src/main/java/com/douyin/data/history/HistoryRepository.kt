/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines bounded history persistence and source-link retention for re-resolution.
 */
package com.douyin.data.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted parse-history entry.
 * @param sourceUrl Original share URL; Xiaohongshu needs its time-limited token to resolve again.
 */
data class HistoryItem(
    val awemeId: String,
    val title: String,
    val cover: String,
    val time: Long,
    val sourceUrl: String = ""
)

/** History persistence port used by feature code. */
interface HistoryRepository {
    /** Returns history newest first, recovering safely from corrupt storage. */
    fun load(): List<HistoryItem>
    /** Adds or replaces one history entry by stable media ID. */
    fun add(item: HistoryItem)
    /** Deletes all history entries without touching favorites or downloads. */
    fun clear()
}

/** SharedPreferences-backed history with a twenty-item cap and stable-ID deduplication. */
class PrefsHistoryRepository(context: Context) : HistoryRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): List<HistoryItem> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryItem(
                    o.getString("awemeId"),
                    o.getString("title"),
                    o.getString("cover"),
                    o.getLong("time"),
                    o.optString("sourceUrl", "")
                )
            }
        } catch (e: Exception) {
            // Corrupt persisted data is ignored rather than crashing the app.
            emptyList()
        }
    }

    override fun add(item: HistoryItem) {
        val list = load().toMutableList()
        list.removeAll { it.awemeId == item.awemeId }
        list.add(0, item)
        while (list.size > MAX) list.removeAt(list.size - 1)
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("awemeId", it.awemeId)
                    .put("title", it.title)
                    .put("cover", it.cover)
                    .put("time", it.time)
                    .put("sourceUrl", it.sourceUrl)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val PREFS = "history"
        const val KEY = "items"
        const val MAX = 20
    }
}