package com.douyin.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.douyin.core.designsystem.DyAmber
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyCyan
import com.douyin.core.designsystem.DyGreen
import com.douyin.core.designsystem.DyInput
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.core.model.MediaImage
import com.douyin.core.model.MediaInfo
import com.douyin.data.history.HistoryItem
import java.text.SimpleDateFormat
import java.util.Date

/** 图集全屏查看器:左右滑动浏览 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FullscreenImagePager(images: List<MediaImage>, initialIndex: Int, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = images[page].url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            // 页码 + 关闭
            Text(
                "${pagerState.currentPage + 1} / ${images.size}",
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            )
            Text(
                "✕ 关闭",
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color(0x88000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable { onDismiss() }
            )
        }
    }
}

/** 视频全屏播放器(横屏) */
@Composable
private fun FullscreenVideoDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            ExoPlayerPreview(url, Modifier.fillMaxSize())
            Text(
                "✕ 退出全屏",
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color(0x88000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable { onDismiss() }
            )
        }
    }
}

@Composable
internal fun ResultCard(
    media: MediaInfo,
    onDownload: (String, String, String) -> Unit,
    onMerge: (String, String, String) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val playUrl = media.videoUrl
    val musicUrl = media.musicUrl
    val awemeId = media.id
    val title = media.title.ifBlank { "媒体 $awemeId" }
    val images = media.images
    val stats = media.stats
    val context = LocalContext.current

    // 全屏查看器状态
    var fullscreenImageIndex by remember { mutableStateOf(-1) }
    var fullscreenVideo by remember { mutableStateOf(false) }

    if (fullscreenImageIndex >= 0) {
        FullscreenImagePager(images, fullscreenImageIndex) {
            fullscreenImageIndex = -1
        }
    }
    if (fullscreenVideo) {
        FullscreenVideoDialog(url = playUrl, onDismiss = {
            fullscreenVideo = false
            (context as? android.app.Activity)?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        })
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DyCard,
        border = BorderStroke(1.dp, DyInput)
    ) {
        Column(Modifier.padding(12.dp)) {
            val cover = media.coverUrl
            if (cover.isNotEmpty()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            if (playUrl.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))) {
                    // 全屏打开时暂停内嵌播放器,避免双播放器并行
                    ExoPlayerPreview(playUrl, Modifier.fillMaxSize(), active = !fullscreenVideo)
                    // 全屏按钮浮层:PlayerView 会消费触摸,需独立可点击元素
                    Text(
                        "⛶ 全屏",
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color(0x88000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable {
                                fullscreenVideo = true
                                (context as? android.app.Activity)?.requestedOrientation =
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(media.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DyText)
            Spacer(Modifier.height(4.dp))
            Text("@" + media.author, fontSize = 14.sp, color = DyCyan)
            Spacer(Modifier.height(4.dp))
            val secs = media.durationMs / 1000
            Text(
                "时长 ${secs / 60}:${String.format("%02d", secs % 60)}" +
                    " | 点赞 ${stats.diggCount}" +
                    " | 评论 ${stats.commentCount}" +
                    " | 收藏 ${stats.collectCount}",
                fontSize = 12.sp, color = DyTextSecondary
            )
            // 发布时间 + 音乐名
            val createTime = media.createdAtSeconds
            val musicTitle = media.musicTitle
            if (createTime > 0 || musicTitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (createTime > 0) {
                            append("发布于 ")
                            append(SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(createTime * 1000)))
                        }
                        if (musicTitle.isNotEmpty()) {
                            if (isNotEmpty()) append("  |  ")
                            append("音乐:$musicTitle")
                        }
                    },
                    fontSize = 12.sp, color = DyTextSecondary
                )
            }
            Spacer(Modifier.height(10.dp))
            // 收藏 + 分享(收藏时心形弹跳)
            val favScale = remember { Animatable(1f) }
            LaunchedEffect(isFavorite) {
                if (isFavorite) {
                    favScale.snapTo(0.5f)
                    favScale.animateTo(1.3f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    favScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, if (isFavorite) DyRed else DyInput)
                ) {
                    Text(
                        if (isFavorite) "♥ 已收藏" else "♡ 收藏",
                        fontSize = 14.sp,
                        color = if (isFavorite) DyRed else DyText,
                        modifier = Modifier.graphicsLayer {
                            scaleX = favScale.value
                            scaleY = favScale.value
                        }
                    )
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, DyInput)
                ) {
                    Text("分享", fontSize = 14.sp, color = DyText)
                }
            }
            if (playUrl.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ActionButton("下载无水印视频", DyRed) {
                    onDownload(title, safeFilename(title, "mp4"), playUrl)
                }
            }
            if (musicUrl.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ActionButton("下载音乐", DyCard) {
                        onDownload("音乐 $title", safeFilename("$title-音乐", "mp3"), musicUrl)
                }
            }
            images.forEachIndexed { i, img ->
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = img.url,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { fullscreenImageIndex = i },
                    contentScale = ContentScale.Crop
                )
                if (img.livePhotoUrl.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ActionButton("下载动图 ${i + 1}", DyGreen) {
                        onDownload("动图 ${i + 1}", "${awemeId}_${i + 1}_动图.mp4", img.livePhotoUrl)
                    }
                    if (musicUrl.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        ActionButton("动图+音乐 合成 ${i + 1}", DyAmber) {
                            onMerge(img.livePhotoUrl, musicUrl, "${awemeId}_${i + 1}_合并.mp4")
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onDownload("图片 ${i + 1}", "${awemeId}_${i + 1}.jpg", img.url) },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DyRed)
                        ) {
                            Text("下载图片 ${i + 1}", fontSize = 14.sp)
                        }
                        if (img.originalUrl.isNotEmpty() && img.originalUrl != img.url) {
                            OutlinedButton(
                                onClick = { onDownload("原图 ${i + 1}", "${awemeId}_${i + 1}_原图.jpg", img.originalUrl) },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(23.dp),
                                border = BorderStroke(1.dp, DyInput)
                            ) {
                                Text("原图", fontSize = 14.sp, color = DyText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(23.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(text, fontSize = 14.sp)
    }
}

/** ExoPlayer 预览:生命周期感知,离开组合自动释放;active=false 时暂停 */
@Composable
private fun ExoPlayerPreview(url: String, modifier: Modifier = Modifier, active: Boolean = true) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    // url 变化时更新媒体源
    LaunchedEffect(url) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (active) player.play()
    }
    LaunchedEffect(active) {
        if (active) player.play() else player.pause()
    }
    // 释放绑定 url:url 变化时旧 player 立即释放,避免解码器/音频会话泄漏
    DisposableEffect(lifecycleOwner, url) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                this.player = player
            }
        },
        modifier = modifier
    )
}

@Composable
internal fun HistoryCard(item: HistoryItem, onClick: (HistoryItem) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onClick(item) },
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
        }
    }
}

/** 由标题生成安全文件名:清洗非法字符、截断长度、保证扩展名 */
private fun safeFilename(title: String, ext: String): String {
    val cleaned = title
        .replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(60)
        .ifBlank { "下载文件" }
    return "$cleaned.$ext"
}


