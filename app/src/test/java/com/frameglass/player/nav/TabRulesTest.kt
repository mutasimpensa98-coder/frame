package com.frameglass.player.nav

import com.frameglass.data.api.BarSection
import com.frameglass.data.api.BarSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which tab shows, where Back goes, and which drag becomes a swipe between the tabs. */
class TabRulesTest {
    private val home = BarSection.HOME
    private val folders = BarSection.FOLDERS
    private val library = BarSection.LIBRARY
    private val search = BarSection.SEARCH
    private val settings = BarSection.SETTINGS
    private val default = BarSections.Default

    @Test
    fun theSectionInViewIsTheOneWantedWhileItHasATab() {
        assertEquals(library, TabRules.shown(library, default))
        assertEquals(home, TabRules.shown(null, default))
        // Search has no tab by default, and a section that lost its tab gives way to the first one.
        assertEquals(home, TabRules.shown(search, default))
        assertEquals(folders, TabRules.shown(home, listOf(folders, library)))
        assertEquals(library, TabRules.shown(settings, listOf(library)))
    }

    @Test
    fun backGoesToTheFirstTabAndThenLeaves() {
        for (section in default.drop(1)) assertEquals(home, TabRules.backTarget(section, default))
        assertNull(TabRules.backTarget(home, default))
    }

    @Test
    fun withHomeOffBackGoesToWhateverTabIsFirst() {
        val bar = listOf(folders, library, settings)
        assertEquals(folders, TabRules.backTarget(library, bar))
        assertEquals(folders, TabRules.backTarget(settings, bar))
        assertNull(TabRules.backTarget(folders, bar))
        // One tab: Back leaves at once.
        assertNull(TabRules.backTarget(library, listOf(library)))
    }

    @Test
    fun aSectionWithNoTabOpensAsAPageOfItsOwn() {
        assertFalse(TabRules.opensAsPage(library, default))
        assertTrue(TabRules.opensAsPage(library, listOf(home, folders)))
        assertTrue(TabRules.opensAsPage(settings, listOf(home, folders, library)))
        assertTrue(TabRules.opensAsPage(search, default))
    }

    @Test
    fun aNeighbourSlidesInAndAFarTabIsCutToOneShortOfIt() {
        assertEquals(0, TabRules.runUp(from = 0, target = 1))
        assertEquals(2, TabRules.runUp(from = 2, target = 1))
        assertEquals(1, TabRules.runUp(from = 1, target = 1))
        // From the first to the fourth the pages cut to the third, and the fourth slides in from the end side.
        assertEquals(2, TabRules.runUp(from = 0, target = 3))
        assertEquals(1, TabRules.runUp(from = 4, target = 0))
        assertEquals(3, TabRules.runUp(from = 0, target = 4))
    }

    @Test
    fun noSwipeWhileSelectingOrUnderASheetOrWhileATabHoldsTheLock() {
        assertFalse(TabSwipeRules.locked(selecting = false, overlayOpen = false, tabLock = false))
        assertTrue(TabSwipeRules.locked(selecting = true, overlayOpen = false, tabLock = false))
        assertTrue(TabSwipeRules.locked(selecting = false, overlayOpen = true, tabLock = false))
        assertTrue(TabSwipeRules.locked(selecting = false, overlayOpen = false, tabLock = true))
        // A drag that starts where nothing scrolls sideways reaches the pager, which takes it unless locked.
        assertTrue(TabSwipeRules.pagerEnabled(locked = false))
        assertFalse(TabSwipeRules.pagerEnabled(locked = true))
    }

    @Test
    fun aRowThatCanMoveKeepsItsDrag() {
        val owner = TabSwipeRules.owner(SwipeOwner.Undecided, consumed = -18f, leftOver = 0f, locked = false)
        assertEquals(SwipeOwner.Row, owner)
        assertFalse(TabSwipeRules.passesToPages(owner))
    }

    @Test
    fun aRowAlreadyAtItsEndHandsADragPastThatEndToThePages() {
        val owner = TabSwipeRules.owner(SwipeOwner.Undecided, consumed = 0f, leftOver = -18f, locked = false)
        assertEquals(SwipeOwner.Pages, owner)
        assertTrue(TabSwipeRules.passesToPages(owner))
        // The same toward the other end.
        assertEquals(SwipeOwner.Pages, TabSwipeRules.owner(SwipeOwner.Undecided, 0f, 18f, locked = false))
    }

