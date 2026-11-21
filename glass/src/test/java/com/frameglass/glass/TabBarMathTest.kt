package com.frameglass.glass

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tab bar as numbers: its width per count, the pill under a finger in both directions, ticks and colours. */
class TabBarMathTest {
    private val tolerance = 0.001f

    // A 412 dp phone at 1 px per dp: the bar is given 364 and pads 10 at each end.
    private val full = 364f
    private val padding = 10f
    private val cap = 96f
    private val slot4 = (full - 2 * padding) / 4f

    private fun rest(count: Int) = TabBarMath.centers(FloatArray(count) { 1f })

    @Test
    fun fourAndFiveTabsSpanTheWidth() {
        assertEquals(full, TabBarMath.barWidth(4f, full, padding, cap), tolerance)
        assertEquals(full, TabBarMath.barWidth(5f, full, padding, cap), tolerance)
        assertEquals(slot4, TabBarMath.slotWidth(4f, full, padding), tolerance)
        assertEquals((full - 2 * padding) / 5f, TabBarMath.slotWidth(5f, full, padding), tolerance)
    }

    @Test
    fun threeAndTwoTabsAreAShorterCapsuleWithTheSlotOfTheFourTabBar() {
        val three = TabBarMath.barWidth(3f, full, padding, cap)
        val two = TabBarMath.barWidth(2f, full, padding, cap)
        assertEquals(2 * padding + 3 * slot4, three, tolerance)
        assertEquals(2 * padding + 2 * slot4, two, tolerance)
        assertEquals(slot4, TabBarMath.slotWidth(3f, three, padding), tolerance)
        assertEquals(slot4, TabBarMath.slotWidth(2f, two, padding), tolerance)
        assertTrue(two < three && three < full)
    }

    @Test
    fun onAWideScreenTheShortCapsuleIsCappedAndTheFullBarIsNot() {
        val wide = 900f
        assertEquals(wide, TabBarMath.barWidth(4f, wide, padding, cap), tolerance)
        assertEquals(2 * padding + 3 * cap, TabBarMath.barWidth(3f, wide, padding, cap), tolerance)
        assertEquals(2 * padding + 2 * cap, TabBarMath.barWidth(2f, wide, padding, cap), tolerance)
    }

    @Test
    fun theWidthNeverJumpsWhileATabArrivesOrLeaves() {
        for (given in listOf(full, 900f)) {
            var last = TabBarMath.barWidth(1f, given, padding, cap)
            var count = 1f
            while (count <= 5f) {
                val width = TabBarMath.barWidth(count, given, padding, cap)
                // One step of 0.01 tabs never moves the edge by more than a hundredth of the widest step.
                assertTrue("count $count on $given", width >= last - tolerance && width - last <= given * 0.011f)
                assertTrue(width <= given + tolerance)
                last = width
                count += 0.01f
            }
        }
    }

    @Test
    fun centresOfTabsAtRestAreHalfSlotsAndAShrinkingTabTakesLessRoom() {
        assertArrayEquals(floatArrayOf(0.5f, 1.5f, 2.5f, 3.5f), rest(4), tolerance)
        // The second of four is halfway out: it is half a slot wide, and the ones after it have moved up.
        assertArrayEquals(floatArrayOf(0.5f, 1.25f, 2f, 3f), TabBarMath.centers(floatArrayOf(1f, 0.5f, 1f, 1f)), tolerance)
        // A tab with no width yet changes nothing for the others.
        assertArrayEquals(floatArrayOf(0.5f, 1f, 1.5f), TabBarMath.centers(floatArrayOf(1f, 0f, 1f)), tolerance)
    }

    @Test
    fun aFingerAndThePillAgreeLeftToRight() {
        val centers = rest(4)
        for (index in 0..3) {
            val x = padding + slot4 * (index + 0.5f)
            val unit = TabBarMath.unit(TabBarMath.fromStart(x, full, rtl = false), slot4, padding)
            assertEquals(centers[index], unit, tolerance)
            assertEquals(index, TabBarMath.nearest(unit, centers))
            // And back: the pill drawn for that unit is under the finger.
            assertEquals(x, TabBarMath.fromStart(TabBarMath.pixel(unit, slot4, padding), full, rtl = false), tolerance)
        }
    }

