/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines favorite-item persistence and stable source-link contracts.
 */
package com.douyin.data.favorites

import com.douyin.core.model.MediaPlatform

/** Persisted favorite item with the source URL required for reliable re-resolution. */
data class FavoriteItem(
    val awemeId: String,
    val title: String,
    val cover: String,
    val time: Long,
    val sourceUrl: String = "",
    val platform: MediaPlatform? = null,
)

/** Favorite persistence port used by feature code. */
interface FavoritesRepository {
    /** Returns persisted favorites, recovering safely from corrupt storage. */
    fun load(): List<FavoriteItem>
    /** Adds or replaces one favorite by stable media ID. */
    fun add(item: FavoriteItem)
    /** Removes a favorite without affecting history or downloaded files. */
    fun remove(awemeId: String)
    /** Returns whether the stable media ID is currently favorited. */
    fun isFavorite(awemeId: String): Boolean
    /** Removes every favorite from local storage. */
    fun clear()
}