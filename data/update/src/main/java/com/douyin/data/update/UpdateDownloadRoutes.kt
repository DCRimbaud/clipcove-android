package com.douyin.data.update

internal object UpdateDownloadRoutes {
    val mirrorPrefixes = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
    )

    fun candidates(originalUrl: String, allowMirrors: Boolean): List<String> {
        if (!allowMirrors || !originalUrl.startsWith("https://github.com/")) {
            return listOf(originalUrl)
        }
        return buildList {
            add(originalUrl)
            mirrorPrefixes.forEach { prefix ->
                add(prefix.trimEnd('/') + "/" + originalUrl)
            }
        }.distinct()
    }
}