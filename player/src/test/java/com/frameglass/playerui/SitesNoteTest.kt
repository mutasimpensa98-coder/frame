package com.frameglass.playerui

import com.frameglass.data.api.ApiKeyKind
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.PlaybackItem
import com.frameglass.data.api.SubtitleSiteInfo
import com.frameglass.data.api.SubtitleSites
import com.frameglass.data.api.TrackPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SitesNoteTest {
    private val seriesSite = SubtitleSiteInfo("Series Site", key = null, seriesOnly = true)
    private val keyedSite = SubtitleSiteInfo("Keyed Site", key = ApiKeyKind.SUBDL, seriesOnly = false)
    private val keyless = SubtitleSites(active = listOf(seriesSite), withFreeKey = listOf(keyedSite))

    private fun item(episode: Int?) = PlaybackItem(
        ref = PlayRef(fileRowId = 1),
        playableKey = "k",
        fileRowId = 1,
        titleId = 1,
        uri = "/x/Invented Show.mkv",
        title = "Invented Show",
        subtitle = null,
        season = episode?.let { 1 },
        episode = episode,
        posterPath = null,
        resumeMs = 0,
        durationMs = 0,
        sidecars = emptyList(),
        segments = emptyList(),
        prefs = TrackPrefs(),
        next = null,
        nextLabel = null,
    )

    @Test
    fun anEpisodeNamesTheSeriesSiteAsSearched() {
        val note = SitesNote.of(keyless, item(episode = 3))
        assertEquals(listOf(seriesSite), note.searched)
        assertEquals(listOf(keyedSite), note.withFreeKey)
        assertFalse(note.filmsNeedKey)
    }

    @Test
    fun aFilmDoesNotClaimTheSeriesSiteAndSaysFilmsNeedAKey() {
        val note = SitesNote.of(keyless, item(episode = null))
        assertTrue(note.searched.isEmpty())
        assertTrue(note.filmsNeedKey)
    }

    @Test
    fun noActiveSiteAtAllKeepsTheGenericNoKeyWords() {
        val note = SitesNote.of(SubtitleSites(emptyList(), listOf(keyedSite)), item(episode = null))
        assertFalse(note.filmsNeedKey)
    }

    @Test
    fun aSavedKeyMakesAFilmSearchable() {
        val sites = SubtitleSites(active = listOf(seriesSite, keyedSite), withFreeKey = emptyList())
        val note = SitesNote.of(sites, item(episode = null))
        assertEquals(listOf(keyedSite), note.searched)
        assertFalse(note.filmsNeedKey)
    }
}
