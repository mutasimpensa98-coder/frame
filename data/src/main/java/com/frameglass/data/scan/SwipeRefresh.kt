package com.frameglass.data.scan

import com.frameglass.data.api.RefreshProgress
import com.frameglass.data.api.RefreshSkip
import com.frameglass.data.api.RefreshStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/** The steps of one swipe. The repository supplies the real ones. */
internal interface SwipeSteps {
    suspend fun scan(tally: ScanTally)
    suspend fun queueRefresh()
    suspend fun blocker(): RefreshSkip?

    /** Ends within about [budgetMs], whatever is left in the queue. */
    suspend fun drain(budgetMs: Long)
    suspend fun titlesLeft(): Int

    /** A running total that other runs may raise too. The swipe shows how far it moved. */
    val titlesUpdated: Int
    fun continueInBackground()
}

/**
 * A swipe to refresh: scan the files, then fetch details for what is due, for a bounded time. Counts are sampled on a
 * timer rather than sent per file, so a scan of thousands of files costs a handful of values. A stage that fails ends
 * like one that finished, and the summary still comes: the next swipe or app open runs again.
 */
internal class SwipeRefresh(
    private val steps: SwipeSteps,
    private val fetchBudgetMs: Long = FETCH_BUDGET_MS,
    private val tickMs: Long = TICK_MS,
) {
    fun run(): Flow<RefreshProgress> = channelFlow {
        var shown = RefreshProgress(RefreshStage.SCANNING)
        send(shown)

        suspend fun show(next: RefreshProgress) {
            if (next == shown) return
            shown = next
            send(next)
        }

        // Only the ticker calls show while work runs, and it has stopped before anything else does.
        suspend fun watch(sample: suspend () -> RefreshProgress, work: suspend () -> Unit) {
            val ticker = launch {
                while (true) {
                    delay(tickMs)
                    show(sample())
                }
            }
            try {
                quietly(Unit) { work() }
            } finally {
                ticker.cancelAndJoin()
            }
            show(sample())
        }

        val tally = ScanTally()
        watch({ shown.copy(filesChecked = tally.filesChecked, newVideos = tally.newVideos) }) { steps.scan(tally) }

        quietly(Unit) { steps.queueRefresh() }
        val skipped = quietly(null) { steps.blocker() }
        if (skipped == null) {
            val start = steps.titlesUpdated
            val fetching: suspend () -> RefreshProgress = {
                shown.copy(
                    stage = RefreshStage.FETCHING,
                    titlesUpdated = (steps.titlesUpdated - start).coerceAtLeast(0),
                    titlesLeft = quietly(shown.titlesLeft) { steps.titlesLeft() },
                )
            }
            show(fetching())
            watch(fetching) { steps.drain(fetchBudgetMs) }
        }
        quietly(Unit) { steps.continueInBackground() }
        val left = quietly(shown.titlesLeft) { steps.titlesLeft() }
        show(shown.copy(stage = RefreshStage.DONE, titlesLeft = left, skipped = skipped))
    }.conflate()

    private suspend fun <T> quietly(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fallback
    }

    companion object {
        /** The swipe waits this long for details. Anything left carries on in the background. */
        const val FETCH_BUDGET_MS = 8_000L
        const val TICK_MS = 200L
    }
}
