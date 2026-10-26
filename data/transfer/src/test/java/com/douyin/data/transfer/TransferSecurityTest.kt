/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Protects LAN transfer authentication and response-hardening invariants.
 */
package com.douyin.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protects LAN token generation, comparison, and attachment-header safety. */
class TransferSecurityTest {

    /** Ensures LAN transfer tokens remain high-entropy session secrets. */
    @Test
    fun `tokens are unpredictable 256-bit hex values`() {
        val first = TransferSecurity.newToken()
        val second = TransferSecurity.newToken()

        assertEquals(64, first.length)
        assertTrue(first.matches(Regex("""[a-f0-9]{64}""")))
        assertFalse(first == second)
    }

    /** Ensures LAN token checks accept only the exact session value. */
    @Test
    fun `token comparison accepts exact value and rejects others`() {
        val token = TransferSecurity.newToken()

        assertTrue(TransferSecurity.tokenMatches(token, token))
        assertFalse(TransferSecurity.tokenMatches(token, token.dropLast(1) + "0"))
    }

    /** Ensures untrusted attachment names cannot inject HTTP response headers. */
    @Test
    fun `attachment names cannot inject response headers`() {
        val safe = TransferSecurity.sanitizeAttachmentName("bad\"name\r\nX-Evil: 1.mp4")

        assertFalse(safe.contains('\r'))
        assertFalse(safe.contains('\n'))
        assertFalse(safe.contains('"'))
    }
}