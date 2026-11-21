package com.frameglass.player.model

import com.frameglass.data.api.VaultDeleteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The delete inside the private folder is for good, so the one thing tested hardest is that it never runs unasked. */
class VaultDeleteFlowTest {
    private val vault = FakeVault().apply { current.value = current.value.copy(configured = true, unlocked = true) }
    private val flow = VaultDeleteFlow(FakeServices(vault = vault), CoroutineScope(Dispatchers.Unconfined))

    private fun ready() = flow.stage as VaultDeleteStage.Ready

    @Test
    fun askingOnlyPlansAndOpensTheConfirmation() {
        flow.ask(listOf(4L, 9L))
        assertEquals(listOf(listOf(4L, 9L)), vault.planned)
        assertEquals(2, ready().plan.fileCount)
        assertEquals(200L, ready().plan.totalBytes)
        assertTrue(vault.deletedPlans.isEmpty())
    }

    @Test
    fun nothingIsDeletedUntilThePlanOnScreenIsConfirmed() {
        // No plan on screen: confirm is a dead button.
        flow.confirm()
        assertTrue(vault.deletedPlans.isEmpty())
        assertNull(flow.stage)

        flow.ask(listOf(4L))
        assertTrue(vault.deletedPlans.isEmpty())
        flow.confirm()
        assertEquals(listOf(4L), vault.deletedPlans.single().titleIds)
    }

    @Test
    fun cancelLeavesEveryVideoWhereItIs() {
        var cleared = false
        flow.ask(listOf(4L)) { cleared = true }
        flow.dismiss()
        assertNull(flow.stage)
        assertTrue(vault.deletedPlans.isEmpty())
        assertFalse(cleared)
        // The confirmation is gone, so a late tap on its button deletes nothing either.
        flow.confirm()
        assertTrue(vault.deletedPlans.isEmpty())
    }

    @Test
    fun anEmptyAskOpensNothing() {
        flow.ask(emptyList())
        assertNull(flow.stage)
        assertTrue(vault.planned.isEmpty())
    }

    @Test
    fun aConfirmedDeleteRunsOnceAndEndsWithItsResult() {
        var cleared = 0
        flow.ask(listOf(4L, 9L)) { cleared++ }
        flow.confirm()
        flow.confirm()
        assertEquals(1, vault.deletedPlans.size)
        assertEquals(VaultDeleteStage.Finished(VaultDeleteResult(2, 0, 200)), flow.stage)
        assertEquals(1, cleared)
        flow.dismiss()
        assertNull(flow.stage)
    }

    @Test
    fun aRunningDeleteCannotBeDismissedOrAskedAgain() {
        vault.deleteGate = CompletableDeferred()
        flow.ask(listOf(4L))
        flow.confirm()
        assertTrue(flow.stage is VaultDeleteStage.Running)
        flow.dismiss()
        assertTrue(flow.stage is VaultDeleteStage.Running)
        flow.ask(listOf(9L))
        assertEquals(1, vault.planned.size)
        vault.deleteGate!!.complete(Unit)
        assertTrue(flow.stage is VaultDeleteStage.Finished)
    }

    @Test
    fun aLockedFolderGivesNoPlanAndDeletesNothing() {
        vault.current.value = vault.current.value.copy(unlocked = false)
        flow.ask(listOf(4L))
        assertEquals(VaultDeleteStage.Finished(null), flow.stage)
        flow.confirm()
        assertTrue(vault.deletedPlans.isEmpty())
    }

    private fun lock() {
        vault.current.value = vault.current.value.copy(unlocked = false)
    }

    @Test
    fun aFolderThatLocksTakesTheConfirmationAndItsNamesAway() {
        // The app left the screen with the sheet open, and the folder locked itself: the names must not be there
        // on the way back, over the lock screen.
        var cleared = false
        flow.ask(listOf(4L)) { cleared = true }
        assertEquals(listOf("Video 4"), ready().plan.names)
        assertTrue(flow.unlocked)
        lock()
        assertFalse(flow.unlocked)
        assertNull(flow.stage)
        // And the red button that was on screen deletes nothing any more.
        flow.confirm()
        assertTrue(vault.deletedPlans.isEmpty())
        assertFalse(cleared)
    }

    @Test
    fun aPlanStillBeingMadeIsDroppedWhenTheFolderLocks() {
        vault.planGate = CompletableDeferred()
        flow.ask(listOf(4L))
        assertEquals(VaultDeleteStage.Planning, flow.stage)
        lock()
        assertNull(flow.stage)
        vault.planGate!!.complete(Unit)
        assertNull(flow.stage)
    }

    @Test
    fun aResultWithItsCountsGoesWhenTheFolderLocks() {
        flow.ask(listOf(4L, 9L))
        flow.confirm()
        assertTrue(flow.stage is VaultDeleteStage.Finished)
        lock()
        assertNull(flow.stage)
    }

