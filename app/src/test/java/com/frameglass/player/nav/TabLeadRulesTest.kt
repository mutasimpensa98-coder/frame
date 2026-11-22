package com.frameglass.player.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who the pages follow: a hand, or a request from the bar. Each test is a moment the review of 2026-11-22 found, or
 * one next to it, written as the state the rules are asked about.
 */
class TabLeadRulesTest {
    private val home = 0
    private val library = 2
    private val settings = 3

    /** Pages standing whole on [page] with nothing touching them. */
    private fun resting(page: Int) =
        PagesNow(page = page, whole = true, scrolling = false, handed = false, fingerDown = false, led = false)

    /** Pages between two tabs, nearest to [page], with nothing moving them. */
    private fun adrift(page: Int) = resting(page).copy(whole = false)

    @Test
    fun aSwipeThatSettledOnAnotherTabIsReported() {
        assertEquals(library, TabLeadRules.report(resting(library), asked = false, selected = home))
        assertNull(TabLeadRules.report(resting(home), asked = false, selected = home))
    }

    @Test
    fun nothingIsReportedWhileThePagesMoveOrStandBetweenTabs() {
        val flinging = resting(library).copy(scrolling = true)
        val handed = resting(library).copy(handed = true)
        assertNull(TabLeadRules.report(flinging, asked = false, selected = home))
        assertNull(TabLeadRules.report(handed, asked = false, selected = home))
        assertNull(TabLeadRules.report(adrift(library), asked = false, selected = home))
    }

    @Test
    fun pagesStillOnTheTabTheyWereAskedToLeaveAreNotReported() {
        // The tap on Settings has changed the selection, and its move has not begun, or was cut short at once.
        // Reporting Home here is what undid a quick second tap.
        assertNull(TabLeadRules.report(resting(home), asked = true, selected = settings))
        assertNull(TabLeadRules.report(resting(home).copy(led = true), asked = true, selected = settings))
    }

    @Test
    fun aRequestWhoseMoveDiedIsStillBroughtToItsTab() {
        // Whole on Home, no move under way, Settings asked for: the guard takes the pages there.
        assertEquals(settings, TabLeadRules.guard(resting(home), asked = true, selected = settings))
        // Cut short between Library and Settings with the pill on Home: the pages go to Home.
        assertEquals(home, TabLeadRules.guard(adrift(library), asked = true, selected = home))
        // And once they are there, nothing is left to do.
        assertNull(TabLeadRules.guard(resting(settings), asked = true, selected = settings))
    }

    @Test
    fun pagesLeftBetweenTwoTabsByAHandGoToTheNearerOne() {
        assertEquals(library, TabLeadRules.guard(adrift(library), asked = false, selected = home))
        assertNull(TabLeadRules.guard(resting(library), asked = false, selected = home))
    }

    @Test
    fun theGuardStandsDownWhileARequestMovesOrAFingerIsDownOrAnythingMovesThePages() {
        val wrong = adrift(library)
        assertNull(TabLeadRules.guard(wrong.copy(led = true), asked = true, selected = home))
        assertNull(TabLeadRules.guard(wrong.copy(fingerDown = true), asked = true, selected = home))
        assertNull(TabLeadRules.guard(wrong.copy(scrolling = true), asked = true, selected = home))
        assertNull(TabLeadRules.guard(wrong.copy(handed = true), asked = false, selected = home))
        // Its own slide is a scroll too: while it runs it asks for nothing more, and it is not what stops it.
        assertNull(TabLeadRules.guard(wrong.copy(scrolling = true), asked = false, selected = home))
    }

    @Test
    fun aHandThatTakesThePagesEndsARequest() {
        val dragging = adrift(library).copy(fingerDown = true, scrolling = true)
        assertTrue(TabLeadRules.handTook(dragging))
        assertTrue(TabLeadRules.handTook(adrift(library).copy(fingerDown = true, handed = true)))
        assertFalse(TabLeadRules.stillAsked(asked = true, dragging, selected = home))
        // Where it lets go is then reported like any swipe.
        assertEquals(library, TabLeadRules.report(resting(library), asked = false, selected = home))
    }

    @Test
    fun aFingerAloneOrAMoveWithNoFingerTakesNothing() {
        // A finger resting on the pages has moved nothing.
        assertFalse(TabLeadRules.handTook(resting(home).copy(fingerDown = true)))
        // A fling, the guard's slide or the pager's own snap with no finger down is not a hand.
        assertFalse(TabLeadRules.handTook(adrift(library).copy(scrolling = true)))
        // While a request moves the pages a finger elsewhere on them does not end it by this rule.
        assertFalse(TabLeadRules.handTook(adrift(library).copy(fingerDown = true, scrolling = true, led = true)))
        assertTrue(TabLeadRules.stillAsked(asked = true, adrift(library).copy(scrolling = true, led = true), selected = home))
    }

