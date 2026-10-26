/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines the Xiaohongshu page-fetching port used by the feature layer.
 */
package com.douyin.data.xhs

import com.douyin.core.model.ParseResult

/** Xiaohongshu fetch port backed by the official note-page SSR payload. */
interface XhsRepository {
    suspend fun resolve(url: String): ParseResult
}