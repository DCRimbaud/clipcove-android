package com.douyin.data.xhs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 小红书解析实现(多源):
 * 1. 主源:直接抓取笔记页 SSR(window.__INITIAL_STATE__),数据最全(标题/作者/点赞/评论/收藏/时间/视频/图片),零第三方依赖
 * 2. 备源:parse.shenzjd.com 公开解析 API
 * 3. 兜底:xhs-download.online(双重 JSON 编码)
 *
 * 说明:分享链接带时效(xsec_token/日期),旧链接会解析失败,返回统一错误提示。
 * 返回统一结构(与抖音一致):{"aweme_id","title","author","cover","play_url","images","stats","duration","create_time"}
 */
class XhsHttpRepository : XhsRepository {

    private val ua =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    override suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        // 域名白名单:仅处理小红书相关域名,防误解析任意 URL
        val host = runCatching {
            java.net.URL(if (url.startsWith("http")) url else "http://$url").host
        }.getOrNull().orEmpty()
        if (!isAllowedHost(host)) {
            return@withContext JSONObject().put("error", "仅支持小红书链接").toString()
        }
        trySsr(url)
            ?: tryParseShenzjd(url)
            ?: tryParseBackup(url)
            ?: JSONObject().put("error", "解析失败,请确认粘贴的是最新分享链接").toString()
    }

    // ============ 主源:SSR 提取 ============

    private fun trySsr(url: String): String? {
        return try {
            val html = fetchWithRedirects(url) ?: return null
            val state = extractSsrJson(html)
            if (state == null) return null
            val note = state.optJSONObject("note") ?: return null
            val ndm = note.optJSONObject("noteDetailMap") ?: return null
            val key = ndm.keys().nextOrNull() ?: return null
            val noteObj = ndm.optJSONObject(key)?.optJSONObject("note") ?: return null
            normalizeSsr(noteObj, key)
        } catch (e: Exception) {
            null
        }
    }

    /** 抓取页面,手动跟随重定向(处理 HTTP→HTTPS 跨协议跳转,最多 5 跳) */
    private fun fetchWithRedirects(url: String, maxHops: Int = 5): String? {
        var current = url
        var hops = 0
        while (hops < maxHops) {
            val conn = java.net.URL(current).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", ua)
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val code = conn.responseCode
            if (code in 301..308) {
                // 重定向目标也须在小红书域内(防跳转到任意站点)
                val location = conn.getHeaderField("Location")
                if (location == null) return null
                val targetHost = runCatching {
                    java.net.URL(java.net.URL(current), location).host
                }.getOrNull() ?: return null
                // 短链可能先在同域/跨域跳转多次，最终才进入 xiaohongshu.com。
                if (!isAllowedHost(targetHost)) return null
                current = java.net.URL(java.net.URL(current), location).toString()
                hops++
                continue
            }
            if (code != 200) return null
            return conn.getInputStream().bufferedReader().use { it.readText() }
        }
        return null
    }

    /** 提取 window.__INITIAL_STATE__ JSON(括号配平,跳过字符串,清洗 undefined) */
    private fun extractSsrJson(html: String): JSONObject? {
        val idx = html.indexOf("window.__INITIAL_STATE__")
        if (idx < 0) return null
        val start = html.indexOf('{', idx)
        val end = html.indexOf("</script>", idx)
        if (start < 0 || end < 0 || end <= start) return null
        val raw = html.substring(start, end)
        var depth = 0
        var inStr = false
        var esc = false
        var segment: String? = null
        for (i in raw.indices) {
            val ch = raw[i]
            if (inStr) {
                if (esc) esc = false
                else if (ch == '\\') esc = true
                else if (ch == '"') inStr = false
                continue
            }
            when (ch) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        segment = raw.substring(0, i + 1)
                        break
                    }
                }
            }
        }
        segment ?: return null
        // 清洗 JS 非法值 undefined
        segment = segment.replace(":undefined", ":null")
            .replace("undefined,", "null,")
            .replace("[undefined", "[null")
            .replace("undefined]", "null]")
        return try {
            JSONObject(segment)
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeSsr(note: JSONObject, noteId: String): String {
        val out = JSONObject()
        val video = note.optJSONObject("video")
        val images = note.optJSONArray("imageList")
        val cover = when {
            video != null -> video.optJSONObject("cover")?.optJSONArray("urlList")?.optString(0, "")
                ?: ""
            images != null && images.length() > 0 -> images.getJSONObject(0)
                .optString("urlDefault", images.getJSONObject(0).optString("url", ""))
            else -> ""
        }
        out.put("aweme_id", note.optString("noteId", noteId))
        out.put("title", note.optString("title", "(无标题)"))
        out.put("author", note.optJSONObject("user")?.optString("nickname", "") ?: "")
        out.put("cover", cover)
        // 统一 create_time 为秒(小红书为毫秒,抖音为秒)
        val rawTime = note.optLong("time", 0)
        out.put("create_time", if (rawTime > 1_000_000_000_000L) rawTime / 1000 else rawTime)
        out.put("platform", "xhs")
        // 视频
        var playUrl = ""
        var duration = 0L
        if (video != null) {
            val streams = video.optJSONObject("media")?.optJSONObject("stream")
            val h264 = streams?.optJSONArray("h264")
            if (h264 != null && h264.length() > 0) {
                val h = h264.getJSONObject(0)
                playUrl = h.optString("masterUrl", "")
                // 时长毫秒(与抖音一致);备用字段 videoDuration
                duration = h.optLong("duration", h.optLong("videoDuration", 0))
            }
            if (playUrl.isEmpty()) {
                val backup = streams?.optJSONArray("backupUrls")
                if (backup != null && backup.length() > 0) {
                    playUrl = backup.optString(0, "")
                }
            }
        }
        out.put("play_url", playUrl)
        out.put("play_urls", JSONArray())
        out.put("duration", duration)
        out.put("music", "")
        out.put("music_title", "")
        // 统计
        val stats = JSONObject()
        val ii = note.optJSONObject("interactInfo")
        if (ii != null) {
            stats.put("digg_count", ii.optLong("likedCount", 0))
            stats.put("comment_count", ii.optLong("commentCount", 0))
            stats.put("collect_count", ii.optLong("collectedCount", 0))
            stats.put("share_count", ii.optLong("shareCount", 0))
        }
        out.put("stats", stats)
        // 图片
        val imgArr = JSONArray()
        if (images != null) {
            for (i in 0 until images.length()) {
                val im = images.getJSONObject(i)
                val url = im.optString("urlDefault", im.optString("url", ""))
                if (url.isNotEmpty()) {
                    imgArr.put(
                        JSONObject()
                            .put("url", url)
                            .put("original_url", im.optString("originalImageUrl", url))
                            .put("gif_url", "")
                    )
                }
            }
        }
        out.put("images", imgArr)
        return out.toString()
    }

    // ============ 备源 1:parse.shenzjd.com ============

    private fun tryParseShenzjd(url: String): String? {
        return try {
            val api = "https://parse.shenzjd.com/api/xhs?url=" + java.net.URLEncoder.encode(url, "UTF-8")
            val conn = java.net.URL(api).openConnection()
            conn.setRequestProperty("User-Agent", ua)
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            val text = conn.getInputStream().bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            if (obj.optInt("code", -1) != 200) return null
            val data = obj.optJSONObject("data") ?: return null
            normalizeMain(data, url)
        } catch (e: Exception) {
            null
        }
    }

    // ============ 备源 2:xhs-download.online ============

    private fun tryParseBackup(url: String): String? {
        return try {
            val api = "https://www.xhs-download.online/xhs-download-api?url=" +
                java.net.URLEncoder.encode(url, "UTF-8")
            val conn = java.net.URL(api).openConnection()
            conn.setRequestProperty("User-Agent", ua)
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            val text = conn.getInputStream().bufferedReader().use { it.readText() }
            val inner1 = JSONObject(text).optString("data", "")
            val inner2 = JSONObject(inner1).optString("data", "")
            val obj = JSONObject(inner2)
            if (obj.optInt("code", -1) != 0) return null
            val videos = obj.optJSONArray("videoList")
            val images = obj.optJSONArray("imgList")
            val hasContent = (videos != null && videos.length() > 0) ||
                (images != null && images.length() > 0)
            if (!hasContent) return null
            normalizeBackup(obj, url)
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeMain(data: JSONObject, url: String): String {
        val out = JSONObject()
        out.put("aweme_id", deriveId(url, data.optString("authorID", "")))
        out.put("title", data.optString("title", data.optString("desc", "(无标题)")))
        out.put("author", data.optString("author", ""))
        out.put("cover", data.optString("cover", ""))
        out.put("play_url", data.optString("url", ""))
        out.put("play_urls", JSONArray())
        out.put("music", "")
        out.put("music_title", "")
        out.put("create_time", 0)
        out.put("duration", 0)
        out.put("platform", "xhs")
        out.put("stats", JSONObject())
        val images = JSONArray()
        data.optJSONArray("images")?.let { arr ->
            for (i in 0 until arr.length()) {
                val u = arr.optString(i)
                if (u.isNotEmpty()) {
                    images.put(
                        JSONObject()
                            .put("url", u)
                            .put("original_url", u)
                            .put("gif_url", "")
                    )
                }
            }
        }
        out.put("images", images)
        return out.toString()
    }

    private fun normalizeBackup(obj: JSONObject, url: String): String {
        val out = JSONObject()
        out.put("aweme_id", deriveId(url, ""))
        out.put("title", obj.optString("title", "(无标题)"))
        out.put("author", obj.optString("author", ""))
        out.put("cover", obj.optString("coverImage", ""))
        val videos = obj.optJSONArray("videoList")
        out.put("play_url", if (videos != null && videos.length() > 0) videos.optString(0) else "")
        out.put("play_urls", JSONArray())
        out.put("music", "")
        out.put("music_title", "")
        out.put("create_time", 0)
        out.put("duration", 0)
        out.put("platform", "xhs")
        out.put("stats", JSONObject())
        val images = JSONArray()
        obj.optJSONArray("imgList")?.let { arr ->
            for (i in 0 until arr.length()) {
                val u = arr.optString(i)
                if (u.isNotEmpty()) {
                    images.put(
                        JSONObject()
                            .put("url", u)
                            .put("original_url", u)
                            .put("gif_url", "")
                    )
                }
            }
        }
        out.put("images", images)
        return out.toString()
    }

    /** 从链接派生稳定 ID:优先 24 位 noteId;备源无 ID 时用作者+标题摘要 */
    private fun deriveId(url: String, authorId: String): String {
        Regex("(?:explore|discovery/item|item)/([a-f0-9]{24})").find(url)?.let {
            return it.groupValues[1]
        }
        val stable = authorId.ifEmpty { url }
        // 用内容摘要,避免 hashCode 碰撞与不稳定
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest(stable.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "xhs" + digest.take(16)
    }
    private fun isAllowedHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "xiaohongshu.com" || normalized.endsWith(".xiaohongshu.com") ||
            normalized == "xhslink.com" || normalized.endsWith(".xhslink.com") ||
            normalized == "xhslink.cn" || normalized.endsWith(".xhslink.cn")
    }
}

private fun Iterator<String>.nextOrNull(): String? {
    return if (hasNext()) next() else null
}
