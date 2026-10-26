/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Persists a bounded, sanitized diagnostics history for user-exported support reports.
 */
package com.douyin.parser

import android.content.Context
import android.os.Build
import com.douyin.core.common.diagnostics.DiagnosticEntry
import com.douyin.core.common.diagnostics.DiagnosticSanitizer
import com.douyin.core.common.diagnostics.DiagnosticsRepository
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Bounded, local-only diagnostics backed by SharedPreferences. */
class PrefsDiagnosticsRepository(context: Context) : DiagnosticsRepository {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    override fun record(category: String, message: String, detail: String?) {
        runCatching {
            val entry = DiagnosticEntry(
                timestamp = System.currentTimeMillis(),
                category = DiagnosticSanitizer.sanitize(category, 80),
                message = DiagnosticSanitizer.sanitize(message, 300),
                detail = DiagnosticSanitizer.sanitize(detail),
            )
            val items = load().toMutableList()
            items.add(0, entry)
            save(items.take(MAX_ENTRIES))
        }
    }

    @Synchronized
    override fun load(): List<DiagnosticEntry> {
        val raw = prefs.getString(KEY_ITEMS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val message = item.optString("message")
                    if (message.isBlank()) continue
                    add(
                        DiagnosticEntry(
                            timestamp = item.optLong("timestamp").coerceAtLeast(0),
                            category = item.optString("category", "Parse"),
                            message = message,
                            detail = item.optString("detail"),
                        )
                    )
                }
            }.sortedByDescending { it.timestamp }.take(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    override fun exportReport(versionName: String, versionCode: Int): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val entries = load()
        return buildString {
            appendLine("ClipCove diagnostics")
            appendLine("App: $versionName ($versionCode)")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}")
            appendLine("Entries: ${entries.size}/$MAX_ENTRIES")
            appendLine("Privacy: tokens, cookies, phones, emails, and IPs are redacted.")
            appendLine()
            if (entries.isEmpty()) {
                appendLine("(no diagnostic entries)")
            } else {
                entries.forEachIndexed { index, entry ->
                    appendLine("[${index + 1}] ${formatter.format(Date(entry.timestamp))} | ${entry.category}")
                    appendLine("Message: ${entry.message}")
                    if (entry.detail.isNotBlank()) {
                        appendLine("Detail: ${entry.detail}")
                    }
                    appendLine()
                }
            }
        }.trimEnd()
    }

    @Synchronized
    override fun clear() {
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    private fun save(entries: List<DiagnosticEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("timestamp", entry.timestamp)
                    .put("category", entry.category)
                    .put("message", entry.message)
                    .put("detail", entry.detail)
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val PREFS = "diagnostics"
        const val KEY_ITEMS = "entries"
        const val MAX_ENTRIES = 50
    }
}