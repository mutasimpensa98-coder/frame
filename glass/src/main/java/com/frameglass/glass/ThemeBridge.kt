package com.frameglass.glass

import androidx.compose.runtime.mutableIntStateOf

/** One [FrameTheme] on screen, with the palette plain reads get while it is in front. */
internal class ThemeWindow(var palette: FramePalette) {
    var published = palette
}

/**
 * What a plain [FrameColors] read gets.
 *
 * Inside :glass colours come from [FrameTheme.colors], scoped to its composition. :app and :player also
 * read [FrameColors] from top-level values and draw lambdas, where no composition local reaches, and a
 * light app can sit behind the always-dark player in one process. So a plain read follows the window
 * in front, without recomposing the window behind it while the two swap.
 *
 * Everything here runs on the main thread.
 */
internal object ThemeBridge {
    private var front: ThemeWindow? = null
    private val generation = mutableIntStateOf(0)

    val palette: FramePalette
        get() {
            // A snapshot read, so whoever reads colours, in composition or in draw, can be told to read again.
            generation.intValue
            return front?.palette ?: FramePalettes.Default
        }

    fun enter(window: ThemeWindow) {
        front = window
    }

    /**
     * A window that stops may have recomposed while the other one was in front, with that window's
     * colours. This is the moment to repair it: its recomposer is paused, so it reads again only when it
     * returns, and the window it hands over to has nothing to lose but one recomposition.
     */
    fun stopped(window: ThemeWindow) {
        val other = front ?: return
        if (other !== window && other.palette !== window.palette) generation.intValue++
    }

    fun published(window: ThemeWindow) {
        if (window.published === window.palette) return
        window.published = window.palette
        if (front === window) generation.intValue++
    }

    fun leave(window: ThemeWindow) {
        if (front === window) front = null
    }
}
