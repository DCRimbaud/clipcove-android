package com.douyin.data.douyin

import com.douyin.core.model.ParseResult

/** 抖音解析端口。feature 层不感知 Python、HTTP 或平台 JSON 结构。 */
interface DouyinRepository {
    suspend fun resolve(input: String): ParseResult
}
