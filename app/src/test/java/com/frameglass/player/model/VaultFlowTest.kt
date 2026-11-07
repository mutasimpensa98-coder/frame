package com.frameglass.player.model

import com.frameglass.data.api.VaultClosedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultFlowTest {
    private val vault = FakeVault()
    private val flow = VaultFlow(FakeServices(vault = vault), CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun setupHoldsTheCodeUntilTheOwnerConfirms() {
        flow.setup("1234")
        assertFalse(flow.settingUp)
        assertEquals("ABCD-EFGH-JKMN-PQRS", flow.recoveryCode)
        flow.codeSaved()
        assertNull(flow.recoveryCode)
    }

    @Test
    fun aMoveBeforeSetupOpensThePageAndRunsAfterTheCodeIsSaved() {
        var opened = false
        flow.moveIn(listOf(4L), emptyList()) { opened = true }
        assertTrue(opened)
        assertTrue(vault.moved.isEmpty())
        flow.setup("1234")
        assertTrue(vault.moved.isEmpty())
        flow.codeSaved()
        assertEquals(listOf(listOf(4L) to emptyList<Long>()), vault.moved)
        assertEquals(MoveOutcome.Moved(1, out = false), flow.outcome)
    }

    @Test
    fun aMoveIsCountedWhileItRunsAndEndsWithItsResult() {
        vault.current.value = vault.current.value.copy(configured = true)
        vault.moveResult = Result.success(3)
        flow.moveIn(listOf(4L), listOf(9L)) { error("the folder exists") }
        assertEquals(0, flow.runningMoves)
        assertEquals(MoveOutcome.Moved(3, out = false), flow.outcome)
    }

    @Test
    fun aFailedMoveOutSaysSo() {
        vault.moveResult = Result.failure(VaultClosedException())
        flow.moveOut(4L)
        assertEquals(MoveOutcome.Failed(out = true), flow.outcome)
        flow.clearOutcome()
        assertNull(flow.outcome)
    }

    @Test
    fun leavingLocksAtOnceWhenSetToZero() {
        flow.pageLeft(0)
        assertEquals(1, vault.locks)
    }

    @Test
    fun comingBackInTimeCancelsTheWaitingLock() = runBlocking {
        val scoped = VaultFlow(FakeServices(vault = vault), CoroutineScope(Dispatchers.Default))
        scoped.pageLeft(1)
        scoped.pageEntered()
        delay(1_200)
        assertEquals(0, vault.locks)
    }

    @Test
    fun aDelayedLockFires() = runBlocking {
        val scoped = VaultFlow(FakeServices(vault = vault), CoroutineScope(Dispatchers.Default))
        scoped.pageLeft(1)
        delay(1_200)
        assertEquals(1, vault.locks)
    }
}
