package com.frameglass.player.select

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The state one screen keeps while the owner picks: when the mode starts, what it holds, and when it ends. */
class SelectionStateTest {
    private val film = SelectItem.Title(3, "Paper Lanterns", onStorage = true, watched = false)
    private val series = SelectItem.Title(7, "Night Harbour", onStorage = true, watched = false)
    private val clip = SelectItem.Title(11, "Beach day", onStorage = true, watched = false)
    private val selection = SelectionState<SelectItem> { it.key }

    @Test
    fun itStartsOutsideTheMode() {
        assertFalse(selection.active)
        assertEquals(0, selection.count)
        assertFalse(selection.isPicked(film))
    }

    @Test
    fun selectFromTheMenuEntersTheModeWithThatCardPicked() {
        selection.start(film)
        assertTrue(selection.active)
        assertTrue(selection.isPicked(film))
        assertEquals(listOf<SelectItem>(film), selection.items)
    }

    @Test
    fun tapsPickAndLetGoAndTheLastOneLeavesTheMode() {
        selection.start(film)
        selection.toggle(series)
        assertEquals(2, selection.count)
        selection.toggle(film)
        assertTrue(selection.active)
        assertEquals(listOf<SelectItem>(series), selection.items)
        selection.toggle(series)
        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun enteringWithNothingPickedStaysOpenForTheFirstPick() {
        selection.enter()
        assertTrue(selection.active)
        assertEquals(0, selection.count)
        selection.toggle(film)
        assertTrue(selection.active)
        assertTrue(selection.isPicked(film))
    }

    @Test
    fun selectAllPicksTheWholeListAndAnEmptyListDoesNothing() {
        selection.selectAll(emptyList())
        assertFalse(selection.active)
        selection.start(series)
        selection.selectAll(listOf(film, series, clip))
        assertEquals(listOf<SelectItem>(series, film, clip), selection.items)
    }

    @Test
    fun clearAllKeepsTheModeAndCloseLeavesIt() {
        selection.start(film)
        selection.clearPicks()
        assertTrue(selection.active)
        assertEquals(0, selection.count)
        selection.toggle(clip)
        selection.clear()
        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun aPickThatLeavesTheListIsDropped() {
        selection.start(film)
        selection.toggle(series)
        selection.retain(listOf(series, clip))
        assertTrue(selection.active)
        assertEquals(listOf<SelectItem>(series), selection.items)
    }

    @Test
    fun whenTheLastPickLeavesTheListTheModeEnds() {
        selection.start(film)
        selection.retain(listOf(clip))
        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun aListUpdateAfterClearAllDoesNotEndTheMode() {
        selection.start(film)
        selection.clearPicks()
        selection.retain(listOf(clip))
        assertTrue(selection.active)
    }

    @Test
    fun aPickFollowsItsCardWhenTheCardChanges() {
        selection.start(film)
        val watched = film.copy(watched = true)
        selection.retain(listOf(watched, clip))
        assertEquals(listOf<SelectItem>(watched), selection.items)
        assertTrue(selection.isPicked(film))
    }
}
