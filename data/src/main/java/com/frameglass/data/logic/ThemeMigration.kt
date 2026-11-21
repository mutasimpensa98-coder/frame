package com.frameglass.data.logic

import com.frameglass.data.api.AccentColor
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.CustomThemeDefaults
import com.frameglass.data.api.ThemeId
import com.frameglass.data.api.ThemeMode

/**
 * Turns the appearance saved before the ready themes (2026-11-21) into today's, so an update or an older backup keeps
 * the look it had: the dark look is BLACK, the light one is LIGHT, and "follow the phone" follows it between those two.
 */
internal object ThemeMigration {
    /** The theme and the follow switch for an old `themeMode`. Anything unknown, including null, is the default dark. */
    fun theme(legacyMode: String?): Pair<ThemeId, Boolean> = when (legacyMode) {
        ThemeMode.LIGHT.name -> ThemeId.LIGHT to false
        ThemeMode.SYSTEM.name -> ThemeId.BLACK to true
        else -> ThemeId.BLACK to false
    }

    /**
     * The accent for an old `accent`. The old neutral was white on dark and near-black on light, which is exactly what
     * BLACK and LIGHT give by themselves, so it becomes THEME and a ready theme picked later shows its own colour. A
     * colour the owner chose stays that colour on every theme.
     */
    fun accent(legacyAccent: String?): AccentColor = when (legacyAccent) {
        AccentColor.BLUE.name -> AccentColor.BLUE
        AccentColor.RED.name -> AccentColor.RED
        AccentColor.GREEN.name -> AccentColor.GREEN
        AccentColor.AMBER.name -> AccentColor.AMBER
        AccentColor.VIOLET.name -> AccentColor.VIOLET
        else -> AccentColor.THEME
    }

    /** The old `themeMode` nearest to this look, for the field older backups carry. */
    fun legacyMode(s: AppSettings): ThemeMode = when {
        s.followSystem -> ThemeMode.SYSTEM
        s.theme == ThemeId.LIGHT || s.theme == ThemeId.PAPER -> ThemeMode.LIGHT
        else -> ThemeMode.DARK
    }
}

/** Keeps stored and restored custom values inside what the sliders can produce. */
internal object CustomThemeValues {
    private const val MAX_HUE = 360f
    private const val OPAQUE = 0xFF000000.toInt()

    fun hue(value: Float?): Float =
        if (value == null || !value.isFinite()) CustomThemeDefaults.HUE else value.coerceIn(0f, MAX_HUE)

    fun unit(value: Float?, fallback: Float): Float =
        if (value == null || !value.isFinite()) fallback else value.coerceIn(0f, 1f)

    /** An accent is always opaque. */
    fun accent(value: Int?): Int = (value ?: CustomThemeDefaults.ACCENT) or OPAQUE

    fun sane(s: AppSettings): AppSettings = s.copy(
        customHue = hue(s.customHue),
        customShade = unit(s.customShade, CustomThemeDefaults.SHADE),
        customSurface = unit(s.customSurface, CustomThemeDefaults.SURFACE),
        customAccent = accent(s.customAccent),
    )
}
