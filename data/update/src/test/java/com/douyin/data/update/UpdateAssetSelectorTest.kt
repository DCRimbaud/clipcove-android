/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Protects ABI-aware APK and checksum selection for one release.
 */
package com.douyin.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for release asset role and ABI selection. */
class UpdateAssetSelectorTest {

    private val arm64 = asset("ClipCove-1.3.1-arm64-v8a.apk")
    private val x86 = asset("ClipCove-1.3.1-x86_64.apk")
    private val universal = asset("ClipCove-1.3.1-universal.apk")

    /** ARM64 devices select the ARM64 package even if another ABI appears first. */
    @Test
    fun arm64DeviceGetsArm64Package() {
        assertEquals(
            arm64,
            UpdateAssetSelector.selectApk(listOf(x86, universal, arm64), listOf("arm64-v8a")),
        )
    }

    /** x86_64 emulators select the x86_64 package rather than an ARM package. */
    @Test
    fun x86DeviceGetsX86Package() {
        assertEquals(
            x86,
            UpdateAssetSelector.selectApk(listOf(arm64, x86), listOf("x86_64", "x86")),
        )
    }

    /** Universal packages remain usable when no ABI-specific package is published. */
    @Test
    fun universalPackageIsFallback() {
        assertEquals(
            universal,
            UpdateAssetSelector.selectApk(listOf(arm64, universal), listOf("x86_64")),
        )
    }

    /** ABI-tagged packages must not be installed on a non-matching device. */
    @Test
    fun mismatchedTaggedPackageIsRejected() {
        assertNull(UpdateAssetSelector.selectApk(listOf(arm64), listOf("x86_64")))
    }

    /** Checksum lookup must select the file belonging to the chosen APK. */
    @Test
    fun checksumMatchesSelectedApk() {
        val arm64Checksum = asset("${arm64.name}.sha256")
        val x86Checksum = asset("${x86.name}.sha256")

        assertEquals(
            x86Checksum,
            UpdateAssetSelector.checksumFor(listOf(arm64Checksum, x86Checksum), x86),
        )
    }

    /** Creates a minimal release asset for selector tests. */
    private fun asset(name: String): ReleaseAsset =
        ReleaseAsset(name = name, downloadUrl = "HTTPS://example.invalid/$name", sizeBytes = 1)
}
