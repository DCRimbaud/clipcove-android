package com.douyin.core.common.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSanitizerTest {

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

    @Test
    fun `collapses whitespace and enforces a length limit`() {
        val clean = DiagnosticSanitizer.sanitize("a\n\tb   c", maxLength = 5)

        assertEquals("a b c", clean)
        assertEquals(5, DiagnosticSanitizer.sanitize("123456789", maxLength = 5).length)
    }
}
