/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Protects APK checksum and package verification behavior.
 */
package com.douyin.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Protects checksum parsing and file hash verification. */
class UpdatePackageVerifierTest {

    /** Ensures checksum assets extract a validated SHA-256 digest. */
    @Test
    fun `parses sha256 from checksum file`() {
        val checksum = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD  ClipCove.apk"

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdatePackageVerifier.parseExpectedSha256(checksum),
        )
    }

    /** Ensures malformed checksum assets cannot be treated as valid. */
    @Test
    fun `rejects malformed checksum file`() {
        assertNull(UpdatePackageVerifier.parseExpectedSha256("not-a-checksum"))
    }

    /** Ensures artifact hashing matches the published checksum. */
    @Test
    fun `computes sha256 for downloaded file`() {
        val file = File.createTempFile("clipcove-update", ".apk").apply {
            writeText("abc")
            deleteOnExit()
        }

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdatePackageVerifier.sha256(file),
        )
    }
}