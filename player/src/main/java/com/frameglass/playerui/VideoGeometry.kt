package com.frameglass.playerui

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** F-P6. The names are stored in TrackPrefs.aspectMode, so renaming one forgets what people chose. */
internal enum class AspectMode {
    FIT,
    FILL,
    RATIO_16_9,
    RATIO_4_3,
    CROP,
    ;

    fun next(): AspectMode = entries[(ordinal + 1) % entries.size]
}

internal data class VideoBox(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

internal data class VideoTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
)

/**
 * The video view always keeps its fit box as its layout size, and every mode is a transform of that box,
 * so changing mode never resizes the surface. The subtitle layers sit in [subtitleBox], centred on the
 * same point, so they keep their geometry and stay on screen in Crop and while zoomed.
 */
internal object VideoGeometry {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 4f
    private const val ZOOM_SNAP = 1.03f

    private const val RATIO_16_9 = 16f / 9f
    private const val RATIO_4_3 = 4f / 3f

    fun fit(viewWidth: Int, viewHeight: Int, aspect: Float): VideoBox {
        if (viewWidth <= 0 || viewHeight <= 0 || aspect <= 0f) return VideoBox(0, 0, max(viewWidth, 0), max(viewHeight, 0))
        val viewAspect = viewWidth.toFloat() / viewHeight
        val width = if (aspect >= viewAspect) viewWidth else (viewHeight * aspect).roundToInt()
        val height = if (aspect >= viewAspect) (viewWidth / aspect).roundToInt() else viewHeight
        return VideoBox((viewWidth - width) / 2, (viewHeight - height) / 2, width, height)
    }

    fun transform(
        viewWidth: Int,
        viewHeight: Int,
        box: VideoBox,
        mode: AspectMode,
        zoom: Float,
        panX: Float,
        panY: Float,
    ): VideoTransform {
        if (box.width <= 0 || box.height <= 0) return VideoTransform()
        val widthRatio = viewWidth.toFloat() / box.width
        val heightRatio = viewHeight.toFloat() / box.height
        var baseX = 1f
        var baseY = 1f
        when (mode) {
            AspectMode.FIT -> Unit
            AspectMode.FILL -> {
                baseX = widthRatio
                baseY = heightRatio
            }
            AspectMode.CROP -> {
                baseX = max(widthRatio, heightRatio)
                baseY = baseX
            }
            AspectMode.RATIO_16_9, AspectMode.RATIO_4_3 -> {
                val target = fit(viewWidth, viewHeight, if (mode == AspectMode.RATIO_16_9) RATIO_16_9 else RATIO_4_3)
                baseX = target.width.toFloat() / box.width
                baseY = target.height.toFloat() / box.height
            }
        }
        val scaleX = baseX * zoom
        val scaleY = baseY * zoom
        return VideoTransform(
            scaleX = scaleX,
            scaleY = scaleY,
            translationX = panX.coerceIn(-maxPan(box.width, scaleX, viewWidth), maxPan(box.width, scaleX, viewWidth)),
            translationY = panY.coerceIn(-maxPan(box.height, scaleY, viewHeight), maxPan(box.height, scaleY, viewHeight)),
        )
    }

    /**
     * The fit box, cut down to the picture on any axis a mode narrows it. Without that a 4:3 picture would
     * leave text and positioned signs out on the black bars. Zoom stays out of this, so a pinch moves no text.
     */
    fun subtitleBox(viewWidth: Int, viewHeight: Int, box: VideoBox, mode: AspectMode): VideoBox {
        val picture = transform(viewWidth, viewHeight, box, mode, MIN_ZOOM, 0f, 0f)
        val width = min(box.width, (box.width * picture.scaleX).roundToInt())
        val height = min(box.height, (box.height * picture.scaleY).roundToInt())
        return VideoBox((viewWidth - width) / 2, (viewHeight - height) / 2, width, height)
    }

    private fun maxPan(size: Int, scale: Float, view: Int): Float = max(0f, (size * scale - view) / 2f)

    /** Near 1x counts as 1x, so a pinch can land exactly on the unzoomed picture. */
    fun clampZoom(zoom: Float): Float {
        val clamped = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        return if (clamped < ZOOM_SNAP) MIN_ZOOM else clamped
    }
}

/** What is stored per title: the mode, then the zoom when there is one, such as "CROP" or "FIT@1.50". */
internal data class AspectMemory(val mode: AspectMode, val zoom: Float) {
    fun encode(): String =
        if (zoom > VideoGeometry.MIN_ZOOM) "${mode.name}$SEPARATOR${"%.2f".format(Locale.ROOT, zoom)}" else mode.name

    companion object {
        private const val SEPARATOR = '@'

        fun decode(text: String?): AspectMemory {
            val name = text?.substringBefore(SEPARATOR)
            val mode = AspectMode.entries.firstOrNull { it.name == name } ?: AspectMode.FIT
            val zoom = text?.substringAfter(SEPARATOR, "")?.toFloatOrNull() ?: VideoGeometry.MIN_ZOOM
            return AspectMemory(mode, VideoGeometry.clampZoom(zoom))
        }
    }
}
