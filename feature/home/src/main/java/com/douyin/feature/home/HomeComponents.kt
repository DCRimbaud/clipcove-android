package com.douyin.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.douyin.core.designsystem.DyBorder
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyCyan
import com.douyin.core.designsystem.DyGreen
import com.douyin.core.designsystem.DyIconTile
import com.douyin.core.designsystem.DyMotion
import com.douyin.core.designsystem.DyInput
import com.douyin.core.designsystem.DyPill
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DySurfaceHigh
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.core.designsystem.dyPressable
import com.douyin.core.model.MediaImage
import com.douyin.core.model.MediaInfo
import com.douyin.core.model.MediaPlatform
import com.douyin.core.model.MediaStats
import com.douyin.core.model.VideoCandidate
import com.douyin.core.model.VideoSourceKind
import com.douyin.data.history.HistoryItem
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun ResultCard(
    media: MediaInfo,
    onDownload: (String, String, String) -> Unit,
    onDownloadGallery: (String, String, List<MediaImage>) -> Unit,
    onMerge: (String, String, String) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onShare: () -> Unit = {},
) {
    val context = LocalContext.current
    var fullscreenImageIndex by remember { mutableIntStateOf(-1) }
    var fullscreenVideo by remember { mutableStateOf(false) }
    var confirmWatermarkDownload by remember { mutableStateOf(false) }
    val favoriteScale = remember { Animatable(1f) }
    val platformColor = if (media.platform == MediaPlatform.XIAOHONGSHU) DyRed else DyCyan
    val title = media.title.ifBlank { "媒体 ${media.id}" }
    val candidates = remember(media.id, media.videoCandidates, media.videoUrl) {
        media.videoCandidates.ifEmpty {
            if (media.videoUrl.isBlank()) {
                emptyList()
            } else {
                listOf(
                    VideoCandidate(
                        label = "Platform stream",
                        url = media.videoUrl,
                        sourceKind = VideoSourceKind.OTHER,
                    )
                )
            }
        }
    }
    val initialCandidateIndex = remember(media.id, candidates) {
        candidates.indexOfFirst { it.sourceKind == VideoSourceKind.ORIGINAL }.takeIf { it >= 0 } ?: 0
    }
    var selectedCandidateIndex by remember(media.id) { mutableIntStateOf(initialCandidateIndex) }
    val selectedCandidate = candidates.getOrNull(selectedCandidateIndex)
    val previewUrl = selectedCandidate?.url ?: media.videoUrl
    val favoriteTint by animateColorAsState(
        targetValue = if (isFavorite) DyRed else DyTextSecondary,
        animationSpec = tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing),
        label = "favoriteTint",
    )
    val favoriteContainer by animateColorAsState(
        targetValue = if (isFavorite) DyRed.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing),
        label = "favoriteContainer",
    )
    val downloadVideo = {
        if (previewUrl.isNotBlank()) {
            onDownload(title, safeFilename(title, "mp4"), previewUrl)
        }
    }

    if (fullscreenImageIndex >= 0) {
        FullscreenImagePager(media.images, fullscreenImageIndex) {
            fullscreenImageIndex = -1
        }
    }
    if (fullscreenVideo) {
        FullscreenVideoDialog(previewUrl) {
            fullscreenVideo = false
            (context as? android.app.Activity)?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(isFavorite) {
        if (isFavorite) {
            favoriteScale.snapTo(0.82f)
            favoriteScale.animateTo(
                1.08f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            )
            favoriteScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = DyCard,
        border = BorderStroke(1.dp, DyBorder.copy(alpha = 0.78f)),
    ) {
        Column {
            MediaPreview(
                media = media,
                previewUrl = previewUrl,
                platformColor = platformColor,
                onFullscreenVideo = {
                    fullscreenVideo = true
                    (context as? android.app.Activity)?.requestedOrientation =
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                },
                onOpenImage = { fullscreenImageIndex = it },
            )

            Column(Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DyPill(media.platform.displayName, platformColor)
                    DyPill(if (media.hasVideo) "视频" else "图集", DyTextSecondary)
                    if (media.durationMs > 0) {
                        Text(
                            formatDuration(media.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = DyTextSecondary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (media.createdAtSeconds > 0) {
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                                .format(Date(media.createdAtSeconds * 1000)),
                            style = MaterialTheme.typography.bodySmall,
                            color = DyTextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = DyText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (media.author.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text("@${media.author}", style = MaterialTheme.typography.bodyMedium, color = platformColor)
                }
                if (media.stats.hasAnyValue()) {
                    Spacer(Modifier.height(12.dp))
                    StatsRow(media.stats)
                }
                if (candidates.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    VideoCandidateSelector(
                        candidates = candidates,
                        selectedIndex = selectedCandidateIndex,
                        onSelected = { selectedCandidateIndex = it },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isFavorite) DyRed.copy(alpha = 0.7f) else DyBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = favoriteContainer,
                        ),
                    ) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = favoriteTint,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = favoriteScale.value
                                    scaleY = favoriteScale.value
                                },
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isFavorite) "已收藏" else "收藏", color = if (isFavorite) DyRed else DyText)
                    }
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DyBorder),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = DyTextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("分享", color = DyText)
                    }
                }
                if (previewUrl.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (selectedCandidate?.watermarkRisk == true) {
                                confirmWatermarkDownload = true
                            } else {
                                downloadVideo()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedCandidate?.watermarkRisk == true) DyAmber else DyRed,
                        ),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                selectedCandidate?.sourceKind == VideoSourceKind.ORIGINAL -> "下载原片"
                                selectedCandidate?.watermarkRisk == true -> "下载转码版本"
                                else -> "下载视频"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (media.musicUrl.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    MusicDownloadRow(
                        title = media.musicTitle.ifBlank { "原声" },
                        onDownload = {
                            onDownload(
                                "音乐 $title",
                                safeFilename("$title-原声", "mp3"),
                                media.musicUrl,
                            )
                        },
                    )
                }
                if (media.images.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = DyTextSecondary, modifier = Modifier.size(18.dp))
                        Text("图集 · ${media.images.size} 张", style = MaterialTheme.typography.titleMedium, color = DyText)
                        Spacer(Modifier.weight(1f))
                        if (media.images.size > 1) {
                            TextButton(
                                onClick = {
                                    onDownloadGallery(title, media.id, media.images)
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("下载全部", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ImageGrid(
                        media = media,
                        title = title,
                        onOpen = { fullscreenImageIndex = it },
                        onDownload = onDownload,
                        onMerge = onMerge,
                    )
                }
            }
        }
    }

    if (confirmWatermarkDownload) {
        AlertDialog(
            onDismissRequest = { confirmWatermarkDownload = false },
            containerColor = DyCard,
            icon = { Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = DyAmber) },
            title = { Text("这个版本可能含水印", color = DyText) },
            text = {
                Text(
                    "当前选择的是平台转码流，画面中可能包含平台或作者水印。建议先返回上一级重新解析，必要时再下载此版本。",
                    color = DyTextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWatermarkDownload = false
                        downloadVideo()
                    },
                ) { Text("仍然下载", color = DyAmber) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWatermarkDownload = false }) {
                    Text("取消", color = DyTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun VideoCandidateSelector(
    candidates: List<VideoCandidate>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val onlyWatermarked = candidates.isNotEmpty() && candidates.all { it.watermarkRisk }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.HighQuality,
                contentDescription = null,
                tint = DyCyan,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text("下载版本", style = MaterialTheme.typography.titleMedium, color = DyText)
            Spacer(Modifier.weight(1f))
            Text(
                if (onlyWatermarked) "仅转码流" else "默认原片优先",
                style = MaterialTheme.typography.bodySmall,
                color = if (onlyWatermarked) DyAmber else DyGreen,
            )
        }
        candidates.forEachIndexed { index, candidate ->
            val selected = index == selectedIndex
            val selectionColor by animateColorAsState(
                targetValue = if (selected) DyRed.copy(alpha = 0.08f) else DyInput,
                animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
                label = "candidateColor_$index",
            )
            val selectionBorder by animateColorAsState(
                targetValue = if (selected) DyRed.copy(alpha = 0.68f) else DyBorder.copy(alpha = 0.62f),
                animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
                label = "candidateBorder_$index",
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .dyPressable(pressedScale = 0.985f) { onSelected(index) },
                shape = RoundedCornerShape(14.dp),
                color = selectionColor,
                border = BorderStroke(1.dp, selectionBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSelected(index) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = DyRed,
                            unselectedColor = DyTextSecondary,
                        ),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            videoCandidateTitle(candidate, index),
                            style = MaterialTheme.typography.labelLarge,
                            color = DyText,
                        )
                        val metadata = videoCandidateMetadata(candidate)
                        if (metadata.isNotBlank()) {
                            Text(
                                metadata,
                                style = MaterialTheme.typography.bodySmall,
                                color = DyTextSecondary,
                            )
                        }
                    }
                    DyPill(
                        text = when {
                            candidate.sourceKind == VideoSourceKind.ORIGINAL -> "无水印原片"
                            candidate.watermarkRisk -> "可能含水印"
                            else -> "转码"
                        },
                        color = when {
                            candidate.sourceKind == VideoSourceKind.ORIGINAL -> DyGreen
                            candidate.watermarkRisk -> DyAmber
                            else -> DyCyan
                        },
                    )
                }
            }
        }
        if (onlyWatermarked) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                color = DyAmber.copy(alpha = 0.09f),
                border = BorderStroke(1.dp, DyAmber.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = DyAmber,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        "当前只拿到平台转码流，画面可能包含水印。",
                        style = MaterialTheme.typography.bodySmall,
                        color = DyTextSecondary,
                    )
                }
            }
        }
    }
}

