package com.frameglass.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The pill of the tab bar as numbers: held by a finger or the pages, let go, and going to a tab by itself. */
class PillMotionTest {
    private val frame = 1f / 120f
    private val tolerance = 0.0001f

    /** Steps until the pill says it is done, and returns how many frames that took. */
    private fun PillMotion.settle(
        tie: PillTie,
        unit: Float,
        goal: Float,
        instant: Boolean = false,
        anchor: Any? = null,
    ): Int {
        var frames = 0
        while (step(frame, tie, unit, goal, instant, anchor)) {
            frames++
            assertTrue("still moving after $frames frames", frames < 1_000)
        }
        return frames
    }

    @Test
    fun aFingerThatMovesOnEveryFrameIsFollowedOnEveryFrame() {
        // The fault of 2026-11-22: the pill stood still for as long as the finger kept moving.
        val pill = PillMotion(0.5f)
        pill.settle(PillTie.Finger, 0.5f, 0.5f)
        var finger = 0.5f
        repeat(120) {
            finger += 0.02f
            pill.step(frame, PillTie.Finger, finger, 0.5f, instant = false)
            assertEquals(finger, pill.shown, tolerance)
            assertEquals(finger, pill.at(PillTie.Finger, finger), tolerance)
        }
    }

    @Test
    fun betweenTwoFramesThePillIsAlreadyWhereItsHolderIs() {
        // A move that arrives after the frame's step is drawn in that same frame.
        val pill = PillMotion(1.5f)
        pill.settle(PillTie.Pages, 1.5f, 1.5f)
        assertEquals(1.8f, pill.at(PillTie.Pages, 1.8f), tolerance)
        assertEquals(1.8f, pill.at(PillTie.Pages, 1.8f), tolerance)
        assertEquals(2.1f, pill.at(PillTie.Pages, 2.1f), tolerance)
    }

    @Test
    fun aFingerThatTakesThePillFromAfarIsCaughtWithoutAJumpAndNeverPassed() {
        val pill = PillMotion(0.5f)
        // Taken: the pill has not moved yet.
        assertEquals(0.5f, pill.at(PillTie.Finger, 2.5f), tolerance)
        var last = pill.shown
        var frames = 0
        while (pill.step(frame, PillTie.Finger, 2.5f, 0.5f, instant = false)) {
            assertTrue("went back at frame $frames", pill.shown >= last - tolerance)
            assertTrue("passed the finger at frame $frames", pill.shown <= 2.5f + 0.01f)
            last = pill.shown
            frames++
            assertTrue(frames < 1_000)
        }
        assertEquals(2.5f, pill.shown, tolerance)
        // FrameMotion.follow(): there in a few frames, not in a second.
        assertTrue("took $frames frames", frames in 6..60)
    }

    @Test
    fun theCatchUpGoesOnWhileTheFingerMoves() {
        val pill = PillMotion(0.5f)
        var finger = 2.5f
        pill.at(PillTie.Finger, finger)
        var gap = abs(pill.shown - finger)
        repeat(20) {
            finger += 0.01f
            pill.step(frame, PillTie.Finger, finger, 0.5f, instant = false)
            val now = abs(pill.shown - finger)
            assertTrue("the gap grew from $gap to $now", now <= gap + tolerance)
            gap = now
        }
        assertTrue("the gap is still $gap", gap < 0.5f)
    }

    @Test
    fun aChangeOfHolderIsNotAJump() {
        // The pages rest on the first tab and a finger lands on the third and drags.
        val pill = PillMotion(0.5f)
        pill.settle(PillTie.Pages, 0.5f, 0.5f)
        assertEquals(0.5f, pill.at(PillTie.Finger, 2.6f), tolerance)
        pill.settle(PillTie.Finger, 2.6f, 0.5f)
        assertEquals(2.6f, pill.shown, tolerance)
        // And back to the pages, which never left the first tab.
        assertEquals(2.6f, pill.at(PillTie.Pages, 0.5f), tolerance)
        pill.settle(PillTie.Pages, 0.5f, 0.5f)
        assertEquals(0.5f, pill.shown, tolerance)
    }

    @Test
    fun letGoThePillGoesToItsTabFromWhereItWas() {
        val pill = PillMotion(0.5f)
        pill.settle(PillTie.Finger, 1.8f, 0.5f)
        // The frame in which the finger lifts draws the pill where it was.
        assertEquals(1.8f, pill.at(PillTie.None, 1.5f), tolerance)
        assertEquals(PillTie.None, pill.tie)
        var frames = 0
        var furthest = pill.shown
        while (pill.step(frame, PillTie.None, 1.5f, 1.5f, instant = false)) {
            furthest = minOf(furthest, pill.shown)
            frames++
            assertTrue(frames < 1_000)
        }
        assertEquals(1.5f, pill.shown, 0f)
        // FrameMotion.defaultSpatial(): a small overshoot and no more.
        assertTrue("overshot to $furthest", furthest > 1.4f)
        assertTrue("took $frames frames", frames in 10..120)
    }

