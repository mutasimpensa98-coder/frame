package com.frameglass.data.api

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * The language picked for this app alone. From Android 13 the system keeps that choice (LocaleManager), applies it to
 * every context and recreates the activity. Android 10 to 12 have no such service, so there the app keeps the choice
 * in a small preferences file and applies it itself: [wrap] in attachBaseContext of each activity and service,
 * [resources] behind the application's getResources, and [applyDefaults] for the process (waiver of 2026-11-19).
 */
object AppLocale {
    private const val FILE = "app_locale"
    private const val KEY = "language"

    /** True from Android 13: nothing in this object but [chosen], [choose] and [current] does anything there. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val keptBySystem: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The chosen language tag, or null when the app follows the phone. */
    fun chosen(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33.chosen(context)
        } else {
            stored(context)
        }

    /**
     * Saves the choice; null follows the phone again. From Android 13 the system recreates the activity. Below it the
     * caller recreates its own activity after this returns, which is the only screen that needs it.
     */
    @SuppressLint("ApplySharedPref")
    fun choose(context: Context, language: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33.choose(context, language)
            return
        }
        // commit, not apply: the recreated activity reads it at once.
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            if (language == null) remove(KEY) else putString(KEY, language)
        }.commit()
        known = Stored(language)
        applyDefaults(context)
    }

    /** The first language the app shows: the chosen one, or the phone's. */
    fun current(context: Context): Locale? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33.current(context)
        } else {
            stored(context)?.let(Locale::forLanguageTag) ?: systemLocales()[0]
        }

    /** For attachBaseContext. Returns [base] itself from Android 13, and while the app follows the phone. */
    fun wrap(base: Context): Context {
        if (keptBySystem) return base
        val language = stored(base) ?: return base
        return base.createConfigurationContext(override(language))
    }

    /**
     * For the application's getResources, which unlike an activity cannot be recreated when the choice changes.
     * [base] is what the application would return by itself.
     */
    fun resources(context: Context, base: Resources): Resources {
        if (keptBySystem) return base
        val language = stored(context) ?: return base
        val held = localized
        if (held != null && held.first == language) return held.second
        val made = context.createConfigurationContext(override(language)).resources
        localized = language to made
        return made
    }

    /** Sets what Locale.getDefault answers below Android 13, at process start and after each choice. */
    fun applyDefaults(context: Context) {
        if (keptBySystem) return
        val system = systemLocales()
        val language = stored(context)
        val list = if (language == null) system else LocaleList.forLanguageTags("$language,${system.toLanguageTags()}")
        if (list.isEmpty) return
        LocaleList.setDefault(list)
    }

    @Volatile
    private var localized: Pair<String, Resources>? = null

    private class Stored(val language: String?)

    @Volatile
    private var known: Stored? = null

    /** Read on every getResources of the application, so the file is opened once and the answer kept. */
    private fun stored(context: Context): String? {
        known?.let { return it.language }
        val language = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        known = Stored(language)
        return language
    }

    /**
     * Only the language is set, so everything else keeps following the phone. setLocales sets the direction too.
     * The app ships as one APK that holds both languages, so there is no language split to download.
     */
    @SuppressLint("AppBundleLocaleChanges")
    private fun override(language: String): Configuration =
        Configuration().apply { setLocales(LocaleList.forLanguageTags(language)) }

    private fun systemLocales(): LocaleList = Resources.getSystem().configuration.locales

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33 {
        private fun manager(context: Context): LocaleManager = context.getSystemService(LocaleManager::class.java)

        fun chosen(context: Context): String? = manager(context).applicationLocales.get(0)?.language

        fun choose(context: Context, language: String?) {
            manager(context).applicationLocales =
                if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
        }

        /** LocaleManager answers for any context, while a process with no activity may still carry the phone's language. */
        fun current(context: Context): Locale? {
            val manager = manager(context)
            val chosen = manager.applicationLocales
            return (if (chosen.isEmpty) manager.systemLocales else chosen)[0]
        }
    }
}
