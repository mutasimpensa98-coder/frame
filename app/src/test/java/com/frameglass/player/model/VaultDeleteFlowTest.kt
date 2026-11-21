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

    @Test
    fun aFolderThatLocksBeforeTheConfirmationDeletesNothing() {
        var cleared = false
        flow.ask(listOf(4L)) { cleared = true }
        vault.current.value = vault.current.value.copy(unlocked = false)
        flow.confirm()
        assertEquals(VaultDeleteStage.Finished(null), flow.stage)
        assertTrue(vault.deletedPlans.isEmpty())
        assertFalse(cleared)
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
