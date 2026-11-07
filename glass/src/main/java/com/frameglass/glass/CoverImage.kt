package com.frameglass.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File

private const val ShimmerMillis = 1400L
private const val ShimmerBand = 0.7f

/** A placeholder shape while something loads, with a soft shimmer. Never a spinner. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = FrameShapes.Poster) {
    val shimmer = rememberEffectsLevel() == EffectsLevel.Full
    val colors = FrameTheme.colors
    val phase = remember { mutableFloatStateOf(0f) }
    if (shimmer) {
        // Taken from the frame clock, so every skeleton on screen moves together.
        LaunchedEffect(Unit) {
            while (true) {
                withFrameMillis { time -> phase.floatValue = (time % ShimmerMillis) / ShimmerMillis.toFloat() }
            }
        }
    }
    Box(
        modifier
            .clip(shape)
            .background(colors.elevated)
            .drawWithCache {
                val band = size.width * ShimmerBand
                val light = Brush.horizontalGradient(
                    listOf(Color.Transparent, colors.shimmer, Color.Transparent),
                    startX = 0f,
                    endX = band,
                )
                val rtl = layoutDirection == LayoutDirection.Rtl
                onDrawBehind {
                    if (shimmer) {
                        val travelled = phase.floatValue * (size.width + band) - band
                        translate(left = if (rtl) size.width - band - travelled else travelled) {
                            drawRect(light, size = Size(band, size.height))
                        }
                    }
                }
            },
    )
}

/** A local image with a [Skeleton] until it loads. A null [path] shows a plain surface. */
@Composable
fun CoverImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = FrameShapes.Poster,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val colors = FrameTheme.colors
    var loading by remember(path) { mutableStateOf(path != null) }
    val request = remember(path, context) {
        path?.let { ImageRequest.Builder(context).data(File(it)).crossfade(true).build() }
    }
    Box(
        modifier
            .clip(shape)
            .background(colors.elevated)
            .border(0.5.dp, colors.hairline, shape),
    ) {
        if (loading) {
            Skeleton(Modifier.matchParentSize(), RectangleShape)
        }
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) {
                        loading = false
                    }
                },
            )
        }
    }
}
