/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Reads GitHub release metadata with direct and proxy fallbacks.
 */
package com.douyin.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Anonymous, manual-release lookup against the public GitHub Releases API. */
class GitHubUpdateRepository(
    private val client: OkHttpClient = defaultClient(),
) : UpdateRepository {

    override suspend fun getReleases(allowMirrors: Boolean): List<AppRelease> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (url in UpdateDownloadRoutes.candidates(RELEASES_URL, allowMirrors)) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "ClipCove-Android")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.code == 403 || response.code == 429) {
                        throw IOException("GitHub API rate limit reached; try again later")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("GitHub API returned HTTP ${response.code}")
                    }
                    if (body.isBlank()) throw IOException("GitHub API returned an empty response")
                    return@withContext parseReleases(body)
                }
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IOException("Unable to reach GitHub Releases", lastError)
    }

    private fun parseReleases(raw: String): List<AppRelease> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val tag = item.optString("tag_name").trim()
                if (tag.isBlank()) continue
                val htmlUrl = item.optString("html_url")
                    .ifBlank { "https://github.com/DCRimbaud/clipcove-android/releases/tag/$tag" }
                add(
                    AppRelease(
                        tagName = tag,
                        name = item.optString("name").ifBlank { tag },
                        htmlUrl = htmlUrl,
                        notes = item.optString("body").take(MAX_NOTES_LENGTH),
                        publishedAt = item.optString("published_at"),
                        prerelease = item.optBoolean("prerelease"),
                        draft = item.optBoolean("draft"),
                        assets = item.optJSONArray("assets").toReleaseAssets(),
                    )
                )
            }
        }
    }

    private fun JSONArray?.toReleaseAssets(): List<ReleaseAsset> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val url = item.optString("browser_download_url")
                if (url.isBlank()) continue
                add(
                    ReleaseAsset(
                        name = item.optString("name"),
                        downloadUrl = url,
                        sizeBytes = item.optLong("size").coerceAtLeast(0),
                    )
                )
            }
        }
    }

    private companion object {
        const val RELEASES_URL =
            "https://api.github.com/repos/DCRimbaud/clipcove-android/releases?per_page=30"
        const val MAX_NOTES_LENGTH = 20_000

        /** Creates a short-lived client for bounded release metadata requests. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}