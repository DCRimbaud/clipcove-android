/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Persists the user's app-language choice and applies it to Android contexts.
 */
package com.douyin.data.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Language choices exposed by ClipCove, including the system default. */
enum class AppLanguage(val storageValue: String, val languageTag: String) {
    /** Follow the current system or Android per-app locale. */
    SYSTEM("system", ""),

    /** Force English interface resources. */
    ENGLISH("english", "en"),

    /** Force Simplified Chinese interface resources. */
    CHINESE("chinese", "zh-CN");

    companion object {
        /** Resolves a persisted value while tolerating legacy or corrupt data. */
        fun fromStorage(value: String?): AppLanguage =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/** SharedPreferences access shared by the repository and context localizer. */
object AppLanguagePreferences {
    internal const val PREFS_NAME = "settings"
    private const val KEY_APP_LANGUAGE = "app_language"

    /** Reads the current choice without requiring dependency injection. */
    fun read(context: Context): AppLanguage {
        val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLanguage.fromStorage(preferences.getString(KEY_APP_LANGUAGE, null))
    }

    /** Stores a new choice before the activity is recreated. */
    fun write(context: Context, language: AppLanguage) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LANGUAGE, language.storageValue)
            .apply()
    }
}

/**
 * Returns a context configured for the selected language.
 *
 * Returning the original context for [AppLanguage.SYSTEM] preserves Android 13+
 * per-app language behavior instead of overriding the framework choice.
 */
fun Context.withAppLanguage(): Context {
    val language = AppLanguagePreferences.read(this)
    if (language == AppLanguage.SYSTEM) return this
    val locale = Locale.forLanguageTag(language.languageTag)
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}