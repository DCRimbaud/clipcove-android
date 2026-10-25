package com.douyin.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferSecurityTest {

    @Test
    fun `tokens are unpredictable 256-bit hex values`() {
        val first = TransferSecurity.newToken()
        val second = TransferSecurity.newToken()

        assertEquals(64, first.length)
        assertTrue(first.matches(Regex("""[a-f0-9]{64}""")))
        assertFalse(first == second)
    }

    @Test
    fun `token comparison accepts exact value and rejects others`() {
        val token = TransferSecurity.newToken()

        assertTrue(TransferSecurity.tokenMatches(token, token))
        assertFalse(TransferSecurity.tokenMatches(token, token.dropLast(1) + "0"))
    }

    @Test
    fun `attachment names cannot inject response headers`() {
        val safe = TransferSecurity.sanitizeAttachmentName("bad\"name\r\nX-Evil: 1.mp4")

        assertFalse(safe.contains('\r'))
        assertFalse(safe.contains('\n'))
        assertFalse(safe.contains('"'))
    }
}