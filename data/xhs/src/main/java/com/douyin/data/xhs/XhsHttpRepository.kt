package com.douyin.data.xhs

import android.util.Log
import com.douyin.core.model.ParseFailure
import com.douyin.core.model.ParseFailureType
import com.douyin.core.model.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 小红书官方笔记页适配器。
 *
 * 只走“官方短链跳转 + 笔记页 SSR”这一条可验证链路。旧版两个第三方接口
 * 已失效（一个要求公众号认证，另一个返回空数据），继续调用只会增加延迟。
 */
class XhsHttpRepository : XhsRepository {

    override suspend fun resolve(url: String): ParseResult = withContext(Dispatchers.IO) {
        val host = runCatching { URL(url.ifBlank { "http://" }).host }.getOrNull().orEmpty()
        if (!isAllowedHost(host)) {
            return@withContext ParseResult.Failure(
                ParseFailure(ParseFailureType.UNSUPPORTED_PLATFORM, "仅支持小红书链接")
            )
        }

        try {
            val html = fetchWithRedirects(url)
            XhsSsrParser.parse(html, url)
        } catch (e: XhsFetchException) {
            Log.w(TAG, "XHS fetch failed: ${e.debugDetail}", e)
            ParseResult.Failure(ParseFailure(e.type, e.message.orEmpty(), e.debugDetail))
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "XHS request timed out", e)
            ParseResult.Failure(
                ParseFailure(ParseFailureType.NETWORK, "请求小红书超时，请检查网络后重试", e.message)
            )
        } catch (e: IOException) {
            Log.w(TAG, "XHS network error", e)
            ParseResult.Failure(
                ParseFailure(ParseFailureType.NETWORK, "网络连接失败，请检查网络后重试", e.message)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected XHS resolver error", e)
            ParseResult.Failure(
                ParseFailure(ParseFailureType.UNKNOWN, "小红书解析失败，请稍后重试", e.message)
            )
        }
    }

    private fun fetchWithRedirects(initialUrl: String, maxHops: Int = 6): String {
        var current = initialUrl
        var hops = 0
        while (hops < maxHops) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
            connection.setRequestProperty("Referer", "https://www.xiaohongshu.com/")

            val code = connection.responseCode
            if (code in 300..399 && code != 304) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw XhsFetchException(
                        ParseFailureType.INVALID_LINK,
                        "小红书短链缺少跳转地址",
                        "HTTP $code without Location",
                    )
                }
                val next = URL(URL(current), location)
                if (!isAllowedHost(next.host)) {
                    throw XhsFetchException(
                        ParseFailureType.INVALID_LINK,
                        "小红书短链跳转到了非官方域名",
                        next.host,
                    )
                }
                current = next.toString()
                hops++
                continue
            }

            if (code != 200) {
                connection.disconnect()
                throw statusFailure(code)
            }

            return try {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    .also {
                        if (it.isBlank()) {
                            throw XhsFetchException(
                                ParseFailureType.RESPONSE_CHANGED,
                                "小红书返回了空白页面",
                                "HTTP 200, empty body",
                            )
                        }
                    }
            } finally {
                connection.disconnect()
            }
        }
        throw XhsFetchException(
            ParseFailureType.RESPONSE_CHANGED,
            "小红书短链跳转次数过多",
            "redirects > $maxHops",
        )
    }

    private fun statusFailure(code: Int): XhsFetchException = when {
        code == 401 || code == 403 || code == 412 || code == 429 -> XhsFetchException(
            ParseFailureType.RISK_CONTROL,
            "小红书访问被限流，请稍后重试",
            "HTTP $code",
        )
        code == 404 || code == 410 -> XhsFetchException(
            ParseFailureType.CONTENT_UNAVAILABLE,
            "笔记不存在、已删除或当前不可见",
            "HTTP $code",
        )
        code >= 500 -> XhsFetchException(
            ParseFailureType.NETWORK,
            "小红书服务暂时不可用，请稍后重试",
            "HTTP $code",
        )
        else -> XhsFetchException(
            ParseFailureType.RESPONSE_CHANGED,
            "小红书接口返回异常（HTTP $code）",
            "HTTP $code",
        )
    }

    private fun isAllowedHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "xiaohongshu.com" || normalized.endsWith(".xiaohongshu.com") ||
            normalized == "xhslink.com" || normalized.endsWith(".xhslink.com") ||
            normalized == "xhslink.cn" || normalized.endsWith(".xhslink.cn")
    }

    private class XhsFetchException(
        val type: ParseFailureType,
        message: String,
        val debugDetail: String,
    ) : IOException(message)

    private companion object {
        const val TAG = "XhsRepository"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 15_000
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }
}