private fun videoCandidateTitle(candidate: VideoCandidate, index: Int): String = when {
    candidate.sourceKind == VideoSourceKind.ORIGINAL -> "原片 · 原画质"
    candidate.sourceKind == VideoSourceKind.TRANSCODED -> "平台转码视频"
    else -> "备用视频线路 ${index + 1}"
}

private fun videoCandidateMetadata(candidate: VideoCandidate): String = buildList {
    if (candidate.resolutionLabel.isNotBlank()) add(candidate.resolutionLabel)
    if (candidate.sizeBytes > 0) add(formatFileSize(candidate.sizeBytes))
    if (candidate.bitrate > 0) add(formatBitrate(candidate.bitrate))
}.joinToString(" · ")

@Composable
private fun MediaPreview(
    media: MediaInfo,
    previewUrl: String,
    platformColor: Color,
    onFullscreenVideo: () -> Unit,
    onOpenImage: (Int) -> Unit,
) {
    when {
        previewUrl.isNotBlank() -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            ) {
                ExoPlayerPreview(previewUrl, Modifier.fillMaxSize(), autoPlay = false)
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("视频预览", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
                TextButton(
                    onClick = onFullscreenVideo,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                ) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("全屏", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        media.images.isNotEmpty() -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .dyPressable(pressedScale = 0.99f) { onOpenImage(0) },
            ) {
                AsyncImage(
                    model = media.coverUrl.ifBlank { media.images.first().preferredUrl },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                            )
                        )
                )
                DyPill(
                    text = if (media.images.size > 1) "1 / ${media.images.size}" else "查看原图",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    icon = Icons.Filled.Fullscreen,
                )
            }
        }
        media.coverUrl.isNotBlank() -> {
            AsyncImage(
                model = media.coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                contentScale = ContentScale.Crop,
            )
        }
        else -> {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 7f).background(DyInput),
                contentAlignment = Alignment.Center,
            ) {
                DyPill("无预览图", platformColor)
            }
        }
    }
}

