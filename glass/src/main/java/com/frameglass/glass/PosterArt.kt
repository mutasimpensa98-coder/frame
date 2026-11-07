package com.frameglass.glass

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

// A square picture still fills a wide frame well enough, so only a clearly tall one counts.
private const val PortraitMargin = 1.1f
private const val MaxKnownShapes = 512
private const val WashDecodePx = 96
private const val WashWidthPx = 48
private const val WashRadiusPx = 4
private const val WashPasses = 3
private const val DarkWashVeil = 0.42f
private const val LightWashVeil = 0.30f
private val StageShadow = 20.dp
private val StageShadowColor = Color.Black.copy(alpha = 0.55f)

/** True for a picture clearly taller than it is wide, such as a poster or a phone video held upright. */
fun isPortrait(width: Int, height: Int): Boolean = width > 0 && height > 0 && height > width * PortraitMargin

/**
 * The shape of each local picture, kept by path so a screen can pick its layout on the first frame.
 * [CoverImage] records every picture it draws, and [rememberPortrait] reads the file header of the rest.
 */
object ArtShapes {
    private val known = LruCache<String, Boolean>(MaxKnownShapes)

    /** Null until the picture has been drawn or measured. */
    fun portrait(path: String): Boolean? = known.get(path)

    internal fun record(path: String, width: Int, height: Int) {
        if (width > 0 && height > 0) known.put(path, isPortrait(width, height))
    }

    /** Reads only the header. Null when the file is missing or is not a picture. */
    suspend fun measure(path: String): Boolean? = known.get(path) ?: withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth > 0 && options.outHeight > 0) isPortrait(options.outWidth, options.outHeight) else null
    }?.also { known.put(path, it) }
}

/** Whether the picture at [path] is portrait, or null while that is not known yet. */
@Composable
fun rememberPortrait(path: String?): Boolean? {
    var portrait by remember(path) { mutableStateOf(path?.let(ArtShapes::portrait)) }
    LaunchedEffect(path) {
        if (path != null && portrait == null) portrait = ArtShapes.measure(path)
    }
    return portrait
}

/**
 * A separable box blur run [passes] times, which comes close to a Gaussian. Edges repeat their last pixel, so
 * no dark rim creeps in from outside the picture.
 */
internal fun softenPixels(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int): IntArray {
    require(width >= 0 && height >= 0 && pixels.size == width * height) { "pixels do not match $width x $height" }
    var source = pixels.copyOf()
    if (radius <= 0 || passes <= 0 || pixels.isEmpty()) return source
    var target = IntArray(pixels.size)
    repeat(passes) {
        boxPass(source, target, width, height, radius, horizontal = true)
        boxPass(target, source, width, height, radius, horizontal = false)
    }
    return source
}

private fun boxPass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val span = 2 * radius + 1
    val half = span / 2
    for (line in 0 until lines) {
        fun index(position: Int): Int {
            val clamped = position.coerceIn(0, length - 1)
            return if (horizontal) line * width + clamped else clamped * width + line
        }
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val pixel = src[index(offset)]
            alpha += pixel ushr 24
            red += pixel shr 16 and 0xFF
            green += pixel shr 8 and 0xFF
            blue += pixel and 0xFF
        }
        for (position in 0 until length) {
            dst[index(position)] = ((alpha + half) / span shl 24) or
                ((red + half) / span shl 16) or
                ((green + half) / span shl 8) or
                ((blue + half) / span)
            val leaving = src[index(position - radius)]
            val entering = src[index(position + radius + 1)]
            alpha += (entering ushr 24) - (leaving ushr 24)
            red += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
            green += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

/**
 * Blurs a tiny copy once, when the picture loads. Drawn stretched, it costs nothing per frame and adds no blurred
 * layer to the limit of three (brief 7.3), so it also stays on in degraded mode.
 */
private object Soften : Transformation() {
    override val cacheKey: String = "frameglass.soften.1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = WashWidthPx
        val height = max(1, (WashWidthPx * input.height.toFloat() / input.width.coerceAtLeast(1)).roundToInt())
        val small = input.scale(width, height)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== input) small.recycle()
        return createBitmap(width, height).apply {
            setPixels(softenPixels(pixels, width, height, WashRadiusPx, WashPasses), 0, width, 0, 0, width, height)
        }
    }
}

/** A poster blurred into a soft field of its own colours, under a veil of the canvas. */
@Composable
fun PosterWash(path: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = FrameTheme.colors
    val request = remember(path, context) {
        path?.let {
            ImageRequest.Builder(context)
                .data(File(it))
                .size(WashDecodePx)
                .allowHardware(false)
                .transformations(Soften)
                .crossfade(true)
                .build()
        }
    }
    Box(modifier.background(colors.elevated)) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(colors.canvas.copy(alpha = if (colors.isLight) LightWashVeil else DarkWashVeil)),
        )
    }
}

/** A poster shown whole and lifted off its wash, where a wide backdrop would otherwise go. */
@Composable
fun StagePoster(path: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    CoverImage(
        path = path,
        contentDescription = contentDescription,
        modifier = modifier.shadow(
            elevation = StageShadow,
            shape = FrameShapes.Poster,
            ambientColor = StageShadowColor,
            spotColor = StageShadowColor,
        ),
        shape = FrameShapes.Poster,
    )
}

/** What a wide slot draws: a picture that fills it, or a portrait one whole on its wash. */
internal sealed interface WideSlot {
    data class Fill(val path: String?) : WideSlot

    data class Pillarbox(val path: String) : WideSlot
}

/**
 * A wide picture fills the slot. A portrait one is pillarboxed rather than cut into a strip, and with no picture
 * at all a portrait [fallback], such as the series poster, stands in. A wide fallback does not: a frame of the
 * series would pass for this episode.
 */
internal fun wideSlot(path: String?, portrait: Boolean?, fallback: String?, fallbackPortrait: Boolean?): WideSlot = when {
    path != null && portrait != true -> WideSlot.Fill(path)
    path != null -> WideSlot.Pillarbox(path)
    fallback != null && fallbackPortrait == true -> WideSlot.Pillarbox(fallback)
    else -> WideSlot.Fill(null)
}

/** A 16:9 slot for a still, a frame or a Continue card. See [wideSlot]. */
@Composable
fun WideArt(
    path: String?,
    modifier: Modifier = Modifier,
    fallback: String? = null,
    shape: Shape = FrameShapes.Poster,
) {
    val slot = wideSlot(path, rememberPortrait(path), fallback, rememberPortrait(fallback.takeIf { path == null }))
    when (slot) {
        is WideSlot.Fill -> CoverImage(slot.path, null, modifier, shape)
        is WideSlot.Pillarbox -> {
            val context = LocalContext.current
            val colors = FrameTheme.colors
            val request = remember(slot.path, context) {
                ImageRequest.Builder(context).data(File(slot.path)).crossfade(true).build()
            }
            Box(modifier.clip(shape).border(0.5.dp, colors.hairline, shape)) {
                PosterWash(slot.path, Modifier.matchParentSize())
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
