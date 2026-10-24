package com.douyin.parser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.douyin.core.designsystem.DyBg
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyInput
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.core.designsystem.DyTheme
import com.douyin.data.download.DownloadRepository
import com.douyin.data.favorites.FavoritesRepository
import com.douyin.feature.downloads.DownloadsScreen
import com.douyin.feature.favorites.FavoritesScreen
import com.douyin.feature.home.HomeScreen
import com.douyin.feature.settings.SettingsScreen
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

/** 应用壳:底部导航 + 页面路由 */
class MainActivity : ComponentActivity() {

    private val downloadRepository: DownloadRepository by inject()
    private val favoritesRepository: FavoritesRepository by inject()

    /** 分享/启动 intent 流:onCreate 与 onNewIntent 统一入口 */
    val pendingIntent = androidx.compose.runtime.mutableStateOf<android.content.Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        pendingIntent.value = intent
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.lifecycle.compose.LocalLifecycleOwner provides this
            ) {
                DyTheme {
                    DyNavHost(downloadRepository, favoritesRepository, pendingIntent)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent.value = intent
    }

    /** Android 13+ 请求通知权限(下载前台服务通知需要) */
    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIFICATION
                )
            }
        }
    }

    private companion object {
        const val REQ_NOTIFICATION = 100
    }
}

private sealed class Dest(val route: String, val label: String) {
    data object Home : Dest("home", "首页")
    data object Downloads : Dest("downloads", "下载")
    data object Favorites : Dest("favorites", "收藏")
    data object Settings : Dest("settings", "设置")
}

@Composable
private fun DyNavHost(
    downloadRepository: DownloadRepository,
    favoritesRepository: FavoritesRepository,
    pendingIntent: androidx.compose.runtime.MutableState<Intent?>
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    Scaffold(
        containerColor = DyBg,
        bottomBar = {
            NavigationBar(containerColor = DyCard) {
                val items = listOf(Dest.Home, Dest.Favorites, Dest.Downloads, Dest.Settings)
                items.forEach { dest ->
                    val selected = currentDest?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val icon = when (dest) {
                                Dest.Home -> Icons.Filled.Home
                                Dest.Favorites -> Icons.Filled.Favorite
                                Dest.Downloads -> Icons.Filled.Download
                                Dest.Settings -> Icons.Filled.Settings                            }
                            Icon(icon, null, tint = if (selected) DyRed else DyTextSecondary)
                        },
                        label = {
                            Text(dest.label, fontSize = 11.sp, color = if (selected) DyRed else DyTextSecondary)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = DyInput,
                            selectedIconColor = DyRed,
                            unselectedIconColor = DyTextSecondary,
                            selectedTextColor = DyRed,
                            unselectedTextColor = DyTextSecondary
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding),
            enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) },
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) },
            popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) }
        ) {
            composable(Dest.Home.route) {
                val vm: com.douyin.feature.home.HomeViewModel = koinViewModel()
                val input by vm.input.collectAsStateWithLifecycle()
                val loading by vm.loading.collectAsStateWithLifecycle()
                val error by vm.error.collectAsStateWithLifecycle()
                val result by vm.result.collectAsStateWithLifecycle()
                val history by vm.history.collectAsStateWithLifecycle()
                val clipboardSuggestion by vm.clipboardSuggestion.collectAsStateWithLifecycle()
                val isFavorite by vm.isFavorite.collectAsStateWithLifecycle()
                val context = LocalContext.current

                LaunchedEffect(pendingIntent.value) {
                    vm.handleIntent(pendingIntent.value)
                }
                LaunchedEffect(Unit) {
                    vm.checkClipboard()
                }
                HomeScreen(
                    input = input,
                    onInputChange = vm::onInputChange,
                    onParse = vm::parse,
                    loading = loading,
                    error = error,
                    result = result,
                    history = history,
                    onDownload = vm::enqueueDownload,
                    onMerge = vm::mergeVideo,
                    onHistoryClick = { item ->
                        // 小红书历史:用原始链接重新解析(token 有时效);抖音:用 ID
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
                        result?.let { obj ->
                            // 按平台标注来源
                            val platform = if (obj.optString("platform", "") == "xhs") "小红书" else "抖音"
                            val text = buildString {
                                append("【$platform】${obj.optString("title", "(无标题)")}\n")
                                append("作者:@${obj.optString("author", "")}\n")
                                append("ID:${obj.optString("aweme_id", "")}")
                            }
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(share, "分享到"))
                        }
                    }
                )
            }
            composable(Dest.Favorites.route) {
                val vm: com.douyin.feature.home.HomeViewModel = koinViewModel()
                FavoritesScreen(favoritesRepository) { id ->
                    vm.onInputChange(id)
                    vm.parse()
                    navController.navigate(Dest.Home.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                    }
                }
            }
            composable(Dest.Downloads.route) {
                DownloadsScreen(downloadRepository)
            }
            composable(Dest.Settings.route) {
                val vm: com.douyin.feature.settings.SettingsViewModel = koinViewModel()
                SettingsScreen(vm)
            }
        }
    }
}

