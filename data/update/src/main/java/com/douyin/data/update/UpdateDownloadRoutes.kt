/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines approved update asset and GitHub API mirror routes.
 */
package com.douyin.data.update

/** Allowlisted mirror routes for GitHub release metadata and APK assets. */
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

    /**
     * Builds direct-first candidates without reusing binary proxies for API calls.
     *
     * @param originalUrl Trusted GitHub asset or API URL from the release payload.
     * @param allowMirrors Whether approved mirror routes are enabled.
     * @return A deduplicated route list; non-GitHub hosts never receive prefixes.
     */
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