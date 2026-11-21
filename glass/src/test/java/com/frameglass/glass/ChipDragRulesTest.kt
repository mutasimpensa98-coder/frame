package com.frameglass.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** A chip dragged along the row of Library's filters, as numbers along the row, with no layout. */
class ChipDragRulesTest {
    private val gap = 8f

    /** A row of chips of [sizes] laid out from 0 with [gap] between them, as (start, size). */
    private fun row(sizes: List<Float>): List<Pair<Float, Float>> {
        var at = 0f
        return sizes.map { size -> (at to size).also { at += size + gap } }
    }

    private fun middle(slot: Pair<Float, Float>) = slot.first + slot.second / 2f

    /** The step for the chip at [index] of [sizes] drawn at [start]. */
    private fun step(sizes: List<Float>, index: Int, start: Float): Int {
        val slots = row(sizes)
        return ChipDragRules.step(
            start = start,
            size = sizes[index],
            beforeMiddle = slots.getOrNull(index - 1)?.let(::middle),
            afterMiddle = slots.getOrNull(index + 1)?.let(::middle),
        )
    }

    @Test
    fun aChipThatWasOnlyLiftedStaysWhereItIs() {
        val sizes = listOf(60f, 140f, 60f, 90f)
        row(sizes).forEachIndexed { index, slot -> assertEquals(0, step(sizes, index, slot.first)) }
    }

    @Test
    fun aChipTradesWithTheNextWhenItsFarEdgePassesThatChipsMiddle() {
        val sizes = listOf(60f, 100f)
        // The next chip lies at 68..168, so its middle is 118: the far edge of the dragged chip must pass it.
        assertEquals(0, step(sizes, 0, start = 57f))
        assertEquals(0, step(sizes, 0, start = 58f))
        assertEquals(1, step(sizes, 0, start = 58.5f))
    }

    @Test
    fun aChipTradesWithTheOneBeforeWhenItsNearEdgePassesThatChipsMiddle() {
        val sizes = listOf(100f, 60f)
        // The chip before lies at 0..100, middle 50.
        assertEquals(0, step(sizes, 1, start = 50f))
        assertEquals(-1, step(sizes, 1, start = 49.5f))
    }

    @Test
    fun theEndsOfTheRowHaveNoNeighbourToTradeWith() {
        val sizes = listOf(60f, 60f)
        assertEquals(0, step(sizes, 0, start = -500f))
        assertEquals(0, step(sizes, 1, start = 500f))
    }

    @Test
    fun chipsOfDifferentWidthsNeverTradeBackAndForth() {
        // Every pair of widths, the narrow one dragged over the wide one and the other way round.
        val widths = listOf(40f, 60f, 100f, 180f)
        for (dragged in widths) {
            for (other in widths) {
                val before = listOf(dragged, other)
                // The smallest move that trades: the far edge just past the other chip's middle.
                val start = dragged + gap + other / 2f - dragged + 0.5f
                assertEquals(1, step(before, 0, start))
                // The row lays the trade out: the other chip first, the dragged one after it, drawn where it was.
                val after = listOf(other, dragged)
                assertEquals("$dragged over $other", 0, step(after, 1, start))
                // And the same toward the start.
                val back = other / 2f - 0.5f
                assertEquals(-1, step(after, 1, back))
                assertEquals("$dragged back over $other", 0, step(before, 0, back))
            }
        }
    }

    @Test
    fun aLongDragCrossesOneChipAtATime() {
        // Dragged from the first place to the last in small moves: each trade is with the next chip, in order.
        val sizes = mutableListOf(70f, 50f, 120f, 60f)
        var index = 0
        var start = 0f
        val places = mutableListOf(0)
        while (start < 400f) {
            start += 3f
            when (step(sizes, index, start)) {
                1 -> {
                    sizes.add(index + 1, sizes.removeAt(index))
                    index += 1
                    places += index
                }
                -1 -> throw AssertionError("traded back at $start")
                else -> Unit
            }
        }
        assertEquals(listOf(0, 1, 2, 3), places)
        assertEquals(listOf(50f, 120f, 60f, 70f), sizes)
    }

    @Test
    fun aChipInsideTheRowDoesNotScrollIt() {
        assertEquals(0f, ChipDragRules.overhang(start = 10f, size = 60f, viewportStart = -16f, viewportEnd = 400f))
        assertEquals(0f, ChipDragRules.overhang(start = -16f, size = 416f, viewportStart = -16f, viewportEnd = 400f))
    }

    @Test
    fun aChipPushedPastAnEdgeSticksOutByThatMuch() {
        assertEquals(25f, ChipDragRules.overhang(start = 365f, size = 60f, viewportStart = -16f, viewportEnd = 400f))
        assertEquals(-14f, ChipDragRules.overhang(start = -30f, size = 60f, viewportStart = -16f, viewportEnd = 400f))
    }

    @Test
    fun theRowScrollsFasterTheFurtherTheChipIsOutUpToItsFullSpeed() {
        assertEquals(0f, ChipDragRules.edgeSpeed(0f, zone = 48f, fullSpeed = 1_000f))
        assertEquals(500f, ChipDragRules.edgeSpeed(24f, zone = 48f, fullSpeed = 1_000f))
        assertEquals(1_000f, ChipDragRules.edgeSpeed(48f, zone = 48f, fullSpeed = 1_000f))
        assertEquals(1_000f, ChipDragRules.edgeSpeed(300f, zone = 48f, fullSpeed = 1_000f))
        // Toward the start the speed is negative, which is a scroll back along the row in either reading direction.
        assertEquals(-250f, ChipDragRules.edgeSpeed(-12f, zone = 48f, fullSpeed = 1_000f))
        assertEquals(-1_000f, ChipDragRules.edgeSpeed(-90f, zone = 48f, fullSpeed = 1_000f))
        assertEquals(0f, ChipDragRules.edgeSpeed(30f, zone = 0f, fullSpeed = 1_000f))
    }

    @Test
    fun aMoveIsOneItemTakenOutAndPutBackIn() {
        val order = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), ChipDragRules.moved(order, from = 0, to = 2))
        assertEquals(listOf("d", "a", "b", "c"), ChipDragRules.moved(order, from = 3, to = 0))
        assertSame(order, ChipDragRules.moved(order, from = 1, to = 1))
        assertSame(order, ChipDragRules.moved(order, from = -1, to = 2))
        assertSame(order, ChipDragRules.moved(order, from = 1, to = 4))
    }

    @Test
    fun tradesWithNeighboursAddUpToOneMove() {
        // What the row does during a drag, one trade at a time, is what the caller is told once at the drop.
        val order = listOf("a", "b", "c", "d", "e")
        var live = order
        for (step in 1..3) live = ChipDragRules.moved(live, step - 1, step)
        assertEquals(ChipDragRules.moved(order, from = 0, to = 3), live)
    }
}
