package com.frameglass.data.scan

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import android.content.Context
import android.util.Log
import com.frameglass.data.db.FrameDb
import com.frameglass.data.logic.Clock
import com.frameglass.data.meta.CacheSweeper
import com.frameglass.data.meta.MetadataPipeline
import com.frameglass.data.repo.SettingsStore
import com.frameglass.data.work.MetadataWorker
import com.frameglass.data.work.ScanWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Runs one job at a time. A kick during a run makes it run once more, and any number of kicks collapse into one. */
internal class Lane(private val scope: CoroutineScope, private val block: suspend () -> Unit) {
    private val running = AtomicBoolean(false)
    private val again = AtomicBoolean(false)

    fun kick() {
        again.set(true)
        start()
    }

    private fun start() {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (again.getAndSet(false)) {
                    try {
                        block()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // One bad file or row must not end catch-up for the session. The next open retries.
                        Log.w("FrameData", "catch-up step failed", e)
                    }
                }
            } finally {
                running.set(false)
            }
            if (again.get()) start()
        }
    }
}

internal class WorkScheduler(private val context: Context) {
    private val manager get() = WorkManager.getInstance(context)

    fun ensurePeriodicScan() {
        val request = PeriodicWorkRequestBuilder<ScanWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints(requiresBatteryNotLow = true))
            .build()
        manager.enqueueUniquePeriodicWork(SCAN, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Waits for a network, so a queue parked offline resumes as soon as the network returns. */
    fun scheduleMetadata(delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<MetadataWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniqueWork(METADATA, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val SCAN = "frameglass-scan"
        const val METADATA = "frameglass-metadata"
    }
}

/**
 * Section 4.7. Catch-up on app open is the primary path and everything here returns at once. Scan, frames
 * and metadata run in separate lanes, so slow metadata cannot hold back a mount rescan, which has 10 s.
 */
internal class CatchUp(
    private val scope: CoroutineScope,
    private val db: FrameDb,
    private val scanner: Scanner,
    private val enricher: Enricher,
    private val pipeline: MetadataPipeline,
    private val sweeper: CacheSweeper,
    private val signal: MediaSignal,
    private val settings: SettingsStore,
    private val work: WorkScheduler,
    private val clock: Clock,
) {
    private val metadataLane = Lane(scope) { runMetadata() }
    private val framesLane = Lane(scope) { drainFrames() }
    private val scanLane = Lane(scope) { scanOnce() }
    private var debounce: Job? = null

    /** Matches wait for durations, so finished probes can release them. A running scan kicks matching itself when it ends. */
    private suspend fun drainFrames() {
        enricher.drain()
        if (!scanner.running) metadataLane.kick()
    }

    private suspend fun scanOnce() {
        framesLane.kick()
        scanner.scan()
        settings.setMediaSignal(signal.read())
        framesLane.kick()
        metadataLane.kick()
    }

    private suspend fun sweepDaily() {
        val now = clock.now()
        if (now - settings.lastSweepAt() < SWEEP_EVERY_MS) return
        sweeper.sweep()
        settings.setLastSweepAt(now)
    }

    private suspend fun runMetadata() {
        sweepDaily()
        pipeline.drain()?.let(work::scheduleMetadata)
    }

    fun onOpen() {
        scanLane.kick()
        scope.launch { runCatching { work.ensurePeriodicScan() } }
    }

    fun onFoldersChanged() = scanLane.kick()

    fun onNewRows() = framesLane.kick()

    fun continueMetadata() = metadataLane.kick()

    fun onMetadataEnabled() {
        scope.launch {
            runCatching { db.meta().makeAllDue(clock.now()) }
            metadataLane.kick()
        }
    }

    fun onMounted() = scanLane.kick()

    fun onUnmounted(volume: String) {
        scope.launch { runCatching { db.scan().markVolumeUnavailable(volume) } }
    }

    /** MediaStore changed while the app is visible. It settles first, because copies fire many events. */
    fun onMediaChanged() {
        debounce?.cancel()
        debounce = scope.launch {
            delay(DEBOUNCE_MS)
            if (settings.mediaSignal() != signal.read()) scanLane.kick()
        }
    }

    suspend fun scanNow() {
        scanner.scan()
        settings.setMediaSignal(signal.read())
        framesLane.kick()
        metadataLane.kick()
    }

    /** One background run, for the periodic worker. The caller bounds the time. */
    suspend fun background() {
        if (settings.mediaSignal() != signal.read()) scanNow()
        enricher.drain()
        sweepDaily()
        pipeline.drain()?.let(work::scheduleMetadata)
    }

    private companion object {
        const val DEBOUNCE_MS = 3_000L
        val SWEEP_EVERY_MS = TimeUnit.HOURS.toMillis(24)
    }
}
