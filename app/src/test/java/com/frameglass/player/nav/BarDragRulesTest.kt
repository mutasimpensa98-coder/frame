package com.frameglass.player.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pages under a finger that drags the pill along the bar, as numbers. */
class BarDragRulesTest {
    private val tolerance = 0.0001f
    private val last = 3

    @Test
    fun pagesNearTheFingerSetOutFromWhereTheyStand() {
        // The finger came down on the edge of the selected tab: nothing is cut, and nothing jumps.
        assertFalse(BarDragRules.cuts(pages = 0f, finger = 0.45f))
        assertEquals(0f, BarDragRules.setOut(pages = 0f, finger = 0.45f), tolerance)
        assertEquals(2f, BarDragRules.setOut(pages = 2f, finger = 1f), tolerance)
        assertFalse(BarDragRules.cuts(pages = 2f, finger = 1f))
    }

    @Test
    fun pagesFarFromTheFingerAreCutToOneTabFromIt() {
        // The drag began on the last tab with the first one in view: the two tabs between are never drawn.
        assertTrue(BarDragRules.cuts(pages = 0f, finger = 3f))
        assertEquals(2f, BarDragRules.setOut(pages = 0f, finger = 3f), tolerance)
        assertTrue(BarDragRules.cuts(pages = 3f, finger = 0.4f))
        assertEquals(1.4f, BarDragRules.setOut(pages = 3f, finger = 0.4f), tolerance)
    }

    @Test
    fun thePagesCloseTheirDistanceAndThenStandUnderTheFinger() {
        val ahead = 0f - 0.45f
        assertEquals(0f, BarDragRules.place(finger = 0.45f, ahead = ahead, left = 1f, last = last), tolerance)
        assertEquals(0.225f, BarDragRules.place(finger = 0.45f, ahead = ahead, left = 0.5f, last = last), tolerance)
        assertEquals(0.45f, BarDragRules.place(finger = 0.45f, ahead = ahead, left = 0f, last = last), tolerance)
        // The finger moves on while the distance closes, and the pages go with it.
        assertEquals(1.2f - 0.45f * 0.5f, BarDragRules.place(finger = 1.2f, ahead = ahead, left = 0.5f, last = last), tolerance)
        assertEquals(2.7f, BarDragRules.place(finger = 2.7f, ahead = ahead, left = 0f, last = last), tolerance)
    }

    @Test
    fun thePagesNeverLeaveTheTabs() {
        assertEquals(0f, BarDragRules.place(finger = 0.1f, ahead = -0.4f, left = 1f, last = last), tolerance)
        assertEquals(3f, BarDragRules.place(finger = 2.9f, ahead = 0.4f, left = 1f, last = last), tolerance)
        assertEquals(0f, BarDragRules.place(finger = 0.5f, ahead = 0f, left = 0f, last = 0), tolerance)
        // A share outside 0 to 1 is held to it.
        assertEquals(1f, BarDragRules.place(finger = 1f, ahead = 0.5f, left = -2f, last = last), tolerance)
        assertEquals(1.5f, BarDragRules.place(finger = 1f, ahead = 0.5f, left = 7f, last = last), tolerance)
    }

    @Test
    fun aPlaceIsCutToAsAPageAndAnOffsetWithinHalfATab() {
        assertEquals(2 to 0f, BarDragRules.cut(2f))
        val (page, offset) = BarDragRules.cut(1.4f)
        assertEquals(1, page)
        assertEquals(0.4f, offset, tolerance)
        val (next, back) = BarDragRules.cut(1.6f)
        assertEquals(2, next)
        assertEquals(-0.4f, back, tolerance)
    }
}
