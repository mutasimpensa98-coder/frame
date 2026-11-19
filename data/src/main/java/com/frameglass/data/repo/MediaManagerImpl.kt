package com.frameglass.data.repo

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import com.frameglass.data.R
import com.frameglass.data.api.CleanupSource
import com.frameglass.data.api.CleanupSuggestions
import com.frameglass.data.api.DeletePlan
import com.frameglass.data.api.DeleteResult
import com.frameglass.data.api.MediaEntry
import com.frameglass.data.api.MediaGroup
import com.frameglass.data.api.MediaManager
import com.frameglass.data.api.StorageSummary
import com.frameglass.data.api.TrashConsentSource
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.MediaFile
import com.frameglass.data.logic.CleanupRules
import com.frameglass.data.logic.DeletePlanner
import com.frameglass.data.logic.DeleteTarget
import com.frameglass.data.logic.MediaViews
import com.frameglass.data.logic.SidecarMatcher
import com.frameglass.data.logic.Snapshot
import com.frameglass.data.scan.CatchUp
import com.frameglass.data.scan.DeviceStorage
import com.frameglass.data.scan.MediaStoreTrash
import com.frameglass.data.scan.SidecarDisk
import com.frameglass.data.scan.Volumes
import com.frameglass.data.secure.SafeFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The Media Manager of the 2026-11-07 waiver. Deleting a video leaves its row, marked unavailable, so progress,
 * match and cover stay as for any missing file, and a file restored from the trash reconnects by itself.
 * freedBytes counts only what left storage: a trashed file holds its space until the system empties the trash.
 */
