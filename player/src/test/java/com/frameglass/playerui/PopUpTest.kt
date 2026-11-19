package com.frameglass.playerui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopUpTest {
    @Test
    fun aNormalVideoKeepsItsOwnShape() {
        assertEquals(1778, PopUpShape.width(1920f / 1080f))
        assertEquals(1333, PopUpShape.width(4f / 3f))
        assertEquals(1000, PopUpShape.width(1f))
        assertEquals(563, PopUpShape.width(1080f / 1920f))
    }

    @Test
    fun aCinemaScopeFilmFitsWithoutClamping() {
        assertEquals(2370, PopUpShape.width(1920f / 810f))
    }

    @Test
    fun shapesWiderOrTallerThanAndroidAllowsAreHeldInside() {
        val widest = PopUpShape.width(3840f / 1080f)!!
        val tallest = PopUpShape.width(1080f / 3840f)!!
        assertEquals(2380, widest)
        assertEquals(420, tallest)
        // The platform limits are 2.39:1 and 1:2.39, and it throws outside them.
        assertTrue(widest.toFloat() / PopUpShape.BASE < 2.39f)
        assertTrue(tallest.toFloat() / PopUpShape.BASE > 1f / 2.39f)
        assertEquals(2380, PopUpShape.width(Float.POSITIVE_INFINITY))
    }

    @Test
    fun noShapeUntilASizeIsKnown() {
        assertNull(PopUpShape.width(0f))
        assertNull(PopUpShape.width(-1.5f))
        assertNull(PopUpShape.width(Float.NaN))
    }

    @Test
    fun aNewVideoWaitsForItsOwnPicture() {
        val gate = PopUpGate()
        gate.request(loading = true, pictureKnown = true)
        assertTrue(gate.pending)
        assertFalse(gate.ready)
        // A frame of the video that was playing before must not start the move.
        gate.onPicture()
        assertFalse(gate.ready)
        gate.onLoaded()
        assertFalse(gate.ready)
        gate.onPicture()
        assertTrue(gate.ready)
    }

    @Test
    fun theVideoAlreadyPlayingEntersAtOnce() {
        val gate = PopUpGate()
        gate.request(loading = false, pictureKnown = true)
        assertTrue(gate.ready)
    }

    @Test
    fun theVideoAlreadyOpenWithNoPictureYetWaitsForOne() {
        val gate = PopUpGate()
        gate.request(loading = false, pictureKnown = false)
        assertFalse(gate.ready)
        gate.onPicture()
        assertTrue(gate.ready)
    }

    @Test
    fun soundOnlyHasNoPictureToWaitFor() {
        val gate = PopUpGate()
        gate.request(loading = true, pictureKnown = false)
        gate.onNoPicture()
        assertFalse(gate.ready)
        gate.onLoaded()
        gate.onNoPicture()
        assertTrue(gate.ready)
    }

    @Test
    fun aReloadOfTheSameVideoWaitsForItsPictureAgain() {
        val gate = PopUpGate()
        gate.request(loading = true, pictureKnown = false)
        gate.onLoaded()
        gate.onPicture()
        gate.onLoaded()
        assertFalse(gate.ready)
        gate.onPicture()
        assertTrue(gate.ready)
    }

    @Test
    fun nothingIsReadyWithoutARequest() {
        val gate = PopUpGate()
        gate.onLoaded()
        gate.onPicture()
        assertFalse(gate.pending)
        assertFalse(gate.ready)
        assertFalse(gate.holdsOrientation)
    }

    @Test
    fun theOrientationWaitsUntilTheWindowIsAPopUpOrStaysFull() {
        val gate = PopUpGate()
        gate.request(loading = true, pictureKnown = false)
        assertTrue(gate.holdsOrientation)
        gate.onLoaded()
        gate.onPicture()
        gate.asked()
        assertFalse(gate.pending)
        assertFalse(gate.ready)
        assertTrue(gate.entering)
        assertTrue(gate.holdsOrientation)
        gate.clear()
        assertFalse(gate.entering)
        assertFalse(gate.holdsOrientation)
    }

    @Test
    fun aNewRequestEndsAnEarlierWaitForTheWindow() {
        val gate = PopUpGate()
        gate.request(loading = false, pictureKnown = true)
        gate.asked()
        gate.request(loading = true, pictureKnown = false)
        assertFalse(gate.entering)
        assertTrue(gate.pending)
        assertFalse(gate.ready)
    }

    @Test
    fun clearForgetsWhatWasSeen() {
        val gate = PopUpGate()
        gate.request(loading = true, pictureKnown = false)
        gate.onLoaded()
        gate.onPicture()
        gate.clear()
        assertFalse(gate.ready)
        gate.request(loading = true, pictureKnown = false)
        assertFalse(gate.ready)
    }
}
