pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
    }
}
rootProject.name = "ClipCove"
include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:ui-resources")
include(":core:common")
include(":data:douyin")
include(":data:download")
include(":data:history")
include(":data:merger")
include(":data:settings")
include(":data:favorites")
include(":data:transfer")
include(":data:xhs")
include(":data:update")
include(":feature:home")
include(":feature:downloads")
include(":feature:settings")
include(":feature:favorites")