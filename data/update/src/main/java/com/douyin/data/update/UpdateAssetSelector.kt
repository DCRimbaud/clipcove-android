/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Selects the installable APK and matching checksum for the device ABI.
 */
package com.douyin.data.update

/** Resolves ABI-specific release assets without guessing across variants. */
internal object UpdateAssetSelector {

    private val abiTokens = listOf(
        "arm64-v8a",
        "armeabi-v7a",
        "x86_64",
        "x86",
    )

    /**
     * Chooses the best APK for the ordered device ABI list.
     *
     * ABI-tagged APKs are preferred over universal or untagged packages. When
     * every APK is ABI-specific and none matches, null is returned so the
     * installer never downloads a package that Android cannot install.
     */
    fun selectApk(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null

        for (abi in supportedAbis) {
            val match = apks.firstOrNull { asset ->
                asset.name.contains(abi, ignoreCase = true)
            }
            if (match != null) return match
        }

        apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }?.let { return it }

        val tagged = apks.filter { asset -> abiTokens.any { asset.name.contains(it, ignoreCase = true) } }
        return if (tagged.isEmpty()) apks.first() else null
    }

    /** Requires the checksum asset that belongs to the selected APK. */
    fun checksumFor(assets: List<ReleaseAsset>, apk: ReleaseAsset): ReleaseAsset? {
        val expectedName = apk.name + ".sha256"
        return assets.firstOrNull { it.name.equals(expectedName, ignoreCase = true) }
            ?: assets.firstOrNull {
                it.name.endsWith(".sha256", ignoreCase = true) &&
                    it.name.startsWith(apk.nameWithoutExtension(), ignoreCase = true)
            }
    }

    /** Removes the final extension for tolerant checksum-name matching. */
    private fun ReleaseAsset.nameWithoutExtension(): String =
        name.substringBeforeLast('.', name)
}