    @Test
    fun rightToLeftTheFirstTabIsUnderAFingerAtTheRightEnd() {
        val centers = rest(4)
        for (index in 0..3) {
            val x = full - padding - slot4 * (index + 0.5f)
            val unit = TabBarMath.unit(TabBarMath.fromStart(x, full, rtl = true), slot4, padding)
            assertEquals(centers[index], unit, tolerance)
            assertEquals(index, TabBarMath.nearest(unit, centers))
            assertEquals(x, TabBarMath.fromStart(TabBarMath.pixel(unit, slot4, padding), full, rtl = true), tolerance)
        }
        // A finger moving right moves the pill toward the first tab.
        val left = TabBarMath.unit(TabBarMath.fromStart(100f, full, rtl = true), slot4, padding)
        val right = TabBarMath.unit(TabBarMath.fromStart(200f, full, rtl = true), slot4, padding)
        assertTrue(right < left)
    }

    @Test
    fun theFingerPositionIsFractionalBetweenTabs() {
        val x = padding + slot4 * 1.75f
        assertEquals(1.75f, TabBarMath.unit(TabBarMath.fromStart(x, full, rtl = false), slot4, padding), tolerance)
        assertEquals(1.75f, TabBarMath.unit(TabBarMath.fromStart(full - x, full, rtl = true), slot4, padding), tolerance)
    }

    @Test
    fun thePillStopsAtTheCentresOfTheEndTabs() {
        val centers = rest(4)
        assertEquals(0.5f, TabBarMath.clamp(TabBarMath.unit(0f, slot4, padding), centers), tolerance)
        assertEquals(0.5f, TabBarMath.clamp(-3f, centers), tolerance)
        assertEquals(3.5f, TabBarMath.clamp(TabBarMath.unit(full, slot4, padding), centers), tolerance)
        assertEquals(2.2f, TabBarMath.clamp(2.2f, centers), tolerance)
        assertEquals(0f, TabBarMath.clamp(2.2f, FloatArray(0)), tolerance)
    }

    @Test
    fun aPagePositionBecomesAUnitAndIsHeldToThePages() {
        val centers = rest(4)
        assertEquals(0.5f, TabBarMath.unitAt(0f, centers), tolerance)
        assertEquals(1.9f, TabBarMath.unitAt(1.4f, centers), tolerance)
        assertEquals(3.5f, TabBarMath.unitAt(3f, centers), tolerance)
        assertEquals(3.5f, TabBarMath.unitAt(7f, centers), tolerance)
        assertEquals(0.5f, TabBarMath.unitAt(-1f, centers), tolerance)
        // While the second tab is half gone, halfway between the first and the second is between their centres.
        val uneven = TabBarMath.centers(floatArrayOf(1f, 0.5f, 1f))
        assertEquals((0.5f + 1.25f) / 2f, TabBarMath.unitAt(0.5f, uneven), tolerance)
    }

    @Test
    fun theNearestTabChangesAtTheMidpoint() {
        val centers = rest(4)
        assertEquals(0, TabBarMath.nearest(0.99f, centers))
        assertEquals(1, TabBarMath.nearest(1.01f, centers))
        assertEquals(3, TabBarMath.nearest(9f, centers))
        assertEquals(0, TabBarMath.nearest(-9f, centers))
    }

    @Test
    fun oneTickPerTabCrossedAndNoneForATremorOnTheMidpoint() {
        val centers = rest(4)
        var last = 0
        var ticks = 0
        fun moveTo(unit: Float) {
            val next = TabBarMath.tick(last, unit, centers)
            if (next != last) {
                ticks++
                last = next
            }
        }
        // Across the whole bar and back.
        var unit = 0.5f
        while (unit <= 3.5f) {
            moveTo(unit)
            unit += 0.01f
        }
        assertEquals(3, ticks)
        assertEquals(3, last)
        while (unit >= 0.5f) {
            moveTo(unit)
            unit -= 0.01f
        }
        assertEquals(6, ticks)
        assertEquals(0, last)
        // A finger trembling on the line between the first two tabs.
        ticks = 0
        repeat(50) { step -> moveTo(1f + if (step % 2 == 0) 0.05f else -0.05f) }
        assertEquals(0, ticks)
        // Past the line by more than the hysteresis it ticks once, and trembling there adds nothing.
        repeat(50) { step -> moveTo(1.12f + if (step % 2 == 0) 0.03f else -0.03f) }
        assertEquals(1, ticks)
        assertEquals(1, last)
    }

