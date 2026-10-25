package com.douyin.feature.favorites

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.douyin.core.designsystem.DyBorder
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyCyan
import com.douyin.core.designsystem.DyEmptyState
import com.douyin.core.designsystem.DyPageHeader
import com.douyin.core.designsystem.DyPill
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DySurfaceHigh
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.core.model.MediaPlatform
import com.douyin.data.favorites.FavoriteItem
import com.douyin.data.favorites.FavoritesRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FavoritesScreen(
    repository: FavoritesRepository,
    onOpen: (FavoriteItem) -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val items = remember(version) { repository.load() }
    val filteredItems = items.filter { item ->
        query.isBlank() ||
            item.title.contains(query, ignoreCase = true) ||
            item.awemeId.contains(query, ignoreCase = true) ||
            item.sourceUrl.contains(query, ignoreCase = true)
    }

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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索标题、作品 ID 或链接", color = DyTextSecondary) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = DyTextSecondary)
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空搜索", tint = DyTextSecondary)
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DyText,
                    unfocusedTextColor = DyText,
                    focusedBorderColor = DyRed.copy(alpha = 0.75f),
                    unfocusedBorderColor = DyBorder,
                    focusedContainerColor = DySurfaceHigh,
                    unfocusedContainerColor = DySurfaceHigh,
                    cursorColor = DyRed,
                ),
            )
            Spacer(Modifier.height(12.dp))
            if (filteredItems.isEmpty()) {
                DyEmptyState(
                    icon = Icons.Filled.Search,
                    title = "没有匹配的收藏",
                    description = "换一个标题、作品 ID 或链接关键词试试。",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredItems, key = { it.awemeId }) { item ->
                        FavoriteCard(
                            item = item,
                            onRemove = {
                                repository.remove(item.awemeId)
                                version++
                            },
                            onCopyLink = { copyFavoriteLink(context, item) },
                            onClick = { onOpen(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(
    item: FavoriteItem,
    onRemove: () -> Unit,
    onCopyLink: () -> Unit,
    onClick: () -> Unit,
) {
    val isXhs = item.platform == MediaPlatform.XIAOHONGSHU ||
        item.awemeId.matches(Regex("""(?i)[a-f0-9]{24}"""))
    val platformColor = if (isXhs) DyRed else DyCyan
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    DyPill(if (isXhs) "小红书" else "抖音", platformColor)
                    Text(
                        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(item.time)),
                        style = MaterialTheme.typography.bodySmall,
                        color = DyCyan,
                    )
                }
                TextButton(
                    onClick = onCopyLink,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) {
                    Text("复制链接", color = platformColor, fontSize = 12.sp)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Favorite, contentDescription = "取消收藏", tint = DyRed)
            }
        }
    }
}

private fun copyFavoriteLink(context: Context, item: FavoriteItem) {
    val fallback = when {
        item.platform == MediaPlatform.XIAOHONGSHU ||
            item.awemeId.matches(Regex("""(?i)[a-f0-9]{24}""")) ->
            "https://www.xiaohongshu.com/explore/${item.awemeId}"
        item.awemeId.matches(Regex("""\d{15,20}""")) ->
            "https://www.douyin.com/video/${item.awemeId}"
        else -> item.awemeId
    }
    val link = item.sourceUrl.ifBlank { fallback }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ClipCove link", link))
    Toast.makeText(context, "已复制源链接", Toast.LENGTH_SHORT).show()
}
