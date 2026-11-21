package com.frameglass.data.api

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.frameglass.data.backup.BackupSettings
import com.frameglass.data.repo.SettingsPrefs
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sections of the tab bar: the rules, the settings file and the backup. */
class BarSectionsTest {
    private val home = BarSection.HOME
    private val folders = BarSection.FOLDERS
    private val library = BarSection.LIBRARY
    private val search = BarSection.SEARCH
    private val settings = BarSection.SETTINGS
    private val key = stringPreferencesKey("barSections")

    @Test
    fun theDefaultBarHasFoldersAndNoSearch() {
        assertEquals(listOf(home, folders, library, settings), BarSections.Default)
        assertEquals(BarSections.Default, AppSettings().visibleSections())
        assertNull(AppSettings().barSections)
    }

    @Test
    fun nothingStoredIsTheDefault() {
        assertEquals(BarSections.Default, BarSections.parse(null))
        assertNull(BarSections.normalise(null))
    }

    @Test
    fun aStoredChoiceComesBackInBarOrderWhateverOrderItWasWrittenIn() {
        assertEquals(listOf(home, library, search), BarSections.parse("search,home,library"))
        assertEquals(listOf(home, library, search), BarSections.parse(" library , search,home "))
        assertEquals("home,library,search", BarSections.normalise("search,home,library,home"))
    }

    @Test
    fun everySetWithAContentSectionRoundTrips() {
        val all = BarSection.entries
        for (mask in 1 until (1 shl all.size)) {
            val chosen = all.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            if (chosen.none { it.isContent }) continue
            assertEquals(chosen, BarSections.parse(BarSections.encode(chosen)))
        }
    }

    @Test
    fun anIdThisBuildDoesNotKnowIsSkippedAndTheRestIsKept() {
        assertEquals(listOf(home, settings), BarSections.parse("home,watchlist,settings"))
        assertEquals("home,settings", BarSections.normalise("home,watchlist,settings"))
        // The stored ids are lower case, and nothing else is one.
        assertEquals(listOf(library), BarSections.parse("HOME,library"))
    }

    @Test
    fun aValueThatLeavesNoContentSectionIsTheDefault() {
        for (damaged in listOf("", ",", "settings", "watchlist", "watchlist,settings", "  ")) {
            assertEquals(damaged, BarSections.Default, BarSections.parse(damaged))
            assertNull(damaged, BarSections.normalise(damaged))
        }
    }

    @Test
    fun settingsMayLeaveTheBarAndTheLastContentSectionMayNot() {
        assertTrue(BarSections.canTurnOff(settings, BarSections.Default))
        assertTrue(BarSections.canTurnOff(settings, listOf(home, settings)))
        assertTrue(BarSections.canTurnOff(home, listOf(home, library)))
        assertFalse(BarSections.canTurnOff(home, listOf(home, settings)))
        assertFalse(BarSections.canTurnOff(library, listOf(library)))
        // A section that is off has nothing to turn off.
        assertFalse(BarSections.canTurnOff(search, BarSections.Default))
    }

    @Test
    fun noSequenceOfSwitchesEmptiesTheBarOfContent() {
        var enabled = BarSections.Default
        for (section in BarSection.entries + BarSection.entries) {
            enabled = BarSections.toggle(enabled, section, on = false)
            assertTrue(enabled.toString(), enabled.any { it.isContent })
        }
        // What is left is one content section, and its switch does not move.
        assertEquals(1, enabled.size)
        assertEquals(enabled, BarSections.toggle(enabled, enabled.single(), on = false))
    }

    @Test
    fun aSwitchTurnedOnTakesItsPlaceInBarOrder() {
        assertEquals(listOf(home, folders, library, search, settings), BarSections.toggle(BarSections.Default, search, on = true))
        assertEquals(listOf(home, library), BarSections.toggle(listOf(library), home, on = true))
        // Turning on what is on changes nothing.
        assertEquals(BarSections.Default, BarSections.toggle(BarSections.Default, home, on = true))
    }

    @Test
    fun theChoiceIsOneKeyInTheSettingsFile() {
        val chosen = AppSettings().withSections(listOf(search, home))
        assertEquals("home,search", chosen.barSections)
        val file = mutablePreferencesOf()
        SettingsPrefs.write(file, chosen)
        assertEquals("home,search", file[key])
        assertEquals(chosen, SettingsPrefs.read(file))
        assertEquals(listOf(home, search), SettingsPrefs.read(file).visibleSections())
    }

    @Test
    fun anInstallFromBeforeTheSettingOpensOnTheNewDefaultAndWritesNoKey() {
        // What an older version left: its own keys, and none for the bar.
        val file = mutablePreferencesOf()
        SettingsPrefs.write(file, AppSettings(cinemaMode = true))
        file.remove(key)
        val read = SettingsPrefs.read(file)
        assertNull(read.barSections)
        assertEquals(listOf(home, folders, library, settings), read.visibleSections())
        // Saving another setting does not freeze the default into the file.
        SettingsPrefs.write(file, read.copy(coverTint = true))
        assertFalse(file.contains(key))
    }

    @Test
    fun aDamagedKeyIsTheDefaultAndTheNextSaveRemovesIt() {
        val file = mutablePreferencesOf()
        SettingsPrefs.write(file, AppSettings(cinemaMode = true))
        file[key] = "settings,,nowhere"
        val read = SettingsPrefs.read(file)
        assertNull(read.barSections)
        assertTrue(read.cinemaMode)
        SettingsPrefs.write(file, read)
        assertFalse(file.contains(key))
    }

    @Test
    fun theBackupCarriesTheChoice() {
        val chosen = AppSettings().withSections(listOf(folders, library))
        val back = BackupSettings.from(chosen).toAppSettings(fontExists = false, AppSettings())
        assertEquals(chosen, back)
    }

    @Test
    fun aBackupWrittenBeforeTheSettingKeepsThePhonesBar() {
        val json = Json { encodeDefaults = true }
        val older = json.encodeToString(BackupSettings.serializer(), BackupSettings.from(AppSettings()))
            .replace(Regex(""","barSections":(null|"[a-z,]*")"""), "")
        assertFalse("barSections" in older)
        val saved = json.decodeFromString(BackupSettings.serializer(), older)
        assertNull(saved.barSections)
        val onPhone = AppSettings().withSections(listOf(home, search, settings))
        assertEquals(onPhone.barSections, saved.toAppSettings(fontExists = false, onPhone).barSections)
    }

    @Test
    fun aBackupOfTheDefaultBarAlsoKeepsThePhonesBar() {
        val saved = BackupSettings.from(AppSettings())
        assertNull(saved.barSections)
        val onPhone = AppSettings().withSections(listOf(library))
        assertEquals("library", saved.toAppSettings(fontExists = false, onPhone).barSections)
    }

    @Test
    fun aBackupWithNoSectionThisBuildKnowsKeepsThePhonesBar() {
        val saved = BackupSettings.from(AppSettings()).copy(barSections = "watchlist,settings")
        val onPhone = AppSettings().withSections(listOf(home, folders))
        assertEquals("home,folders", saved.toAppSettings(fontExists = false, onPhone).barSections)
    }
}
