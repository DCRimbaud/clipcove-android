# Chaquopy:Python 桥接类不可混淆
-keep class com.chaquo.python.** { *; }
-keep class com.douyin.data.douyin.** { *; }
-keep class com.douyin.parser.ChaquopyDouyinRepository { *; }

# ffmpeg-kit JNI
-keep class com.arthenica.ffmpegkit.** { *; }

# Kotlin 协程
-dontwarn kotlinx.coroutines.**

# Koin 注入(反射构造)
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.KoinInject *;
}

# JSON 反射字段
-keepclassmembers class com.douyin.data.download.DownloadTask { *; }
-keepclassmembers class com.douyin.data.history.HistoryItem { *; }
-keepclassmembers class com.douyin.data.favorites.FavoriteItem { *; }
