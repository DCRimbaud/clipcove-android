package com.douyin.data.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 解析历史条目。
 * @param sourceUrl 原始链接(小红书必需:分享链接带时效 token,仅存 ID 无法重新解析)
 */
data class HistoryItem(
    val awemeId: String,
    val title: String,
    val cover: String,
    val time: Long,
    val sourceUrl: String = ""
)

/** 历史仓库接口 */
interface HistoryRepository {
    fun load(): List<HistoryItem>
    fun add(item: HistoryItem)
    fun clear()
}

/** SharedPreferences 实现:最多 20 条,同 ID 去重 */
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
            // 数据损坏时容错,不崩溃
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
