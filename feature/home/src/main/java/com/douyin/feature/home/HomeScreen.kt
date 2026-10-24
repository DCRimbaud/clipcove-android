package com.douyin.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyInput
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.core.model.MediaInfo
import com.douyin.core.model.ParseFailure
import com.douyin.data.history.HistoryItem

/** 首页入口:接收 ViewModel 状态,渲染完整首页 */
@Composable
fun HomeScreen(
    input: String,
    onInputChange: (String) -> Unit,
    onParse: () -> Unit,
    loading: Boolean,
    error: ParseFailure?,
    result: MediaInfo?,
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
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x22FF6B6B),
                        border = BorderStroke(1.dp, Color(0x66FF6B6B))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                error.message,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = Color(0xFFFF8A8A)
                            )
                            if (error.isRetryable) {
                                TextButton(onClick = onParse) {
                                    Text("重试", color = DyRed)
                                }
                            }
                        }
                    }
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
                            media = it,
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
