package com.frameglass.player.views

import com.frameglass.data.api.ViewSize
import com.frameglass.glass.CardDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The five view sizes with no screen: cells to a line, what a cell says, and which cells a lazy list may reuse. */
class ViewRulesTest {
    private fun columns(frames: Boolean) = ViewSize.entries.map { ViewRules.columns(it, frames) }

    @Test
    fun postersGoOneOneFourThreeTwoToALine() {
        assertEquals(listOf(1, 1, 4, 3, 2), columns(frames = false))
    }

    @Test
    fun framesGoOneOneThreeTwoOneToALine() {
        assertEquals(listOf(1, 1, 3, 2, 1), columns(frames = true))
    }

    @Test
    fun theMediumGridIsTheGridTheAppAlwaysHad() {
        assertEquals(3, ViewRules.columns(ViewSize.MEDIUM_GRID, frames = false))
        assertEquals(2, ViewRules.columns(ViewSize.MEDIUM_GRID, frames = true))
        assertEquals(CardDensity.Medium, ViewRules.density(ViewSize.MEDIUM_GRID))
    }

    @Test
    fun aLargerGridNeverHoldsMoreToALine() {
        for (frames in listOf(false, true)) {
            val grids = listOf(ViewSize.SMALL_GRID, ViewSize.MEDIUM_GRID, ViewSize.LARGE_GRID)
                .map { ViewRules.columns(it, frames) }
            assertEquals(grids.sortedDescending(), grids)
            assertTrue(grids.all { it >= 1 })
        }
    }

    @Test
    fun aFrameIsWideSoItTakesOneColumnFewerThanAPoster() {
        for (size in listOf(ViewSize.SMALL_GRID, ViewSize.MEDIUM_GRID, ViewSize.LARGE_GRID)) {
            assertEquals(ViewRules.columns(size, frames = false) - 1, ViewRules.columns(size, frames = true))
        }
    }

    @Test
    fun onlyTheGridsChangeHowMuchACardSays() {
        assertEquals(CardDensity.Small, ViewRules.density(ViewSize.SMALL_GRID))
        assertEquals(CardDensity.Large, ViewRules.density(ViewSize.LARGE_GRID))
    }

    @Test
    fun aSmallPosterIsThePictureAlone() {
        val face = ViewRules.face(ViewSize.SMALL_GRID, frames = false)
        assertEquals(CellFace(name = false, facts = false, moreFacts = false, rating = false), face)
    }

    @Test
    fun aSmallFrameKeepsItsNameBecauseAFrameNamesNothing() {
        val face = ViewRules.face(ViewSize.SMALL_GRID, frames = true)
        assertTrue(face.name)
        assertFalse(face.facts)
        assertFalse(face.rating)
    }

    @Test
    fun theMediumGridShowsTheNameTheYearAndTheRating() {
        for (frames in listOf(false, true)) {
            val face = ViewRules.face(ViewSize.MEDIUM_GRID, frames)
            assertEquals(CellFace(name = true, facts = true, moreFacts = false, rating = true), face)
        }
    }

    @Test
    fun theLargeGridAddsOneMoreLineOfFacts() {
        val large = ViewRules.face(ViewSize.LARGE_GRID, frames = false)
        assertEquals(ViewRules.face(ViewSize.MEDIUM_GRID, frames = false).copy(moreFacts = true), large)
    }

    @Test
    fun theCompactListHasOneLineOfFactsAndTheListHasItsChips() {
        val compact = ViewRules.face(ViewSize.COMPACT_LIST, frames = false)
        assertTrue(compact.name && compact.facts && compact.rating)
        assertFalse(compact.moreFacts)
        assertTrue(ViewRules.face(ViewSize.LIST, frames = true).moreFacts)
    }

    @Test
    fun everySizeButTheSmallPosterWritesTheName() {
        for (size in ViewSize.entries) {
            for (frames in listOf(false, true)) {
                val pictureOnly = size == ViewSize.SMALL_GRID && !frames
                assertEquals(!pictureOnly, ViewRules.face(size, frames).name)
            }
        }
    }

    @Test
    fun theLineOfALargeCardIsTheYearThenTheGenre() {
        assertEquals(listOf("2024", "Drama"), ViewRules.largeFacts("2024", "Drama"))
        assertEquals(listOf("2024"), ViewRules.largeFacts("2024", null))
        assertEquals(listOf("2024"), ViewRules.largeFacts("2024", " "))
        assertEquals(listOf("Drama"), ViewRules.largeFacts(null, "Drama"))
        assertNull(ViewRules.largeFacts(null, null))
    }

    @Test
    fun noTwoLooksShareAContentType() {
        val types = ViewSize.entries.flatMap { size ->
            listOf(false, true).map { frames -> ViewRules.contentType("title", size, frames) }
        }
        // The compact row is the same for a poster and a frame; every other look is its own.
        assertEquals(9, types.toSet().size)
        assertEquals(
            ViewRules.contentType("title", ViewSize.COMPACT_LIST, frames = false),
            ViewRules.contentType("title", ViewSize.COMPACT_LIST, frames = true),
        )
    }

    @Test
    fun aTitleCellIsNeverReusedForAFolder() {
        for (size in ViewSize.entries) {
            val title = ViewRules.contentType("title", size, frames = true)
            val folder = ViewRules.contentType("folder", size, frames = true)
            assertFalse(title == folder)
        }
    }

    @Test
    fun aChangeOfSizeChangesTheContentType() {
        for (frames in listOf(false, true)) {
            val types = ViewSize.entries.map { ViewRules.contentType("title", it, frames) }
            assertEquals(types.size, types.toSet().size)
        }
    }
}
