/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Verifies stable persistence mappings for the app-language preference.
 */
package com.douyin.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for tolerant language preference decoding. */
class AppLanguageTest {

    /** Unknown and missing values must fall back to the system locale. */
    @Test
    fun missingAndUnknownValuesUseSystemLanguage() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorage(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorage("unsupported"))
    }

    /** Every persisted enum value must round-trip without ambiguity. */
    @Test
    fun persistedValuesRoundTrip() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.fromStorage(language.storageValue))
        }
    }
}
