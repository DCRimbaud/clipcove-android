package com.douyin.data.update

import java.io.File
import java.security.MessageDigest

object UpdatePackageVerifier {
    private val sha256Pattern = Regex("""(?i)\b([a-f0-9]{64})\b""")

    fun parseExpectedSha256(text: String): String? =
        sha256Pattern.find(text)?.groupValues?.getOrNull(1)?.lowercase()

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