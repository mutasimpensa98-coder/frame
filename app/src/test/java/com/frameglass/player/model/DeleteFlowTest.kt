package com.frameglass.player.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteFlowTest {
    private val media = FakeMedia()
    private val flow = DeleteFlow(FakeServices(media), CoroutineScope(Dispatchers.Unconfined))
    private val two = listOf(entry("a.mkv"), entry("b.mkv"))

    private fun ready() = flow.stage as DeleteStage.Ready

    @Test
    fun askingOnlyPlans() {
        flow.ask(two)
        assertEquals(2, ready().plan.fileCount)
        assertFalse(ready().plan.includeSidecars)
        assertNull(media.deleted)
    }

    @Test
    fun anEmptyAskOpensNothing() {
        flow.ask(emptyList())
        assertNull(flow.stage)
        assertEquals(0, media.plans)
    }

    @Test
    fun theSidecarSwitchMakesANewPlan() {
        flow.ask(two)
        flow.includeSidecars(true)
        assertTrue(ready().plan.includeSidecars)
        assertEquals(2, media.plans)
    }

    @Test
    fun confirmDeletesTheShownPlanAndEndsWithItsResult() {
        var cleared = false
        flow.ask(two, onDeleted = { cleared = true })
        val shown = ready().plan
        flow.confirm()
        assertEquals(shown, media.deleted)
        assertEquals(2, (flow.stage as DeleteStage.Finished).result.trashed)
        assertTrue(cleared)
    }

    @Test
    fun confirmDoesNothingWithoutAPlanOnScreen() {
        flow.confirm()
        assertNull(flow.stage)
        assertNull(media.deleted)
    }

    @Test
    fun cancelLeavesTheFilesAlone() {
        flow.ask(two)
        flow.dismiss()
        assertNull(flow.stage)
        assertNull(media.deleted)
    }

    @Test
    fun aRunningDeleteCannotBeDismissedAndStillEndsWithItsMessage() {
        val gate = CompletableDeferred<Unit>()
        media.gate = gate
        flow.ask(two)
        flow.confirm()
        assertTrue(flow.stage is DeleteStage.Running)
        flow.dismiss()
        assertTrue(flow.stage is DeleteStage.Running)
        flow.ask(listOf(entry("c.mkv")))
        assertTrue(flow.stage is DeleteStage.Running)
        gate.complete(Unit)
        assertTrue(flow.stage is DeleteStage.Finished)
    }

    @Test
    fun aFailedPlanSaysNothingWasDeleted() {
        media.planFails = true
        flow.ask(two)
        val result = (flow.stage as DeleteStage.Finished).result
        assertEquals(listOf("a.mkv", "b.mkv"), result.failed)
        assertEquals(0, result.trashed)
    }

    @Test
    fun aFailedDeleteReportsItsFiles() {
        media.deleteFails = true
        flow.ask(two)
        flow.confirm()
        assertEquals(2, (flow.stage as DeleteStage.Finished).result.failed.size)
    }
}
