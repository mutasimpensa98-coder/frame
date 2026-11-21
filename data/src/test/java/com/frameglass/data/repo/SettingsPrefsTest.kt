package com.frameglass.data.repo

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.frameglass.data.api.AccentColor
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.AutoSubtitleMode
import com.frameglass.data.api.CustomThemeDefaults
import com.frameglass.data.api.ThemeId
import com.frameglass.data.api.ViewPlace
import com.frameglass.data.api.ViewSize
import com.frameglass.data.api.storedViewSize
import com.frameglass.data.api.withViewSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The settings file as a round trip, and as an older version of the app left it. */
class SettingsPrefsTest {
    private val oldMode = stringPreferencesKey("themeMode")
    private val accent = stringPreferencesKey("accent")

    /** What a version before the ready themes had written: its own keys, and none of today's. */
    private fun oldFile(mode: String?, oldAccent: String?) = mutablePreferencesOf().apply {
        this[booleanPreferencesKey("coverTint")] = true
        this[booleanPreferencesKey("cinemaMode")] = true
        if (mode != null) this[oldMode] = mode
        if (oldAccent != null) this[accent] = oldAccent
    }

    @Test
    fun anEmptyFileIsTheDefaults() {
        assertEquals(AppSettings(), SettingsPrefs.read(mutablePreferencesOf()))
    }

    @Test
    fun everySettingComesBackAsItWasSaved() {
        val chosen = AppSettings(
            onlineFeatures = false,
            doubleTapSeconds = 30,
            backgroundAudio = true,
            autoPlayNext = false,
            nightAudio = true,
            subtitleScale = 1.4f,
            subtitleColor = 0xFFFFE27A.toInt(),
            subtitleBackground = true,
            subtitleFontPath = "/fonts/a.ttf",
            subtitleBottomOffset = 0.1f,
            scanWholePhone = false,
            scanHiddenFolders = true,
            scanNomediaFolders = true,
            showUnavailable = true,
            tmdbBaseUrl = "https://example.invalid",
            theme = ThemeId.CUSTOM,
            followSystem = true,
            accent = AccentColor.CUSTOM,
            customHue = 301.5f,
            customShade = 0.87f,
            customSurface = 0.12f,
            customAccent = 0xFF8A4B1F.toInt(),
            coverTint = true,
            cinemaMode = true,
            hideUnseenStills = false,
            sendReleaseName = false,
            sendFileHash = false,
            autoSubtitles = AutoSubtitleMode.ARABIC_THEN_ENGLISH,
            vaultLockSeconds = 60,
            neverSleepingHintDone = true,
            onboardingDone = true,
            tmdbEnabled = false,
            imdbEnabled = false,
            viewLibrary = ViewSize.SMALL_GRID,
            viewFolders = ViewSize.LARGE_GRID,
            viewPrivate = ViewSize.COMPACT_LIST,
            viewLists = ViewSize.LIST,
        )
        val file = mutablePreferencesOf()
        SettingsPrefs.write(file, chosen)
        assertEquals(chosen, SettingsPrefs.read(file))
    }

    @Test
    fun everyViewSizeOfEveryPlaceRoundTrips() {
        for (place in ViewPlace.entries) {
            for (size in ViewSize.entries) {
                val chosen = AppSettings().withViewSize(place, size)
                val file = mutablePreferencesOf()
                SettingsPrefs.write(file, chosen)
                assertEquals(chosen, SettingsPrefs.read(file))
                // One key for the place that was chosen, and none for the others.
                for (other in ViewPlace.entries) {
                    assertEquals(other == place, file.contains(stringPreferencesKey(ViewKeys.of(other))))
                }
            }
        }
    }

    @Test
    fun aViewSizeThatWasClearedLeavesNoKeyBehind() {
        val file = mutablePreferencesOf()
        val chosen = AppSettings().withViewSize(ViewPlace.FOLDERS, ViewSize.LIST)
        SettingsPrefs.write(file, chosen)
        SettingsPrefs.write(file, chosen.withViewSize(ViewPlace.FOLDERS, null))
        assertFalse(file.contains(stringPreferencesKey(ViewKeys.of(ViewPlace.FOLDERS))))
        assertEquals(AppSettings(), SettingsPrefs.read(file))
    }

