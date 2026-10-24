package com.douyin.feature.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.data.favorites.FavoritesRepository
import com.douyin.data.favorites.FavoriteItem
import java.text.SimpleDateFormat
import java.util.Date

/** 收藏页:订阅 FavoritesRepository */
@Composable
fun FavoritesScreen(
    repository: FavoritesRepository,
    onOpen: (String) -> Unit
) {
    var version by remember { mutableIntStateOf(0) }
    val items = remember(version) { repository.load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("收藏", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DyText)
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text(
                "暂无收藏\n解析结果卡片上点 ♥ 即可收藏",
                fontSize = 14.sp, color = DyTextSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.awemeId }) { item ->
                    FavoriteCard(
                        item = item,
                        onRemove = {
                            repository.remove(item.awemeId)
                            version++
                        },
                        onClick = { onOpen(item.awemeId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(item: FavoriteItem, onRemove: () -> Unit, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = DyCard
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.cover,
                contentDescription = null,
                modifier = Modifier.size(width = 70.dp, height = 90.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 14.sp, color = DyText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(
                    SimpleDateFormat("MM-dd HH:mm").format(Date(item.time)),
                    fontSize = 12.sp, color = DyTextSecondary
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "取消收藏", tint = DyTextSecondary)
            }
        }
    }
}
