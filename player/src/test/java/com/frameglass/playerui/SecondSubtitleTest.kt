package com.frameglass.playerui

import com.frameglass.engine.CueTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondSubtitleTest {
    private fun row(
        group: Int,
        language: String?,
        codec: String? = "SRT",
        sidecar: String? = null,
        ordinal: Int? = if (sidecar == null) group else null,
        selected: Boolean = false,
        supported: Boolean = true,
    ) = TrackRow(
        group = group,
        track = 0,
        language = language,
        label = null,
        codec = codec,
        channels = 0,
        forced = false,
        signs = false,
        sidecarPath = sidecar,
        isDefault = false,
        selected = selected,
        supported = supported,
        embeddedOrdinal = ordinal,
    )

    @Test
    fun theMainSubtitleIsNeverOfferedAgain() {
        val arabic = row(0, "ar", selected = true)
        val english = row(1, "en")
        assertEquals(listOf(english), SecondPicker.candidates(listOf(arabic, english), arabic))
    }

    @Test
    fun bitmapAndUnplayableTracksAreLeftOut() {
        val rows = listOf(
            row(0, "en", codec = "PGS"),
            row(1, "en", codec = "VobSub"),
            row(2, "en", codec = "DVBSUBS"),
            row(3, "fr", supported = false),
            row(4, "en", codec = "ASS"),
        )
        assertEquals(listOf(4), SecondPicker.candidates(rows, null).map { it.group })
    }

    @Test
    fun filesEmbeddedAndDownloadedTracksAreAllOffered() {
        val embedded = row(0, "en")
        val sidecar = row(1, "ar", sidecar = "/subs/one.ar.srt")
        val downloaded = row(-1, "fr", sidecar = "/app/player-subs/one.fr.srt")
        val offered = SecondPicker.candidates(listOf(embedded, sidecar, downloaded), null)
        assertEquals(3, offered.size)
    }

    @Test
    fun theSameFileListedTwiceShowsOnce() {
        val sidecar = row(1, "en", sidecar = "/subs/one.en.srt")
        val live = row(-1, "en", sidecar = "/subs/one.en.srt")
        assertEquals(1, SecondPicker.candidates(listOf(sidecar, live), null).size)
    }

    @Test
    fun aRowWithNoKeyCannotBeChosen() {
        val sidecarOfNoFile = row(2, "en", sidecar = null, ordinal = null)
        assertTrue(SecondPicker.candidates(listOf(sidecarOfNoFile), null).isEmpty())
    }

    @Test
    fun keysFollowThePathOrThePlaceInTheContainer() {
        assertEquals(SecondKey("/a.srt", null), SecondKey.of(row(3, "en", sidecar = "/a.srt")))
        assertEquals(SecondKey(null, 2), SecondKey.of(row(7, "en", ordinal = 2)))
    }

    @Test
    fun memoryFindsTheSameLanguageInTheNextEpisode() {
        val episodeOne = listOf(row(0, "ar", selected = true), row(1, "en"), row(2, "fr"))
        val main = episodeOne[0]
        val stored = SecondPicker.encode(episodeOne, main, episodeOne[1])
        // The next file lists its tracks in another order and has the English one as a sidecar.
        val episodeTwo = listOf(row(0, "fr"), row(1, "ar"), row(2, "en", sidecar = "/subs/two.en.srt"))
        val found = SecondPicker.remembered(episodeTwo, episodeTwo[1], stored)
        assertEquals("/subs/two.en.srt", found?.sidecarPath)
    }

    @Test
    fun memoryFindsNothingWhenTheLanguageIsMissing() {
        val rows = listOf(row(0, "ar"), row(1, "fr"))
        assertNull(SecondPicker.remembered(rows, rows[0], "en#0"))
        assertNull(SecondPicker.remembered(rows, rows[0], null))
    }

    @Test
    fun memoryNeverPicksTheMainTrack() {
        val rows = listOf(row(0, "en", selected = true))
        assertNull(SecondPicker.remembered(rows, rows[0], "en#0"))
    }

    @Test
    fun aSeriesSharesOneMemoryAndAVideoWithNoTitleKeepsItsOwn() {
        assertEquals(SecondPicker.memoryKey(5L, "a"), SecondPicker.memoryKey(5L, "b"))
        assertFalse(SecondPicker.memoryKey(null, "a") == SecondPicker.memoryKey(null, "b"))
    }

    @Test
    fun theClockFollowsTheMainDelayOrItsOwn() {
        assertEquals(400L, SecondTiming.delayMs(followsMain = true, mainDelayMs = 400, ownDelayMs = -900))
        assertEquals(-900L, SecondTiming.delayMs(followsMain = false, mainDelayMs = 400, ownDelayMs = -900))
        // Positive delay shows later: at 10 s with +400 ms the timeline is read at 9.6 s.
        assertEquals(9_600_000L, SecondTiming.lookupUs(10_000, 400))
    }

    @Test
    fun embeddedTracksStartOnTheirOwnClock() {
        assertTrue(SecondTiming.followsMainAtStart(isFile = true))
        assertFalse(SecondTiming.followsMainAtStart(isFile = false))
    }

    @Test
    fun aFrameRateFixStretchesEveryTime() {
        val entries = listOf(CueTimeline.Entry(1_000_000L, 2_000_000L, "a"), CueTimeline.Entry(3_000_000L, Long.MAX_VALUE, "b"))
        val stretched = SecondTiming.stretched(entries, 25.0 / 24.0)
        assertEquals(1_041_666L, stretched[0].startUs)
        assertEquals(2_083_333L, stretched[0].endUs)
        assertEquals(Long.MAX_VALUE, stretched[1].endUs)
        assertTrue(SecondTiming.stretched(entries, 1.0) === entries)
    }
}
