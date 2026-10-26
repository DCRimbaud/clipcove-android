/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines release models, repository contracts, and release selection rules.
 */
package com.douyin.data.update

/**
 * Downloadable GitHub release asset.
 *
 * @property name Asset filename used for APK/checksum role selection.
 * @property downloadUrl HTTPS browser download URL.
 * @property sizeBytes Declared size, or zero when GitHub omits it.
 */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

/**
 * Normalized GitHub release used by selection and update UI.
 *
 * @property tagName Git tag; it must match the APK version after removing ``v``.
 * @property name Human-readable release title.
 * @property htmlUrl Official release page.
 * @property notes Markdown release notes truncated by the adapter.
 * @property publishedAt ISO-8601 publication timestamp.
 * @property prerelease Whether GitHub marks the release as a candidate.
 * @property draft Whether the release is hidden from public listing.
 * @property assets Downloadable APK and checksum assets.
 */
data class AppRelease(
    val tagName: String,
    val name: String,
    val htmlUrl: String,
    val notes: String,
    val publishedAt: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<ReleaseAsset> = emptyList(),
)

/** Manual GitHub release lookup port with optional approved proxy fallback. */
interface UpdateRepository {
    suspend fun getReleases(allowMirrors: Boolean = true): List<AppRelease>
}

/** Pure semantic-version selection policy for update candidates. */
object ReleaseSelector {
    /**
     * Selects the newest release newer than the installed version.
     *
     * @param releases Raw release list; drafts are ignored.
     * @param currentVersion Installed semantic version.
     * @param includePrereleases Whether release candidates are eligible.
     * @return The newest eligible release, or null when no upgrade exists.
     */
    fun newest(
        releases: List<AppRelease>,
        currentVersion: String,
        includePrereleases: Boolean = true,
    ): AppRelease? {
        val current = SemanticVersion.parse(currentVersion) ?: return null
        return releases.asSequence()
            .filterNot { it.draft }
            .filter { includePrereleases || !it.prerelease }
            .mapNotNull { release ->
                SemanticVersion.parse(release.tagName)?.let { version -> release to version }
            }
            .filter { (_, version) -> version > current }
            .maxByOrNull { (_, version) -> version }
            ?.first
    }
}