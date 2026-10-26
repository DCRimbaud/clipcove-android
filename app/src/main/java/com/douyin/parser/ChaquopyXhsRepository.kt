/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Fetches Xiaohongshu pages through Chaquopy and delegates SSR normalization to the data layer.
 */
package com.douyin.parser

import android.util.Log
import com.chaquo.python.Python
import com.douyin.core.common.diagnostics.DiagnosticSanitizer
import com.douyin.core.model.ParseFailure
import com.douyin.core.model.ParseFailureType
import com.douyin.core.model.ParseResult
import com.douyin.data.xhs.XhsRepository
import com.douyin.data.xhs.XhsSsrParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Xiaohongshu adapter: Python fetches only the official page; SSR normalization remains in data:xhs Kotlin code.
 * This keeps the more reliable Android Python network stack while preserving JVM testability for parsing.
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
            Log.d(
                TAG,
                "XHS python page bytes=${html.length} finalUrl=" +
                    DiagnosticSanitizer.sanitize(finalUrl, 180),
            )
            XhsSsrParser.parse(html, finalUrl)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "XHS Python fetch failed: ${e::class.java.simpleName}: " +
                    DiagnosticSanitizer.sanitize(e.message),
            )
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