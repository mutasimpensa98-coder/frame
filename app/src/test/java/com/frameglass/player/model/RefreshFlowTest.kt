package com.frameglass.player.model

import com.frameglass.data.api.RefreshProgress
import com.frameglass.data.api.RefreshSkip
import com.frameglass.data.api.RefreshStage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshFlowTest {
    private val reports = Channel<RefreshProgress>(Channel.UNLIMITED)
    private val held = CompletableDeferred<Unit>()
    private var starts = 0
    private val flow = RefreshFlow(
        scope = CoroutineScope(Dispatchers.Unconfined),
        start = {
            starts++
            reports.consumeAsFlow()
        },
        hold = { held.await() },
    )

    private fun send(progress: RefreshProgress) {
        reports.trySend(progress)
    }

    private fun fetching(updated: Int, left: Int) = RefreshProgress(RefreshStage.FETCHING, 400, 2, updated, left)

    @Test
    fun aSwipeStartsBeforeAnyReport() {
        flow.refresh()
        assertEquals(RefreshLine.Starting, flow.ui.value.line())
    }

    @Test
    fun scanningCountsFiles() {
        flow.refresh()
        send(RefreshProgress(RefreshStage.SCANNING, filesChecked = 412))
        assertEquals(RefreshLine.Scanning(412), flow.ui.value.line())
        assertNull(flow.ui.value.line()!!.fraction())
    }

    @Test
    fun detailsCountUpAndNeverFall() {
        flow.refresh()
        send(fetching(updated = 0, left = 12))
        assertEquals(RefreshLine.Fetching(0, 12), flow.ui.value.line())
        send(fetching(updated = 3, left = 9))
        assertEquals(RefreshLine.Fetching(3, 12), flow.ui.value.line())
        // More titles queued: the total grows, and what is done stays.
        send(fetching(updated = 3, left = 14))
        assertEquals(RefreshLine.Fetching(3, 17), flow.ui.value.line())
        send(fetching(updated = 4, left = 10))
        val line = flow.ui.value.line() as RefreshLine.Fetching
        assertEquals(RefreshLine.Fetching(7, 17), line)
        assertEquals(7f / 17f, line.fraction()!!, 0.0001f)
    }

    @Test
    fun theSummaryStaysThenGoes() {
        flow.refresh()
        send(RefreshProgress(RefreshStage.DONE, 400, newVideos = 2, titlesUpdated = 5))
        reports.close()
        assertEquals(RefreshLine.Result(2, 5, 0, null), flow.ui.value.line())
        assertTrue(flow.ui.value is RefreshUi.Finished)
        held.complete(Unit)
        assertSame(RefreshUi.Idle, flow.ui.value)
    }

    @Test
    fun aSecondSwipeWhileRunningIsIgnored() {
        flow.refresh()
        flow.refresh()
        assertEquals(1, starts)
    }

    @Test
    fun aFailedWalkStillEndsWithWhatItGot() {
        val failing = RefreshFlow(
            scope = CoroutineScope(Dispatchers.Unconfined),
            start = {
                flow {
                    emit(RefreshProgress(RefreshStage.SCANNING, filesChecked = 10, newVideos = 1))
                    error("walk failed")
                }
            },
            hold = { held.await() },
        )
        failing.refresh()
        assertEquals(RefreshLine.Result(1, 0, 0, null), failing.ui.value.line())
    }

    @Test
    fun skippedDetailsAreCarriedToTheSummary() {
        flow.refresh()
        send(RefreshProgress(RefreshStage.DONE, 10, titlesLeft = 4, skipped = RefreshSkip.ONLINE_OFF))
        reports.close()
        assertEquals(RefreshLine.Result(0, 0, 4, RefreshSkip.ONLINE_OFF), flow.ui.value.line())
    }

    @Test
    fun idleShowsNothing() {
        assertNull(RefreshUi.Idle.line())
    }
}
