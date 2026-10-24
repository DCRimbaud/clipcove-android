package com.douyin.core.model

enum class ParseFailureType {
    INVALID_LINK,
    UNSUPPORTED_PLATFORM,
    NETWORK,
    RISK_CONTROL,
    CONTENT_UNAVAILABLE,
    RESPONSE_CHANGED,
    UNKNOWN,
}

data class ParseFailure(
    val type: ParseFailureType,
    val message: String,
    val debugDetail: String? = null,
) {
    /** 仅瞬时问题自动重试；链接失效、风控和结构变化不应盲目重试。 */
    val isRetryable: Boolean
        get() = type == ParseFailureType.NETWORK || type == ParseFailureType.RESPONSE_CHANGED
}

sealed interface ParseResult {
    data class Success(val media: MediaInfo) : ParseResult
    data class Failure(val error: ParseFailure) : ParseResult
}
