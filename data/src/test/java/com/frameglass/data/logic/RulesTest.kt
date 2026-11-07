package com.frameglass.data.logic

import com.frameglass.data.api.SegmentKind
import com.frameglass.data.db.EpisodeLite
import com.frameglass.data.db.IntroSegment
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

internal fun file(
    rowId: Long,
    key: String,
    season: Int? = null,
    episode: Int? = null,
    height: Int = 1080,
    size: Long = 100,
    available: Boolean = true,
    titleId: Long = 1,
    firstSeenAt: Long = 0,
    nameKind: String = NameKinds.EPISODE,
    episodeId: Long? = null,
    relPath: String = "f$rowId.mkv",
    end: Int? = null,
    volume: String = "primary",
) = MediaFile(
    rowId = rowId, fileId = "id$rowId", volumeUuid = volume, relPath = relPath, size = size, mtime = 0, height = height,
    osHash = "", available = available, firstSeenAt = firstSeenAt, lastSeenAt = 0, titleId = titleId, episodeId = episodeId,
    releaseTags = null, playableKey = key, parsedSeason = season, parsedEpisode = episode, parsedEpisodeEnd = end,
    parsedAbsolute = null, nameKind = nameKind, durationMs = 1_000_000,
)

internal fun watch(key: String, position: Long = 0, watched: Boolean = false, at: Long = 0) =
    WatchState(key, position, 1_000_000, watched, at, 1)

internal fun playable(number: Int, watched: Boolean = false, position: Long = 0, at: Long = 0, available: Boolean = true): Playable {
    val key = "t:1:s1:e$number"
    return Playable(
        key = key, titleId = 1, season = 1, number = number, numberEnd = null, numbered = true, episode = null,
        files = listOf(file(number.toLong(), key, 1, number, available = available)),
        watch = if (watched || position > 0 || at > 0) watch(key, position, watched, at) else null,
    )
}

class MetaLanguageTest {
    @Test
    fun onlyTheLanguageCounts() {
        assertEquals("ar", MetaLanguage.of("ar"))
        assertEquals("ar", MetaLanguage.of("ar-LY"))
        assertEquals("ar", MetaLanguage.of("ar-EG-u-nu-latn"))
        assertEquals("en", MetaLanguage.of("en-US"))
    }

    @Test
    fun noLanguageFallsBackToEnglish() {
        assertEquals("en", MetaLanguage.of(null))
        assertEquals("en", MetaLanguage.of(""))
        assertEquals("en", MetaLanguage.of("und"))
    }
}

class WatchRulesTest {
    private val key = "t:1"

    @Test
    fun marksWatchedAtNinetyPercent() {
        assertFalse(WatchRules.onPosition(null, key, 1, 899, 1000, 1).watched)
        assertTrue(WatchRules.onPosition(null, key, 1, 900, 1000, 1).watched)
    }

    @Test
    fun watchedIsSticky() {
        val done = WatchRules.onPosition(null, key, 1, 950, 1000, 1)
        assertTrue(WatchRules.onPosition(done, key, 1, 100, 1000, 2).watched)
    }

    @Test
    fun completionSetsWatchedAndPositionZero() {
        val state = WatchRules.onFinished(watch(key, 500), key, 1, 1000, 9)
        assertTrue(state.watched)
        assertEquals(0, state.positionMs)
        assertEquals(9, state.lastPlayedAt)
    }

    @Test
    fun watchedRestartsFromZero() {
        assertEquals(0, WatchRules.resumeMs(watch(key, 950_000, true), sessionLive = false))
    }

    @Test
    fun leavingPastTheWatchedMarkRestartsEvenIfItWasTheLastSession() {
        assertEquals(0, WatchRules.resumeMs(watch(key, 950_000, true), sessionLive = true))
    }

    @Test
    fun aRewatchKilledMidwayRestoresItsPosition() {
        assertEquals(300_000, WatchRules.resumeMs(watch(key, 300_000, true), sessionLive = true))
    }

    @Test
    fun unwatchedResumes() {
        assertEquals(1234, WatchRules.resumeMs(watch(key, 1234), sessionLive = false))
    }

