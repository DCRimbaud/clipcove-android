package com.douyin.data.favorites

/** 收藏条目 */
data class FavoriteItem(
    val awemeId: String,
    val title: String,
    val cover: String,
    val time: Long
)

/** 收藏仓库接口 */
interface FavoritesRepository {
    fun load(): List<FavoriteItem>
    fun add(item: FavoriteItem)
    fun remove(awemeId: String)
    fun isFavorite(awemeId: String): Boolean
    fun clear()
}
