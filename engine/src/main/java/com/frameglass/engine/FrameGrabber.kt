package com.frameglass.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.inspector.frame.FrameExtractor
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

private suspend fun <T : Any> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (failed: ExecutionException) {
                continuation.resumeWithException(failed.cause ?: failed)
            } catch (cancelled: CancellationException) {
                continuation.cancel(cancelled)
            }
        },
        Runnable::run,
    )
    continuation.invokeOnCancellation { cancel(false) }
}

/**
 * F-T1. Keyframe previews for the seek bar, kept on disk so a second scrub costs no decoding.
 * Nothing here decodes until the first scrub, and playback never waits on it.
 *
 * With [keepOnDisk] off nothing is read or written there, because frames of a private video must not sit
 * outside the private folder.
 */
@OptIn(UnstableApi::class)
class SeekThumbnails(
    context: Context,
    private val uri: Uri,
    cacheKey: String,
    durationMs: Long,
    private val keepOnDisk: Boolean = true,
) : Closeable {
    private val appContext = context.applicationContext
    private val folder = File(File(appContext.cacheDir, CACHE_FOLDER), cacheKey)
    private val spacingMs = max(MIN_SPACING_MS, durationMs / MAX_FRAMES)
    private var extractor: FrameExtractor? = null

    fun bucketOf(positionMs: Long): Long = positionMs.coerceAtLeast(0) / spacingMs

    /** Null when this file has no preview, such as a codec only FFmpeg can decode. */
    suspend fun frame(bucket: Long): Bitmap? = withContext(Dispatchers.IO) {
        val cached = File(folder, "$bucket.jpg").takeIf { keepOnDisk }
        if (cached != null && cached.length() > 0) return@withContext BitmapFactory.decodeFile(cached.path)
        try {
            val bitmap = extractor().getFrame(bucket * spacingMs).await().bitmap
            cached?.let { store(bitmap, it) }
            bitmap
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun extractor(): FrameExtractor = extractor ?: FrameExtractor.Builder(appContext, MediaItem.fromUri(uri))
        .setSeekParameters(SeekParameters.CLOSEST_SYNC)
        .setEffects(listOf(Presentation.createForHeight(THUMBNAIL_HEIGHT)))
        .build()
        .also { extractor = it }

    private fun store(bitmap: Bitmap, target: File) {
        runCatching {
            folder.mkdirs()
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        }
    }

    override fun close() {
        extractor?.close()
        extractor = null
    }

    companion object {
        private const val CACHE_FOLDER = "thumbs"
        private const val THUMBNAIL_HEIGHT = 216
        private const val JPEG_QUALITY = 70
        private const val MIN_SPACING_MS = 4_000L
        private const val MAX_FRAMES = 300L
        private const val CACHE_LIMIT_BYTES = 256L * 1024 * 1024

        /** Keeps the disk cache bounded, dropping the folders that were used longest ago. */
        fun trimCache(context: Context) {
            val root = File(context.applicationContext.cacheDir, CACHE_FOLDER)
            val folders = root.listFiles().orEmpty().filter { it.isDirectory }.sortedByDescending { it.lastModified() }
            var used = 0L
            for (folder in folders) {
                used += folder.listFiles().orEmpty().sumOf { it.length() }
                if (used > CACHE_LIMIT_BYTES) folder.deleteRecursively()
            }
        }
    }
}

/** F-T4: one frame at full video resolution, with no subtitles in it. */
@OptIn(UnstableApi::class)
object FrameGrabber {
    suspend fun fullFrame(context: Context, uri: Uri, positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val extractor = FrameExtractor.Builder(context.applicationContext, MediaItem.fromUri(uri)).build()
        try {
            extractor.getFrame(positionMs).await().bitmap
        } finally {
            extractor.close()
        }
    }
}
