package com.frameglass.glass

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import java.io.File

/**
 * The one place that decides how an unseen episode's picture is hidden (waiver of 2026-11-12). Modifier.blur and
 * BlurEffect draw with RenderEffect, which arrives in API 31: below it they do nothing, and the picture would show
 * whole. So Android 11 hides it another way, chosen here for the episode list, the Home card and the tour.
 */
object Spoiler {
    /** False on Android 11. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val blurs: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Strong enough that faces and places cannot be read in a 148 dp still, light enough that it still looks like a picture. */
    val Blur = 14.dp

    /**
     * How wide the still is decoded where the phone cannot blur. Stretched over the card it keeps the colours of the
     * picture and nothing in it that can be read.
     */
    internal const val SmearWidthPx = 12
    internal const val SmearHeightPx = 7

    /** How much of drawn art a flat cover hides where the phone cannot blur. */
    internal const val CoverAlpha = 0.86f
}

/**
 * [WideArt] for an episode's own picture. While [hidden] it is blurred, or on Android 11 decoded
 * [Spoiler.SmearWidthPx] px wide and stretched, so the full picture is never drawn there. A [fallback] poster standing
 * in for a missing picture is the series' own and is never hidden.
 */
@Composable
fun SpoilerArt(
    path: String?,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    fallback: String? = null,
    shape: Shape = FrameShapes.Poster,
) {
    when {
        !hidden || path == null -> WideArt(path, modifier, fallback, shape)
        Spoiler.blurs -> WideArt(path, modifier.blur(Spoiler.Blur), fallback, shape)
        else -> SmearedArt(path, modifier, shape)
    }
}

@Composable
private fun SmearedArt(path: String, modifier: Modifier, shape: Shape) {
    val context = LocalContext.current
    val colors = FrameTheme.colors
    // A key of its own, so the memory cache can never answer this request with the full picture.
    val request = remember(path, context) {
        ImageRequest.Builder(context)
            .data(File(path))
            .size(Spoiler.SmearWidthPx, Spoiler.SmearHeightPx)
            .precision(Precision.EXACT)
            .memoryCacheKey("spoiler:$path")
            .build()
    }
    Box(
        modifier
            .clip(shape)
            .background(colors.elevated)
            .border(0.5.dp, colors.hairline, shape),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * For art drawn in code, as on the tour, where the blur itself is set on a graphics layer while [Spoiler.blurs].
 * Where the phone cannot blur, this lays a flat [cover] over the art instead; [hidden] runs from 1, hidden, to 0.
 */
fun Modifier.spoilerCover(cover: Color, hidden: () -> Float): Modifier =
    if (Spoiler.blurs) {
        this
    } else {
        drawWithContent {
            drawContent()
            val amount = hidden().coerceIn(0f, 1f)
            if (amount > 0f) drawRect(cover, alpha = amount * Spoiler.CoverAlpha)
        }
    }