internal class MediaManagerImpl(
    private val context: Context,
    private val db: FrameDb,
    private val snapshots: SnapshotSource,
    private val volumes: Volumes,
    private val store: MediaStoreTrash,
    private val catchUp: () -> CatchUp,
) : MediaManager, TrashConsentSource, CleanupSource {
    private val running = Mutex()
    private val consents = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    private val answers = Channel<Boolean>(Channel.CONFLATED)

    private fun <T> view(block: (Snapshot) -> T): Flow<T> =
        snapshots.flow.map { block(it) }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private val clipsName: String get() = context.getString(R.string.data_media_clips)

    override val summary: Flow<StorageSummary> =
        view { MediaViews.summary(it, DeviceStorage.totalBytes(), DeviceStorage.freeBytes()) }

    override fun entries(group: MediaGroup): Flow<List<MediaEntry>> = view { MediaViews.entries(it, group, clipsName) }

    override fun children(entry: MediaEntry): Flow<List<MediaEntry>> = view { MediaViews.children(it, entry) }

    override val cleanupSuggestions: Flow<CleanupSuggestions> = view { CleanupRules.build(it) }

    override suspend fun planDelete(entries: List<MediaEntry>, includeSidecars: Boolean): DeletePlan = withContext(Dispatchers.IO) {
        val paths = volumes.pathMap()
        val videos = MediaViews.expand(snapshots.fresh(), entries).mapNotNull { file ->
            val base = paths[file.volumeUuid] ?: return@mapNotNull null
            File(base, file.relPath).takeIf { SafeFiles.isPlain(it, File(base)) }?.let { file to it }
        }
        val sidecars = videos.groupBy { (_, disk) -> disk.parentFile }.flatMap { (folder, group) ->
            if (folder == null) emptyList() else sidecarsOf(folder, group)
        }
        val index = store.lookup(videos.map { (_, disk) -> disk.path } + sidecars.map { it.path })
        fun recoverable(path: String) = path.lowercase() in index
        DeletePlanner.plan(
            videos = videos.map { (file, disk) ->
                DeleteTarget(disk.path, disk.name, disk.parent.orEmpty(), disk.length(), file.rowId, listOf(file.rowId), recoverable(disk.path))
            },
            sidecars = sidecars.map { it.copy(recoverable = recoverable(it.path)) },
            includeSidecars = includeSidecars,
        )
    }

    /** A subtitle that a video staying in the folder also reads is left out, because it is not the owner's to lose. */
    private fun sidecarsOf(folder: File, group: List<Pair<MediaFile, File>>): List<DeleteTarget> {
        val rows = group.associate { (file, disk) -> disk.name to file.rowId }
        return SidecarDisk.claims(folder, rows.keys).filter { !it.shared }.mapNotNull { claim ->
            val file = File(claim.hit.file.folder, claim.hit.file.name)
            if (!plainBeside(file, folder)) return@mapNotNull null
            DeleteTarget(file.path, file.name, file.parent.orEmpty(), file.length(), null, claim.owners.mapNotNull(rows::get), false)
        }
    }

    private fun plainBeside(file: File, folder: File): Boolean =
        SidecarMatcher.isSubtitle(file.name) && SafeFiles.isPlain(file, folder)

    private enum class Outcome { TRASHED, DELETED, NEEDS_CONSENT, FAILED }

    private inner class Run(targets: List<DeleteTarget>) {
        private val index: Map<String, Uri> = store.lookup(targets.map { it.path })
        val waiting = ArrayList<Pair<DeleteTarget, File>>()
        val failed = ArrayList<String>()
        val gone = HashSet<Long>()
        var trashed = 0
        var deleted = 0
        var freed = 0L

        fun uriOf(target: DeleteTarget): Uri? = index[target.path.lowercase()]

        /**
         * A file promised to the trash is never deleted for good. If the trash is gone or refused, it stays and is
         * reported. A file the plan called permanent still goes to the trash when it has become possible.
         */
        private fun remove(target: DeleteTarget, file: File): Outcome {
            val uri = uriOf(target)
            if (uri != null && store.trash(uri) && !file.exists()) return Outcome.TRASHED
            if (target.recoverable) return if (uri != null) Outcome.NEEDS_CONSENT else Outcome.FAILED
            return if (SafeFiles.remove(file)) Outcome.DELETED else Outcome.FAILED
        }

        fun run(target: DeleteTarget, file: File?) {
            val outcome = if (file == null) Outcome.FAILED else remove(target, file)
            when (outcome) {
                Outcome.TRASHED -> done(target, trash = true)
                Outcome.DELETED -> done(target, trash = false)
                Outcome.NEEDS_CONSENT -> waiting += target to file!!
                Outcome.FAILED -> failed += target.name
            }
        }

        private fun done(target: DeleteTarget, trash: Boolean) {
            if (trash) {
                trashed++
            } else {
                deleted++
                freed += target.sizeBytes
            }
            target.rowId?.let(gone::add)
        }

        fun settle(confirmed: Boolean) {
            for ((target, file) in waiting) {
                if (confirmed && !file.exists()) done(target, trash = true) else failed += target.name
            }
            waiting.clear()
        }
    }

    /** The row must still be the file the owner saw: same place, and the same size as when he saw it. */
    private suspend fun checkedVideo(target: DeleteTarget, paths: Map<String, String>): File? {
        val row = target.rowId?.let { db.scan().file(it) } ?: return null
        val base = paths[row.volumeUuid] ?: return null
        val file = File(base, row.relPath)
        val same = row.available && file.path == target.path && file.length() == target.sizeBytes
        return file.takeIf { same && SafeFiles.isPlain(it, File(base)) }
    }

    private fun checkedSidecar(target: DeleteTarget, owner: MediaFile?, paths: Map<String, String>): File? {
        val base = owner?.let { paths[it.volumeUuid] } ?: return null
        val file = File(target.path)
        val folder = File(base, owner.relPath).parentFile ?: return null
        val beside = file.parentFile == folder || file.parentFile?.parentFile == folder
        return file.takeIf { beside && it.length() == target.sizeBytes && plainBeside(it, folder) }
    }

    override suspend fun delete(plan: DeletePlan): DeleteResult = withContext(Dispatchers.IO + NonCancellable) {
        running.withLock {
            val targets = plan.entries.mapNotNull(DeletePlanner::decode)
            val paths = volumes.pathMap()
            val run = Run(targets)
            val (videos, listed) = targets.partition { it.rowId != null }
            val sidecars = if (plan.includeSidecars) listed else emptyList()
            for (target in videos) run.run(target, checkedVideo(target, paths))

            // A subtitle goes only with its video. If that video stayed, so does the subtitle.
            val pending = run.waiting.mapNotNullTo(HashSet()) { (target, _) -> target.rowId }
            for (target in sidecars) {
                if (target.owners.isEmpty() || !target.owners.all { it in run.gone || it in pending }) continue
                val file = checkedSidecar(target, db.scan().file(target.owners.first()), paths)
                if (file != null && target.owners.any { it in pending }) {
                    val uri = run.uriOf(target)
                    if (uri != null) run.waiting += target to file else run.failed += target.name
                } else {
                    run.run(target, file)
                }
            }
            if (run.waiting.isNotEmpty()) {
                val uris = run.waiting.mapNotNull { (target, _) -> run.uriOf(target) }
                run.settle(askConsent(uris))
            }

            run.gone.toList().chunked(ROW_CHUNK).forEach { db.scan().markUnavailable(it) }
            if (targets.isNotEmpty()) catchUp().onFoldersChanged()
            DeleteResult(run.trashed, run.deleted, run.failed, run.freed)
        }
    }

    override val trashConsents: Flow<IntentSender> get() = consents

    override fun answerTrashConsent(confirmed: Boolean) {
        answers.trySend(confirmed)
    }

    /** With no screen listening nothing can ask the owner, so those files stay and are reported. */
    private suspend fun askConsent(uris: List<Uri>): Boolean {
        if (consents.subscriptionCount.value == 0) return false
        val sender = store.consentRequest(uris) ?: return false
        answers.tryReceive()
        consents.emit(sender)
        return withTimeoutOrNull(CONSENT_WAIT_MS) { answers.receive() } == true
    }

    private companion object {
        const val ROW_CHUNK = 500
        val CONSENT_WAIT_MS = TimeUnit.MINUTES.toMillis(2)
    }
}
