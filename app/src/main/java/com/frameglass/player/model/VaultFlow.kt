package com.frameglass.player.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.frameglass.data.api.FrameServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MillisPerSecond = 1_000L

/** The end of a move, for the final message. */
internal sealed interface MoveOutcome {
    val out: Boolean

    data class Moved(val count: Int, override val out: Boolean) : MoveOutcome
    data class Failed(override val out: Boolean) : MoveOutcome
}

private class PendingMove(val titleIds: List<Long>, val fileRowIds: List<Long>)

/**
 * What the private folder needs to outlive its page: the recovery code until the owner confirms it, moves, and
 * the lock after leaving. The code stays in memory only and is never put in saved state.
 */
@Stable
internal class VaultFlow(private val services: FrameServices, private val scope: CoroutineScope) {
    private val vault get() = services.vault
    private var pending: PendingMove? = null
    private var lockAfterLeaving: Job? = null

    var settingUp by mutableStateOf(false)
        private set
    var recoveryCode by mutableStateOf<String?>(null)
        private set
    var runningMoves by mutableIntStateOf(0)
        private set
    var movingOut by mutableStateOf(false)
        private set
    var outcome by mutableStateOf<MoveOutcome?>(null)
        private set

    fun setup(password: String) {
        if (settingUp) return
        settingUp = true
        scope.launch {
            recoveryCode = runCatching { vault.setup(password) }.getOrNull()
            settingUp = false
        }
    }

    /** The owner confirmed the code. A move that was waiting for the folder to exist starts now. */
    fun codeSaved() {
        recoveryCode = null
        val move = pending ?: return
        pending = null
        moveIn(move.titleIds, move.fileRowIds) { }
    }

    /** With no password yet this keeps the move for after setup, and [needsSetup] opens the page. */
    fun moveIn(titleIds: List<Long>, fileRowIds: List<Long>, needsSetup: () -> Unit) {
        scope.launch {
            if (!vault.state.first().configured) {
                pending = PendingMove(titleIds, fileRowIds)
                needsSetup()
            } else {
                track(out = false) { vault.moveIn(titleIds, fileRowIds) }
            }
        }
    }

    fun moveOut(titleId: Long) {
        scope.launch { track(out = true) { vault.moveOut(titleId) } }
    }

    private suspend fun track(out: Boolean, move: suspend () -> Result<Int>) {
        outcome = null
        movingOut = out
        runningMoves++
        val result = move()
        runningMoves--
        outcome = result.fold({ MoveOutcome.Moved(it, out) }, { MoveOutcome.Failed(out) })
    }

    fun clearOutcome() {
        outcome = null
    }

    fun pageEntered() {
        lockAfterLeaving?.cancel()
    }

    /** The app leaving the screen is handled in :data. This is the owner leaving the page inside the app. */
    fun pageLeft(lockSeconds: Int) {
        lockAfterLeaving?.cancel()
        if (lockSeconds <= 0) {
            vault.lock()
        } else {
            lockAfterLeaving = scope.launch {
                delay(lockSeconds * MillisPerSecond)
                vault.lock()
            }
        }
    }
}
