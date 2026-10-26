/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Coordinates parsing, platform detection, history, favorites, sharing, and download enqueueing.
 */
package com.douyin.feature.home

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.core.common.diagnostics.DiagnosticSanitizer
import com.douyin.core.common.diagnostics.DiagnosticsRepository
import com.douyin.core.model.MediaImage
import com.douyin.core.model.MediaInfo
import com.douyin.core.model.ParseFailure
import com.douyin.core.model.ParseFailureType
import com.douyin.core.model.ParseResult
import com.douyin.core.uiresources.R as SharedR
import com.douyin.data.douyin.DouyinRepository
import com.douyin.data.download.DownloadRepository
import com.douyin.data.favorites.FavoriteItem
import com.douyin.data.favorites.FavoritesRepository
import com.douyin.data.history.HistoryItem
import com.douyin.data.history.HistoryRepository
import com.douyin.data.merger.VideoMerger
import com.douyin.data.settings.SettingsRepository
import com.douyin.data.settings.withAppLanguage
import com.douyin.data.xhs.XhsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Home coordinator that owns platform parsing, history, favorites, sharing, and download enqueueing. */
class HomeViewModel(
    private val douyinRepo: DouyinRepository,
    private val xhsRepo: XhsRepository,
    private val historyRepo: HistoryRepository,
    private val downloadRepo: DownloadRepository,
    private val merger: VideoMerger,
    private val favoritesRepo: FavoritesRepository,
    private val diagnosticsRepo: DiagnosticsRepository,
    private val settingsRepo: SettingsRepository,
    app: Application,
) : AndroidViewModel(app) {

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<ParseFailure?>(null)
    val error: StateFlow<ParseFailure?> = _error

    private val _result = MutableStateFlow<MediaInfo?>(null)
    val result: StateFlow<MediaInfo?> = _result

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history

    private val _clipboardSuggestion = MutableStateFlow<String?>(null)
    val clipboardSuggestion: StateFlow<String?> = _clipboardSuggestion

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    private var lastSuccessSourceUrl: String = ""

    init {
        _history.value = historyRepo.load()
    }

    /** Updates the editable link text and clears a stale parse error. */
    fun onInputChange(value: String) {
        _input.value = value
        if (_error.value != null) _error.value = null
    }

    /** Re-sync local repositories after Settings clears or restores app data. */
    fun refreshLocalState() {
        _history.value = historyRepo.load()
        _result.value?.let { media ->
            _isFavorite.value = favoritesRepo.isFavorite(media.id)
        }
    }

    /** Fills an empty input from a shared intent only when the text looks supported. */
    fun acceptIncoming(text: String?) {
        val clean = text?.trim()?.takeIf { it.length in 1..MAX_INPUT_LENGTH } ?: return
        if (looksLikeSupportedLink(clean) && _input.value.isBlank()) {
            _input.value = clean
        }
    }

    /** Reads the current clipboard on the UI caller and copies its text into the input. */
    fun pasteClipboard() {
        val app = getApplication<Application>()
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            _input.value = text.take(MAX_INPUT_LENGTH)
            _error.value = null
        }
    }

    /** Offers a supported clipboard link without starting network work. */
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

    /** Accepts the clipboard suggestion, closes it, and starts parsing. */
    fun parseClipboardSuggestion() {
        val link = _clipboardSuggestion.value ?: return
        _input.value = link
        _clipboardSuggestion.value = null
        parse()
    }

    /** Hides the clipboard suggestion without changing the input. */
    fun dismissClipboardSuggestion() {
        _clipboardSuggestion.value = null
    }

    /** Adds or removes the current result from local favorites and updates observable state. */
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
                    sourceUrl = lastSuccessSourceUrl,
                    platform = media.platform,
                )
            )
            _isFavorite.value = true
        }
    }

    /**
     * Consumes a share intent exactly once and optionally starts parsing.
     *
     * Side effects: updates input, may launch a parse coroutine, and advances
     * intent consumption so a configuration recomposition cannot duplicate work.
     */
    fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        val clean = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.trim()
            ?.takeIf { it.length in 1..MAX_INPUT_LENGTH }
            ?: return
        if (!looksLikeSupportedLink(clean)) return
        _input.value = clean
        _error.value = null
        _clipboardSuggestion.value = null
        if (settingsRepo.autoParseSharedLinks.value) parse()
    }

    /**
     * Parses the current input on the IO dispatcher and publishes UI state.
     *
     * On success the result, favorite status, and local history are refreshed.
     * On failure the previous result and favorite state are cleared. Concurrent
     * parses are ignored while loading is true.
     */
    fun parse() {
        val originalInput = _input.value.trim()
        if (originalInput.isEmpty()) {
            _error.value = ParseFailure(ParseFailureType.INVALID_LINK, "请先粘贴链接")
            return
        }
        if (_loading.value) return

        _loading.value = true
        _error.value = null
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
                        val failure = outcome.error
                        failure.debugDetail?.let {
                            Log.w(
                                TAG,
                                "Parse failed: ${failure.type} ${DiagnosticSanitizer.sanitize(it)}",
                            )
                        }
                        diagnosticsRepo.record(
                            category = platform.name.lowercase().replaceFirstChar { it.uppercase() },
                            message = failure.message,
                            detail = buildString {
                                append("type=")
                                append(failure.type)
                                append(" retryable=")
                                append(failure.isRetryable)
                                if (!failure.debugDetail.isNullOrBlank()) {
                                    append('\n')
                                    append(failure.debugDetail)
                                }
                            },
                        )
                        _result.value = null
                        _isFavorite.value = false
                        lastSuccessSourceUrl = ""
                        _error.value = failure
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Unexpected parse failure: ${e::class.java.simpleName}: " +
                        DiagnosticSanitizer.sanitize(e.message),
                )
                diagnosticsRepo.record(
                    category = platform.name.lowercase().replaceFirstChar { it.uppercase() },
                    message = "Unexpected parser exception",
                    detail = "${e::class.java.simpleName}: ${e.message}",
                )
                _result.value = null
                _isFavorite.value = false
                lastSuccessSourceUrl = ""
                _error.value = ParseFailure(
                    ParseFailureType.UNKNOWN,
                    "解析失败，请稍后重试",
                    e.message,
                )
            } finally {
                _loading.value = false
            }
        }
    }

    /** Enqueues one non-empty media URL and reports acceptance with a toast. */
    fun enqueueDownload(title: String, filename: String, url: String) {
        downloadRepo.enqueue(title, filename, url)
        Toast.makeText(
            getApplication(),
            localizedContext().getString(SharedR.string.home_download_added, title),
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** Enqueues every gallery image that has a usable preferred URL. */
    fun enqueueGallery(title: String, mediaId: String, images: List<MediaImage>) {
        val downloadable = images.filter { it.preferredUrl.isNotBlank() }
        downloadable.forEachIndexed { index, image ->
            downloadRepo.enqueue(
                title = "${title}_image${index + 1}",
                filename = "${mediaId}_${index + 1}_image.jpg",
                url = image.preferredUrl,
            )
        }
        if (downloadable.isNotEmpty()) {
            Toast.makeText(
                getApplication(),
                localizedContext().getString(SharedR.string.home_gallery_added, downloadable.size),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** Starts an FFmpeg merge and reports the terminal result on the caller's UI surface. */
    fun mergeVideo(videoUrl: String, audioUrl: String, filename: String) {
        val context = localizedContext()
        Toast.makeText(
            context,
            context.getString(SharedR.string.home_merge_started, filename),
            Toast.LENGTH_SHORT,
        ).show()
        merger.merge(getApplication(), videoUrl, audioUrl, filename) { success, _ ->
            val text = if (success) {
                context.getString(SharedR.string.home_merge_complete, filename)
            } else {
                context.getString(SharedR.string.home_merge_failed)
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun resolve(platform: SourcePlatform, target: String): ParseResult = when (platform) {
        SourcePlatform.DOUYIN -> douyinRepo.resolve(target)
        SourcePlatform.XIAOHONGSHU -> xhsRepo.resolve(target)
    }

    private fun applySuccess(media: MediaInfo, originalInput: String) {
        _result.value = media
        lastSuccessSourceUrl = originalInput
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

    /** Returns a context whose resources follow the persisted app-language choice. */
    private fun localizedContext(): Context = getApplication<Application>().withAppLanguage()

    private fun detectPlatform(input: String): SourcePlatform {
        val normalized = input.lowercase()
        return if (normalized.contains("xiaohongshu.com") ||
            normalized.contains("xhslink.com") ||
            normalized.contains("xhslink.cn") ||
            input.matches(Regex("""(?i)[a-f0-9]{24}"""))
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
            text.matches(Regex("""\d{15,20}""")) ||
            text.matches(Regex("""(?i)[a-f0-9]{24}"""))
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