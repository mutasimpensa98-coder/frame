package com.frameglass.player.settings

import android.content.res.AssetManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LogoAsset = "tmdb/tmdb-logo-alt-short.svg"

private val ViewBox = Regex("""viewBox="0 0 ([\d.]+) ([\d.]+)"""")
private val GradientLine = Regex("""<linearGradient [^>]*y1="([\d.]+)" x2="([\d.]+)" y2="([\d.]+)"""")
private val GradientStop = Regex("""<stop offset="([\d.]+)" stop-color="#([0-9a-fA-F]{6})"/>""")
private val PathData = Regex("""<path [^>]* d="([^"]+)"""")

private class LogoDrawing(val width: Float, val height: Float, val path: Path, val brush: Brush)

// There is no SVG renderer here and F-M12 wants the mark unchanged, so the published file ships as it
// came and this draws its one path with its own gradient. A file of any other shape reads as no logo.
private fun readLogo(assets: AssetManager): LogoDrawing? {
    val svg = assets.open(LogoAsset).bufferedReader().use { it.readText() }
    val (width, height) = ViewBox.find(svg)?.destructured ?: return null
    val (startY, endX, endY) = GradientLine.find(svg)?.destructured ?: return null
    val data = PathData.find(svg)?.groupValues?.get(1) ?: return null
    val stops = GradientStop.findAll(svg).map { stop ->
        val (offset, rgb) = stop.destructured
        offset.toFloat() to Color(0xFF000000 or rgb.toLong(radix = 16))
    }.toList()
    if (stops.size < 2) return null
    return LogoDrawing(
        width = width.toFloat(),
        height = height.toFloat(),
        path = PathParser().parsePathString(data).toPath(),
        brush = Brush.linearGradient(
            colorStops = stops.toTypedArray(),
            start = Offset(0f, startY.toFloat()),
            end = Offset(endX.toFloat(), endY.toFloat()),
        ),
    )
}

/** The official TMDB logo at [height]. Its shape and colours are the file's, and it is never mirrored. */
@Composable
internal fun TmdbLogo(description: String, height: Dp, modifier: Modifier = Modifier) {
    val assets = LocalContext.current.assets
    val loaded by produceState<LogoDrawing?>(null, assets) {
        value = withContext(Dispatchers.IO) { runCatching { readLogo(assets) }.getOrNull() }
    }
    val logo = loaded ?: return
    Canvas(
        modifier
            .height(height)
            .width(height * (logo.width / logo.height))
            .semantics { contentDescription = description },
    ) {
        val factor = size.height / logo.height
        withTransform({ scale(factor, factor, Offset.Zero) }) {
            drawPath(logo.path, logo.brush)
        }
    }
}
