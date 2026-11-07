package com.frameglass.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

private const val SampleWidth = 72
private const val SampleHeight = 156
private const val SamplePeriodMillis = 250L
private const val EdgeSkip = 0.08f
private const val LevelStep = 6f

/**
 * Estimates the level behind the tab bar and top bar at 4 Hz at most (brief 7.3), so the contrast
 * rule works over any art without the screens reporting anything. One small copy of the window
 * serves every surface. It sleeps while nothing draws, so an idle screen costs nothing.
 */
internal class GlassSampler {
    private val layers = ArrayList<GlassLayer>()

    var layerCount by mutableIntStateOf(0)
        private set

    fun add(layer: GlassLayer) {
        layers += layer
        layerCount = layers.size
    }

    fun remove(layer: GlassLayer) {
        layers -= layer
        layerCount = layers.size
    }

    suspend fun run(window: Window, view: View) {
        val bitmap = createBitmap(SampleWidth, SampleHeight)
        val pixels = IntArray(SampleWidth * SampleHeight)
        val levels = FloatArray(pixels.size)
        val handler = Handler(Looper.getMainLooper())
        val redrawn = Channel<Unit>(Channel.CONFLATED)
        val listener = ViewTreeObserver.OnDrawListener { redrawn.trySend(Unit) }
        val observer = view.viewTreeObserver
        observer.addOnDrawListener(listener)
        redrawn.trySend(Unit)
        try {
            while (true) {
                redrawn.receive()
                delay(SamplePeriodMillis)
                if (!copyWindow(window, bitmap, handler)) continue
                bitmap.getPixels(pixels, 0, SampleWidth, 0, 0, SampleWidth, SampleHeight)
                val decor = window.decorView
                for (layer in layers) measure(layer, pixels, levels, decor.width, decor.height)
            }
        } finally {
            (if (observer.isAlive) observer else view.viewTreeObserver).removeOnDrawListener(listener)
        }
    }

    private suspend fun copyWindow(window: Window, bitmap: Bitmap, handler: Handler): Boolean =
        suspendCancellableCoroutine { continuation ->
            try {
                PixelCopy.request(
                    window,
                    bitmap,
                    { result -> continuation.resume(result == PixelCopy.SUCCESS) },
                    handler,
                )
            } catch (_: IllegalArgumentException) {
                // The window has no surface at this moment. The next redraw tries again.
                continuation.resume(false)
            }
        }

    private fun measure(
        layer: GlassLayer,
        pixels: IntArray,
        levels: FloatArray,
        windowWidth: Int,
        windowHeight: Int,
    ) {
        val coordinates = layer.coordinates?.takeIf { it.isAttached } ?: return
        val bounds = coordinates.boundsInWindow()
        if (bounds.isEmpty || windowWidth <= 0 || windowHeight <= 0) return
        val tint = (layer.tint?.value ?: return) * layer.alpha()

        val left = sampleIndex(bounds.left + bounds.width * EdgeSkip, windowWidth, SampleWidth)
        val right = sampleIndex(bounds.right - bounds.width * EdgeSkip, windowWidth, SampleWidth)
        val top = sampleIndex(bounds.top + bounds.height * EdgeSkip, windowHeight, SampleHeight)
        val bottom = sampleIndex(bounds.bottom - bounds.height * EdgeSkip, windowHeight, SampleHeight)
        var count = 0
        for (y in top..bottom) {
            for (x in left..right) {
                levels[count++] = GlassContrast.pixelLevel(pixels[y * SampleWidth + x])
            }
        }
        if (count == 0) return

        // The 80th percentile keeps the bright side of the art and drops white icons and text.
        levels.sort(0, count)
        val level = GlassContrast.backdropLevel(levels[(count - 1) * 4 / 5], tint)
        if (abs(level - layer.level) >= LevelStep) layer.level = level
    }

    private fun sampleIndex(position: Float, windowSize: Int, sampleSize: Int): Int =
        (position / windowSize * sampleSize).toInt().coerceIn(0, sampleSize - 1)
}

/** Runs the sampler while the screen is resumed and a surface needs it. */
@Composable
internal fun GlassSampling(glass: GlassState) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val active = glass.sampler.layerCount > 0
    LifecycleResumeEffect(glass, window, active) {
        val job = if (active) scope.launch { glass.sampler.run(window, view) } else null
        onPauseOrDispose { job?.cancel() }
    }
}
