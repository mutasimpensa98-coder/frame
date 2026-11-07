package com.frameglass.playerui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.frameglass.engine.SeekThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * F-T1. One decode at a time, and always the newest position asked for. A fast scrub skips the frames in
 * between, so the extra decoder never competes with playback for long.
 */
@Stable
internal class ScrubPreviews(private val context: Context, private val scope: CoroutineScope) {
    private var source: SeekThumbnails? = null
    private var worker: Job? = null
    private var wanted = NOTHING
    private var loaded = NOTHING

    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    fun bind(uri: Uri, cacheKey: String, durationMs: Long, keepOnDisk: Boolean) {
        close()
        source = SeekThumbnails(context, uri, cacheKey, durationMs, keepOnDisk)
        scope.launch(Dispatchers.IO) { SeekThumbnails.trimCache(context) }
    }

    fun request(positionMs: Long) {
        val thumbnails = source ?: return
        wanted = thumbnails.bucketOf(positionMs)
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (loaded != wanted) {
                val bucket = wanted
                loaded = bucket
                thumbnails.frame(bucket)?.let { image = it.asImageBitmap() }
            }
        }
    }

    fun endScrub() {
        image = null
        loaded = NOTHING
        wanted = NOTHING
    }

    fun close() {
        worker?.cancel()
        worker = null
        source?.close()
        source = null
        endScrub()
    }

    private companion object {
        const val NOTHING = -1L
    }
}