    @Test
    fun aHolderWithNoPlaceIsNoHolder() {
        val pill = PillMotion(0.5f)
        pill.settle(PillTie.Finger, 1.2f, 0.5f)
        assertEquals(1.2f, pill.at(PillTie.Finger, Float.NaN), tolerance)
        assertEquals(PillTie.None, pill.tie)
    }

    @Test
    fun aSpringStartedAgainOnEveryFrameStillArrives() {
        // Each step moves by its own time, whatever came before it.
        val pill = PillMotion(0.5f)
        var frames = 0
        while (pill.step(PillMotion.FirstStep, PillTie.None, 2.5f, 2.5f, instant = false)) {
            frames++
            assertTrue(frames < 1_000)
        }
        assertEquals(2.5f, pill.shown, 0f)
    }

    @Test
    fun atRestThereIsNothingMoreToDo() {
        val pill = PillMotion(1.5f)
        assertFalse(pill.step(frame, PillTie.None, 1.5f, 1.5f, instant = false))
        assertFalse(pill.step(frame, PillTie.Pages, 1.5f, 1.5f, instant = false))
        assertEquals(0f, pill.speed, 0f)
    }

    @Test
    fun aHeldPillStopsAskingForFramesOnceItsHolderIsStill() {
        val pill = PillMotion(0.5f)
        var finger = 0.5f
        repeat(30) {
            finger += 0.03f
            assertTrue(pill.step(frame, PillTie.Finger, finger, 0.5f, instant = false))
        }
        assertTrue("speed ${pill.speed}", pill.speed > 1f)
        val frames = pill.settle(PillTie.Finger, finger, 0.5f)
        assertTrue("took $frames frames", frames in 1..120)
        assertEquals(0f, pill.speed, 0f)
        assertEquals(finger, pill.shown, tolerance)
    }

    @Test
    fun theSpeedOfAHeldPillIsItsHoldersAndIsCarriedIntoTheSpring() {
        val pill = PillMotion(0.5f)
        var finger = 0.5f
        // Three units a second for half a second.
        repeat(60) {
            finger += 3f * frame
            pill.step(frame, PillTie.Finger, finger, 0.5f, instant = false)
        }
        assertEquals(3f, pill.speed, 0.2f)
        pill.at(PillTie.None, 2.5f)
        val before = pill.shown
        pill.step(frame, PillTie.None, 2.5f, 2.5f, instant = false)
        // Still going the way the finger was, at about its speed.
        assertEquals(3f * frame, pill.shown - before, 0.01f)
    }

    @Test
    fun aFlickTowardTheLastTabDoesNotThrowThePillOutOfTheBar() {
        // As fast as a finger goes, and let go a little short of the last tab's centre.
        val pill = PillMotion(0.5f)
        var finger = 0.5f
        repeat(20) {
            finger += 20f * frame
            pill.step(frame, PillTie.Finger, finger.coerceAtMost(3.4f), 0.5f, instant = false)
        }
        pill.at(PillTie.None, 3.5f)
        var furthest = pill.shown
        var frames = 0
        while (pill.step(frame, PillTie.None, 3.5f, 3.5f, instant = false)) {
            furthest = maxOf(furthest, pill.shown)
            frames++
            assertTrue(frames < 1_000)
        }
        assertTrue("went as far as $furthest", furthest < 3.5f + 1f / 15f)
        assertEquals(3.5f, pill.shown, 0f)
    }

    @Test
    fun aPillTakenInMidFlightKeepsItsPaceAndIsNotThrownBack() {
        val pill = PillMotion(0.5f)
        repeat(8) { pill.step(frame, PillTie.None, 3.5f, 3.5f, instant = false) }
        val flying = pill.shown
        assertTrue(flying > 0.6f && flying < 3.4f)
        assertEquals(flying, pill.at(PillTie.Pages, 3.5f), tolerance)
        var last = flying
        var frames = 0
        while (pill.step(frame, PillTie.Pages, 3.5f, 3.5f, instant = false)) {
            assertTrue("went back at frame $frames", pill.shown >= last - tolerance)
            last = pill.shown
            frames++
            assertTrue(frames < 1_000)
        }
        assertEquals(3.5f, pill.shown, tolerance)
    }

