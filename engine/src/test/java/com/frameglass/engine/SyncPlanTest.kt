package com.frameglass.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlanTest {
    @Test
    fun aFilmIsReadInSpreadWindowsAwayFromTheCredits() {
        val duration = 120 * 60_000L
        val windows = SyncPlan.windows(duration)
        assertEquals(SyncPlan.MAX_WINDOWS, windows.size)
        assertTrue(windows.all { it.endMs - it.startMs == SyncPlan.WINDOW_MS })
        assertTrue(windows.all { it.startMs >= duration * 0.02 && it.endMs <= duration * 0.94 })
        val sorted = windows.sortedBy { it.startMs }
        assertTrue(sorted.zipWithNext().all { (a, b) -> a.endMs <= b.startMs })
        // The first four are already far apart, so an early stop still spans the film.
        val firstFour = windows.take(4).map { it.startMs }.sorted()
        assertTrue(firstFour.zipWithNext().all { (a, b) -> b - a > duration / 5 })
        assertTrue(firstFour.last() - firstFour.first() > duration * 2 / 3)
    }

    @Test
    fun aShortClipIsReadWhole() {
        val windows = SyncPlan.windows(100_000)
        assertEquals(listOf(SyncPlan.Window(0, 30_000), SyncPlan.Window(30_000, 60_000), SyncPlan.Window(60_000, 90_000), SyncPlan.Window(90_000, 100_000)), windows)
        assertTrue(SyncPlan.windows(0).isEmpty())
    }

    @Test
    fun theAlignerTriesAtTheEighthThenEveryFourthAndAtTheEnd() {
        val tries = (1..40).filter { SyncPlan.isCheckpoint(it, 40) }
        assertEquals(listOf(8, 12, 16, 20, 24, 28, 32, 36, 40), tries)
        assertTrue(SyncPlan.isCheckpoint(3, 3))
        assertFalse(SyncPlan.isCheckpoint(2, 3))
    }

    @Test
    fun everySlotIsUsedOnce() {
        for (slots in listOf(1, 2, 3, 7, 40)) assertEquals((0 until slots).toList(), SyncPlan.spreadOrder(slots).sorted())
    }
}
