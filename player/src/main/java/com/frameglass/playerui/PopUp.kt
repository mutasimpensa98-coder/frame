package com.frameglass.playerui

import kotlin.math.roundToInt

/**
 * The shape of the picture-in-picture window. Android accepts 1:2.39 to 2.39:1 and throws on anything outside, so
 * the video's own shape is held a hair inside both ends.
 */
internal object PopUpShape {
    const val BASE = 1000
    private const val TALLEST = 420
    private const val WIDEST = 2380

    /** Width over [BASE] for a video of [aspect] (width / height), or null while no size is known. */
    fun width(aspect: Float): Int? {
        if (aspect.isNaN() || aspect <= 0f) return null
        return (aspect * BASE).roundToInt().coerceIn(TALLEST, WIDEST)
    }
}

/**
 * "Play in pop-up" opens the player and moves it into picture-in-picture by itself. The move waits for the picture
 * of the video that was asked for, so the window takes that video's shape and never the one that played before it.
 */
internal class PopUpGate {
    var pending = false
        private set

    /** The system was asked for the pop-up and the window has not reported it yet. */
    var entering = false
        private set
    private var loaded = false
    private var pictureKnown = false

    /** [loading] is false when the video asked for is the one already playing, whose picture may be [pictureKnown]. */
    fun request(loading: Boolean, pictureKnown: Boolean) {
        pending = true
        entering = false
        loaded = !loading
        this.pictureKnown = !loading && pictureKnown
    }

    /** The player took the video asked for. Any picture seen until now belonged to the one before. */
    fun onLoaded() {
        loaded = true
        pictureKnown = false
    }

    /** A frame or a size of what is loaded. With nothing loaded yet it is the old video's, and does not count. */
    fun onPicture() {
        if (loaded) pictureKnown = true
    }

    /** Ready to play with sound only: there is no picture to wait for. */
    fun onNoPicture() = onPicture()

    val ready: Boolean get() = pending && loaded && pictureKnown

    /**
     * The player wants landscape, and the full window of a pop-up shows for a moment only: turning the screen for it
     * would be all the owner sees. So the orientation waits until the window is a pop-up, or stays a full window.
     */
    val holdsOrientation: Boolean get() = pending || entering

    /** The system agreed to the pop-up. Nothing more to wait for but the window saying it is one. */
    fun asked() {
        clear()
        entering = true
    }

    /**
     * The wait for the window ran out. True when the pop-up really did not come: the request is then over. False, with
     * nothing changed, when no request is waiting any more or the window is a pop-up whose report is only late.
     */
    fun timedOut(inPopUp: Boolean): Boolean {
        if (!entering || inPopUp) return false
        clear()
        return true
    }

    /** A normal open took the place of the request. True when there was one, at either stage. */
    fun replaced(): Boolean {
        val was = pending || entering
        clear()
        return was
    }

    /** Entered, refused or no longer wanted. */
    fun clear() {
        pending = false
        entering = false
        loaded = false
        pictureKnown = false
    }
}
