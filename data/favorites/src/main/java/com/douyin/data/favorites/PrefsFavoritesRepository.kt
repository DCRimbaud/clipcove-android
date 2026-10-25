package com.douyin.data.favorites

import android.content.Context
import com.douyin.core.model.MediaPlatform
import org.json.JSONArray
import org.json.JSONObject

/** SharedPreferences 实现 */
class PrefsFavoritesRepository(context: Context) : FavoritesRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): List<FavoriteItem> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FavoriteItem(
                    awemeId = o.getString("awemeId"),
                    title = o.getString("title"),
                    cover = o.getString("cover"),
                    time = o.getLong("time"),
                    sourceUrl = o.optString("sourceUrl"),
                    platform = o.optString("platform")
                        .takeIf { it.isNotBlank() }
                        ?.let { value -> runCatching { MediaPlatform.valueOf(value) }.getOrNull() },
                )
            }
        } catch (e: Exception) {
            // 数据损坏时容错,不崩溃
            emptyList()
        }
    }

    override fun add(item: FavoriteItem) {
        val list = load().toMutableList()
        list.removeAll { it.awemeId == item.awemeId }
        list.add(0, item)
        save(list)
    }

    override fun remove(awemeId: String) {
        save(load().filterNot { it.awemeId == awemeId })
    }

    override fun isFavorite(awemeId: String): Boolean =
        load().any { it.awemeId == awemeId }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(list: List<FavoriteItem>) {
        val arr = JSONArray()
        list.forEach {
            val json = JSONObject()
                .put("awemeId", it.awemeId)
                .put("title", it.title)
                .put("cover", it.cover)
                .put("time", it.time)
                .put("sourceUrl", it.sourceUrl)
            it.platform?.let { platform -> json.put("platform", platform.name) }
            arr.put(json)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val PREFS = "favorites"
        const val KEY = "items"
    }
}
