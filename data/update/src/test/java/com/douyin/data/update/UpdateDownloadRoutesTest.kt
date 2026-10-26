/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Protects direct-only and mirror-route selection behavior.
 */
package com.douyin.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Protects update route allowlisting and direct-only behavior. */
class UpdateDownloadRoutesTest {

    private val githubUrl =
        "https://github.com/DCRimbaud/clipcove-android/releases/download/v1.0.0/app.apk"

    /** Ensures direct-only mode never adds mirror routes. */
    @Test
    fun `direct-only mode returns only GitHub URL`() {
        assertEquals(listOf(githubUrl), UpdateDownloadRoutes.candidates(githubUrl, false))
    }

    /** Ensures automatic mode applies only approved binary mirror routes in order. */
    @Test
    fun `automatic mode puts GitHub first then mirrors`() {
        val routes = UpdateDownloadRoutes.candidates(githubUrl, true)

        assertEquals(8, routes.size)
        assertEquals(githubUrl, routes.first())
        assertEquals(githubUrl, routes[1].removePrefix("https://gh.llkk.cc/"))
        assertEquals(githubUrl, routes[2].removePrefix("https://gh.ddlc.top/"))
        assertEquals(githubUrl, routes[3].removePrefix("https://gh.monlor.com/"))
        assertEquals(githubUrl, routes[4].removePrefix("https://gh.idayer.com/"))
        assertEquals(githubUrl, routes[5].removePrefix("https://ghfast.top/"))
        assertEquals(githubUrl, routes[6].removePrefix("https://gh-proxy.com/"))
        assertEquals(githubUrl, routes[7].removePrefix("https://ghproxy.net/"))
    }

    /** Ensures API requests use only API-compatible proxy routes. */
    @Test
    fun `GitHub API gets dedicated API mirror candidates`() {
        val apiUrl = "https://api.github.com/repos/DCRimbaud/clipcove-android/releases?per_page=30"
        val routes = UpdateDownloadRoutes.candidates(apiUrl, true)

        assertEquals(3, routes.size)
        assertEquals(apiUrl, routes.first())
        assertEquals(apiUrl, routes[1].removePrefix("https://gh-proxy.com/"))
        assertEquals(apiUrl, routes[2].removePrefix("https://gh.idayer.com/"))
    }

    /** Ensures small checksum files use the public Gitee raw route. */
    @Test
    fun `checksum uses Gitee mirror`() {
        val checksumUrl = "$githubUrl.sha256"
        val routes = UpdateDownloadRoutes.candidates(checksumUrl, true)

        assertEquals(9, routes.size)
        assertEquals(
            "https://gitee.com/a917947622/clipcove-android/raw/release-assets/v1.0.0/app.apk.sha256",
            routes[1],
        )
    }

    /** Ensures APK downloads use a chunk manifest rather than a login-gated raw APK. */
    @Test
    fun `APK maps to chunk manifest`() {
        assertEquals(
            "https://gitee.com/a917947622/clipcove-android/raw/release-assets/v1.0.0/app.apk.parts.json",
            UpdateDownloadRoutes.giteeChunkManifest(githubUrl),
        )
    }

    /** Ensures chunk URLs stay beneath the approved manifest directory. */
    @Test
    fun `chunk URL rejects path traversal`() {
        val manifest =
            "https://gitee.com/a917947622/clipcove-android/raw/release-assets/v1.0.0/app.apk.parts.json"

        assertEquals(
            "https://gitee.com/a917947622/clipcove-android/raw/release-assets/v1.0.0/app.apk.part000",
            UpdateDownloadRoutes.giteeChunkUrl(manifest, "app.apk.part000"),
        )
        assertNull(UpdateDownloadRoutes.giteeChunkUrl(manifest, "../secret"))
    }

    /** Ensures other GitHub projects never receive the ClipCove Gitee mirror route. */
    @Test
    fun `other GitHub repositories do not use ClipCove mirror`() {
        val other = "https://github.com/example/another/releases/download/v1.0.0/app.apk"
        val routes = UpdateDownloadRoutes.candidates(other, true)

        assertFalse(routes.any { it.contains("gitee.com/a917947622") })
    }

    /** Ensures arbitrary download hosts cannot receive GitHub proxy prefixes. */
    @Test
    fun `non-GitHub URL is never sent through mirrors`() {
        val custom = "https://downloads.example.com/app.apk"

        assertEquals(listOf(custom), UpdateDownloadRoutes.candidates(custom, true))
    }
}
