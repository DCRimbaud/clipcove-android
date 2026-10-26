/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines diagnostics storage/export contracts and sanitization helpers shared by feature modules.
 */
package com.douyin.core.common.diagnostics

/**
 * One bounded, sanitized diagnostic record.
 *
 * @property timestamp Unix epoch milliseconds when the event was recorded.
 * @property category Stable subsystem category used for support filtering.
 * @property message Short sanitized summary safe to export.
 * @property detail Optional sanitized context; never contains raw share links or cookies.
 */
data class DiagnosticEntry(
    val timestamp: Long,
    val category: String,
    val message: String,
    val detail: String = "",
)

/**
 * Local-only diagnostic log used to make parser failures supportable without
 * collecting share URLs, cookies, or account data.
 */
/**
 * Port for app-private diagnostics storage and support-report export.
 *
 * Implementations must keep at most a bounded number of entries and must
 * sanitize text before persistence. No method performs network I/O.
 */
interface DiagnosticsRepository {
    /** Records one sanitized event, evicting the oldest entry when the cap is reached. */
    fun record(category: String, message: String, detail: String? = null)
    /** Returns diagnostics newest first; corrupt storage is treated as empty. */
    fun load(): List<DiagnosticEntry>
    /** Builds a plain-text support report stamped with the app version. */
    fun exportReport(versionName: String, versionCode: Int): String
    /** Deletes all stored diagnostics. */
    fun clear()
}

/**
 * Best-effort redaction for text copied into diagnostics. The app never
 * records raw share URLs, but parser exceptions can still embed query strings.
 */
object DiagnosticSanitizer {
    private const val DEFAULT_MAX_LENGTH = 800

    private val sensitiveQuery = Regex(
        """(?i)([?&](?:xsec_token|xsec_source|share_token|access_token|refresh_token|session|cookie|authorization|signature|sig|key)=)[^&#\s]+"""
    )
    private val sensitiveAssignment = Regex(
        """(?i)\b(cookie|authorization|proxy-authorization|xsec_token|share_token|access_token|refresh_token|session|password|secret)\b\s*[:=]\s*[^\s,;]+"""
    )
    private val bearerToken = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""")
    private val phoneNumber = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""")
    private val emailAddress = Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""")
    private val ipv4Address = Regex("""(?<!\d)(?:\d{1,3}\.){3}\d{1,3}(?!\d)""")

    /**
     * Redacts credentials and personal identifiers from untrusted diagnostic text.
     *
     * @param value Raw exception or parser text.
     * @param maxLength Maximum output length; negative values collapse to empty.
     * @return Sanitized single-line text safe for local storage and export.
     */
    fun sanitize(value: String?, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        if (value.isNullOrBlank()) return ""
        var text = value.replace('\u0000', ' ')
        text = sensitiveQuery.replace(text) { match ->
            match.groupValues[1] + "[redacted]"
        }
        text = sensitiveAssignment.replace(text) { match ->
            match.groupValues[1] + "=[redacted]"
        }
        text = bearerToken.replace(text, "Bearer [redacted]")
        text = emailAddress.replace(text, "[email]")
        text = phoneNumber.replace(text, "[phone]")
        text = ipv4Address.replace(text, "[ip]")
        text = text.replace(Regex("""\s+"""), " ").trim()
        return text.take(maxLength.coerceAtLeast(0))
    }
}