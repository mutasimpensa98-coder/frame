package com.frameglass.player.model

import com.frameglass.data.api.RefreshProgress
import com.frameglass.data.api.RefreshSkip
import com.frameglass.data.api.RefreshStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val ResultMillis = 3_500L

/** What the refresh pill says. Counts only, so the words stay with the screen. */
internal sealed interface RefreshLine {
    /** The swipe was released and nothing has been reported yet. */
    data object Starting : RefreshLine

    data class Scanning(val files: Int) : RefreshLine

    /** [total] is 0 while the queue is not known yet. */
    data class Fetching(val done: Int, val total: Int) : RefreshLine

    /** The summary. [left] titles carry on in the background, unless [skipped] says details were not fetched. */
    data class Result(val newVideos: Int, val updated: Int, val left: Int, val skipped: RefreshSkip?) : RefreshLine
}

/** Where a swipe to refresh stands. [Idle] shows no pill. */
internal sealed interface RefreshUi {
    data object Idle : RefreshUi

    /** [total] is the most titles this swipe has had to do, so done = total - left never falls. */
    data class Running(val progress: RefreshProgress?, val total: Int = 0) : RefreshUi

    data class Finished(val summary: RefreshProgress) : RefreshUi
}

/** One step of the pill, from the last state and the newest report. */
internal fun RefreshUi.Running.advance(next: RefreshProgress): RefreshUi.Running {
    val done = (total - (progress?.titlesLeft ?: 0)).coerceAtLeast(0)
    val grown = if (next.stage == RefreshStage.FETCHING) maxOf(total, done + next.titlesLeft) else total
    return RefreshUi.Running(next, grown)
}

internal fun RefreshUi.line(): RefreshLine? = when (this) {
    RefreshUi.Idle -> null
    is RefreshUi.Finished -> summary.toResult()
    is RefreshUi.Running -> when (progress?.stage) {
        null -> RefreshLine.Starting
        RefreshStage.SCANNING -> RefreshLine.Scanning(progress.filesChecked)
        RefreshStage.FETCHING -> RefreshLine.Fetching((total - progress.titlesLeft).coerceIn(0, total), total)
        RefreshStage.DONE -> progress.toResult()
    }
}

private fun RefreshProgress.toResult() = RefreshLine.Result(newVideos, titlesUpdated, titlesLeft, skipped)

/** How full the thin line is, or null when the end is not known and it should show activity instead. */
internal fun RefreshLine.fraction(): Float? = when (this) {
    is RefreshLine.Fetching -> if (total > 0) done.toFloat() / total else null
    is RefreshLine.Result -> 1f
    else -> null
}

/**
 * One swipe to refresh at a time. It lives with the app, so leaving the tab does not stop the walk or lose
 * the summary, which stays for a few seconds.
 */
internal class RefreshFlow(
    private val scope: CoroutineScope,
    private val start: () -> Flow<RefreshProgress>,
    private val hold: suspend () -> Unit = { delay(ResultMillis) },
) {
    private val state = MutableStateFlow<RefreshUi>(RefreshUi.Idle)
    val ui: StateFlow<RefreshUi> = state.asStateFlow()

    fun refresh() {
        if (state.value is RefreshUi.Running) return
        state.value = RefreshUi.Running(null)
        scope.launch {
            var last: RefreshProgress? = null
            try {
                start().collect { next ->
                    last = next
                    val now = state.value as? RefreshUi.Running ?: RefreshUi.Running(null)
                    state.value = now.advance(next)
                }
            } catch (e: CancellationException) {
                state.value = RefreshUi.Idle
                throw e
            } catch (_: Exception) {
                // A failed walk still ends with a summary of what it got through.
            }
            val summary = last?.copy(stage = RefreshStage.DONE) ?: RefreshProgress(RefreshStage.DONE)
            val finished = RefreshUi.Finished(summary)
            state.value = finished
            hold()
            state.compareAndSet(finished, RefreshUi.Idle)
        }
    }
}
