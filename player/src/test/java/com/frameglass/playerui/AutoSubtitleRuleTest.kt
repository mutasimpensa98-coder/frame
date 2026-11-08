package com.frameglass.playerui

import com.frameglass.data.api.AutoSubtitleMode
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.PlaybackItem
import com.frameglass.data.api.TrackPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the player asks for a subtitle by itself (waiver of 2026-11-08). */
class AutoSubtitleRuleTest {
    private val item = PlaybackItem(
        ref = PlayRef(fileRowId = 1),
        playableKey = "k",
        fileRowId = 1,
        titleId = 1,
        uri = "/x/Invented Film.mkv",
        title = "Invented Film",
        subtitle = null,
        season = null,
        episode = null,
        posterPath = null,
        resumeMs = 0,
        durationMs = 0,
        sidecars = emptyList(),
        segments = emptyList(),
        prefs = TrackPrefs(),
        next = null,
        nextLabel = null,
    )

    private fun row(language: String?, forced: Boolean = false, signs: Boolean = false, sidecar: String? = null, supported: Boolean = true) =
        TrackRow(
            group = 0, track = 0, language = language, label = null, codec = "SRT", channels = 0, forced = forced,
            signs = signs, sidecarPath = sidecar, isDefault = false, selected = false, supported = supported,
        )

    private fun languages(
        rows: List<TrackRow> = emptyList(),
        mode: AutoSubtitleMode = AutoSubtitleMode.ARABIC,
        online: Boolean = true,
        private: Boolean = false,
        off: Boolean = false,
    ) = AutoSubtitleRule.languages(mode, online, item.copy(isPrivate = private), off, rows)

    @Test
    fun noSubtitleAtAllAsksForArabic() {
        assertEquals(listOf("ar"), languages())
    }

    @Test
    fun anEmbeddedOrSidecarArabicTrackAsksNothing() {
        assertTrue(languages(listOf(row("ara"))).isEmpty())
        assertTrue(languages(listOf(row("ar", sidecar = "/x/Invented Film.ar.srt"))).isEmpty())
    }

    @Test
    fun aForcedOrSignsArabicTrackAloneStillAsks() {
        assertEquals(listOf("ar"), languages(listOf(row("ar", forced = true), row("ar", signs = true))))
    }

    @Test
    fun anUnplayableArabicTrackStillAsks() {
        assertEquals(listOf("ar"), languages(listOf(row("ar", supported = false))))
    }

    @Test
    fun theSettingOffAsksNothing() {
        assertTrue(languages(mode = AutoSubtitleMode.OFF).isEmpty())
    }

    @Test
    fun onlineOffAsksNothing() {
        assertTrue(languages(online = false).isEmpty())
    }

    @Test
    fun aPrivateVideoAsksNothing() {
        assertTrue(languages(private = true).isEmpty())
    }

    @Test
    fun subtitlesTurnedOffForThisVideoAsksNothing() {
        assertTrue(languages(off = true).isEmpty())
    }

    @Test
    fun arabicThenEnglishAddsEnglishOnlyWhenTheVideoHasNone() {
        assertEquals(listOf("ar", "en"), languages(mode = AutoSubtitleMode.ARABIC_THEN_ENGLISH))
        assertEquals(listOf("ar"), languages(listOf(row("eng")), mode = AutoSubtitleMode.ARABIC_THEN_ENGLISH))
    }

    @Test
    fun arabicOnlyNeverAsksForEnglish() {
        assertEquals(listOf("ar"), languages(listOf(row("fr"))))
    }

    @Test
    fun aSidecarInNoStatedLanguageAsksNothing() {
        assertTrue(languages(listOf(row(null, sidecar = "/x/Invented Film.srt"))).isEmpty())
        assertEquals(listOf("ar"), languages(listOf(row(null))))
    }
}
