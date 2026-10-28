import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.chaquopy)
}

// Stable releases use 1.x.x; reserve 2.x.x for a major architecture change.
val versionMajor = 1
val versionMinor = 4
val versionPatch = 1
val versionSuffix = ""
val appVersionName = "$versionMajor.$versionMinor.$versionPatch$versionSuffix"
val appVersionCode = 26
val requestedAbis = providers.gradleProperty("clipcoveAbis")
    .orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.takeIf(List<String>::isNotEmpty)
    ?: listOf("arm64-v8a", "x86_64")

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigning = keystorePropertiesFile.exists() &&
    releaseSigningKeys.all { !keystoreProperties.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.douyin.parser"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.dcrimbaud.clipcove"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            abiFilters += requestedAbis
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("stagingRelease") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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
        // PYTHON_EXE can override the local Python path.
        buildPython(System.getenv("PYTHON_EXE") ?: "python")
        pip {
            install("requests==2.32.3")
            install("gmssl==3.2.2")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui-resources"))
    implementation(project(":core:common"))
    implementation(project(":data:douyin"))
    implementation(project(":data:download"))
    implementation(project(":data:history"))
    implementation(project(":data:merger"))
    implementation(project(":data:settings"))
    implementation(project(":data:favorites"))
    implementation(project(":data:transfer"))
    implementation(project(":data:xhs"))
    implementation(project(":data:update"))
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
