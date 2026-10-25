package com.douyin.parser

import com.chaquo.python.android.PyApplication
import com.douyin.core.common.diagnostics.DiagnosticsRepository
import com.douyin.data.douyin.DouyinRepository
import com.douyin.data.download.AndroidDownloadRepository
import com.douyin.data.download.DownloadRepository
import com.douyin.data.history.HistoryRepository
import com.douyin.data.history.PrefsHistoryRepository
import com.douyin.data.merger.FfmpegVideoMerger
import com.douyin.data.merger.VideoMerger
import com.douyin.data.settings.PrefsSettingsRepository
import com.douyin.data.settings.SettingsRepository
import com.douyin.data.favorites.FavoritesRepository
import com.douyin.data.favorites.PrefsFavoritesRepository
import com.douyin.data.transfer.WifiTransferServer
import com.douyin.data.update.AndroidUpdateInstaller
import com.douyin.data.update.GitHubUpdateRepository
import com.douyin.data.update.UpdateInstaller
import com.douyin.data.update.UpdateRepository
import com.douyin.data.xhs.XhsRepository
import com.douyin.feature.home.HomeViewModel
import com.douyin.feature.settings.SettingsViewModel
import com.douyin.feature.settings.WifiTransferController
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** 依赖注入装配:app 层是唯一知道具体实现的地方,feature 只依赖接口 */
val appModule = module {
    single<DouyinRepository> { ChaquopyDouyinRepository() }
    single<XhsRepository> { ChaquopyXhsRepository() }
    single<DownloadRepository> { AndroidDownloadRepository().also { it.init(androidContext()) } }
    single<HistoryRepository> { PrefsHistoryRepository(androidContext()) }
    single<VideoMerger> { FfmpegVideoMerger() }
    single<SettingsRepository> { PrefsSettingsRepository(androidContext()) }
    single<FavoritesRepository> { PrefsFavoritesRepository(androidContext()) }
    single<DiagnosticsRepository> { PrefsDiagnosticsRepository(androidContext()) }
    single<UpdateRepository> { GitHubUpdateRepository() }
    single<UpdateInstaller> {
        AndroidUpdateInstaller(
            context = androidContext(),
            fileProviderAuthority = "${androidContext().packageName}.fileprovider",
        )
    }
    single { WifiTransferServer(androidContext()) }
    single { WifiTransferController(get()) }

    single { AutoSaveService(androidContext(), get(), get()).also { it.start() } }

    viewModel {
        HomeViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            androidApplication()
        )
    }

    viewModel {
        SettingsViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
    }
}

class DouyinApp : PyApplication() {
    override fun onCreate() {
        super.onCreate()
        // Coil 全局配置:按图片域名设置对应平台的 Referer(抖音/小红书 CDN 各自校验)
        val okHttp = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val host = chain.request().url.host
                val referer = when {
                    host.endsWith("douyinpic.com") || host.endsWith("douyinstatic.com") ||
                        host.endsWith("douyinvod.com") -> "https://www.douyin.com/"
                    host.endsWith("xhscdn.com") -> "https://www.xiaohongshu.com/"
                    else -> "https://www.douyin.com/"
                }
                val req = chain.request().newBuilder()
                    .header("Referer", referer)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
                    )
                    .build()
                chain.proceed(req)
            }
            .build()
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .okHttpClient(okHttp)
                .build()
        )
        startKoin {
            androidContext(this@DouyinApp)
            modules(appModule)
        }
        startDownloadService()
    }

    /** 启动下载前台服务(服务内部在无活动任务时自动停止) */
    private fun startDownloadService() {
        try {
            val intent = android.content.Intent(this, DownloadService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("DYAPP", "start download service fail", e)
        }
    }
}
