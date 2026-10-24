package com.douyin.feature.home

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.data.douyin.DouyinRepository
import com.douyin.data.download.DownloadRepository
import com.douyin.data.history.HistoryItem
import com.douyin.data.history.HistoryRepository
import com.douyin.data.merger.VideoMerger
import com.douyin.data.favorites.FavoritesRepository
import com.douyin.data.favorites.FavoriteItem
import com.douyin.data.xhs.XhsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 首页 ViewModel:解析状态、历史、下载/合成入口。
 * 依赖注入:DouyinRepository / HistoryRepository / DownloadRepository / VideoMerger
 */
class HomeViewModel(
    private val douyinRepo: DouyinRepository,
    private val xhsRepo: XhsRepository,
    private val historyRepo: HistoryRepository,
    private val downloadRepo: DownloadRepository,
    private val merger: VideoMerger,
    private val favoritesRepo: FavoritesRepository,
    app: Application
) : AndroidViewModel(app) {

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error

    private val _result = MutableStateFlow<JSONObject?>(null)
    val result: StateFlow<JSONObject?> = _result

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history

    /** 剪贴板检测到链接时弹出快捷解析卡片 */
    private val _clipboardSuggestion = MutableStateFlow<String?>(null)
    val clipboardSuggestion: StateFlow<String?> = _clipboardSuggestion

    /** 当前结果是否已收藏 */
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    init {
        _history.value = historyRepo.load()
    }

    fun onInputChange(value: String) {
        _input.value = value
        if (_error.value.isNotEmpty()) _error.value = ""
    }

    /** 从分享文案中提取第一个 http(s) 链接 */
    private fun extractHttpUrl(text: String): String {
        Regex("https?://[^\\s\\u4e00-\\u9fff]+").find(text)?.let {
            return it.value.trimEnd('.', '，', ',', '。', '!', '?', ';', '；', '、')
        }
        return text
    }

    /** 处理分享/剪贴板来源的链接 */
    fun acceptIncoming(text: String?) {
        val clean = text?.trim()?.takeIf { it.length <= 200 } ?: return
        val isLink = clean.contains("v.douyin.com") || clean.contains("douyin.com/video") ||
            clean.contains("douyin.com/note") || clean.contains("douyin.com/user") ||
            clean.contains("xiaohongshu.com") || clean.contains("xhslink.com") ||
            clean.contains("xhslink.cn") || clean.matches(Regex("\\d{15,20}"))
        if (isLink && _input.value.isEmpty()) {
            _input.value = clean
        }
    }

    fun checkClipboard() {
        val app = getApplication<Application>()
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!cm.hasPrimaryClip()) return
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString() ?: return
        // 输入框为空且检测到链接 → 弹快捷解析卡片;否则只静默填入
        val clean = text.trim()
        val isLink = clean.contains("v.douyin.com") || clean.contains("douyin.com/video") ||
            clean.contains("douyin.com/note") || clean.contains("xiaohongshu.com") ||
            clean.contains("xhslink.com") || clean.contains("xhslink.cn") ||
            clean.matches(Regex("\\d{15,20}"))
        if (!isLink) return
        if (_input.value.isEmpty()) {
            _clipboardSuggestion.value = clean
        } else {
            acceptIncoming(clean)
        }
    }

    /** 使用剪贴板建议的链接解析 */
    fun parseClipboardSuggestion() {
        val url = _clipboardSuggestion.value ?: return
        _input.value = url
        _clipboardSuggestion.value = null
        parse()
    }

    fun dismissClipboardSuggestion() {
        _clipboardSuggestion.value = null
    }

    fun toggleFavorite() {
        val obj = _result.value ?: return
        val id = obj.optString("aweme_id", "")
        if (favoritesRepo.isFavorite(id)) {
            favoritesRepo.remove(id)
            _isFavorite.value = false
        } else {
            favoritesRepo.add(
                FavoriteItem(
                    id,
                    obj.optString("title", "(无标题)"),
                    obj.optString("cover", ""),
                    System.currentTimeMillis()
                )
            )
            _isFavorite.value = true
        }
    }

    fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            acceptIncoming(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    /** 检测到小红书链接时发出请求,UI 打开提取页 */
    private val _xhsRequest = MutableStateFlow<String?>(null)
    val xhsRequest: StateFlow<String?> = _xhsRequest

    fun consumeXhsRequest() {
        _xhsRequest.value = null
    }

    fun parse() {
        val url = _input.value.trim()
        if (url.isEmpty()) {
            _error.value = "请先粘贴链接"
            return
        }
        if (_loading.value) return
        _loading.value = true
        _error.value = ""
        // 小红书链接 → 调小红书仓库解析;否则走抖音引擎
        val isXhs = url.contains("xiaohongshu.com") || url.contains("xhslink.com") || url.contains("xhslink.cn")
        val target = if (isXhs) extractHttpUrl(url) else url
        viewModelScope.launch {
            try {
                // 自动重试 1 次(网络抖动/接口瞬时失败)
                var json = if (isXhs) xhsRepo.resolve(target) else douyinRepo.resolve(target)
                val firstErr = runCatching { JSONObject(json).has("error") }.getOrDefault(false)
                if (firstErr) {
                    kotlinx.coroutines.delay(800)
                    json = if (isXhs) xhsRepo.resolve(target) else douyinRepo.resolve(target)
                }
                val obj = try {
                    JSONObject(json)
                } catch (e: Exception) {
                    JSONObject().put("error", "结果异常:${e.message}")
                }
                if (obj.has("error")) {
                    _error.value = obj.optString("error", "解析失败")
                    _result.value = null
                } else {
                    _result.value = obj
                    _isFavorite.value = favoritesRepo.isFavorite(obj.optString("aweme_id", ""))
                    historyRepo.add(
                        HistoryItem(
                            obj.optString("aweme_id", ""),
                            obj.optString("title", "(无标题)"),
                            obj.optString("cover", ""),
                            System.currentTimeMillis(),
                            url  // 存原始输入(小红书 token 有时效,需原链接重解析)
                        )
                    )
                    _history.value = historyRepo.load()
                }
            } catch (e: Exception) {
                // 解析异常:保证 loading 复位,不崩溃
                _error.value = "解析失败:${e.message}"
                _result.value = null
            } finally {
                _loading.value = false
            }
        }
    }

    fun enqueueDownload(title: String, filename: String, url: String) {
        downloadRepo.enqueue(title, filename, url)
        Toast.makeText(getApplication(), "已加入下载:$title", Toast.LENGTH_SHORT).show()
    }

    fun mergeVideo(videoUrl: String, audioUrl: String, filename: String) {
        Toast.makeText(getApplication(), "开始合成:$filename", Toast.LENGTH_SHORT).show()
        merger.merge(getApplication(), videoUrl, audioUrl, filename) { success, msg ->
            Toast.makeText(
                getApplication(),
                if (success) "$msg:$filename" else msg,
                if (success) Toast.LENGTH_LONG else Toast.LENGTH_LONG
            ).show()
        }
    }
}

