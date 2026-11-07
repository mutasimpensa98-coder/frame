package com.frameglass.player.details

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsBarTest {
    private val art = 900f
    private val bar = 250f
    private val collapse = 160f

    private fun past(index: Int, offset: Int) = scrolledPastArt(index, offset, art, bar, collapse)

    @Test
    fun theBarStaysClearWhileTheArtFillsIt() {
        assertEquals(0f, past(0, 0), 0f)
        assertEquals(0f, past(0, 490), 0f)
    }

    @Test
    fun theGlassIsWholeWhenTheArtLeavesTheBar() {
        assertEquals(80f, past(0, 570), 0f)
        assertEquals(collapse, past(0, (art - bar).toInt()), 0f)
    }

    @Test
    fun anythingBelowTheArtIsUnderGlass() {
        assertEquals(Float.MAX_VALUE, past(3, 0), 0f)
    }
}
