package com.frameglass.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class CueBidiTest {
    @Test
    fun latinOpeningInAnArabicLineGetsAMark() {
        assertEquals(listOf(0), CueBidi.markOffsets("S01 هو الأول."))
    }

    @Test
    fun arabicOpeningNeedsNothing() {
        assertEquals(emptyList<Int>(), CueBidi.markOffsets("قال \"hello\" ثم مشى."))
        assertEquals(emptyList<Int>(), CueBidi.markOffsets("(ضحك) نعم!"))
    }

    @Test
    fun plainEnglishIsLeftAlone() {
        assertEquals(emptyList<Int>(), CueBidi.markOffsets("Hello there, friend."))
        assertEquals(emptyList<Int>(), CueBidi.markOffsets("This is English with one word مرحبا in it."))
    }

    @Test
    fun eachLineIsJudgedOnItsOwn() {
        val first = "مرحبا"
        assertEquals(listOf(first.length + 1), CueBidi.markOffsets("$first\nS02 الحلقة الثانية"))
    }

    @Test
    fun authorDirectionIsRespected() {
        assertEquals(emptyList<Int>(), CueBidi.markOffsets("‫S01 هو الأول."))
    }

    @Test
    fun emptyTextIsFine() {
        assertEquals(emptyList<Int>(), CueBidi.markOffsets(""))
    }
}