    @Test
    fun handMarkDoesNotCountAsPlayed() {
        val state = WatchRules.onManual(null, key, 1, true)
        assertEquals(0, state.lastPlayedAt)
        assertTrue(state.watched)
        assertFalse(WatchRules.onManual(state, key, 1, false).watched)
    }

    @Test
    fun mergeKeepsWatchedAndTheNewerPosition() {
        val old = watch(key, 10, watched = true, at = 1)
        val new = watch(key, 20, watched = false, at = 2)
        val merged = WatchRules.merge(old, new)
        assertTrue(merged.watched)
        assertEquals(20, merged.positionMs)
        assertEquals(2, merged.lastPlayedAt)
        assertEquals(merged, WatchRules.merge(new, old))
    }
}

class ResumeLogicTest {
    @Test
    fun latestPlayedWinsWhenUnwatched() {
        val items = listOf(playable(1, watched = true, at = 1), playable(2, position = 5, at = 9), playable(3))
        assertEquals(2, ResumeLogic.pick(items)?.number)
    }

    @Test
    fun nextUnwatchedAfterAFinishedEpisode() {
        val items = listOf(playable(1, watched = true, at = 5), playable(2), playable(3))
        assertEquals(2, ResumeLogic.pick(items)?.number)
    }

    @Test
    fun skipsUnavailableEpisodes() {
        val items = listOf(playable(1, watched = true, at = 5), playable(2, available = false), playable(3))
        assertEquals(3, ResumeLogic.pick(items)?.number)
    }

    @Test
    fun latestUnavailableMovesToTheNextAvailable() {
        val items = listOf(playable(1, position = 5, at = 9, available = false), playable(2))
        assertEquals(2, ResumeLogic.pick(items)?.number)
    }

    @Test
    fun wrapsToEarlierUnwatchedAtTheEnd() {
        val items = listOf(playable(1), playable(2, watched = true, at = 5))
        assertEquals(1, ResumeLogic.pick(items)?.number)
    }

    @Test
    fun handMarkedEpisodesAdvanceToo() {
        val items = listOf(playable(1, watched = true), playable(2, watched = true), playable(3))
        assertEquals(3, ResumeLogic.pick(items)?.number)
    }

    @Test
    fun allWatchedHasNothingNext() {
        assertNull(ResumeLogic.pick(listOf(playable(1, watched = true, at = 1))))
    }

    @Test
    fun unstartedStartsAtTheFirst() {
        assertEquals(1, ResumeLogic.pick(listOf(playable(1), playable(2)))?.number)
    }
}

class VersionsTest {
    @Test
    fun highestResolutionWins() {
        val files = listOf(file(1, "k", height = 720, size = 9), file(2, "k", height = 1080, size = 1))
        assertEquals(2, Versions.pick(files).rowId)
    }

    @Test
    fun anAvailableFileBeatsABetterGoneOne() {
        val files = listOf(file(1, "k", height = 2160, available = false), file(2, "k", height = 720))
        assertEquals(2, Versions.pick(files).rowId)
    }

    @Test
    fun largerFileBreaksATie() {
        val files = listOf(file(1, "k", size = 1), file(2, "k", size = 2))
        assertEquals(2, Versions.pick(files).rowId)
    }
}

class CachePolicyTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1000 * day

    @Test
    fun keepsYoungData() {
        assertEquals(CachePolicy.Action.KEEP, CachePolicy.decide(now - 149 * day, now))
        assertEquals(CachePolicy.Action.KEEP, CachePolicy.decide(null, now))
    }

    @Test
    fun refreshesFromDay150() {
        assertEquals(CachePolicy.Action.REFRESH, CachePolicy.decide(now - 150 * day, now))
        assertEquals(CachePolicy.Action.REFRESH, CachePolicy.decide(now - 174 * day, now))
    }

    @Test
    fun purgesAtDay175() {
        assertEquals(CachePolicy.Action.PURGE, CachePolicy.decide(now - 175 * day, now))
    }

    @Test
    fun cutoffsMatchTheDecision() {
        assertEquals(CachePolicy.Action.REFRESH, CachePolicy.decide(CachePolicy.refreshCutoff(now), now))
        assertEquals(CachePolicy.Action.PURGE, CachePolicy.decide(CachePolicy.purgeCutoff(now), now))
    }
}

