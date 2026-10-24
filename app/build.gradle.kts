plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.chaquopy)
}

// ===== 鐗堟湰绠＄悊 =====
// 娴嬭瘯鐗?0.x.x(鏈寮忓彂甯?鎸夊姛鑳?淇杩唬閫掑 minor/patch)
// 姝ｅ紡鐗?1.0.0 璧锋,涔嬪悗姘歌繙 1.x.x(2.0.0 浠呭湪閲嶅ぇ閲嶆瀯鏃朵娇鐢?
val versionMajor = 0        // 0=test 1=release
val versionMinor = 8        // feature
val versionPatch = 1        // fix
val appVersionName = "$versionMajor.$versionMinor.$versionPatch"
val appVersionCode = 10

android {
    namespace = "com.douyin.parser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.douyin.parser"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        // 鍙敤鐜鍙橀噺 PYTHON_EXE 瑕嗙洊(榛樿鏈満璺緞)
        buildPython(System.getenv("PYTHON_EXE") ?: "C:/Users/Admin/AppData/Local/Programs/Python/Python312/python.exe")
        pip {
            install("requests==2.32.3")
            install("gmssl==3.2.2")
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":data:douyin"))
    implementation(project(":data:download"))
    implementation(project(":data:history"))
    implementation(project(":data:merger"))
    implementation(project(":data:settings"))
    implementation(project(":data:favorites"))
    implementation(project(":data:transfer"))
    implementation(project(":data:xhs"))
    implementation(project(":feature:home"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:favorites"))
    implementation(project(":feature:settings"))

    implementation(libs.ffmpeg.kit)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}

