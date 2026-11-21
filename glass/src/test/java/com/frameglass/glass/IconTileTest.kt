package com.frameglass.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconTileTest {
    private val oldCards = listOf(FrameThemeId.Black, FrameThemeId.Light).map { FramePalettes.of(it).card }
    private val everyTheme = FrameThemeId.entries.map { FramePalettes.of(it) } +
        (0 until 360 step 60).flatMap { hue ->
            listOf(0f, 0.3f, 0.6f, 0.7f, 1f).map { shade ->
                FramePalettes.of(FrameLook(custom = CustomTheme(hue.toFloat(), shade, 1f)))
            }
        }

    @Test
    fun whiteGlyphReadsOnEveryHue() {
        TileHue.entries.forEach { hue ->
            val ratio = GlassContrast.ratio(TileGlyph, hue.fill)
            assertTrue("$hue glyph is $ratio:1", ratio >= GlassContrast.MinRatio)
        }
    }

    @Test
    fun everyHueStandsApartFromTheCardsOfThePlainDarkAndLightThemes() {
        TileHue.entries.forEach { hue ->
            oldCards.forEach { card ->
                val ratio = GlassContrast.ratio(hue.fill, card)
                assertTrue("$hue on $card is $ratio:1", ratio >= 2.5f)
                assertFalse(tileNeedsRim(hue.fill, card))
            }
        }
    }

    @Test
    fun onAnyThemeATileThatComesCloseToTheCardTakesARimThatShows() {
        TileHue.entries.forEach { hue ->
            everyTheme.forEach { palette ->
                if (tileNeedsRim(hue.fill, palette.card)) {
                    // The rim is the outline colour, which shows on a card as it does on the canvas.
                    assertTrue("$hue rim on ${palette.look}", GlassContrast.reads(palette.outline, palette.card, 1.3f))
                }
            }
        }
    }

    @Test
    fun huesAreDistinct() {
        assertEquals(TileHue.entries.size, TileHue.entries.map { it.fill }.toSet().size)
    }
}