    @Test
    fun aRequestStandsUntilThePagesAreWholeOnItsTabWithNothingMoving() {
        assertTrue(TabLeadRules.stillAsked(asked = true, resting(home), selected = settings))
        assertTrue(TabLeadRules.stillAsked(asked = true, adrift(settings), selected = settings))
        // Whole on the tab, and the move that brought them is still finishing: it stands a moment longer.
        assertTrue(TabLeadRules.stillAsked(asked = true, resting(settings).copy(led = true), selected = settings))
        assertTrue(TabLeadRules.stillAsked(asked = true, resting(settings).copy(scrolling = true), selected = settings))
        assertFalse(TabLeadRules.stillAsked(asked = true, resting(settings), selected = settings))
        // What was never asked is not asked.
        assertFalse(TabLeadRules.stillAsked(asked = false, resting(home), selected = settings))
    }

    @Test
    fun afterAMoveTheBarAskedForThePagesAreSetOnItsTabUnlessAHandHasThem() {
        // Ended as it should.
        assertFalse(TabLeadRules.snapAfterLed(resting(settings), target = settings))
        // Cut short by something that is gone: between two tabs, or whole on the wrong one.
        assertTrue(TabLeadRules.snapAfterLed(adrift(library), target = settings))
        assertTrue(TabLeadRules.snapAfterLed(resting(home), target = settings))
        // Cut short by a finger, or with the pages still in a settle: theirs.
        assertFalse(TabLeadRules.snapAfterLed(adrift(library).copy(fingerDown = true), target = settings))
        assertFalse(TabLeadRules.snapAfterLed(adrift(library).copy(scrolling = true), target = settings))
        assertFalse(TabLeadRules.snapAfterLed(adrift(library).copy(handed = true), target = settings))
    }

    @Test
    fun noSequenceLeavesThePagesBetweenTabsOrShortOfARequest() {
        // Every state with nothing moving the pages, no finger and no request under way is either as it should be,
        // or one the guard acts on. Nothing is both wrong and left alone.
        for (page in 0..3) for (whole in listOf(true, false)) for (asked in listOf(true, false)) for (selected in 0..3) {
            val now = PagesNow(page, whole, scrolling = false, handed = false, fingerDown = false, led = false)
            val wanted = if (asked) selected else page
            val right = whole && page == wanted
            assertEquals("$now asked=$asked selected=$selected", !right, TabLeadRules.guard(now, asked, selected) != null)
            if (!right) assertEquals(wanted, TabLeadRules.guard(now, asked, selected))
            // And what is reported is never a tab the pages were asked to leave.
            if (asked) assertNull(TabLeadRules.report(now, asked, selected))
        }
    }

    @Test
    fun thePillTravelsWithThePagesUnlessTheBarLeadsThem() {
        assertTrue(TabLeadRules.pillFollowsPages(resting(home), selected = home))
        assertTrue(TabLeadRules.pillFollowsPages(adrift(library).copy(scrolling = true), selected = home))
        assertTrue(TabLeadRules.pillFollowsPages(adrift(library).copy(handed = true), selected = home))
        // A request under way, or one whose move has not begun: the pill goes to the selected tab by itself.
        assertFalse(TabLeadRules.pillFollowsPages(adrift(library).copy(scrolling = true, led = true), selected = settings))
        assertFalse(TabLeadRules.pillFollowsPages(resting(home), selected = settings))
    }

    @Test
    fun aHandedOverDragIsActiveUntilItsSettleEndsAndNoLonger() {
        assertFalse(TabSwipeRules.handActive(SwipeOwner.Undecided, settling = false))
        assertFalse(TabSwipeRules.handActive(SwipeOwner.Row, settling = false))
        assertTrue(TabSwipeRules.handActive(SwipeOwner.Pages, settling = false))
        // The next touch has reset the owner, and the settle of the last gesture still runs.
        assertTrue(TabSwipeRules.handActive(SwipeOwner.Undecided, settling = true))
    }

    @Test
    fun aTouchThatFindsThePagesStillOwnedWithNoSettleRunningSettlesThem() {
        // The row that held the last gesture left the screen and never reported the release.
        assertTrue(TabSwipeRules.settlesOnDown(SwipeOwner.Pages, settling = false))
        // The release was reported and the settle runs: nothing more to start.
        assertFalse(TabSwipeRules.settlesOnDown(SwipeOwner.Pages, settling = true))
        assertFalse(TabSwipeRules.settlesOnDown(SwipeOwner.Undecided, settling = false))
        assertFalse(TabSwipeRules.settlesOnDown(SwipeOwner.Row, settling = false))
    }

    @Test
    fun anOpenSearchHoldsTheTabsOnlyOnTheTabInView() {
        assertTrue(TabSwipeRules.modeHolds(open = true, tabInView = true))
        // Left open on a tab the owner tapped away from: a swipe back into that tab must not be stopped by it.
        assertFalse(TabSwipeRules.modeHolds(open = true, tabInView = false))
        assertFalse(TabSwipeRules.modeHolds(open = false, tabInView = true))
    }

    @Test
    fun theKeyboardIsAskedForOnlyOnTheTabInViewAndOnlyByAnEmptyField() {
        assertTrue(TabSwipeRules.raisesKeyboard(autoFocus = true, tabInView = true, query = ""))
        // The tab is only passing under a swipe.
        assertFalse(TabSwipeRules.raisesKeyboard(autoFocus = true, tabInView = false, query = ""))
        assertFalse(TabSwipeRules.raisesKeyboard(autoFocus = true, tabInView = true, query = "dune"))
        assertFalse(TabSwipeRules.raisesKeyboard(autoFocus = false, tabInView = true, query = ""))
    }
}
