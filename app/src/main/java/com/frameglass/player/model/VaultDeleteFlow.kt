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

/**
 * The delete of the private folder (owner request of 2026-11-21). It is for good, so it has one way through:
 * [ask] only plans and opens the confirmation, and only [confirm] on a plan that is on screen deletes. Anything
 * else, a dismissal included, leaves every video where it is. It lives with the app, so leaving the page cannot
 * lose the confirmation or the final message.
 */
@Stable
internal class VaultDeleteFlow(private val services: FrameServices, private val scope: CoroutineScope) {
    private val vault get() = services.vault

    var stage by mutableStateOf<VaultDeleteStage?>(null)
        private set

    private var onDeleted: (() -> Unit)? = null
    private var planning: Job? = null

    /** Opens the confirmation for these cards of the folder. Nothing is deleted until [confirm]. */
    fun ask(titleIds: List<Long>, onDeleted: (() -> Unit)? = null) {
        if (titleIds.isEmpty() || stage is VaultDeleteStage.Running) return
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
