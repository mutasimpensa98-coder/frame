package com.frameglass.playerui

import com.frameglass.engine.FrameRateFix
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SyncTextTest {
    @Test
    fun delaysAreSignedSecondsWithTwoDecimals() {
        assertEquals("+1.25", SyncText.seconds(Locale.US, 1_250))
        assertEquals("-0.50", SyncText.seconds(Locale.US, -500))
        assertEquals("+47.50", SyncText.seconds(Locale.US, 47_500))
    }

    @Test
    fun wholeFrameRatesHaveNoDecimals() {
        assertEquals("25", SyncText.fps(Locale.US, FrameRateFix.FROM_25_TO_23976.from))
        assertEquals("23.976", SyncText.fps(Locale.US, FrameRateFix.FROM_25_TO_23976.to))
        assertEquals("24", SyncText.fps(Locale.US, FrameRateFix.FROM_24_TO_25.from))
    }
}
