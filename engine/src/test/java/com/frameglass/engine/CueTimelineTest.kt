package com.frameglass.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CueTimelineTest {
    private fun entry(start: Long, end: Long, text: String) = CueTimeline.Entry(start, end, text)

    @Test
    fun aCueShowsFromItsStartUpToItsEnd() {
        val timeline = CueTimeline(listOf(entry(1_000, 2_000, "a")))
        assertEquals(emptyList<String>(), timeline.at(999))
        assertEquals(listOf("a"), timeline.at(1_000))
        assertEquals(listOf("a"), timeline.at(1_999))
        assertEquals(emptyList<String>(), timeline.at(2_000))
    }

    @Test
    fun overlappingCuesShowTogetherInFileOrder() {
        val timeline = CueTimeline(listOf(entry(0, 3_000, "first"), entry(0, 1_000, "second"), entry(2_000, 4_000, "third")))
        assertEquals(listOf("first", "second"), timeline.at(500))
        assertEquals(listOf("first"), timeline.at(1_500))
        assertEquals(listOf("first", "third"), timeline.at(2_500))
        assertEquals(listOf("third"), timeline.at(3_500))
    }

    @Test
    fun oneStretchOfTimeIsOneListObject() {
        val timeline = CueTimeline(listOf(entry(1_000, 5_000, "a")))
        assertSame(timeline.at(1_500), timeline.at(4_500))
    }

    @Test
    fun aCueWithNoLengthIsDropped() {
        val timeline = CueTimeline(listOf(entry(1_000, 1_000, "a"), entry(3_000, 2_000, "b")))
        assertTrue(timeline.isEmpty)
        assertEquals(emptyList<String>(), timeline.at(1_000))
    }

    @Test
    fun aPositionBeforeZeroIsHandled() {
        val timeline = CueTimeline(listOf(entry(1_000, 2_000, "a")))
        assertEquals(emptyList<String>(), timeline.at(-5_000))
        assertEquals(1_000L, timeline.nextChange(-5_000))
    }

    @Test
    fun nextChangeWalksTheEdgesAndThenEnds() {
        val timeline = CueTimeline(listOf(entry(1_000, 2_000, "a"), entry(2_000, 3_000, "b")))
        assertEquals(1_000L, timeline.nextChange(0))
        assertEquals(2_000L, timeline.nextChange(1_000))
        assertEquals(3_000L, timeline.nextChange(2_500))
        assertNull(timeline.nextChange(3_000))
    }

    @Test
    fun aCueWithNoEndStaysToTheLastMoment() {
        val timeline = CueTimeline(listOf(entry(1_000, Long.MAX_VALUE, "a")))
        assertEquals(listOf("a"), timeline.at(Long.MAX_VALUE - 1))
    }
}
