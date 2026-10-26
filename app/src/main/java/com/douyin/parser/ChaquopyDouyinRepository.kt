/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Adapts the Chaquopy Douyin resolver response into the typed Kotlin parse contract.
 */
package com.douyin.parser

import android.util.Log
import com.chaquo.python.Python
import com.douyin.core.common.diagnostics.DiagnosticSanitizer
import com.douyin.core.model.ParseFailure
import com.douyin.core.model.ParseFailureType
import com.douyin.core.model.ParseResult
import com.douyin.data.douyin.DouyinMediaMapper
import com.douyin.data.douyin.DouyinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Chaquopy adapter: invokes Python and delegates platform JSON mapping to the data layer. */
class ChaquopyDouyinRepository : DouyinRepository {

    override suspend fun resolve(input: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            val raw = Python.getInstance()
                .getModule("douyin_resolver")
                .callAttr("resolve", input)
                .toString()
            DouyinMediaMapper.fromPythonJson(raw)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Douyin Python resolver failed: ${e::class.java.simpleName}: " +
                    DiagnosticSanitizer.sanitize(e.message),
            )
            ParseResult.Failure(
                ParseFailure(
                    type = ParseFailureType.UNKNOWN,
                    message = "抖音解析引擎启动失败，请重启 App 后重试",
                    debugDetail = e.message,
                )
            )
        }
    }

    private companion object {
        const val TAG = "DouyinRepository"
    }
}