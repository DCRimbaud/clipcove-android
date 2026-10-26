/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Protects diagnostic redaction invariants for secrets, links, and personal identifiers.
 */
package com.douyin.core.common.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protects diagnostic redaction and bounded-text invariants. */
class DiagnosticSanitizerTest {

    /** Ensures sensitive tokens and personal identifiers never survive diagnostic export. */
    @Test
    fun `redacts credentials and personal identifiers`() {
        val raw = "GET https://example.com/a?xsec_token=secret&xsec_source=pc " +
            "cookie=sessionid%3Dabc phone=13800138000 ip=192.168.1.20 " +
            "email=user@example.com Authorization=Bearer abc.def"

        val clean = DiagnosticSanitizer.sanitize(raw)

        assertFalse(clean.contains("secret"))
        assertFalse(clean.contains("sessionid"))
        assertFalse(clean.contains("13800138000"))
        assertFalse(clean.contains("192.168.1.20"))
        assertFalse(clean.contains("user@example.com"))
        assertTrue(clean.contains("[redacted]"))
        assertTrue(clean.contains("[phone]"))
        assertTrue(clean.contains("[ip]"))
        assertTrue(clean.contains("[email]"))
    }

    /** Ensures diagnostic text is normalized and bounded before storage. */
    @Test
    fun `collapses whitespace and enforces a length limit`() {
        val clean = DiagnosticSanitizer.sanitize("a\n\tb   c", maxLength = 5)

        assertEquals("a b c", clean)
        assertEquals(5, DiagnosticSanitizer.sanitize("123456789", maxLength = 5).length)
    }
}