class RetryPolicyTest {
    private fun hours(ms: Long) = TimeUnit.MILLISECONDS.toHours(ms)

    @Test
    fun retriesAfterOneSixAndTwentyFourHours() {
        assertEquals(1, hours(RetryPolicy.delayMs(1)))
        assertEquals(6, hours(RetryPolicy.delayMs(2)))
        assertEquals(24, hours(RetryPolicy.delayMs(3)))
        assertEquals(24, hours(RetryPolicy.delayMs(9)))
    }
}

class FramePickerTest {
    @Test
    fun samplesAtTenTwentyThirtyPercent() {
        assertEquals(listOf(100L, 200L, 300L), FramePicker.sampleTimesMs(1000))
    }

    @Test
    fun unknownDurationStillSamples() {
        assertEquals(3, FramePicker.sampleTimesMs(0).size)
    }

    @Test
    fun aFlatBlackFrameHasNoVariance() {
        assertEquals(0.0, FramePicker.lumaVariance(IntArray(16) { 0xFF000000.toInt() }), 0.0001)
    }

    @Test
    fun aDetailedFrameBeatsAFlatOne() {
        val detailed = IntArray(16) { if (it % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        val flat = IntArray(16) { 0xFF808080.toInt() }
        assertTrue(FramePicker.lumaVariance(detailed) > FramePicker.lumaVariance(flat))
    }

    @Test
    fun meanLumaOfWhiteIsTop() {
        assertEquals(255.0, FramePicker.meanLuma(IntArray(4) { 0xFFFFFFFF.toInt() }), 0.01)
    }
}

class SegmentRulesTest {
    private fun row(source: String, season: Int?, episodeId: Long?, start: Long, confidence: Double = 1.0) =
        IntroSegment(0, 1, season, episodeId, SegmentKind.OP, start, start + 90, source, confidence)

    @Test
    fun seasonOverrideBeatsWholeSeries() {
        val rows = listOf(row("manual", null, null, 1), row("manual", 2, null, 2))
        assertEquals(2, SegmentRules.pick(rows, 2, 9).single().startMs)
        assertEquals(1, SegmentRules.pick(rows, 1, 9).single().startMs)
    }

    @Test
    fun manualBeatsStored() {
        val rows = listOf(row("aniskip", 1, 9, 5), row("manual", null, null, 1))
        assertEquals("manual", SegmentRules.pick(rows, 1, 9).single().source)
    }

    @Test
    fun storedResultsAreForThatEpisodeOnly() {
        val rows = listOf(row("aniskip", 1, 9, 5))
        assertEquals(1, SegmentRules.pick(rows, 1, 9).size)
        assertTrue(SegmentRules.pick(rows, 1, 8).isEmpty())
    }
}

class AudioSummaryTest {
    @Test
    fun namesCodecsAndChannels() {
        val text = AudioSummary.of(listOf(AudioTrackInfo("eac3", 6), AudioTrackInfo("aac", 2), AudioTrackInfo("aac", 2)))
        assertEquals("E-AC-3 5.1, AAC 2.0", text)
    }

    @Test
    fun nothingKnownIsNull() {
        assertNull(AudioSummary.of(emptyList()))
    }
}

class SearchTextTest {
    @Test
    fun punctuationBecomesSpaces() {
        assertEquals("the show s1", SearchText.clean("the: show! (s1)"))
    }

    @Test
    fun arabicLettersStay() {
        assertEquals("العنوان الثاني", SearchText.clean("العنوان، الثاني"))
    }

    @Test
    fun everyWordIsAPrefix() {
        assertEquals("the* show*", SearchText.match("the show"))
        assertNull(SearchText.match(""))
    }
}

class NextUpTest {
    @Test
    fun skipsToTheNextAvailable() {
        val items = listOf(playable(1), playable(2, available = false), playable(3))
        assertEquals(3, NextUp.after(items, items[0].key)?.number)
    }

    @Test
    fun aLastEpisodeHasNoNext() {
        val items = listOf(playable(1))
        assertNull(NextUp.after(items, items[0].key))
    }
}

internal fun episode(id: Long, number: Int, season: Int = 1, name: String? = "E$number", air: String? = "2020-01-01", fetched: Long? = 1) =
    EpisodeLite(id, 1, season, number, name, null, 24, air, fetched)
