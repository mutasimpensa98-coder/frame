package com.frameglass.playerui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume

/** F-T4. Saves into Pictures/Frame Glass through MediaStore, never into the media folders. */
@OptIn(UnstableApi::class)
internal object Screenshots {
    private const val FOLDER = "Frame Glass"
    private const val QUALITY = 100
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

    /**
     * The screen as it looks, subtitles included. A window copy skips the video surface, so each layer is
     * read on its own and drawn back in the order and place the stage shows them.
     */
    suspend fun compose(stage: VideoStage, assView: ViewGroup, cueView: SubtitleView): Bitmap? {
        if (stage.width <= 0 || stage.height <= 0) return null
        val surface = stage.surface
        val frame = createBitmap(surface.width, surface.height)
        val copied = suspendCancellableCoroutine { continuation ->
            try {
                PixelCopy.request(
                    surface,
                    frame,
                    { result -> continuation.resume(result == PixelCopy.SUCCESS) },
                    Handler(Looper.getMainLooper()),
                )
            } catch (_: IllegalArgumentException) {
                continuation.resume(false)
            }
        }
        if (!copied) return null
        val screen = createBitmap(stage.width, stage.height)
        val canvas = Canvas(screen)
        canvas.drawColor(Color.BLACK)
        canvas.withSave {
            translate(surface.left.toFloat(), surface.top.toFloat())
            concat(surface.matrix)
            drawBitmap(frame, 0f, 0f, null)
        }
        (assView.getChildAt(0) as? TextureView)?.bitmap?.let { overlay ->
            canvas.drawBitmap(overlay, assView.left.toFloat(), assView.top + assView.translationY, null)
        }
        drawView(canvas, cueView)
        return screen
    }

    private fun drawView(canvas: Canvas, view: View) {
        canvas.withSave {
            translate(view.left.toFloat(), view.top + view.translationY)
            view.draw(this)
        }
    }

    suspend fun save(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "frame-glass-${LocalDateTime.now().format(STAMP)}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, it) } == true
        }.getOrDefault(false)
        if (written) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } else {
            resolver.delete(uri, null, null)
        }
        written
    }
}