    @Test
    fun aViewSizeThisBuildDoesNotKnowIsNoChoiceAndTheLookIsKept() {
        val file = mutablePreferencesOf()
        SettingsPrefs.write(file, AppSettings(theme = ThemeId.COFFEE, viewLibrary = ViewSize.LIST))
        file[stringPreferencesKey(ViewKeys.of(ViewPlace.LIBRARY))] = "HUGE_GRID"
        val read = SettingsPrefs.read(file)
        assertNull(read.storedViewSize(ViewPlace.LIBRARY))
        assertEquals(ThemeId.COFFEE, read.theme)
    }

    @Test
    fun anUpdateFromBeforeBothKeepsTheOldLookAndChoosesNoViewSize() {
        // The two settings of 2026-11-21 meet in one file: the old look migrates, and no place has a size yet.
        val read = SettingsPrefs.read(oldFile("LIGHT", "BLUE"))
        assertEquals(ThemeId.LIGHT, read.theme)
        assertEquals(AccentColor.BLUE, read.accent)
        for (place in ViewPlace.entries) assertNull(read.storedViewSize(place))
    }

    @Test
    fun everyThemeAndAccentRoundTrips() {
        for (theme in ThemeId.entries) {
            for (chosenAccent in AccentColor.entries) {
                for (follow in listOf(false, true)) {
                    val chosen = AppSettings(theme = theme, accent = chosenAccent, followSystem = follow)
                    val file = mutablePreferencesOf()
                    SettingsPrefs.write(file, chosen)
                    assertEquals(chosen, SettingsPrefs.read(file))
                }
            }
        }
    }

    @Test
    fun anUpdateFromTheOldDarkLookKeepsIt() {
        val read = SettingsPrefs.read(oldFile("DARK", "WHITE"))
        assertEquals(ThemeId.BLACK, read.theme)
        assertFalse(read.followSystem)
        assertEquals(AccentColor.THEME, read.accent)
        // Nothing else moved.
        assertEquals(AppSettings(coverTint = true, cinemaMode = true), read)
    }

    @Test
    fun anUpdateFromTheOldLightLookKeepsItAndItsAccent() {
        val read = SettingsPrefs.read(oldFile("LIGHT", "VIOLET"))
        assertEquals(ThemeId.LIGHT, read.theme)
        assertFalse(read.followSystem)
        assertEquals(AccentColor.VIOLET, read.accent)
    }

    @Test
    fun anUpdateFromFollowThePhoneKeepsFollowing() {
        val read = SettingsPrefs.read(oldFile("SYSTEM", "AMBER"))
        assertEquals(ThemeId.BLACK, read.theme)
        assertTrue(read.followSystem)
        assertEquals(AccentColor.AMBER, read.accent)
    }

    @Test
    fun anOldFileThatNeverSavedALookIsTheDefault() {
        val read = SettingsPrefs.read(oldFile(null, null))
        assertEquals(ThemeId.BLACK, read.theme)
        assertFalse(read.followSystem)
        assertEquals(AccentColor.THEME, read.accent)
        assertEquals(CustomThemeDefaults.HUE, read.customHue)
        assertEquals(CustomThemeDefaults.ACCENT, read.customAccent)
    }

    @Test
    fun theFirstSaveAfterAnUpdateMakesTheMigrationLasting() {
        val file = oldFile("SYSTEM", "WHITE")
        val migrated = SettingsPrefs.read(file)
        SettingsPrefs.write(file, migrated)
        assertNull(file[oldMode])
        assertEquals(migrated, SettingsPrefs.read(file))
        // From here on white is a choice like any other, and is no longer read as "the old neutral".
        SettingsPrefs.write(file, migrated.copy(accent = AccentColor.WHITE))
        assertEquals(AccentColor.WHITE, SettingsPrefs.read(file).accent)
    }

    @Test
    fun aDamagedLookFallsBackToTheDefaultAndKeepsTheRest() {
        val file = mutablePreferencesOf()
        SettingsPrefs.write(file, AppSettings(theme = ThemeId.FOREST, accent = AccentColor.PINK, coverTint = true))
        file[stringPreferencesKey("theme")] = "NEON"
        file[accent] = "NEON"
        val read = SettingsPrefs.read(file)
        assertEquals(ThemeId.BLACK, read.theme)
        assertEquals(AccentColor.THEME, read.accent)
        assertTrue(read.coverTint)
    }
}
