package com.edgeflow.ui.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** App-wide UI language, independent from LSPosed/system-server configuration. */
object AppLanguage {
    const val SYSTEM = "system"
    const val CHINESE = "zh"
    const val ENGLISH = "en"
    private const val PREFS = "edgeflow_ui_preferences"
    private const val KEY = "ui_language"

    fun selected(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ also permits changing the language via System Settings.
            val list = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            val locale = list?.takeIf { !it.isEmpty }?.get(0)
            return when (locale?.language) {
                "zh" -> CHINESE
                "en" -> ENGLISH
                else -> SYSTEM
            }
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM)?.takeIf { it in setOf(SYSTEM, CHINESE, ENGLISH) } ?: SYSTEM
    }

    fun effectiveLanguage(context: Context): String {
        val selection = selected(context)
        if (selection != SYSTEM) return selection
        // Use system resources to avoid inheriting an app-specific override.
        val locale = android.content.res.Resources.getSystem().configuration.locales.get(0)
        return if (locale.language == CHINESE) CHINESE else ENGLISH
    }

    /** Called before Activity creation, including editor and unlock activities. */
    fun wrap(base: Context): Context {
        val current = effectiveLanguage(base)
        val activeLocale = base.resources.configuration.locales.get(0)
        if (activeLocale.language == current) return base
        val override = Configuration(base.resources.configuration)
        override.setLocale(if (current == CHINESE) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH)
        return base.createConfigurationContext(override)
    }

    fun update(activity: Activity, choice: String) {
        require(choice in setOf(SYSTEM, CHINESE, ENGLISH))
        check(activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, choice).commit()) { "Cannot save language preference" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (choice == SYSTEM) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(if (choice == CHINESE) "zh-CN" else "en")
        }
        // Pre-Android 13 uses the wrapper in attachBaseContext, which is
        // refreshed by recreating the Activity. This is also safe on newer OS.
        activity.recreate()
    }
}
