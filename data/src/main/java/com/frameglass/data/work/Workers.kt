package com.frameglass.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frameglass.data.DataServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private val BUDGET_MS = TimeUnit.MINUTES.toMillis(8)

/**
 * The 12 h periodic scan, which is only a bonus per section 4.7: One UI may never run it. Everything it does is
 * checkpointed, so being stopped loses nothing, and the stop reason lands in ScanLog.
 */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val services = DataServices.of(applicationContext)
        return try {
            withTimeoutOrNull(BUDGET_MS) { services.catchUp.background() }
            Result.success()
        } catch (e: CancellationException) {
            services.scanner.noteStop("scan-worker", stopReason)
            throw e
        }
    }
}

/** Waits for a network. Returning retry keeps it alive for the 1 h, 6 h and 24 h rows, which hold their own due times. */
class MetadataWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val services = DataServices.of(applicationContext)
        return try {
            if (services.pipeline.drain(BUDGET_MS) != null) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            services.scanner.noteStop("metadata-worker", stopReason)
            throw e
        }
    }
}
