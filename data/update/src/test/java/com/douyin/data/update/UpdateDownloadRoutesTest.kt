package com.douyin.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateDownloadRoutesTest {

    private val githubUrl =
        "https://github.com/DCRimbaud/clipcove-android/releases/download/v1.0.0/app.apk"

    @Test
    fun `direct-only mode returns only GitHub URL`() {
        assertEquals(listOf(githubUrl), UpdateDownloadRoutes.candidates(githubUrl, false))
    }

    @Test
    fun `automatic mode puts GitHub first then mirrors`() {
        val routes = UpdateDownloadRoutes.candidates(githubUrl, true)

        assertEquals(4, routes.size)
        assertEquals(githubUrl, routes.first())
        assertEquals(githubUrl, routes[1].removePrefix("https://ghfast.top/"))
        assertEquals(githubUrl, routes[2].removePrefix("https://gh-proxy.com/"))
        assertEquals(githubUrl, routes[3].removePrefix("https://ghproxy.net/"))
    }

    @Test
    fun `non-GitHub URL is never sent through mirrors`() {
        val custom = "https://downloads.example.com/app.apk"

        assertEquals(listOf(custom), UpdateDownloadRoutes.candidates(custom, true))
    }
}