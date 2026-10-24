package com.douyin.feature.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.douyin.core.designsystem.DyBorder
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyCyan
import com.douyin.core.designsystem.DyEmptyState
import com.douyin.core.designsystem.DyPageHeader
import com.douyin.core.designsystem.DyPill
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.data.favorites.FavoriteItem
import com.douyin.data.favorites.FavoritesRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FavoritesScreen(
    repository: FavoritesRepository,
    onOpen: (String) -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    val items = remember(version) { repository.load() }

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 14.dp),
    ) {
        DyPageHeader(
            title = "收藏",
            subtitle = "把值得重看的内容留在手边。",
            eyebrow = "FAVORITES",
            trailing = { DyPill(text = "${items.size} 项", color = DyRed) },
        )
        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            DyEmptyState(
                icon = Icons.Filled.FavoriteBorder,
                title = "还没有收藏",
                description = "解析结果卡片上点一次爱心，作品就会出现在这里。",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.awemeId }) { item ->
                    FavoriteCard(
                        item = item,
                        onRemove = {
                            repository.remove(item.awemeId)
                            version++
                        },
                        onClick = { onOpen(item.awemeId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(item: FavoriteItem, onRemove: () -> Unit, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = DyCard,
        border = BorderStroke(1.dp, DyBorder.copy(alpha = 0.68f)),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = item.cover,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 82.dp, height = 104.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = DyText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                Text("收藏于", style = MaterialTheme.typography.bodySmall, color = DyTextSecondary)
                Text(
                    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(item.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = DyCyan,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Favorite, contentDescription = "取消收藏", tint = DyRed)
            }
        }
    }
}
