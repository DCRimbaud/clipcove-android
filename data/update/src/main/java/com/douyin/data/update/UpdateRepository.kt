package com.douyin.data.update

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

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

interface UpdateRepository {
    suspend fun getReleases(allowMirrors: Boolean = true): List<AppRelease>
}

object ReleaseSelector {
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
