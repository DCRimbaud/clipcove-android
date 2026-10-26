package com.douyin.data.update

internal object UpdateDownloadRoutes {
    private val binaryMirrorPrefixes = listOf(
        "https://gh.llkk.cc/",
        "https://gh.ddlc.top/",
        "https://gh.monlor.com/",
        "https://gh.idayer.com/",
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
    )
    private val apiMirrorPrefixes = listOf(
        "https://gh-proxy.com/",
        "https://gh.idayer.com/",
    )

    fun candidates(originalUrl: String, allowMirrors: Boolean): List<String> {
        if (!allowMirrors) return listOf(originalUrl)

        val prefixes = when {
            originalUrl.startsWith("https://github.com/") -> binaryMirrorPrefixes
            originalUrl.startsWith("https://api.github.com/") -> apiMirrorPrefixes
            else -> emptyList()
        }
        return buildList {
            add(originalUrl)
            prefixes.forEach { prefix ->
                add(prefix.trimEnd('/') + "/" + originalUrl)
            }
        }.distinct()
    }
}