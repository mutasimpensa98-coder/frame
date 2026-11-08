package com.frameglass.data.meta

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockWithinTest {
    @Test
    fun aFreeLockIsTaken() = runBlocking {
        val mutex = Mutex()
        assertTrue(mutex.lockWithin(1_000))
        assertTrue(mutex.isLocked)
    }

    @Test
    fun aLockHeldPastTheTimeoutIsNotTaken() = runBlocking {
        val mutex = Mutex(locked = true)
        assertFalse(mutex.lockWithin(20))
        assertTrue(mutex.isLocked)
    }

    /** The caller is cancelled after lock() resumed and before withTimeoutOrNull hands the result back. */
    @Test
    fun aCallerCancelledJustAfterGettingTheLockGivesItBack() = runBlocking {
        val mutex = Mutex(locked = true)
        lateinit var waiter: Job
        waiter = launch { mutex.lockWithin(60_000) { waiter.cancel() } }
        yield()
        mutex.unlock()
        waiter.join()
        assertTrue(waiter.isCancelled)
        assertFalse(mutex.isLocked)
    }

    @Test
    fun aCallerCancelledWhileWaitingLeavesTheHolderAlone() = runBlocking {
        val mutex = Mutex(locked = true)
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) { mutex.lockWithin(60_000) }
        yield()
        waiter.cancel()
        waiter.join()
        assertTrue(mutex.isLocked)
        mutex.unlock()
        assertTrue(mutex.lockWithin(1_000))
    }
}
