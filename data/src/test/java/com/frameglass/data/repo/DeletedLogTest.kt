package com.frameglass.data.repo

import com.frameglass.data.api.DeletedFiles
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletedLogTest {
    private val files = DeletedFiles(setOf(7L), setOf("/storage/emulated/0/Movies/a.mkv"))

    @Test
    fun aDeleteReachesWhoeverListensAndIsRemembered() = runBlocking {
        val log = DeletedLog()
        val heard = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { log.events.first() } }
        log.add(files)
        assertEquals(files, heard.await())
        assertTrue(log.holds(7L))
    }

    @Test
    fun aSaveForAnotherFileOrForNoFileIsNotRefused() {
        val log = DeletedLog()
        log.add(files)
        assertFalse(log.holds(8L))
        assertFalse(log.holds(null))
    }

    @Test
    fun aFileThatOpensAgainIsSavedForAgain() {
        val log = DeletedLog()
        log.add(files)
        log.forget(7L)
        assertFalse(log.holds(7L))
    }

    @Test
    fun aDeleteThatRemovedNoVideoSaysNothing() {
        val log = DeletedLog()
        log.add(DeletedFiles(emptySet(), emptySet()))
        assertFalse(log.holds(7L))
    }
}
