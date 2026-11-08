package com.frameglass.data.scan

import com.frameglass.data.api.RefreshProgress
import com.frameglass.data.api.RefreshSkip
import com.frameglass.data.api.RefreshStage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class SwipeRefreshTest {
    private class FakeSteps : SwipeSteps {
        val calls = CopyOnWriteArrayList<String>()
        var skip: RefreshSkip? = null
        var budget = -1L

        @Volatile
        var left = 0

        @Volatile
        var updated = 0
        var onScan: suspend (ScanTally) -> Unit = {}
        var onDrain: suspend () -> Unit = {}

        override suspend fun scan(tally: ScanTally) {
            calls += "scan"
            onScan(tally)
        }

        override suspend fun queueRefresh() {
            calls += "queue"
        }

        override suspend fun blocker(): RefreshSkip? {
            calls += "blocker"
            return skip
        }

        override suspend fun drain(budgetMs: Long) {
            calls += "drain"
            budget = budgetMs
            onDrain()
        }

        override suspend fun titlesLeft(): Int = left
        override val titlesUpdated: Int get() = updated

        override fun continueInBackground() {
            calls += "continue"
        }
    }

    private val steps = FakeSteps()

    private fun run(): List<RefreshProgress> = runBlocking { SwipeRefresh(steps, fetchBudgetMs = 1_234, tickMs = 5).run().toList() }

    @Test
    fun aSwipeEndsWithASummaryOfItsOwnWork() {
        steps.left = 4
        steps.updated = 10
        steps.onScan = { tally ->
            repeat(3) { tally.checked(1) }
            tally.added()
            tally.added()
        }
        steps.onDrain = {
            steps.updated += 4
            steps.left = 1
        }
        val seen = run()
        assertEquals(RefreshProgress(RefreshStage.DONE, filesChecked = 3, newVideos = 2, titlesUpdated = 4, titlesLeft = 1), seen.last())
        assertEquals(listOf("scan", "queue", "blocker", "drain", "continue"), steps.calls)
        assertEquals(1_234L, steps.budget)
    }

    @Test
    fun stagesComeInOrderAndEachValueIsNew() {
        steps.left = 2
        val seen = run()
        val stages = seen.map { it.stage }.distinct()
        assertEquals(listOf(RefreshStage.SCANNING, RefreshStage.FETCHING, RefreshStage.DONE), stages)
        assertEquals(seen, seen.distinct())
        assertEquals(RefreshProgress(RefreshStage.SCANNING), seen.first())
    }

    @Test
    fun theScanIsShownWhileItRuns() {
        steps.onScan = { tally ->
            tally.checked(1)
            delay(60)
            tally.checked(1)
            tally.added()
            delay(60)
        }
        val scanning = run().filter { it.stage == RefreshStage.SCANNING }
        assertTrue(scanning.any { it.filesChecked == 1 && it.newVideos == 0 })
        assertEquals(RefreshProgress(RefreshStage.SCANNING, filesChecked = 2, newVideos = 1), scanning.last())
    }

    @Test
    fun theFetchIsShownWhileItRuns() {
        steps.left = 3
        steps.onDrain = {
            steps.updated = 1
            steps.left = 2
            delay(60)
            steps.updated = 3
            steps.left = 0
        }
        val fetching = run().filter { it.stage == RefreshStage.FETCHING }
        assertEquals(3, fetching.first().titlesLeft)
        assertTrue(fetching.any { it.titlesUpdated == 1 && it.titlesLeft == 2 })
        assertEquals(3, fetching.last().titlesUpdated)
    }

    @Test
    fun noDetailsAreAskedForWhenTheyCannotCome() {
        steps.skip = RefreshSkip.NO_NETWORK
        steps.left = 7
        val seen = run()
        assertEquals(RefreshProgress(RefreshStage.DONE, titlesLeft = 7, skipped = RefreshSkip.NO_NETWORK), seen.last())
        assertFalse(seen.any { it.stage == RefreshStage.FETCHING })
        assertEquals(listOf("scan", "queue", "blocker", "continue"), steps.calls)
    }

    @Test
    fun aFailingStageStillEndsWithASummary() {
        steps.onScan = { tally ->
            tally.checked(5)
            error("disk")
        }
        steps.onDrain = { error("database") }
        val last = run().last()
        assertEquals(RefreshStage.DONE, last.stage)
        assertEquals(5, last.filesChecked)
        assertEquals(listOf("scan", "queue", "blocker", "drain", "continue"), steps.calls)
    }

    @Test
    fun cancellingTheCollectionStopsTheSwipe() {
        steps.onScan = { delay(10_000) }
        val first = runBlocking { SwipeRefresh(steps, tickMs = 5).run().first() }
        assertEquals(RefreshStage.SCANNING, first.stage)
        assertFalse("queue" in steps.calls)
        assertFalse("drain" in steps.calls)
    }
}
