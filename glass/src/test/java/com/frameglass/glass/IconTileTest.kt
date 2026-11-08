package com.frameglass.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconTileTest {
    private val cards = listOf(false, true).map { FramePalettes.of(it, FrameAccent.White).card }

    @Test
    fun whiteGlyphReadsOnEveryHue() {
        TileHue.entries.forEach { hue ->
            val ratio = GlassContrast.ratio(TileGlyph, hue.fill)
            assertTrue("$hue glyph is $ratio:1", ratio >= GlassContrast.MinRatio)
        }
    }

    @Test
    fun everyHueStandsApartFromTheCardsOfBothThemes() {
        TileHue.entries.forEach { hue ->
            cards.forEach { card ->
                val ratio = GlassContrast.ratio(hue.fill, card)
                assertTrue("$hue on $card is $ratio:1", ratio >= 2.5f)
            }
        }
    }

    @Test
    fun huesAreDistinct() {
        assertEquals(TileHue.entries.size, TileHue.entries.map { it.fill }.toSet().size)
    }
}
