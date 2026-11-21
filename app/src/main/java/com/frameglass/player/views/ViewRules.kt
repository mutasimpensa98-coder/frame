package com.frameglass.player.views

import com.frameglass.data.api.ViewSize
import com.frameglass.glass.CardDensity

/**
 * What a cell of a list says at one view size. The picture, its progress line, its watched mark and the mark of a
 * picked item are there at every size, so they are not listed.
 */
internal data class CellFace(
    /** The name is written, under the picture or on it. */
    val name: Boolean,
    /** One line of facts: the year, or a folder's count and size. */
    val facts: Boolean,
    /** What only the roomy sizes add: the genre beside the year in a large grid, the fact chips of a list row. */
    val moreFacts: Boolean,
    /** The rating badge. */
    val rating: Boolean,
)

/**
 * The view sizes with no screen (owner request of 2026-11-21): how many cells fit a line, and what a cell says. A
 * poster is 2:3 art that carries its own title; a frame is the 16:9 picture of a video or a folder, which does not.
 */
internal object ViewRules {
    /** Cells to a line. A list has one, and a frame is wide, so it takes one column fewer than a poster. */
    fun columns(size: ViewSize, frames: Boolean): Int = when (size) {
        ViewSize.COMPACT_LIST, ViewSize.LIST -> 1
        ViewSize.SMALL_GRID -> if (frames) 3 else 4
        ViewSize.MEDIUM_GRID -> if (frames) 2 else 3
        ViewSize.LARGE_GRID -> if (frames) 1 else 2
    }

    /**
     * One content type per look, so a lazy list never hands a cell of one size to another. The two lists differ, and
     * a grid of posters is not a grid of frames. [kind] tells titles from folders, which are different cells.
     */
    fun contentType(kind: String, size: ViewSize, frames: Boolean): String = when (size) {
        ViewSize.COMPACT_LIST -> "$kind:compact"
        // The thumbnail of a list row follows the shape of the art, so the row does too.
        ViewSize.LIST -> if (frames) "$kind:list:frame" else "$kind:list:poster"
        else -> "$kind:${size.name}:" + if (frames) "frame" else "poster"
    }

    /** How much a grid card says. The lists draw their own rows and do not use it. */
    fun density(size: ViewSize): CardDensity = when (size) {
        ViewSize.SMALL_GRID -> CardDensity.Small
        ViewSize.LARGE_GRID -> CardDensity.Large
        else -> CardDensity.Medium
    }

    fun face(size: ViewSize, frames: Boolean): CellFace = when (size) {
        ViewSize.COMPACT_LIST -> CellFace(name = true, facts = true, moreFacts = false, rating = true)
        ViewSize.LIST -> CellFace(name = true, facts = true, moreFacts = true, rating = true)
        // A small poster is the picture alone. A small frame keeps its name, because a frame of a video names nothing.
        ViewSize.SMALL_GRID -> CellFace(name = frames, facts = false, moreFacts = false, rating = false)
        ViewSize.MEDIUM_GRID -> CellFace(name = true, facts = true, moreFacts = false, rating = true)
        ViewSize.LARGE_GRID -> CellFace(name = true, facts = true, moreFacts = true, rating = true)
    }

    /** The line under the title of a large card: the year, then the first genre. Null when neither is known. */
    fun largeFacts(year: String?, genre: String?): List<String>? =
        listOfNotNull(year, genre?.takeIf { it.isNotBlank() }).takeIf { it.isNotEmpty() }
}
