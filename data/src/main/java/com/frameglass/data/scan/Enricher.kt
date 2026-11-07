package com.frameglass.data.scan

import com.frameglass.data.db.EnrichState
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Probes files and grabs their local frame after the fast scan pass, so rows appear first and fill in a
 * moment later. Each row is claimed before work starts: a file that crashes the native probe twice is
 * marked failed instead of crashing the app on every open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class Enricher(
    private val db: FrameDb,
    private val volumes: Volumes,
    private val images: ImageStore,
) {
    private val pool = Dispatchers.IO.limitedParallelism(PARALLEL)
    private val recovered = AtomicBoolean(false)
    private val lock = Mutex()

    private suspend fun recover() {
        if (!recovered.compareAndSet(false, true)) return
        val dao = db.scan()
        dao.failStaleClaims(MAX_TRIES)
        dao.releaseClaims()
    }

    /** Runs until no row is left or the coroutine is stopped. Every finished row is already saved. */
    suspend fun drain() = lock.withLock {
        recover()
        val dao = db.scan()
        while (true) {
            coroutineContext.ensureActive()
            val paths = volumes.pathMap()
            val batch = dao.nextToEnrich(BATCH).filter { it.volumeUuid in paths }
            if (batch.isEmpty()) return@withLock
            dao.claim(batch.map { it.rowId })
            coroutineScope {
                batch.map { file -> async(pool) { enrich(file, paths.getValue(file.volumeUuid)) } }.awaitAll()
            }
        }
    }

    private suspend fun enrich(file: MediaFile, base: String) {
        val path = File(base, file.relPath).path
        val facts = Prober.probe(path)
        val target = images.frameFile(file.fileId)
        val colors = if (target.length() > 0) CoverColors.fromFile(target.path) else FrameGrabber.grab(path, facts.durationMs, target)
        val frame = target.path.takeIf { target.length() > 0 }
        withContext(Dispatchers.IO) {
            db.scan().saveProbe(
                rowId = file.rowId,
                durationMs = facts.durationMs.takeIf { it > 0 } ?: file.durationMs,
                width = facts.width,
                height = facts.height,
                videoCodec = facts.videoCodec,
                hdrType = facts.hdrType,
                audioSummary = facts.audioSummary,
                hasEmbeddedSub = facts.hasEmbeddedSub,
                framePath = frame,
                state = EnrichState.DONE,
            )
            val titleId = file.titleId
            if (titleId != null && frame != null) {
                val titles = db.titles()
                titles.fillFrame(titleId, frame)
                if (colors != null) titles.fillColors(titleId, colors.dominant, colors.luma)
            }
        }
    }

    private companion object {
        const val PARALLEL = 3
        const val BATCH = 12
        const val MAX_TRIES = 2
    }
}
