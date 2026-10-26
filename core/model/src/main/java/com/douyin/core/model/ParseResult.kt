/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Typed parse success/failure contracts and retry classification rules.
 */
package com.douyin.core.model

/** Stable failure categories used for UI copy and retry decisions. */
enum class ParseFailureType {
    INVALID_LINK,
    UNSUPPORTED_PLATFORM,
    NETWORK,
    RISK_CONTROL,
    CONTENT_UNAVAILABLE,
    RESPONSE_CHANGED,
    UNKNOWN,
}

/**
 * Typed parse failure with optional sanitized debugging context.
 *
 * @property type Stable failure category.
 * @property message Parser fallback explanation used for diagnostics and compatibility.
 * @property debugDetail Optional redacted detail that is logged or exported only.
 *
 * UI copy is selected from [type] so the same failure remains readable in every app language.
 */
data class ParseFailure(
    val type: ParseFailureType,
    val message: String,
    val debugDetail: String? = null,
) {
    /** Only transient failures are retried; invalid links, risk control, and contract drift are not retried blindly. */
    val isRetryable: Boolean
        get() = type == ParseFailureType.NETWORK || type == ParseFailureType.RESPONSE_CHANGED
}

/** Closed result type for a completed platform parse operation. */
sealed interface ParseResult {
    /** Successful parse containing normalized media metadata. */
    data class Success(val media: MediaInfo) : ParseResult
    /** Expected parse failure that should not leak platform exceptions to the UI. */
    data class Failure(val error: ParseFailure) : ParseResult
}
