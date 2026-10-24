package com.douyin.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.douyin.data.history.HistoryItem
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
/** 首页入口:接收 ViewModel 状态,渲染完整首页 */
@Composable
fun HomeScreen(
    input: String,
    onInputChange: (String) -> Unit,
    onParse: () -> Unit,
    loading: Boolean,
    error: String,
    result: JSONObject?,
    history: List<HistoryItem>,
    onDownload: (String, String, String) -> Unit,
    onMerge: (String, String, String) -> Unit,
    onHistoryClick: (HistoryItem) -> Unit,
    clipboardSuggestion: String? = null,
    onParseClipboard: () -> Unit = {},
    onDismissClipboard: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onShareResult: () -> Unit = {}
) {
    var btnPressed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text("拾刻", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DyText)
                Text("粘贴抖音/小红书链接,一键获取无水印内容", fontSize = 13.sp, color = DyTextSecondary)
                Spacer(Modifier.height(14.dp))
            }
            item {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("粘贴抖音/小红书分享链接或口令", color = DyTextSecondary) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (input.isNotEmpty()) {
                        IconButton(onClick = { onInputChange("") }) {
                            Text("✕", color = DyTextSecondary, fontSize = 16.sp)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DyRed,
                    unfocusedBorderColor = DyInput,
                    focusedContainerColor = DyInput,
                    unfocusedContainerColor = DyInput,
                    cursorColor = DyRed
                )
            )
                Spacer(Modifier.height(12.dp))
                // 解析按钮:按压缩放反馈
                val btnScale by animateFloatAsState(
                    targetValue = if (btnPressed) 0.94f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "btnScale"
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                ) {
                    Button(
                        onClick = onParse,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        btnPressed = true
                                        tryAwaitRelease()
                                        btnPressed = false
                                    },
                                    onTap = { onParse() }
                                )
                            },
                        enabled = !loading,
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DyRed)
                    ) {
                        Text("解析", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (loading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = DyRed,
                        trackColor = DyInput
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("解析中...", fontSize = 12.sp, color = DyTextSecondary)
                }
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, fontSize = 14.sp, color = Color(0xFFFF6B6B))
                }
            }
            result?.let {
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    // 结果卡片:淡入 + 上滑
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(350)) + slideInVertically(
                            animationSpec = tween(350),
                            initialOffsetY = { it / 8 }
                        )
                    ) {
                        ResultCard(
                            obj = it,
                            onDownload = onDownload,
                            onMerge = onMerge,
                            isFavorite = isFavorite,
                            onToggleFavorite = onToggleFavorite,
                            onShare = onShareResult
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(22.dp))
                Text("最近解析", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DyText)
                Spacer(Modifier.height(8.dp))
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "暂无记录,解析过的视频会出现在这里",
                        fontSize = 13.sp,
                        color = DyTextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(history, key = { it.awemeId }) { item ->
                    HistoryCard(item) { onHistoryClick(it) }
                }
            }
        }

        // 剪贴板快捷解析卡片(底部滑入)
        AnimatedVisibility(
            visible = clipboardSuggestion != null,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                initialOffsetY = { it }
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
        ) {
            clipboardSuggestion?.let { link ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clickable { onParseClipboard() },
                    shape = RoundedCornerShape(14.dp),
                    color = DyCard,
                    shadowElevation = 8.dp
                ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("检测到链接", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DyText)
                        Text(link, fontSize = 12.sp, color = DyTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("解析", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DyRed,
                        modifier = Modifier.padding(horizontal = 12.dp))
                    Text("✕", fontSize = 14.sp, color = DyTextSecondary,
                        modifier = Modifier.clickable { onDismissClipboard() })
                }
                }
            }
        }
    }
}

private data class ImageItem(val url: String, val originalUrl: String, val gifUrl: String)

/** 图集全屏查看器:左右滑动浏览 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FullscreenImagePager(images: List<ImageItem>, initialIndex: Int, onDismiss: () -> Unit) {
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
private fun ResultCard(
    obj: JSONObject,
    onDownload: (String, String, String) -> Unit,
    onMerge: (String, String, String) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val playUrl = obj.optString("play_url", "")
    val musicUrl = obj.optString("music", "")
    val awemeId = obj.optString("aweme_id", "")
    val title = obj.optString("title", "视频 $awemeId").ifBlank { "视频 $awemeId" }
    val images = (0 until (obj.optJSONArray("images")?.length() ?: 0)).map { i ->
        val it = obj.optJSONArray("images")!!.getJSONObject(i)
        ImageItem(
            it.optString("url", ""),
            it.optString("original_url", ""),
            it.optString("gif_url", "")
        )
    }
    val stats = obj.optJSONObject("stats") ?: JSONObject()
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
            val cover = obj.optString("cover", "")
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
            Text(obj.optString("title", "(无标题)"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DyText)
            Spacer(Modifier.height(4.dp))
            Text("@" + obj.optString("author", ""), fontSize = 14.sp, color = DyCyan)
            Spacer(Modifier.height(4.dp))
            val secs = obj.optLong("duration", 0) / 1000
            Text(
                "时长 ${secs / 60}:${String.format("%02d", secs % 60)}" +
                    " | 点赞 ${stats.optLong("digg_count", 0)}" +
                    " | 评论 ${stats.optLong("comment_count", 0)}" +
                    " | 收藏 ${stats.optLong("collect_count", 0)}",
                fontSize = 12.sp, color = DyTextSecondary
            )
            // 发布时间 + 音乐名
            val createTime = obj.optLong("create_time", 0)
            val musicTitle = obj.optString("music_title", "")
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
                if (img.gifUrl.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ActionButton("下载动图 ${i + 1}", DyGreen) {
                        onDownload("动图 ${i + 1}", "${awemeId}_${i + 1}_动图.mp4", img.gifUrl)
                    }
                    if (musicUrl.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        ActionButton("动图+音乐 合成 ${i + 1}", DyAmber) {
                            onMerge(img.gifUrl, musicUrl, "${awemeId}_${i + 1}_合并.mp4")
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
private fun HistoryCard(item: HistoryItem, onClick: (HistoryItem) -> Unit) {
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