    @Test
    fun aRowThatReachesItsEndDuringTheDragDoesNotStartASwipe() {
        // First move: the row scrolls. Later moves: it is at its end and leaves everything over.
        var owner = TabSwipeRules.owner(SwipeOwner.Undecided, consumed = -30f, leftOver = 0f, locked = false)
        owner = TabSwipeRules.owner(owner, consumed = -4f, leftOver = -26f, locked = false)
        owner = TabSwipeRules.owner(owner, consumed = 0f, leftOver = -30f, locked = false)
        assertEquals(SwipeOwner.Row, owner)
        assertFalse(TabSwipeRules.passesToPages(owner))
    }

    @Test
    fun aMoveThatReachesTheEndInItsFirstStepIsStillTheRows() {
        val owner = TabSwipeRules.owner(SwipeOwner.Undecided, consumed = -3f, leftOver = -15f, locked = false)
        assertEquals(SwipeOwner.Row, owner)
    }

    @Test
    fun theFirstMoveDecidesForTheWholeGesture() {
        // The pages took the gesture; the finger turns back and the row could now move. It is still the pages'.
        var owner = TabSwipeRules.owner(SwipeOwner.Undecided, consumed = 0f, leftOver = -18f, locked = false)
        owner = TabSwipeRules.owner(owner, consumed = 12f, leftOver = 0f, locked = false)
        assertEquals(SwipeOwner.Pages, owner)
    }

    @Test
    fun roundingNoiseDecidesNothing() {
        assertEquals(SwipeOwner.Undecided, TabSwipeRules.owner(SwipeOwner.Undecided, 0f, 0f, locked = false))
        assertEquals(SwipeOwner.Undecided, TabSwipeRules.owner(SwipeOwner.Undecided, 0.2f, -0.3f, locked = false))
        // An up-and-down scroll reports no sideways move at all, and the next sideways move still decides.
        val later = TabSwipeRules.owner(SwipeOwner.Undecided, 0f, 0f, locked = false)
        assertEquals(SwipeOwner.Pages, TabSwipeRules.owner(later, 0f, 20f, locked = false))
    }

    @Test
    fun whileLockedARowAtItsEndKeepsTheDragAndThePagesStay() {
        val owner = TabSwipeRules.owner(SwipeOwner.Undecided, consumed = 0f, leftOver = -18f, locked = true)
        assertEquals(SwipeOwner.Row, owner)
        assertFalse(TabSwipeRules.passesToPages(owner))
        // The lock ending in the middle of the gesture does not start a swipe.
        assertEquals(SwipeOwner.Row, TabSwipeRules.owner(owner, 0f, -18f, locked = false))
    }

    @Test
    fun aFingerMovingLeftBringsTheNextTabAndRightToLeftItIsMirrored() {
        assertTrue(TabSwipeRules.pageward(-500f, rtl = false) > 0f)
        assertTrue(TabSwipeRules.pageward(500f, rtl = false) < 0f)
        assertTrue(TabSwipeRules.pageward(500f, rtl = true) > 0f)
        assertTrue(TabSwipeRules.pageward(-500f, rtl = true) < 0f)
        // The same fold gives the finger's direction back.
        for (rtl in listOf(false, true)) {
            assertEquals(-37f, TabSwipeRules.pageward(TabSwipeRules.pageward(-37f, rtl), rtl), 0f)
        }
    }

    @Test
    fun pagesStandingWholeLetTheRowBeAskedFirst() {
        assertEquals(0f, TabSwipeRules.pagesFirst(wanted = 40f, offset = 0f, page = 1000f), 0f)
        assertEquals(0f, TabSwipeRules.pagesFirst(wanted = -40f, offset = 0.2f, page = 1000f), 0f)
        assertEquals(0f, TabSwipeRules.pagesFirst(wanted = 40f, offset = 300f, page = 0f), 0f)
    }

    @Test
    fun pagesStandingBetweenTwoTabsTakeTheMoveAsFarAsEitherTab() {
        // 300 px on the way to the next tab of 1000.
        assertEquals(40f, TabSwipeRules.pagesFirst(wanted = 40f, offset = 300f, page = 1000f), 0f)
        assertEquals(-40f, TabSwipeRules.pagesFirst(wanted = -40f, offset = 300f, page = 1000f), 0f)
        // Turning back stops at the tab the swipe started on, and the rest is the row's.
        assertEquals(-300f, TabSwipeRules.pagesFirst(wanted = -450f, offset = 300f, page = 1000f), 0f)
        // Going on stops at the next tab.
        assertEquals(700f, TabSwipeRules.pagesFirst(wanted = 900f, offset = 300f, page = 1000f), 0f)
        // And the same on the way to the previous tab.
        assertEquals(300f, TabSwipeRules.pagesFirst(wanted = 450f, offset = -300f, page = 1000f), 0f)
        assertEquals(-700f, TabSwipeRules.pagesFirst(wanted = -900f, offset = -300f, page = 1000f), 0f)
    }
}
