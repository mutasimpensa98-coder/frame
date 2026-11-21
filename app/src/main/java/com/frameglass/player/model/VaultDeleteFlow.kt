package com.frameglass.player.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.frameglass.data.api.FrameServices
import com.frameglass.data.api.VaultDeletePlan
import com.frameglass.data.api.VaultDeleteResult
import com.frameglass.data.api.deleteForGood
import com.frameglass.data.api.planDelete
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where one delete inside the private folder stands. */
internal sealed interface VaultDeleteStage {
    data object Planning : VaultDeleteStage

    /** The sheet shows what would go. Nothing has been touched. */
    data class Ready(val plan: VaultDeletePlan) : VaultDeleteStage
    data class Running(val plan: VaultDeletePlan) : VaultDeleteStage

    /** [result] is null when the delete could not run at all, as when the folder locked itself meanwhile. */
    data class Finished(val result: VaultDeleteResult?) : VaultDeleteStage
}

/** What the confirmation sheet may draw. A locked folder says nothing of what it holds: no name, count or size. */
internal enum class VaultSheetFace {
    NOTHING,
    PLANNING,

    /** The names, the count and the size, with the two buttons. Only while the folder is open. */
    PLAN,
    RUNNING,

    /** A delete still running after the folder locked: that it runs, and no number. */
    RUNNING_QUIET,
    RESULT,

    /** A delete that ended after the folder locked: that it ended, and no number. */
    RESULT_QUIET,
}

internal object VaultSheetRules {
    /**
     * Whether the move sheet may name the file it is moving. A video on its way out is still a private one, so a
     * folder that locked during the move shows the progress and no name. A video on its way in was picked in the
     * library a moment ago, and is named.
     */
    fun namesMove(movingOut: Boolean, unlocked: Boolean): Boolean = !movingOut || unlocked

    /**
     * [unlocked] is the folder now. A delete that could not run carries nothing of the folder, so its message
     * shows either way.
     */
    fun face(stage: VaultDeleteStage?, unlocked: Boolean): VaultSheetFace = when (stage) {
        null -> VaultSheetFace.NOTHING
        VaultDeleteStage.Planning -> if (unlocked) VaultSheetFace.PLANNING else VaultSheetFace.NOTHING
        is VaultDeleteStage.Ready -> if (unlocked) VaultSheetFace.PLAN else VaultSheetFace.NOTHING
        is VaultDeleteStage.Running -> if (unlocked) VaultSheetFace.RUNNING else VaultSheetFace.RUNNING_QUIET
        is VaultDeleteStage.Finished ->
            if (unlocked || stage.result == null) VaultSheetFace.RESULT else VaultSheetFace.RESULT_QUIET
    }
}

/**
 * The delete of the private folder (owner request of 2026-11-21). It is for good, so it has one way through:
 * [ask] only plans and opens the confirmation, and only [confirm] on a plan that is on screen deletes. Anything
 * else, a dismissal included, leaves every video where it is. It lives with the app, so leaving the page cannot
 * lose the final message.
 *
 * The confirmation names private videos, so it does not outlive an open folder: when the folder locks, as it does
 * by itself once the app leaves the screen, a plan that is waiting for an answer is cancelled and its sheet closes.
 */
@Stable
internal class VaultDeleteFlow(private val services: FrameServices, private val scope: CoroutineScope) {
    private val vault get() = services.vault

    var stage by mutableStateOf<VaultDeleteStage?>(null)
        private set

    /**
     * Whether the folder is open, as this flow last heard. The sheet draws by it. It is kept here, in the scope of
     * the app, and not collected by the sheet with the screen's lifecycle: a folder that locks while the app is off
     * the screen must already read as locked in the first frame drawn on the way back. False until the folder
     * has said it is open.
     */
    var unlocked by mutableStateOf(false)
        private set

    private var onDeleted: (() -> Unit)? = null
    private var planning: Job? = null
    private var watching: Job? = null

    /** Started with the first confirmation, so the folder is not looked at before anyone opens it. */
    private fun watchTheLock() {
        if (watching != null) return
        watching = scope.launch {
            vault.state.collect {
                unlocked = it.unlocked
                if (!it.unlocked) folderLocked()
            }
        }
    }

    /**
     * The folder is locked. Everything on the sheet but a running delete goes: a plan with its names, and a result
     * with its counts. A running delete cannot be called back and ends with a message that names nothing.
     */
    private fun folderLocked() {
        if (stage != null) dismiss()
    }

    /** Opens the confirmation for these cards of the folder. Nothing is deleted until [confirm]. */
    fun ask(titleIds: List<Long>, onDeleted: (() -> Unit)? = null) {
        if (titleIds.isEmpty() || stage is VaultDeleteStage.Running) return
        watchTheLock()
        this.onDeleted = onDeleted
        planning?.cancel()
        stage = VaultDeleteStage.Planning
        planning = scope.launch {
            stage = try {
                val plan = vault.planDelete(titleIds)
                // Nothing to remove needs no confirmation and says so.
                if (plan.isEmpty) VaultDeleteStage.Finished(VaultDeleteResult(0, 0, 0)) else VaultDeleteStage.Ready(plan)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                VaultDeleteStage.Finished(null)
            }
        }
    }

    /** The one step that deletes. It does nothing unless the owner is looking at a finished plan. */
    fun confirm() {
        val ready = stage as? VaultDeleteStage.Ready ?: return
        stage = VaultDeleteStage.Running(ready.plan)
        scope.launch {
            val result = try {
                vault.deleteForGood(ready.plan)
            } catch (_: Exception) {
                null
            }
            stage = VaultDeleteStage.Finished(result)
            // Picks are kept when nothing went, so the owner can try again.
            if (result != null && result.deleted > 0) onDeleted?.invoke()
            onDeleted = null
        }
    }

    /** Cancel, Back, the scrim or a drag. A running delete cannot be called back, so it stays and ends with its message. */
    fun dismiss() {
        if (stage is VaultDeleteStage.Running) return
        planning?.cancel()
        onDeleted = null
        stage = null
    }
}
