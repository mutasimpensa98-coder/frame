package com.frameglass.player.ui

import com.frameglass.data.api.AccentColor
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.CustomThemeDefaults
import com.frameglass.data.api.ThemeId
import com.frameglass.glass.CustomTheme
import com.frameglass.glass.FrameAccent
import com.frameglass.glass.FrameLook
import com.frameglass.glass.FrameThemeId
import com.frameglass.player.settings.defaultLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommonTest {
    @Test
    fun aPathShortensToItsLastTwoFolders() {
        assertEquals("Movies/Old", shortPath("/storage/emulated/0/Movies/Old"))
        assertEquals("Movies", shortPath("/Movies"))
        assertEquals("", shortPath(""))
    }

    @Test
    fun everyThemeAndAccentMapsToItsOwnName() {
        val ready = ThemeId.entries - ThemeId.CUSTOM
        assertEquals(FrameThemeId.entries, ready.map { it.toFrame() })
        assertEquals(ready.map { it.name.replace("_", "") }, FrameThemeId.entries.map { it.name.uppercase() })
        assertNull(ThemeId.CUSTOM.toFrame())

        val named = AccentColor.entries - AccentColor.THEME - AccentColor.CUSTOM
        assertEquals(FrameAccent.entries, named.map { it.toFrame() })
        assertEquals(named.map { it.name }, FrameAccent.entries.map { it.name.uppercase() })
        assertNull(AccentColor.THEME.toFrame())
        assertNull(AccentColor.CUSTOM.toFrame())
    }

    @Test
    fun freshSettingsAreTheDarkLookTheAppAlwaysHad() {
        assertEquals(FrameLook(), AppSettings().toLook())
    }

    @Test
    fun theSettingsBecomeTheLookTheyDescribe() {
        val navy = AppSettings(theme = ThemeId.MIDNIGHT, accent = AccentColor.PINK, followSystem = true)
        assertEquals(
            FrameLook(theme = FrameThemeId.Midnight, accent = FrameAccent.Pink, followSystem = true),
            navy.toLook(),
        )
        // The custom values and the "any colour" accent only count while they are the ones chosen.
        val idle = AppSettings(theme = ThemeId.PAPER, customHue = 10f, customAccent = 0xFF123456.toInt())
        assertEquals(FrameLook(theme = FrameThemeId.Paper), idle.toLook())
        val own = AppSettings(
            theme = ThemeId.CUSTOM,
            accent = AccentColor.CUSTOM,
            customHue = 10f,
            customShade = 0.9f,
            customSurface = 0.2f,
            customAccent = 0xFF123456.toInt(),
        )
        assertEquals(
            FrameLook(custom = CustomTheme(10f, 0.9f, 0.2f), accentArgb = 0xFF123456.toInt()),
            own.toLook(),
        )
    }

    @Test
    fun theCustomThemeStartsWhereItsPictureStarts() {
        assertEquals(CustomTheme(), AppSettings().customTheme())
        assertEquals(CustomTheme.DefaultHue, CustomThemeDefaults.HUE)
        assertEquals(CustomTheme.DefaultShade, CustomThemeDefaults.SHADE)
        assertEquals(CustomTheme.DefaultSurface, CustomThemeDefaults.SURFACE)
        assertEquals(CustomTheme.DefaultAccent, CustomThemeDefaults.ACCENT)
    }

    @Test
    fun resetToDefaultGivesTheDarkLookAndKeepsEverythingElse() {
        val changed = AppSettings(
            theme = ThemeId.CUSTOM,
            accent = AccentColor.CUSTOM,
            customHue = 300f,
            customShade = 1f,
            customSurface = 0f,
            customAccent = 0xFF00FF00.toInt(),
            followSystem = true,
            coverTint = true,
            doubleTapSeconds = 30,
        )
        val reset = defaultLook(changed)
        assertEquals(AppSettings(followSystem = true, coverTint = true, doubleTapSeconds = 30), reset)
        assertEquals(FrameLook(followSystem = true), reset.toLook())
    }
}
