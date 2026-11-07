package com.frameglass.player.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.frameglass.data.api.DeletePlan
import com.frameglass.data.api.DeleteResult
import com.frameglass.data.api.FrameServices
import com.frameglass.data.api.MediaEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where one delete stands. [Planning.shown] keeps the last plan on screen while the sidecars switch replans. */
internal sealed interface DeleteStage {
    data class Planning(val shown: DeletePlan?) : DeleteStage
    data class Ready(val plan: DeletePlan) : DeleteStage
    data class Running(val plan: DeletePlan) : DeleteStage
    data class Finished(val result: DeleteResult) : DeleteStage
}

/**
 * One delete at a time, asked from Media Manager or from a card. It lives with the app, so a language change
 * or leaving the page cannot lose the confirmation or the final message.
 */
@Stable
internal class DeleteFlow(private val services: FrameServices, private val scope: CoroutineScope) {
    var stage by mutableStateOf<DeleteStage?>(null)
        private set

    private var entries = emptyList<MediaEntry>()
    private var onDeleted: (() -> Unit)? = null
    private var planning: Job? = null

    /** Opens the confirmation. Nothing is deleted until [confirm]. */
    fun ask(entries: List<MediaEntry>, onDeleted: (() -> Unit)? = null) {
        if (entries.isEmpty() || stage is DeleteStage.Running) return
        this.entries = entries
        this.onDeleted = onDeleted
        plan(includeSidecars = false)
    }

    fun includeSidecars(include: Boolean) = plan(include)

    private fun failed() = DeleteResult(0, 0, entries.map { it.name }, 0)

    private fun plan(includeSidecars: Boolean) {
        planning?.cancel()
        val shown = when (val current = stage) {
            is DeleteStage.Ready -> current.plan
            is DeleteStage.Planning -> current.shown
            else -> null
        }
        stage = DeleteStage.Planning(shown)
        planning = scope.launch {
            stage = try {
                DeleteStage.Ready(services.media.planDelete(entries, includeSidecars))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                DeleteStage.Finished(failed())
            }
        }
    }

    fun confirm() {
        val ready = stage as? DeleteStage.Ready ?: return
        stage = DeleteStage.Running(ready.plan)
        scope.launch {
            val result = try {
                services.media.delete(ready.plan)
            } catch (_: Exception) {
                failed()
            }
            stage = DeleteStage.Finished(result)
            onDeleted?.invoke()
            onDeleted = null
        }
    }

    /** A running delete cannot be called back, so it stays and ends with its message. */
    fun dismiss() {
        if (stage is DeleteStage.Running) return
        planning?.cancel()
        stage = null
    }
}
