package com.frameglass.playerui

import android.content.res.Resources
import com.frameglass.engine.FrameRateFix
import com.frameglass.engine.SyncProblem
import com.frameglass.glass.bidiIsolate
import java.util.Locale

/** Automatic subtitle sync as the subtitle sheet shows it. */
internal sealed interface SyncState {
    data object Running : SyncState

    /** [delayMs] is the subtitle delay now set, and [fix] the frame rate fix, if any. */
    data class Applied(val delayMs: Long, val fix: FrameRateFix) : SyncState

    /** Confident, and the delay already matched, so nothing changed. */
    data object AlreadyRight : SyncState

    /** The audio was read, no offset could be trusted, and nothing changed. */
    data object Unsure : SyncState

    /** A download that matched this file's hash is timed for it already, so nothing was analysed. */
    data object Exact : SyncState

    data class Unavailable(val problem: SyncProblem) : SyncState
}

internal object SyncText {
    fun summary(resources: Resources, locale: Locale, sync: SyncState): String = when (sync) {
        SyncState.Running -> resources.getString(R.string.player_sync_running)
        is SyncState.Applied -> {
            val delay = resources.getString(R.string.player_sync_applied, bidiIsolate(seconds(locale, sync.delayMs)))
            if (sync.fix == FrameRateFix.NONE) {
                delay
            } else {
                val from = bidiIsolate(fps(locale, sync.fix.from))
                val to = bidiIsolate(fps(locale, sync.fix.to))
                delay + "\n" + resources.getString(R.string.player_sync_ratio, from, to)
            }
        }
        SyncState.AlreadyRight -> resources.getString(R.string.player_sync_already)
        SyncState.Unsure -> resources.getString(R.string.player_sync_unsure)
        SyncState.Exact -> resources.getString(R.string.player_sync_exact)
        is SyncState.Unavailable -> resources.getString(
            when (sync.problem) {
                SyncProblem.NO_AUDIO -> R.string.player_sync_no_audio
                SyncProblem.NO_CUES -> R.string.player_sync_no_cues
                SyncProblem.TIMED_OUT -> R.string.player_sync_timed_out
            },
        )
    }

    /** Signed seconds with two decimals, such as +1.25. */
    fun seconds(locale: Locale, ms: Long): String = String.format(locale, "%+.2f", ms / 1000.0)

    /** 25 or 23.976: whole rates without decimals. */
    fun fps(locale: Locale, value: Double): String =
        if (value == Math.rint(value)) String.format(locale, "%d", value.toLong()) else String.format(locale, "%.3f", value)
}
