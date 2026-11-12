package com.frameglass.playerui

import com.frameglass.data.api.SegmentKind
import com.frameglass.data.api.SkipSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeEndTest {
    private val minute = 60_000L
    private val length = 24 * minute
    private val credits = SkipSegment(SegmentKind.ED, 22 * minute, 23 * minute + 30_000, "aniskip")
    private val intro = SkipSegment(SegmentKind.OP, minute, 2 * minute, "aniskip")

    private fun offer(
        position: Long,
        segments: List<SkipSegment> = listOf(intro, credits),
        hasNext: Boolean = true,
        sleepsAtEnd: Boolean = false,
        looping: Boolean = false,
        dismissed: Boolean = false,
        duration: Long = length,
    ) = EpisodeEnd.offer(position, duration, segments, hasNext, sleepsAtEnd, looping, dismissed)

    @Test
    fun theCardComesWhenTheCreditsBegin() {
        assertEquals(NextOffer.NONE, offer(22 * minute - 1))
        assertEquals(NextOffer.CREDITS_CARD, offer(22 * minute))
        assertEquals(NextOffer.CREDITS_CARD, offer(23 * minute))
    }

    @Test
    fun aSceneAfterTheCreditsHasNoCard() {
        assertEquals(NextOffer.NONE, offer(23 * minute + 40_000))
    }

    @Test
    fun watchCreditsKeepsTheCardAway() {
        assertEquals(NextOffer.NONE, offer(22 * minute + 5_000, dismissed = true))
    }

    @Test
    fun withCreditsDataThereIsNoLightButton() {
        assertEquals(NextOffer.NONE, offer(length - 10_000))
    }

    @Test
    fun withNoCreditsDataTheLightButtonShowsInTheLastMinute() {
        assertEquals(NextOffer.NONE, offer(length - minute - 1, segments = listOf(intro)))
        assertEquals(NextOffer.LIGHT_BUTTON, offer(length - minute, segments = listOf(intro)))
        assertEquals(NextOffer.LIGHT_BUTTON, offer(length - 1_000, segments = emptyList()))
    }

    @Test
    fun aShortVideoOrAnUnknownLengthHasNoLightButton() {
        assertEquals(NextOffer.NONE, offer(30_000, segments = emptyList(), duration = 2 * minute))
        assertEquals(NextOffer.NONE, offer(30_000, segments = emptyList(), duration = 0))
    }

    @Test
    fun nothingWithoutANextEpisode() {
        assertEquals(NextOffer.NONE, offer(22 * minute, hasNext = false))
        assertEquals(NextOffer.NONE, offer(length - 1_000, segments = emptyList(), hasNext = false))
    }

    @Test
    fun theSleepTimerAtTheEndOfTheEpisodeHidesBoth() {
        assertEquals(NextOffer.NONE, offer(22 * minute, sleepsAtEnd = true))
        assertEquals(NextOffer.NONE, offer(length - 1_000, segments = emptyList(), sleepsAtEnd = true))
    }

    @Test
    fun aRunningLoopHidesBoth() {
        assertEquals(NextOffer.NONE, offer(22 * minute, looping = true))
    }

    @Test
    fun creditsInTheFirstHalfAreNoEnding() {
        val song = SkipSegment(SegmentKind.ED, 5 * minute, 6 * minute, "theintrodb")
        assertNull(EpisodeEnd.credits(listOf(song), length))
        assertEquals(NextOffer.NONE, offer(5 * minute + 10_000, segments = listOf(song)))
    }

    @Test
    fun theEarliestEndingCreditsCount() {
        val late = SkipSegment(SegmentKind.ED, 23 * minute, 24 * minute, "manual")
        assertEquals(credits, EpisodeEnd.credits(listOf(late, credits), length))
    }

    @Test
    fun playbackRunningIntoTheCreditsFinishesTheItem() {
        val start = credits.startMs
        assertTrue(EpisodeEnd.reachedByPlayback(start - 250, start, start, 1f))
        assertTrue(EpisodeEnd.reachedByPlayback(start - 100, start + 150, start, 1f))
        // A late tick still counts.
        assertTrue(EpisodeEnd.reachedByPlayback(start - 1_000, start + 900, start, 1f))
    }

    @Test
    fun aSeekIntoTheCreditsFinishesNothing() {
        val start = credits.startMs
        // After a seek the session forgets the last tick.
        assertFalse(EpisodeEnd.reachedByPlayback(null, start + 5_000, start, 1f))
        // A jump from well before the credits into them is a seek, even if no seek was reported.
        assertFalse(EpisodeEnd.reachedByPlayback(start - 60_000, start + 1_000, start, 1f))
        // Already inside, such as a scene after "Watch credits": nothing is crossed.
        assertFalse(EpisodeEnd.reachedByPlayback(start + 100_000, start + 100_250, start, 1f))
        // Still before them.
        assertFalse(EpisodeEnd.reachedByPlayback(start - 500, start - 250, start, 1f))
    }

    @Test
    fun fasterPlaybackTakesLongerStepsAcrossTheStart() {
        val start = credits.startMs
        assertFalse(EpisodeEnd.reachedByPlayback(start - 3_000, start + 500, start, 1f))
        assertTrue(EpisodeEnd.reachedByPlayback(start - 3_000, start + 500, start, 2f))
    }

    @Test
    fun afterTheCreditsMarkOnlyAPlaceWellBeforeThemIsSaved() {
        val start = credits.startMs
        assertFalse(EpisodeEnd.savesAfterCreditsMark(start + 10_000, start))
        assertFalse(EpisodeEnd.savesAfterCreditsMark(start - 10_000, start))
        assertTrue(EpisodeEnd.savesAfterCreditsMark(start - EpisodeEnd.BACK_FROM_CREDITS_MS - 1, start))
        assertTrue(EpisodeEnd.savesAfterCreditsMark(5 * minute, start))
    }

    @Test
    fun theReadOfASecondSubtitleWaitsForPlayback() {
        assertTrue(SecondRead.mustWait(buffering = true, playing = false, bufferedAheadMs = 60_000))
        assertTrue(SecondRead.mustWait(buffering = false, playing = true, bufferedAheadMs = 2_000))
        assertFalse(SecondRead.mustWait(buffering = false, playing = true, bufferedAheadMs = 30_000))
        assertFalse(SecondRead.mustWait(buffering = false, playing = false, bufferedAheadMs = 0))
    }

    @Test
    fun aPrivateVideoKeepsNoCuesAndAChangedFileIsReadAgain() {
        assertNull(SecondRead.cacheIdentity("/v/a.mkv", isPrivate = true, length = 10, modifiedMs = 1))
        val before = SecondRead.cacheIdentity("/v/a.mkv", isPrivate = false, length = 10, modifiedMs = 1)
        val after = SecondRead.cacheIdentity("/v/a.mkv", isPrivate = false, length = 10, modifiedMs = 2)
        assertFalse(before == after)
        assertEquals("content://x/1", SecondRead.cacheIdentity("content://x/1", isPrivate = false, length = 0, modifiedMs = 0))
    }
    @Test
    fun theEndRules() {
        assertEquals(EndAction.STAY_PAUSED, EpisodeEnd.atEnd(sleepsAtEnd = true, hasNext = true, onScreen = true))
        assertEquals(EndAction.CLOSE, EpisodeEnd.atEnd(sleepsAtEnd = true, hasNext = true, onScreen = false))
        assertEquals(EndAction.CLOSE, EpisodeEnd.atEnd(sleepsAtEnd = false, hasNext = false, onScreen = true))
        assertEquals(EndAction.SHOW_NEXT, EpisodeEnd.atEnd(sleepsAtEnd = false, hasNext = true, onScreen = false))
    }

    @Test
    fun aManualPickEndsTheEpisodeTimerAndKeepsMinutes() {
        assertNull(EpisodeEnd.sleepAfterManualPick(SleepTimer(null)))
        assertEquals(SleepTimer(5_000L), EpisodeEnd.sleepAfterManualPick(SleepTimer(5_000L)))
        assertNull(EpisodeEnd.sleepAfterManualPick(null))
    }

    @Test
    fun theManualCreditsMarkIsTimeBeforeTheEnd() {
        val marked = EpisodeEnd.withManualCredits(listOf(intro), 90_000, length)
        val ending = marked.single { it.kind == SegmentKind.ED }
        assertEquals(length - 90_000, ending.startMs)
        assertEquals(length, ending.endMs)
        assertEquals(EpisodeEnd.MANUAL_CREDITS, ending.source)
    }

    @Test
    fun timedCreditsWinOverTheManualMark() {
        assertEquals(listOf(credits), EpisodeEnd.withManualCredits(listOf(credits), 90_000, length))
    }

    @Test
    fun aMarkThatDoesNotFitIsIgnored() {
        assertEquals(listOf(intro), EpisodeEnd.withManualCredits(listOf(intro), null, length))
        assertEquals(listOf(intro), EpisodeEnd.withManualCredits(listOf(intro), 90_000, 0))
        assertEquals(listOf(intro), EpisodeEnd.withManualCredits(listOf(intro), length + 1, length))
    }
}
