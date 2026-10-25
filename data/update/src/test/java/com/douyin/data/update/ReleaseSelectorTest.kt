package com.douyin.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSelectorTest {

    @Test
    fun `selects highest release newer than current`() {
        val releases = listOf(
            release("v1.0.2"),
            release("v1.1.0-rc.1", prerelease = true),
            release("v1.0.9"),
        )

        val latest = ReleaseSelector.newest(releases, "1.0.2")

        assertEquals("v1.1.0-rc.1", latest?.tagName)
    }

    @Test
    fun `can exclude prereleases`() {
        val releases = listOf(
            release("v1.1.0-rc.1", prerelease = true),
            release("v1.0.3"),
        )

        val latest = ReleaseSelector.newest(releases, "1.0.2", includePrereleases = false)

        assertEquals("v1.0.3", latest?.tagName)
    }

    @Test
    fun `returns null when current version is latest`() {
        assertNull(ReleaseSelector.newest(listOf(release("v1.1.0")), "1.1.0"))
    }

    @Test
    fun `stable version sorts after prerelease with same core`() {
        val stable = SemanticVersion.parse("1.1.0")!!
        val prerelease = SemanticVersion.parse("v1.1.0-rc.1")!!

        assertTrue(stable > prerelease)
    }

    private fun release(tag: String, prerelease: Boolean = false) = AppRelease(
        tagName = tag,
        name = tag,
        htmlUrl = "https://github.com/DCRimbaud/clipcove-android/releases/tag/$tag",
        notes = "",
        publishedAt = "",
        prerelease = prerelease,
        draft = false,
    )
}