    @Test
    fun aRunningDeleteOutlivesTheLockAndEndsWithItsMessage() {
        vault.deleteGate = CompletableDeferred()
        flow.ask(listOf(4L))
        flow.confirm()
        lock()
        assertTrue(flow.stage is VaultDeleteStage.Running)
        // The sheet draws by this, so a delete still running says that it runs and gives no number.
        assertEquals(VaultSheetFace.RUNNING_QUIET, VaultSheetRules.face(flow.stage, flow.unlocked))
        vault.current.value = vault.current.value.copy(unlocked = true)
        vault.deleteGate!!.complete(Unit)
        assertEquals(VaultDeleteStage.Finished(VaultDeleteResult(1, 0, 100)), flow.stage)
    }

    @Test
    fun theFolderIsNotWatchedBeforeTheFirstConfirmation() {
        // A lock before anything was asked is nothing to this flow, and a later ask still works.
        lock()
        assertNull(flow.stage)
        // Until the folder has said it is open, the sheet draws as if it were locked.
        assertFalse(flow.unlocked)
        vault.current.value = vault.current.value.copy(unlocked = true)
        flow.ask(listOf(4L))
        assertEquals(1, ready().plan.fileCount)
        assertEquals(VaultSheetFace.PLAN, VaultSheetRules.face(flow.stage, flow.unlocked))
    }

    // What the sheet may draw.

    private val plan = com.frameglass.data.api.VaultDeletePlan(listOf(4L), listOf(40L), 1, 0, 100, listOf("Video 4"))
    private val result = VaultDeleteResult(1, 0, 100)

    @Test
    fun anOpenFolderShowsEveryStage() {
        assertEquals(VaultSheetFace.NOTHING, VaultSheetRules.face(null, unlocked = true))
        assertEquals(VaultSheetFace.PLANNING, VaultSheetRules.face(VaultDeleteStage.Planning, unlocked = true))
        assertEquals(VaultSheetFace.PLAN, VaultSheetRules.face(VaultDeleteStage.Ready(plan), unlocked = true))
        assertEquals(VaultSheetFace.RUNNING, VaultSheetRules.face(VaultDeleteStage.Running(plan), unlocked = true))
        assertEquals(VaultSheetFace.RESULT, VaultSheetRules.face(VaultDeleteStage.Finished(result), unlocked = true))
    }

    @Test
    fun aLockedFolderNeverShowsNamesCountsOrSizes() {
        // Even the last frames of a sheet sliding away over the lock screen draw nothing of the plan.
        assertEquals(VaultSheetFace.NOTHING, VaultSheetRules.face(VaultDeleteStage.Planning, unlocked = false))
        assertEquals(VaultSheetFace.NOTHING, VaultSheetRules.face(VaultDeleteStage.Ready(plan), unlocked = false))
        assertEquals(VaultSheetFace.RUNNING_QUIET, VaultSheetRules.face(VaultDeleteStage.Running(plan), unlocked = false))
        assertEquals(VaultSheetFace.RESULT_QUIET, VaultSheetRules.face(VaultDeleteStage.Finished(result), unlocked = false))
    }

    @Test
    fun aDeleteThatCouldNotRunSaysSoEitherWay() {
        assertEquals(VaultSheetFace.RESULT, VaultSheetRules.face(VaultDeleteStage.Finished(null), unlocked = false))
        assertEquals(VaultSheetFace.RESULT, VaultSheetRules.face(VaultDeleteStage.Finished(null), unlocked = true))
    }

    @Test
    fun aVideoOnItsWayOutIsNotNamedOverALockedFolder() {
        assertFalse(VaultSheetRules.namesMove(movingOut = true, unlocked = false))
        assertTrue(VaultSheetRules.namesMove(movingOut = true, unlocked = true))
        // A video on its way in was picked in the library a moment ago.
        assertTrue(VaultSheetRules.namesMove(movingOut = false, unlocked = false))
        assertTrue(VaultSheetRules.namesMove(movingOut = false, unlocked = true))
    }

    @Test
    fun picksAreKeptWhenNothingCouldBeDeleted() {
        var cleared = false
        vault.deleteResult = VaultDeleteResult(deleted = 0, failed = 1, freedBytes = 0)
        flow.ask(listOf(4L)) { cleared = true }
        flow.confirm()
        assertEquals(VaultDeleteStage.Finished(VaultDeleteResult(0, 1, 0)), flow.stage)
        assertFalse(cleared)
    }

    @Test
    fun aNewAskReplacesThePlanOnScreen() {
        flow.ask(listOf(4L))
        flow.ask(listOf(9L, 11L))
        assertEquals(listOf(9L, 11L), ready().plan.titleIds)
        flow.confirm()
        assertEquals(listOf(9L, 11L), vault.deletedPlans.single().titleIds)
    }
}
