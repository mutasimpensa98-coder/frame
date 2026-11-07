package com.frameglass.glass

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterArtTest {
    private fun grey(level: Int) = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    private fun level(pixel: Int) = pixel and 0xFF

    @Test
    fun aPosterAndAnUprightVideoArePortrait() {
        assertTrue(isPortrait(500, 750))
        assertTrue(isPortrait(1080, 1920))
    }

    @Test
    fun wideSquareAndUnknownPicturesAreNot() {
        assertFalse(isPortrait(1280, 720))
        assertFalse(isPortrait(500, 500))
        assertFalse(isPortrait(500, 540))
        assertFalse(isPortrait(0, 750))
        assertFalse(isPortrait(500, 0))
    }

    @Test
    fun anEvenPictureStaysEvenToItsEdges() {
        val flat = IntArray(12 * 9) { grey(120) }
        assertArrayEquals(flat, softenPixels(flat, 12, 9, radius = 4, passes = 3))
    }

    @Test
    fun aBrightPointSpreadsAndKeepsItsLight() {
        val width = 21
        val pixels = IntArray(width * width) { grey(0) }
        val middle = 10 * width + 10
        pixels[middle] = grey(255)
        val soft = softenPixels(pixels, width, width, radius = 2, passes = 1)
        assertTrue(level(soft[middle]) in 1..254)
        assertEquals(level(soft[middle - 1]), level(soft[middle + 1]))
        assertEquals(level(soft[middle - width]), level(soft[middle + width]))
        assertTrue(level(soft[middle - 2]) > 0)
        assertEquals(0, level(soft[middle - 3]))
        assertEquals(255.0, soft.sumOf { level(it) }.toDouble(), 255.0 * 0.25)
    }

    @Test
    fun alphaIsBlurredLikeColour() {
        val pixels = intArrayOf(0x00000000, 0xFFFFFFFF.toInt(), 0x00000000)
        val soft = softenPixels(pixels, 3, 1, radius = 1, passes = 1)
        assertEquals(soft[0] ushr 24, soft[2] ushr 24)
        assertTrue(soft[1] ushr 24 in 1..254)
    }

    @Test
    fun noRadiusIsACopyAndTheInputIsLeftAlone() {
        val pixels = intArrayOf(grey(10), grey(200), grey(30), grey(90))
        val before = pixels.copyOf()
        val same = softenPixels(pixels, 2, 2, radius = 0, passes = 3)
        assertArrayEquals(before, same)
        softenPixels(pixels, 2, 2, radius = 3, passes = 2)
        assertArrayEquals(before, pixels)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aWrongSizeIsRefused() {
        softenPixels(IntArray(5), 2, 2, radius = 1, passes = 1)
    }

    @Test
    fun aWidePictureFillsTheSlot() {
        assertEquals(WideSlot.Fill("/art/still.jpg"), wideSlot("/art/still.jpg", false, "/art/poster.jpg", true))
    }

    @Test
    fun aPictureOfUnknownShapeFillsUntilItIsMeasured() {
        assertEquals(WideSlot.Fill("/art/still.jpg"), wideSlot("/art/still.jpg", null, null, null))
    }

    @Test
    fun aPortraitPictureIsPillarboxedNotCropped() {
        assertEquals(WideSlot.Pillarbox("/art/poster.jpg"), wideSlot("/art/poster.jpg", true, null, null))
    }

    @Test
    fun withNoPictureThePortraitFallbackStandsIn() {
        assertEquals(WideSlot.Pillarbox("/art/poster.jpg"), wideSlot(null, null, "/art/poster.jpg", true))
    }

    @Test
    fun aWideFallbackNeverPassesForThisEpisode() {
        assertEquals(WideSlot.Fill(null), wideSlot(null, null, "/art/frame.jpg", false))
        assertEquals(WideSlot.Fill(null), wideSlot(null, null, "/art/poster.jpg", null))
        assertEquals(WideSlot.Fill(null), wideSlot(null, null, null, null))
    }
}
