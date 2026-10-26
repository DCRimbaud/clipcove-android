/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Implements constant-time token checks and transfer-header hardening helpers.
 */
package com.douyin.data.transfer

import java.security.MessageDigest
import java.security.SecureRandom

/** Security primitives shared by the LAN transfer server and its tests. */
internal object TransferSecurity {
    private val random = SecureRandom()

    /** Generates a 256-bit URL-safe access token for one server session. */
    fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    /** Compares tokens in constant time and rejects blank values. */
    fun tokenMatches(expected: String, supplied: String): Boolean {
        if (expected.isEmpty()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            supplied.toByteArray(Charsets.UTF_8),
        )
    }

    /** Removes path and header delimiters from a user-visible attachment name. */
    fun sanitizeAttachmentName(name: String): String =
        name.replace(Regex("""[\r\n"\\]"""), "_").take(180).ifBlank { "download" }
}