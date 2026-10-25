package com.douyin.parser

import android.util.Log
import com.chaquo.python.Python
import com.douyin.core.model.ParseFailure
import com.douyin.core.model.ParseFailureType
import com.douyin.core.model.ParseResult
import com.douyin.data.xhs.XhsRepository
import com.douyin.data.xhs.XhsSsrParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 小红书适配器：Python 只负责抓取官方页面，SSR 映射仍由 data:xhs 的 Kotlin 解析器完成。
 * 这样既能使用 Android 上更稳定的 Python 网络栈，也能继续对解析逻辑做 JVM 单测。
 */
class ChaquopyXhsRepository : XhsRepository {

    override suspend fun resolve(url: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            val raw = Python.getInstance()
                .getModule("xhs_resolver")
                .callAttr("fetch_page", url)
                .toString()
            val envelope = JSONObject(raw)
            if (!envelope.isNull("error")) {
                val error = envelope.optJSONObject("error")
                val message = error?.optString("message")
                    ?.ifBlank { null }
                    ?: "小红书页面抓取失败，请稍后重试"
                val type = error?.optString("code")
                    ?.let { runCatching { ParseFailureType.valueOf(it) }.getOrNull() }
                    ?: ParseFailureType.UNKNOWN
                return@withContext ParseResult.Failure(
                    ParseFailure(type, message, error?.optString("debug_detail"))
                )
            }
            val html = envelope.optString("html")
            if (html.isBlank()) {
                return@withContext ParseResult.Failure(
                    ParseFailure(ParseFailureType.RESPONSE_CHANGED, "小红书返回了空白页面")
                )
            }
            val finalUrl = envelope.optString("final_url").ifBlank { url }
            Log.d(TAG, "XHS python page bytes=${html.length} finalUrl=${finalUrl.take(180)}")
            XhsSsrParser.parse(html, finalUrl)
        } catch (e: Exception) {
            Log.e(TAG, "XHS Python fetch failed", e)
            ParseResult.Failure(
                ParseFailure(
                    ParseFailureType.UNKNOWN,
                    "小红书解析引擎启动失败，请重启 App 后重试",
                    e.message,
                )
            )
        }
    }

    private companion object {
        const val TAG = "XhsRepository"
    }
}
