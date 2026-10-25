package com.douyin.data.transfer

import java.security.MessageDigest
import java.security.SecureRandom

internal object TransferSecurity {
    private val random = SecureRandom()

    fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun tokenMatches(expected: String, supplied: String): Boolean {
        if (expected.isEmpty()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            supplied.toByteArray(Charsets.UTF_8),
        )
    }

    fun sanitizeAttachmentName(name: String): String =
        name.replace(Regex("""[\r\n"\\]"""), "_").take(180).ifBlank { "download" }
}