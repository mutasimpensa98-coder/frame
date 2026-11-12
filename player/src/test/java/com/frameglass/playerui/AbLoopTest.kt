package com.frameglass.playerui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbLoopTest {
    @Test
    fun emptyLoopDoesNothing() {
        val loop = AbLoop()
        assertFalse(loop.active)
        assertNull(loop.seekTarget(50_000))
    }

    @Test
    fun bNeedsAFirst() {
        assertNull(AbLoop().withB(20_000))
    }

    @Test
    fun aThenBMakesALoopThatSendsPlaybackBack() {
        val loop = AbLoop().withA(10_000).withB(20_000)!!
        assertTrue(loop.active)
        assertNull(loop.seekTarget(19_999))
        assertEquals(10_000L, loop.seekTarget(20_000))
        assertEquals(10_000L, loop.seekTarget(20_120))
    }

    @Test
    fun aSeekPastBLandsBackAtA() {
        val loop = AbLoop(10_000, 20_000)
        assertEquals(10_000L, loop.seekTarget(45_000))
    }

    @Test
    fun aSeekBeforeAPlaysOnUntilB() {
        val loop = AbLoop(10_000, 20_000)
        assertNull(loop.seekTarget(2_000))
    }

    @Test
    fun bTooCloseToAIsRefused() {
        val withA = AbLoop().withA(10_000)
        assertNull(withA.withB(10_500))
        assertNull(withA.withB(9_000))
        assertTrue(withA.withB(11_000)!!.active)
    }

    @Test
    fun aMovedPastBDropsB() {
        val loop = AbLoop(10_000, 20_000).withA(19_500)
        assertEquals(19_500L, loop.aMs)
        assertNull(loop.bMs)
        assertFalse(loop.active)
    }

    @Test
    fun aMovedEarlierKeepsB() {
        val loop = AbLoop(10_000, 20_000).withA(5_000)
        assertEquals(AbLoop(5_000, 20_000), loop)
    }

    @Test
    fun aNeverGoesBelowZero() {
        assertEquals(0L, AbLoop().withA(-40).aMs)
    }

    @Test
    fun theCheckComesSoonerNearBAndAtSpeed() {
        val loop = AbLoop(0, 10_000)
        assertEquals(AbLoop.MAX_WAIT_MS, loop.waitMs(0, 1f))
        assertEquals(100L, loop.waitMs(9_900, 1f))
        assertEquals(50L, loop.waitMs(9_900, 2f))
        assertEquals(AbLoop.MIN_WAIT_MS, loop.waitMs(9_999, 1f))
        assertEquals(AbLoop.MIN_WAIT_MS, loop.waitMs(12_000, 1f))
    }
}
