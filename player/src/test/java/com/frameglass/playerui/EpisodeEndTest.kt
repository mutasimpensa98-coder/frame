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
    fun theItemIsFinishedWhereTheCreditsBegin() {
        assertFalse(EpisodeEnd.creditsReached(22 * minute - 1, length, listOf(credits)))
        assertTrue(EpisodeEnd.creditsReached(22 * minute, length, listOf(credits)))
        // A long ending that starts at 80 % counts from there, before the 90 % rule of :data.
        val longCredits = SkipSegment(SegmentKind.ED, (length * 0.8).toLong(), length, "theintrodb")
        assertTrue(EpisodeEnd.creditsReached((length * 0.8).toLong(), length, listOf(longCredits)))
        // No data: only the 90 % rule, which is not this one.
        assertFalse(EpisodeEnd.creditsReached(length - 1_000, length, listOf(intro)))
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