@Composable
private fun StatsRow(stats: MediaStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Metric("点赞", stats.diggCount, Modifier.weight(1f))
        Metric("评论", stats.commentCount, Modifier.weight(1f))
        Metric("收藏", stats.collectCount, Modifier.weight(1f))
    }
}

@Composable
private fun Metric(label: String, value: Long, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = DyInput,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, DyBorder.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(formatCount(value), style = MaterialTheme.typography.labelLarge, color = DyText)
            Text(label, style = MaterialTheme.typography.bodySmall, color = DyTextSecondary)
        }
    }
}

@Composable
private fun MusicDownloadRow(title: String, onDownload: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DyInput,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, DyBorder.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DyIconTile(Icons.Filled.MusicNote, tint = DyGreen, size = 38.dp)
            Column(Modifier.weight(1f)) {
                Text("原声", style = MaterialTheme.typography.bodySmall, color = DyTextSecondary)
                Text(title, style = MaterialTheme.typography.labelLarge, color = DyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = DyGreen)
                Spacer(Modifier.width(4.dp))
                Text("下载", color = DyGreen)
            }
        }
    }
}

@Composable
private fun ImageGrid(
    media: MediaInfo,
    title: String,
    onOpen: (Int) -> Unit,
    onDownload: (String, String, String) -> Unit,
    onMerge: (String, String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        media.images.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { columnIndex, image ->
                    val index = rowIndex * 2 + columnIndex
                    ImageTile(
                        image = image,
                        index = index,
                        title = title,
                        mediaId = media.id,
                        onOpen = { onOpen(index) },
                        onDownload = onDownload,
                        onMerge = onMerge,
                        musicUrl = media.musicUrl,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ImageTile(
    image: MediaImage,
    index: Int,
    title: String,
    mediaId: String,
    onOpen: () -> Unit,
    onDownload: (String, String, String) -> Unit,
    onMerge: (String, String, String) -> Unit,
    musicUrl: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .dyPressable(pressedScale = 0.985f, onClick = onOpen),
        ) {
            AsyncImage(
                model = image.url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("${index + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(
                onClick = {
                    onDownload(
                        "${title}_原图${index + 1}",
                        "${mediaId}_${index + 1}_原图.jpg",
                        image.preferredUrl,
                    )
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("原图", fontSize = 12.sp, color = DyText)
            }
            if (image.livePhotoUrl.isNotBlank()) {
                TextButton(
                    onClick = {
                        onDownload(
                            "${title}_动图${index + 1}",
                            "${mediaId}_${index + 1}_动图.mp4",
                            image.livePhotoUrl,
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("动图", fontSize = 12.sp, color = DyGreen)
                }
                if (musicUrl.isNotBlank()) {
                    TextButton(
                        onClick = { onMerge(image.livePhotoUrl, musicUrl, "${mediaId}_${index + 1}_合并.mp4") },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("合成", fontSize = 12.sp, color = DyCyan)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryCard(item: HistoryItem, onClick: (HistoryItem) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dyPressable(pressedScale = 0.985f) { onClick(item) },
        shape = RoundedCornerShape(18.dp),
        color = DyCard,
        border = BorderStroke(1.dp, DyBorder.copy(alpha = 0.62f)),
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
                    .size(width = 78.dp, height = 58.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = DyText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DyPill(
                        text = if (item.sourceUrl.contains("xiaohongshu") || item.sourceUrl.contains("xhslink")) "小红书" else "抖音",
                        color = if (item.sourceUrl.contains("xiaohongshu") || item.sourceUrl.contains("xhslink")) DyRed else DyCyan,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(Date(item.time)),
                        style = MaterialTheme.typography.bodySmall,
                        color = DyTextSecondary,
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DyTextSecondary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullscreenImagePager(images: List<MediaImage>, initialIndex: Int, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = images[page].preferredUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Text(
                "${pagerState.currentPage + 1} / ${images.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 30.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
    }
}

@Composable
private fun FullscreenVideoDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ExoPlayerPreview(url, Modifier.fillMaxSize(), autoPlay = true)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "退出全屏", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ExoPlayerPreview(
    url: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = false
        }
    }
    LaunchedEffect(url) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (active && autoPlay) player.play() else player.pause()
    }
    LaunchedEffect(active, autoPlay) {
        if (!active) player.pause() else if (autoPlay) player.play()
    }
    DisposableEffect(lifecycleOwner, url) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (autoPlay && active) player.play() else player.pause()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
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
        modifier = modifier,
    )
}

private fun MediaStats.hasAnyValue(): Boolean =
    diggCount > 0 || commentCount > 0 || collectCount > 0 || shareCount > 0

private fun formatCount(value: Long): String = when {
    value >= 100_000_000 -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000 -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

private fun formatBitrate(bitsPerSecond: Long): String = when {
    bitsPerSecond >= 1_000_000L -> "%.1f Mbps".format(bitsPerSecond / 1_000_000.0)
    bitsPerSecond >= 1_000L -> "%.0f Kbps".format(bitsPerSecond / 1_000.0)
    else -> "$bitsPerSecond bps"
}

private fun safeFilename(title: String, ext: String): String {
    val cleaned = title
        .replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(60)
        .ifBlank { "下载文件" }
    return "$cleaned.$ext"
}
