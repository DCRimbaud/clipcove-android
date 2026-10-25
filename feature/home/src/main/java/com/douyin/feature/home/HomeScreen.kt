package com.douyin.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.douyin.core.designsystem.DyBorder
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyCyan
import com.douyin.core.designsystem.DyIconTile
import com.douyin.core.designsystem.DyMotion
import com.douyin.core.designsystem.DyInput
import com.douyin.core.designsystem.DyPageHeader
import com.douyin.core.designsystem.DyPanel
import com.douyin.core.designsystem.DyPill
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DySectionHeader
import com.douyin.core.designsystem.DySurfaceHigh
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.core.designsystem.DyTextTertiary
import com.douyin.core.designsystem.dyPressable
import com.douyin.core.model.MediaImage
import com.douyin.core.model.MediaInfo
import com.douyin.core.model.ParseFailure
import com.douyin.data.history.HistoryItem

/** 首页：链接输入、解析状态、结果和最近记录。 */
@Composable
fun HomeScreen(
    input: String,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onParse: () -> Unit,
    loading: Boolean,
    error: ParseFailure?,
    result: MediaInfo?,
    history: List<HistoryItem>,
    onDownload: (String, String, String) -> Unit,
    onDownloadGallery: (String, String, List<MediaImage>) -> Unit,
    onMerge: (String, String, String) -> Unit,
    onHistoryClick: (HistoryItem) -> Unit,
    clipboardSuggestion: String? = null,
    onParseClipboard: () -> Unit = {},
    onDismissClipboard: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onShareResult: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HomeHeader() }
            item {
                ParsePanel(
                    input = input,
                    onInputChange = onInputChange,
                    onPaste = onPaste,
                    onParse = onParse,
                    loading = loading,
                )
            }
            item {
                AnimatedContent(
                    targetState = when {
                        loading -> ParseContentState.Loading
                        error != null -> ParseContentState.Failed(error)
                        result != null -> ParseContentState.Success(result)
                        else -> ParseContentState.Idle
                    },
                    transitionSpec = {
                        (
                            fadeIn(
                                tween(
                                    durationMillis = DyMotion.StandardMs,
                                    easing = DyMotion.EmphasizedEasing,
                                )
                            ) + slideInVertically(
                                animationSpec = tween(
                                    durationMillis = DyMotion.StandardMs,
                                    easing = DyMotion.EmphasizedEasing,
                                ),
                                initialOffsetY = { it / 18 },
                            )
                        ).togetherWith(
                            fadeOut(
                                tween(
                                    durationMillis = DyMotion.QuickMs,
                                    easing = DyMotion.StandardEasing,
                                )
                            )
                        ).using(SizeTransform(clip = false))
                    },
                    label = "parseContent",
                ) { state ->
                    when (state) {
                        ParseContentState.Idle -> Spacer(Modifier.height(0.dp))
                        ParseContentState.Loading -> LoadingPanel()
                        is ParseContentState.Failed -> ErrorPanel(state.error, onParse)
                        is ParseContentState.Success -> ResultCard(
                            media = state.media,
                            onDownload = onDownload,
                            onDownloadGallery = onDownloadGallery,
                            onMerge = onMerge,
                            isFavorite = isFavorite,
                            onToggleFavorite = onToggleFavorite,
                            onShare = onShareResult,
                        )
                    }
                }
            }
            item {
                DySectionHeader(
                    title = "最近解析",
                    subtitle = if (history.isEmpty()) "解析过的内容会保存在这里" else "共 ${history.size} 条本地记录",
                    trailing = {
                        DyPill(text = "${history.size}", color = if (history.isEmpty()) DyTextTertiary else DyCyan)
                    },
                )
            }
            if (history.isEmpty()) {
                item {
                    DyPanel(contentPadding = 14.dp) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DyIconTile(Icons.Filled.History, tint = DyTextSecondary, size = 42.dp)
                            Column(Modifier.weight(1f)) {
                                Text("还没有解析记录", style = MaterialTheme.typography.titleMedium, color = DyText)
                                Text(
                                    "粘贴第一条分享链接，结果会自动保存在本机。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DyTextSecondary,
                                )
                            }
                        }
                    }
                }
            } else {
                items(history, key = { it.awemeId }) { item ->
                    HistoryCard(item) { onHistoryClick(it) }
                }
            }
        }

        AnimatedVisibility(
            visible = clipboardSuggestion != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = tween(
                    durationMillis = DyMotion.StandardMs,
                    easing = DyMotion.EmphasizedEasing,
                ),
                initialOffsetY = { it },
            ) + fadeIn(tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing)),
            exit = slideOutVertically(
                tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing)
            ) { it } + fadeOut(tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing)),
        ) {
            clipboardSuggestion?.let { link ->
                ClipboardSuggestion(
                    link = link,
                    onParse = onParseClipboard,
                    onDismiss = onDismissClipboard,
                )
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    val compact = LocalConfiguration.current.screenWidthDp < 400
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(DyRed.copy(alpha = 0.22f), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(DyRed, Color(0xFFFF7A5C), Color(0xFFFF9A62)),
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("C", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        DyPageHeader(
            title = "ClipCove",
            subtitle = "Keep what you love. Locally.",
            eyebrow = "LOCAL MEDIA · PRIVATE",
            modifier = Modifier.weight(1f),
            trailing = if (compact) {
                null
            } else {
                { DyPill(text = "本地解析", color = DyCyan) }
            },
        )
    }
}

private sealed interface ParseContentState {
    data object Idle : ParseContentState
    data object Loading : ParseContentState
    data class Failed(val error: ParseFailure) : ParseContentState
    data class Success(val media: MediaInfo) : ParseContentState
}

@Composable
private fun LoadingPanel() {
    DyPanel(contentPadding = 14.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = DyRed,
            )
            Column(Modifier.weight(1f)) {
                Text("正在解析", style = MaterialTheme.typography.titleMedium, color = DyText)
                Text(
                    "连接平台并提取原始媒体，请稍候…",
                    style = MaterialTheme.typography.bodySmall,
                    color = DyTextSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = DyRed,
            trackColor = DyBorder.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun ParsePanel(
    input: String,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onParse: () -> Unit,
    loading: Boolean,
) {
    DyPanel(contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("分享链接", style = MaterialTheme.typography.titleMedium, color = DyText)
            Spacer(Modifier.weight(1f))
            DyPill("抖音", DyCyan)
            DyPill("小红书", DyRed)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "粘贴分享文案、短链或作品链接",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DyTextTertiary,
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.Link, contentDescription = null, tint = DyTextSecondary)
            },
            trailingIcon = {
                if (input.isNotEmpty()) {
                    IconButton(onClick = { onInputChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空", tint = DyTextSecondary)
                    }
                }
            },
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DyText,
                unfocusedTextColor = DyText,
                focusedBorderColor = DyRed.copy(alpha = 0.75f),
                unfocusedBorderColor = DyBorder,
                focusedContainerColor = DyInput,
                unfocusedContainerColor = DyInput,
                cursorColor = DyRed,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onPaste,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DyBorder),
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(17.dp), tint = DyText)
                Spacer(Modifier.size(7.dp))
                Text("粘贴", color = DyText)
            }
            Button(
                onClick = onParse,
                enabled = input.isNotBlank() && !loading,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DyRed,
                    contentColor = Color.White,
                    disabledContainerColor = DyRed.copy(alpha = 0.28f),
                    disabledContentColor = Color.White.copy(alpha = 0.55f),
                ),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text(if (loading) "解析中" else "开始解析", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ErrorPanel(error: ParseFailure, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0x1FFF647A),
        border = BorderStroke(1.dp, Color(0x4DFF647A)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Link, contentDescription = null, tint = Color(0xFFFF8A9C), modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("这次没有解析成功", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF9AAA))
                Text(error.message, style = MaterialTheme.typography.bodySmall, color = DyTextSecondary)
            }
            if (error.isRetryable) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun ClipboardSuggestion(
    link: String,
    onParse: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = DySurfaceHigh,
        border = BorderStroke(1.dp, DyBorder),
        shadowElevation = 18.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DyIconTile(Icons.Filled.ContentPaste, tint = DyCyan, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text("检测到分享链接", style = MaterialTheme.typography.labelLarge, color = DyText)
                Text(
                    link,
                    style = MaterialTheme.typography.bodySmall,
                    color = DyTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onParse) { Text("解析", color = DyRed) }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = DyTextSecondary)
            }
        }
    }
}
