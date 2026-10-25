package com.douyin.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** SharedPreferences 实现 */
class PrefsSettingsRepository(context: Context) : SettingsRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override val autoSaveToDownloads: StateFlow<Boolean> =
        MutableStateFlow(prefs.getBoolean(KEY_AUTO_SAVE, true))

    override val autoParseSharedLinks: StateFlow<Boolean> =
        MutableStateFlow(prefs.getBoolean(KEY_AUTO_PARSE_SHARED, true))

    override val autoUpdateMirrors: StateFlow<Boolean> =
        MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE_MIRRORS, true))

    override fun setAutoSave(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SAVE, value).apply()
        (autoSaveToDownloads as MutableStateFlow).value = value
    }

    override fun setAutoParseSharedLinks(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PARSE_SHARED, value).apply()
        (autoParseSharedLinks as MutableStateFlow).value = value
    }

    override fun setAutoUpdateMirrors(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE_MIRRORS, value).apply()
        (autoUpdateMirrors as MutableStateFlow).value = value
    }

    private companion object {
        const val PREFS = "settings"
        const val KEY_AUTO_SAVE = "auto_save_downloads"
        const val KEY_AUTO_PARSE_SHARED = "auto_parse_shared_links"
        const val KEY_AUTO_UPDATE_MIRRORS = "auto_update_mirrors"
    }
}
