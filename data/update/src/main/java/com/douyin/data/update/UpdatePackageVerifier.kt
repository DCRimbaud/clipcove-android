/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Validates update checksums and package/version/signature invariants.
 */
package com.douyin.data.update

import java.io.File
import java.security.MessageDigest

/** Pure checksum/package verification helpers used before opening an installer. */
object UpdatePackageVerifier {
    private val sha256Pattern = Regex("""(?i)\b([a-f0-9]{64})\b""")

    /** Extracts the first 64-hex-character SHA-256 value from a checksum asset. */
    fun parseExpectedSha256(text: String): String? =
        sha256Pattern.find(text)?.groupValues?.getOrNull(1)?.lowercase()

    /** Computes a lowercase SHA-256 digest for an update artifact. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}