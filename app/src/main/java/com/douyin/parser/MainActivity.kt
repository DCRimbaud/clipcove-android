package com.douyin.parser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.douyin.core.designsystem.DyAmbientBackground
import com.douyin.core.designsystem.DyBorder
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyMotion
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.core.designsystem.DyTheme
import com.douyin.core.designsystem.dyPressable
import com.douyin.core.model.MediaPlatform
import com.douyin.data.download.DownloadRepository
import com.douyin.data.favorites.FavoritesRepository
import com.douyin.feature.downloads.DownloadsScreen
import com.douyin.feature.favorites.FavoritesScreen
import com.douyin.feature.home.HomeScreen
import com.douyin.feature.settings.SettingsScreen
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

/** 应用壳：渐变背景、底部导航与页面路由。 */
class MainActivity : ComponentActivity() {

    private val downloadRepository: DownloadRepository by inject()
    private val favoritesRepository: FavoritesRepository by inject()

    val pendingIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        pendingIntent.value = intent
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.lifecycle.compose.LocalLifecycleOwner provides this
            ) {
                DyTheme {
                    DyAmbientBackground(modifier = Modifier.fillMaxSize()) {
                        DyNavHost(downloadRepository, favoritesRepository, pendingIntent)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent.value = intent
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATION,
            )
        }
    }

    private companion object {
        const val REQ_NOTIFICATION = 100
    }
}

private data class Dest(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    Dest("home", "首页", Icons.Filled.Home),
    Dest("favorites", "收藏", Icons.Filled.Favorite),
    Dest("downloads", "下载", Icons.Filled.Download),
    Dest("settings", "设置", Icons.Filled.Settings),
)

@Composable
private fun DyNavHost(
    downloadRepository: DownloadRepository,
    favoritesRepository: FavoritesRepository,
    pendingIntent: androidx.compose.runtime.MutableState<Intent?>,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            DyBottomNavigation(
                currentRoute = currentDest?.route,
                onNavigate = { dest ->
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
            enterTransition = {
                androidx.compose.animation.fadeIn(
                    tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing)
                ) + slideInHorizontally(
                    animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
                    initialOffsetX = { it / 14 },
                )
            },
            exitTransition = {
                androidx.compose.animation.fadeOut(
                    tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing)
                ) + slideOutHorizontally(
                    animationSpec = tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing),
                    targetOffsetX = { -it / 24 },
                )
            },
            popEnterTransition = {
                androidx.compose.animation.fadeIn(
                    tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing)
                ) + slideInHorizontally(
                    animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
                    initialOffsetX = { -it / 14 },
                )
            },
            popExitTransition = {
                androidx.compose.animation.fadeOut(
                    tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing)
                ) + slideOutHorizontally(
                    animationSpec = tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing),
                    targetOffsetX = { it / 24 },
                )
            },
        ) {
            composable("home") {
                val vm: com.douyin.feature.home.HomeViewModel = koinViewModel()
                val input by vm.input.collectAsStateWithLifecycle()
                val loading by vm.loading.collectAsStateWithLifecycle()
                val error by vm.error.collectAsStateWithLifecycle()
                val result by vm.result.collectAsStateWithLifecycle()
                val history by vm.history.collectAsStateWithLifecycle()
                val clipboardSuggestion by vm.clipboardSuggestion.collectAsStateWithLifecycle()
                val isFavorite by vm.isFavorite.collectAsStateWithLifecycle()
                val context = LocalContext.current

                androidx.compose.runtime.LaunchedEffect(pendingIntent.value) {
                    vm.handleIntent(pendingIntent.value)
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    vm.refreshLocalState()
                    vm.checkClipboard()
                }
                HomeScreen(
                    input = input,
                    onInputChange = vm::onInputChange,
                    onPaste = vm::pasteClipboard,
                    onParse = vm::parse,
                    loading = loading,
                    error = error,
                    result = result,
                    history = history,
                    onDownload = vm::enqueueDownload,
                    onDownloadGallery = vm::enqueueGallery,
                    onMerge = vm::mergeVideo,
                    onHistoryClick = { item ->
                        val target = if (item.sourceUrl.isNotEmpty()) item.sourceUrl else item.awemeId
                        vm.onInputChange(target)
                        vm.parse()
                    },
                    clipboardSuggestion = clipboardSuggestion,
                    onParseClipboard = vm::parseClipboardSuggestion,
                    onDismissClipboard = vm::dismissClipboardSuggestion,
                    isFavorite = isFavorite,
                    onToggleFavorite = vm::toggleFavorite,
                    onShareResult = {
                        result?.let { media ->
                            val text = buildString {
                                append("【${media.platform.displayName}】${media.title}\n")
                                append("作者：@${media.author}\n")
                                append("ID：${media.id}")
                            }
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(share, "分享到"))
                        }
                    },
                )
            }
            composable("favorites") {
                val vm: com.douyin.feature.home.HomeViewModel = koinViewModel()
                FavoritesScreen(favoritesRepository) { item ->
                    val target = item.sourceUrl.ifBlank {
                        val isXhs = item.platform == MediaPlatform.XIAOHONGSHU ||
                            item.awemeId.matches(Regex("""(?i)[a-f0-9]{24}"""))
                        if (isXhs) {
                            "https://www.xiaohongshu.com/explore/${item.awemeId}"
                        } else {
                            item.awemeId
                        }
                    }
                    vm.onInputChange(target)
                    vm.parse()
                    navController.navigate("home") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                    }
                }
            }
            composable("downloads") {
                DownloadsScreen(downloadRepository)
            }
            composable("settings") {
                val vm: com.douyin.feature.settings.SettingsViewModel = koinViewModel()
                SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun DyBottomNavigation(
    currentRoute: String?,
    onNavigate: (Dest) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Surface(
            color = DyCard.copy(alpha = 0.98f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, DyBorder.copy(alpha = 0.78f)),
            shadowElevation = 16.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                destinations.forEach { dest ->
                    val selected = currentRoute == dest.route
                    val background by animateColorAsState(
                        targetValue = if (selected) DyRed.copy(alpha = 0.16f) else Color.Transparent,
                        animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
                        label = "navBackground",
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.06f else 0.97f,
                        animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
                        label = "navIconScale",
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (selected) DyRed else DyTextSecondary,
                        animationSpec = tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing),
                        label = "navIconTint",
                    )
                    val labelTint by animateColorAsState(
                        targetValue = if (selected) DyRed else DyTextSecondary,
                        animationSpec = tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing),
                        label = "navLabelTint",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(background)
                            .dyPressable(pressedScale = 0.94f) { onNavigate(dest) }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            dest.icon,
                            contentDescription = dest.label,
                            tint = iconTint,
                            modifier = Modifier
                                .size(21.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            dest.label,
                            color = labelTint,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
