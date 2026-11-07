package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Decision D9: the release name and the hash leave the phone only on a tap, and only while their switch is on. */
class OnlineSubtitleQueryTest {
    private val facts = SubtitleFacts(
        title = "Frieren", year = 2023, season = 1, episode = 5, tmdbId = 42, imdbId = "tt1",
        osHash = "0123456789abcdef", fileSize = 1000, releaseName = "[Group] Frieren - 05",
    )

    @Test
    fun bothSwitchesOnSendBoth() {
        val q = SubtitleQueries.build(facts, AppSettings(sendReleaseName = true, sendFileHash = true))
        assertEquals("[Group] Frieren - 05", q.releaseName)
        assertEquals("0123456789abcdef", q.osHash)
        assertEquals(1000L, q.fileSize)
    }

    @Test
    fun theReleaseSwitchHoldsBackTheName() {
        val q = SubtitleQueries.build(facts, AppSettings(sendReleaseName = false, sendFileHash = true))
        assertNull(q.releaseName)
        assertEquals("0123456789abcdef", q.osHash)
    }

    @Test
    fun theHashSwitchHoldsBackHashAndSize() {
        val q = SubtitleQueries.build(facts, AppSettings(sendReleaseName = true, sendFileHash = false))
        assertNull(q.osHash)
        assertNull(q.fileSize)
        assertEquals("[Group] Frieren - 05", q.releaseName)
    }

    @Test
    fun bothOffLeaveOnlyWhatEveryLookupSends() {
        val q = SubtitleQueries.build(facts, AppSettings(sendReleaseName = false, sendFileHash = false))
        assertNull(q.releaseName)
        assertNull(q.osHash)
        assertNull(q.fileSize)
        assertEquals("Frieren", q.title)
        assertEquals(2023, q.year)
        assertEquals(1, q.season)
        assertEquals(5, q.episode)
        assertEquals(42L, q.tmdbId)
        assertEquals("tt1", q.imdbId)
    }

    @Test
    fun aSizeNeverGoesWithoutItsHash() {
        val q = SubtitleQueries.build(facts.copy(osHash = null), AppSettings())
        assertNull(q.fileSize)
    }

    @Test
    fun arabicIsAskedForFirst() {
        assertEquals(listOf("ar", "en"), SubtitleQueries.build(facts, AppSettings()).languages)
    }

    @Test
    fun onByDefaultPerD9() {
        val settings = AppSettings()
        assertEquals(true, settings.sendReleaseName)
        assertEquals(true, settings.sendFileHash)
    }
}
