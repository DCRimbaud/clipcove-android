/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines the Douyin resolution port consumed by the feature layer.
 */
package com.douyin.data.douyin

import com.douyin.core.model.ParseResult

/** Douyin resolution port. Feature code does not depend on Python, HTTP, or platform JSON. */
interface DouyinRepository {
    suspend fun resolve(input: String): ParseResult
}