    @Test
    fun aHolderThatMovesToAnotherPlaceInOneStepIsCaughtUpWithAndNotJumpedTo() {
        // The tab the pill stands on is switched off while another is still leaving: its own tab is now the first.
        val pill = PillMotion(2.5f)
        pill.settle(PillTie.Tab, 2.5f, 2.5f)
        pill.at(PillTie.Tab, 2.5f, anchor = "library")
        assertEquals(2.5f, pill.at(PillTie.Tab, 0.5f, anchor = "home"), tolerance)
        assertTrue(pill.step(frame, PillTie.Tab, 0.5f, 0.5f, instant = false, anchor = "home"))
        assertTrue("at ${pill.shown}", pill.shown > 0.5f && pill.shown < 2.5f)
        // The same for the pages of a new list of tabs.
        val paged = PillMotion(2.5f)
        paged.at(PillTie.Pages, 2.5f, anchor = "library")
        assertEquals(2.5f, paged.at(PillTie.Pages, 0.5f, anchor = "home"), tolerance)
        // While the anchor stays, the holder is followed at once, however far it goes in a frame.
        assertEquals(1.7f, PillMotion(0.5f).apply { at(PillTie.Pages, 0.5f, "home") }.at(PillTie.Pages, 1.7f, "home"), tolerance)
    }

    @Test
    fun aSelectionThatCatchesUpWithASwipeMovesNothing() {
        // The pages settle on Library and then the selection becomes Library: the anchor changes under a pill
        // that is already there.
        val pill = PillMotion(0.5f)
        pill.settle(PillTie.Pages, 2.5f, 0.5f, anchor = "home")
        assertEquals(2.5f, pill.at(PillTie.Pages, 2.5f, anchor = "library"), tolerance)
        assertFalse(pill.step(frame, PillTie.Pages, 2.5f, 2.5f, instant = false, anchor = "library"))
    }

    @Test
    fun aFrameOfNoTimeMovesNothingAndBreaksNothing() {
        val pill = PillMotion(0.5f)
        pill.step(0f, PillTie.None, 2.5f, 2.5f, instant = false)
        assertEquals(0.5f, pill.shown, 0f)
        pill.at(PillTie.Finger, 1.5f)
        pill.step(0f, PillTie.Finger, 1.5f, 0.5f, instant = false)
        assertEquals(0.5f, pill.shown, tolerance)
        assertFalse(pill.shown.isNaN() || pill.speed.isNaN())
    }

    @Test
    fun whatHoldsThePillIsTheFingerThenTheTabAskedForThenThePagesThenItsOwnTab() {
        val centers = floatArrayOf(0.5f, 1.5f, 2.5f, 3.5f)
        fun hold(drag: Float = Float.NaN, pending: Boolean = false, page: Float? = null, resizing: Boolean = false) =
            PillHold.of(centers, rest = 1, restKey = "folders", drag = drag, pending = pending, page = page, resizing = resizing)

        // A finger wins over everything, and is kept between the first and the last tab.
        assertEquals(PillHold(PillTie.Finger, 3.1f, 1.5f), hold(drag = 3.1f, pending = true, page = 0f, resizing = true))
        assertEquals(3.5f, hold(drag = 9f).unit, tolerance)
        assertEquals(0.5f, hold(drag = -2f).unit, tolerance)
        // A tab the bar asked for: nothing holds the pill, though the pages are still elsewhere.
        assertEquals(PillHold(PillTie.None, 1.5f, 1.5f), hold(pending = true, page = 0f, resizing = true))
        // The pages, wherever they stand, and between two tabs.
        assertEquals(PillHold(PillTie.Pages, 3f, 1.5f, "folders"), hold(page = 2.5f, resizing = true))
        // Its own tab while the bar changes size, and nothing at all at rest with no pages.
        assertEquals(PillHold(PillTie.Tab, 1.5f, 1.5f, "folders"), hold(resizing = true))
        assertEquals(PillHold(PillTie.None, 1.5f, 1.5f), hold())
        // A bar with no tabs.
        assertEquals(PillTie.None, PillHold.of(FloatArray(0), 0, null, Float.NaN, false, 1f, false).tie)
    }

    @Test
    fun aLongFrameIsSteppedAsAShortOneAndNothingBlowsUp() {
        val pill = PillMotion(0.5f)
        pill.step(5f, PillTie.None, 3.5f, 3.5f, instant = false)
        assertTrue("at ${pill.shown}", pill.shown in 0.5f..4f)
        pill.settle(PillTie.None, 3.5f, 3.5f)
        assertEquals(3.5f, pill.shown, 0f)
    }

    @Test
    fun withReducedMotionAHeldPillHasNoLagAndAFreeOneDoesNotOvershoot() {
        val pill = PillMotion(0.5f)
        pill.at(PillTie.Finger, 2.5f)
        pill.step(frame, PillTie.Finger, 2.5f, 0.5f, instant = true)
        assertEquals(2.5f, pill.shown, tolerance)
        assertEquals(0f, pill.speed, 0f)
        pill.at(PillTie.None, 1.5f)
        var lowest = pill.shown
        var frames = 0
        while (pill.step(frame, PillTie.None, 1.5f, 1.5f, instant = true)) {
            lowest = minOf(lowest, pill.shown)
            frames++
            assertTrue(frames < 1_000)
        }
        assertTrue("overshot to $lowest", lowest > 1.49f)
        assertEquals(1.5f, pill.shown, 0f)
    }
}