    @Test
    fun aTickAfterTheTabsChangedStartsFromTheNearestOne() {
        assertEquals(1, TabBarMath.tick(last = 7, unit = 1.4f, centers = rest(3)))
        assertEquals(2, TabBarMath.tick(last = 2, unit = 1.4f, centers = FloatArray(0)))
    }

    @Test
    fun aTabTakesThePillColourByHowMuchOfItThePillCovers() {
        // Under the pill, and a little to either side, the tab is fully covered.
        assertEquals(1f, TabBarMath.coverage(1.5f, 1.5f), tolerance)
        assertEquals(1f, TabBarMath.coverage(1.7f, 1.5f), tolerance)
        // A pill resting on a neighbour does not touch it.
        assertEquals(0f, TabBarMath.coverage(2.5f, 1.5f), tolerance)
        assertEquals(0f, TabBarMath.coverage(0.5f, 1.5f), tolerance)
        // Halfway between two tabs both are partly covered, and equally.
        val left = TabBarMath.coverage(2f, 1.5f)
        val right = TabBarMath.coverage(2f, 2.5f)
        assertEquals(left, right, tolerance)
        assertTrue(left > 0f && left < 1f)
        // It only ever grows as the pill comes nearer.
        var last = 0f
        var unit = 2.5f
        while (unit >= 1.5f) {
            val now = TabBarMath.coverage(unit, 1.5f)
            assertTrue(now >= last - tolerance)
            last = now
            unit -= 0.01f
        }
    }

    @Test
    fun thePillStretchesWithSpeedUpToItsLimit() {
        assertEquals(0f, TabBarMath.stretch(0f), tolerance)
        assertEquals(FrameMotion.PillStretch / 2f, TabBarMath.stretch(TabBarMath.FullStretchSpeed / 2f), tolerance)
        assertEquals(FrameMotion.PillStretch, TabBarMath.stretch(40f), tolerance)
        // The same for a move toward the start.
        assertEquals(FrameMotion.PillStretch, TabBarMath.stretch(-40f), tolerance)
    }

    @Test
    fun labelsKeepTheirSizeWhileTheWidestFitsAndShrinkTogetherWhenItDoesNot() {
        // Four tabs on the phone: 86 px slots, and the widest Arabic label measured at about 56.
        assertEquals(1f, TabBarMath.labelScale(slot = 86f, natural = 56f, gutter = 3f), tolerance)
        // Five tabs: 68.8 px slots still hold it.
        assertEquals(1f, TabBarMath.labelScale(slot = 68.8f, natural = 56f, gutter = 3f), tolerance)
        // A larger font makes it 70 wide: every label is drawn a little smaller, and still whole.
        val scale = TabBarMath.labelScale(slot = 68.8f, natural = 70f, gutter = 3f)
        assertEquals(62.8f / 70f, scale, tolerance)
        assertEquals(1f, TabBarMath.labelAlpha(scale), tolerance)
        assertEquals(1f, TabBarMath.labelScale(slot = 60f, natural = 0f, gutter = 3f), tolerance)
    }

    @Test
    fun labelsThatWouldBeTooSmallToReadAreHiddenAndNeverCut() {
        // 100 px of label in a 68.8 px slot would need 63%: below the limit, so the icons stand alone.
        val scale = TabBarMath.labelScale(slot = 68.8f, natural = 100f, gutter = 3f)
        assertTrue(scale < TabBarMath.MinLabelScale)
        assertEquals(0f, TabBarMath.labelAlpha(scale), tolerance)
        // They fade over a short band above the limit, so a bar that is changing width does not blink.
        assertEquals(0f, TabBarMath.labelAlpha(TabBarMath.MinLabelScale), tolerance)
        assertEquals(0.5f, TabBarMath.labelAlpha(TabBarMath.MinLabelScale + TabBarMath.LabelFade / 2f), tolerance)
        assertEquals(1f, TabBarMath.labelAlpha(TabBarMath.MinLabelScale + TabBarMath.LabelFade), tolerance)
    }
}
