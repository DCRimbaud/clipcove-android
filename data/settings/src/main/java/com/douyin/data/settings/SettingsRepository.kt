/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines user preference flows for saving, parsing, language, and update behavior.
 */
package com.douyin.data.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * Settings repository contract for global preference flows.
 */
interface SettingsRepository {

    /** Whether completed downloads are copied to the public Downloads collection. */
    val autoSaveToDownloads: StateFlow<Boolean>

    /** Auto-parse links received through Android's share sheet. */
    val autoParseSharedLinks: StateFlow<Boolean>

    /** Retry blocked GitHub release downloads through public accelerator mirrors. */
    val autoUpdateMirrors: StateFlow<Boolean>

    /** Current app-language preference. */
    val appLanguage: StateFlow<AppLanguage>

    /** Persists whether completed downloads are copied to public Downloads. */
    fun setAutoSave(value: Boolean)
    /** Persists whether supported shared text starts parsing automatically. */
    fun setAutoParseSharedLinks(value: Boolean)
    /** Persists whether update checks and downloads may use approved mirror routes. */
    fun setAutoUpdateMirrors(value: Boolean)
    /** Persists the app-language choice; the activity applies it on recreation. */
    fun setAppLanguage(language: AppLanguage)
}
