package com.douyin.data.favorites

import android.content.Context
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
                    o.getString("awemeId"),
                    o.getString("title"),
                    o.getString("cover"),
                    o.getLong("time")
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
            arr.put(
                JSONObject()
                    .put("awemeId", it.awemeId)
                    .put("title", it.title)
                    .put("cover", it.cover)
                    .put("time", it.time)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val PREFS = "favorites"
        const val KEY = "items"
    }
}
