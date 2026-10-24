package com.douyin.feature.home

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.core.model.MediaInfo
import com.douyin.core.model.ParseResult
import com.douyin.data.douyin.DouyinRepository
import com.douyin.data.download.DownloadRepository
import com.douyin.data.favorites.FavoriteItem
import com.douyin.data.favorites.FavoritesRepository
import com.douyin.data.history.HistoryItem
import com.douyin.data.history.HistoryRepository
import com.douyin.data.merger.VideoMerger
import com.douyin.data.xhs.XhsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 首页协调器：把平台解析、历史、收藏和下载入口收敛在一个明确状态机中。 */
class HomeViewModel(
    private val douyinRepo: DouyinRepository,
    private val xhsRepo: XhsRepository,
    private val historyRepo: HistoryRepository,
    private val downloadRepo: DownloadRepository,
    private val merger: VideoMerger,
    private val favoritesRepo: FavoritesRepository,
    app: Application,
) : AndroidViewModel(app) {

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error

    private val _result = MutableStateFlow<MediaInfo?>(null)
    val result: StateFlow<MediaInfo?> = _result

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history

    private val _clipboardSuggestion = MutableStateFlow<String?>(null)
    val clipboardSuggestion: StateFlow<String?> = _clipboardSuggestion

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    init {
        _history.value = historyRepo.load()
    }

    fun onInputChange(value: String) {
        _input.value = value
        if (_error.value.isNotEmpty()) _error.value = ""
    }

    fun acceptIncoming(text: String?) {
        val clean = text?.trim()?.takeIf { it.length in 1..MAX_INPUT_LENGTH } ?: return
        if (looksLikeSupportedLink(clean) && _input.value.isBlank()) {
            _input.value = clean
        }
    }

    fun checkClipboard() {
        val app = getApplication<Application>()
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString() ?: return
        val clean = text.trim().takeIf { it.length in 1..MAX_INPUT_LENGTH } ?: return
        if (!looksLikeSupportedLink(clean)) return
        if (_input.value.isBlank()) {
            _clipboardSuggestion.value = clean
        } else {
            acceptIncoming(clean)
        }
    }

    fun parseClipboardSuggestion() {
        val link = _clipboardSuggestion.value ?: return
        _input.value = link
        _clipboardSuggestion.value = null
        parse()
    }

    fun dismissClipboardSuggestion() {
        _clipboardSuggestion.value = null
    }

    fun toggleFavorite() {
        val media = _result.value ?: return
        if (favoritesRepo.isFavorite(media.id)) {
            favoritesRepo.remove(media.id)
            _isFavorite.value = false
        } else {
            favoritesRepo.add(
                FavoriteItem(
                    awemeId = media.id,
                    title = media.title,
                    cover = media.coverUrl,
                    time = System.currentTimeMillis(),
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

    fun parse() {
        val originalInput = _input.value.trim()
        if (originalInput.isEmpty()) {
            _error.value = "请先粘贴链接"
            return
        }
        if (_loading.value) return

        _loading.value = true
        _error.value = ""
        val platform = detectPlatform(originalInput)
        val target = if (platform == SourcePlatform.XIAOHONGSHU) {
            extractHttpUrl(originalInput)
        } else {
            originalInput
        }

        viewModelScope.launch {
            try {
                var outcome = resolve(platform, target)
                if (outcome is ParseResult.Failure && outcome.error.isRetryable) {
                    delay(RETRY_DELAY_MS)
                    outcome = resolve(platform, target)
                }

                when (outcome) {
                    is ParseResult.Success -> applySuccess(outcome.media, originalInput)
                    is ParseResult.Failure -> {
                        outcome.error.debugDetail?.let {
                            Log.w(TAG, "Parse failed: ${outcome.error.type} $it")
                        }
                        _result.value = null
                        _isFavorite.value = false
                        _error.value = outcome.error.message.ifBlank { "解析失败，请稍后重试" }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected parse failure", e)
                _result.value = null
                _isFavorite.value = false
                _error.value = "解析失败，请稍后重试"
            } finally {
                _loading.value = false
            }
        }
    }

    fun enqueueDownload(title: String, filename: String, url: String) {
        downloadRepo.enqueue(title, filename, url)
        Toast.makeText(getApplication(), "已加入下载：$title", Toast.LENGTH_SHORT).show()
    }

    fun mergeVideo(videoUrl: String, audioUrl: String, filename: String) {
        Toast.makeText(getApplication(), "开始合成：$filename", Toast.LENGTH_SHORT).show()
        merger.merge(getApplication(), videoUrl, audioUrl, filename) { success, message ->
            val text = if (success) "$message：$filename" else message
            Toast.makeText(getApplication(), text, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun resolve(platform: SourcePlatform, target: String): ParseResult = when (platform) {
        SourcePlatform.DOUYIN -> douyinRepo.resolve(target)
        SourcePlatform.XIAOHONGSHU -> xhsRepo.resolve(target)
    }

    private fun applySuccess(media: MediaInfo, originalInput: String) {
        _result.value = media
        _isFavorite.value = favoritesRepo.isFavorite(media.id)
        historyRepo.add(
            HistoryItem(
                awemeId = media.id,
                title = media.title,
                cover = media.coverUrl,
                time = System.currentTimeMillis(),
                sourceUrl = originalInput,
            )
        )
        _history.value = historyRepo.load()
    }

    private fun detectPlatform(input: String): SourcePlatform {
        val normalized = input.lowercase()
        return if (normalized.contains("xiaohongshu.com") ||
            normalized.contains("xhslink.com") ||
            normalized.contains("xhslink.cn")
        ) {
            SourcePlatform.XIAOHONGSHU
        } else {
            SourcePlatform.DOUYIN
        }
    }

    private fun looksLikeSupportedLink(text: String): Boolean {
        val normalized = text.lowercase()
        return normalized.contains("v.douyin.com") ||
            normalized.contains("douyin.com/video") ||
            normalized.contains("douyin.com/note") ||
            normalized.contains("douyin.com/user") ||
            normalized.contains("douyin.com/share") ||
            normalized.contains("xiaohongshu.com") ||
            normalized.contains("xhslink.com") ||
            normalized.contains("xhslink.cn") ||
            text.matches(Regex("""\d{15,20}"""))
    }

    private fun extractHttpUrl(text: String): String {
        Regex("""https?://[^\s\u4e00-\u9fff]+""").find(text)?.let {
            return it.value.trimEnd('.', '，', ',', '。', '!', '?', ';', '；', '、', ')', '）')
        }
        return text
    }

    private enum class SourcePlatform { DOUYIN, XIAOHONGSHU }

    private companion object {
        const val TAG = "HomeViewModel"
        const val MAX_INPUT_LENGTH = 500
        const val RETRY_DELAY_MS = 800L
    }
}
