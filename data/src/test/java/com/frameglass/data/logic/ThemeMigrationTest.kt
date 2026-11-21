package com.frameglass.data.logic

import com.frameglass.data.api.AccentColor
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.CustomThemeDefaults
import com.frameglass.data.api.ThemeId
import com.frameglass.data.api.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeMigrationTest {
    @Test
    fun theOldDarkLookIsBlack() {
        assertEquals(ThemeId.BLACK to false, ThemeMigration.theme("DARK"))
    }

    @Test
    fun theOldLightLookIsLight() {
        assertEquals(ThemeId.LIGHT to false, ThemeMigration.theme("LIGHT"))
    }

    @Test
    fun followThePhoneFollowsItBetweenBlackAndLight() {
        assertEquals(ThemeId.BLACK to true, ThemeMigration.theme("SYSTEM"))
    }

    @Test
    fun everyOldModeHasAPlace() {
        // A new value added to the old enum by mistake would fall to the default without a word.
        assertEquals(setOf("SYSTEM", "DARK", "LIGHT"), ThemeMode.entries.map { it.name }.toSet())
    }

    @Test
    fun nothingSavedOrSomethingUnknownIsTheDefaultDark() {
        assertEquals(ThemeId.BLACK to false, ThemeMigration.theme(null))
        assertEquals(ThemeId.BLACK to false, ThemeMigration.theme("NEON"))
        assertEquals(ThemeId.DEFAULT to false, ThemeMigration.theme(""))
        assertEquals(AccentColor.THEME, ThemeMigration.accent(null))
        assertEquals(AccentColor.THEME, ThemeMigration.accent("NEON"))
        assertEquals(AccentColor.DEFAULT, ThemeMigration.accent(""))
    }

    @Test
    fun aChosenAccentStaysThatAccent() {
        for (accent in listOf("BLUE", "RED", "GREEN", "AMBER", "VIOLET")) {
            assertEquals(AccentColor.valueOf(accent), ThemeMigration.accent(accent))
        }
    }

    @Test
    fun theOldNeutralAccentBecomesTheThemesOwn() {
        // White on dark and near-black on light: what BLACK and LIGHT show as their own accent.
        assertEquals(AccentColor.THEME, ThemeMigration.accent("WHITE"))
    }

    @Test
    fun namesThatOnlyExistNowAreNotOldAccents() {
        // An old file can not hold them, and a damaged one that does falls to the default.
        for (accent in listOf("PINK", "CYAN", "CUSTOM", "THEME")) {
            assertEquals(AccentColor.THEME, ThemeMigration.accent(accent))
        }
    }

    @Test
    fun theOldFieldOfABackupIsTheNearestOldMode() {
        fun mode(theme: ThemeId, follow: Boolean = false) =
            ThemeMigration.legacyMode(AppSettings(theme = theme, followSystem = follow))
        assertEquals(ThemeMode.DARK, mode(ThemeId.BLACK))
        assertEquals(ThemeMode.DARK, mode(ThemeId.MIDNIGHT))
        assertEquals(ThemeMode.DARK, mode(ThemeId.CUSTOM))
        assertEquals(ThemeMode.LIGHT, mode(ThemeId.LIGHT))
        assertEquals(ThemeMode.LIGHT, mode(ThemeId.PAPER))
        assertEquals(ThemeMode.SYSTEM, mode(ThemeId.PAPER, follow = true))
        // And back: an old backup of each old mode restores as that mode.
        for (old in ThemeMode.entries) {
            val (theme, follow) = ThemeMigration.theme(old.name)
            assertEquals(old, mode(theme, follow))
        }
    }

    @Test
    fun customValuesStayInsideWhatTheSlidersGive() {
        assertEquals(CustomThemeDefaults.HUE, CustomThemeValues.hue(null))
        assertEquals(CustomThemeDefaults.HUE, CustomThemeValues.hue(Float.NaN))
        assertEquals(360f, CustomThemeValues.hue(999f))
        assertEquals(0f, CustomThemeValues.hue(-5f))
        assertEquals(123.5f, CustomThemeValues.hue(123.5f))
        assertEquals(0.3f, CustomThemeValues.unit(null, 0.3f))
        assertEquals(0.3f, CustomThemeValues.unit(Float.POSITIVE_INFINITY, 0.3f))
        assertEquals(1f, CustomThemeValues.unit(7f, 0.3f))
        assertEquals(0f, CustomThemeValues.unit(-1f, 0.3f))
        assertEquals(0xFF123456.toInt(), CustomThemeValues.accent(0x00123456))
        assertEquals(CustomThemeDefaults.ACCENT, CustomThemeValues.accent(null))
        val wild = AppSettings(customHue = Float.NaN, customShade = 9f, customSurface = -9f, customAccent = 0x10FFFFFF)
        val sane = CustomThemeValues.sane(wild)
        assertEquals(CustomThemeDefaults.HUE, sane.customHue)
        assertEquals(1f, sane.customShade)
        assertEquals(0f, sane.customSurface)
        assertEquals(0xFFFFFFFF.toInt(), sane.customAccent)
    }
}
