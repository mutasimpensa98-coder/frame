package com.frameglass.player.tour

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/**
 * One soft elliptical light, like a CSS radial gradient from [color] to transparent at 70%. Every other field is
 * a share of the page: the radii of its width and height, and the centre from its left and top edges. The tour
 * draws them in physical positions in both directions, as the storyboard does.
 */
@Immutable
internal data class Glow(val color: Color, val rx: Float, val ry: Float, val cx: Float, val cy: Float)

private const val GlowEnd = 0.7f

/** The page's coloured light at the top, drawn behind everything else on it. */
internal fun Modifier.tourGlow(glows: List<Glow>): Modifier = drawBehind {
    glows.forEach { glow ->
        val center = Offset(size.width * glow.cx, size.height * glow.cy)
        val rx = size.width * glow.rx
        val ry = size.height * glow.ry
        // A circle of radius ry stretched sideways into the ellipse. The colour fades to its own transparent
        // version, so the edge does not turn grey on the way out.
        val brush = Brush.radialGradient(
            0f to glow.color,
            GlowEnd to glow.color.copy(alpha = 0f),
            center = center,
            radius = ry,
        )
        val stretch = rx / ry
        scale(stretch, 1f, center) {
            val left = center.x - center.x / stretch
            val right = center.x + (size.width - center.x) / stretch
            drawRect(brush, Offset(left, 0f), Size(right - left, size.height))
        }
    }
}

/**
 * The quiet posters and scenes of the storyboard, drawn in code. No film or series art ships in the APK (R8).
 * Posters are drawn in an 80 by 120 box; designs 0 to 5 fill the library page in order.
 */
internal object TourArt {
    /** The sample rating, year and progress a placeholder shows, as an illustration. */
    data class Sample(val tmdb: Boolean, val rating: Double?, val year: Int?, val progress: Float?)

    /** The pick on the tonight page when the library has no cover. */
    const val TonightPick = 6

    /** What the tonight page shows before the pick when the library has fewer than two covers. */
    const val TonightFirst = 7

    fun sample(design: Int): Sample = when (design) {
        0 -> Sample(tmdb = true, rating = 8.2, year = null, progress = null)
        1 -> Sample(tmdb = false, rating = 8.7, year = null, progress = null)
        2 -> Sample(tmdb = true, rating = 7.9, year = null, progress = 0.55f)
        4 -> Sample(tmdb = false, rating = 9.0, year = null, progress = null)
        TonightPick -> Sample(tmdb = true, rating = 8.4, year = 2024, progress = null)
        TonightFirst -> Sample(tmdb = false, rating = 7.6, year = 2023, progress = null)
        else -> Sample(tmdb = true, rating = null, year = null, progress = null)
    }

    fun DrawScope.poster(design: Int) {
        scale(size.width / 80f, size.height / 120f, Offset.Zero) {
            when (design) {
                0 -> {
                    drawRect(Color(0xFF22303F), size = Size(80f, 120f))
                    drawCircle(Color(0xFFC9D6E6), 16f, Offset(54f, 44f))
                    drawRect(Color(0xFF15202B), Offset(0f, 78f), Size(80f, 42f))
                }
                1 -> {
                    drawRect(Color(0xFF3A2A22), size = Size(80f, 120f))
                    drawPath(triangle(0f, 120f, 40f, 50f, 80f, 120f), Color(0xFF5B4033))
                    drawPath(triangle(28f, 120f, 52f, 80f, 80f, 120f), Color(0xFF7A5644))
                }
                2 -> {
                    drawRect(Color(0xFF1E2B26), size = Size(80f, 120f))
                    repeat(4) { line ->
                        val drop = line * 24f
                        drawLine(Color(0xFF3F5A4F), Offset(-10f, 30f + drop), Offset(90f, 10f + drop), 6f)
                    }
                }
                3 -> {
                    drawRect(Color(0xFF2B2236), size = Size(80f, 120f))
                    drawCircle(Color(0xFF6E5A8C), 26f, Offset(40f, 60f), style = Stroke(5f))
                    drawCircle(Color(0xFF6E5A8C), 9f, Offset(40f, 60f))
                }
                4 -> {
                    val ink = Color(0xFF6B6352)
                    drawRect(Color(0xFF33302A), size = Size(80f, 120f))
                    drawRoundRect(ink, Offset(14f, 20f), Size(52f, 80f), CornerRadius(4f), style = Stroke(4f))
                    drawLine(ink, Offset(14f, 72f), Offset(66f, 72f), 4f)
                }
                5 -> {
                    drawRect(Color(0xFF1F2733), size = Size(80f, 120f))
                    drawPath(wave(84f, 70f), Color(0xFF2E3B4D))
                    drawPath(wave(98f, 86f), Color(0xFF3D4E66))
                }
                TonightPick -> {
                    drawRect(Color(0xFF2F2A3A), size = Size(80f, 120f))
                    drawCircle(Color(0xFFB8A6D9), 18f, Offset(40f, 49f))
                    drawRect(Color(0xFF1D1926), Offset(0f, 83f), Size(80f, 37f))
                }
                else -> {
                    val arch = Color(0xFF4F6B5E)
                    drawRect(Color(0xFF26302C), size = Size(80f, 120f))
                    drawArc(arch, 180f, 180f, true, Offset(16f, 40f), Size(48f, 48f))
                    drawRect(arch, Offset(16f, 64f), Size(48f, 56f))
                    drawRect(Color(0xFF1A211E), Offset(30f, 80f), Size(20f, 40f))
                }
            }
        }
    }

    private fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = Path().apply {
        moveTo(x1, y1)
        lineTo(x2, y2)
        lineTo(x3, y3)
        close()
    }

    // One crest up to [crest], then the mirrored trough, as the storyboard's quadratic wave.
    private fun wave(base: Float, crest: Float) = Path().apply {
        moveTo(0f, base)
        quadraticTo(20f, crest, 40f, base)
        quadraticTo(60f, 2 * base - crest, 80f, base)
        lineTo(80f, 120f)
        lineTo(0f, 120f)
        close()
    }

    /** The player page's scene: two people under a moon, in a 260 by 146 box. */
    fun DrawScope.nightScene() {
        scale(size.width / 260f, size.height / 146f, Offset.Zero) {
            val ink = Color(0xFF0C1117)
            drawRect(Color(0xFF1A222C), size = Size(260f, 146f))
            drawRect(Color(0xFF10161D), Offset(0f, 88f), Size(260f, 58f))
            drawCircle(Color(0xFF3B4757), 20f, Offset(196f, 64f))
            drawRoundRect(ink, Offset(40f, 58f), Size(16f, 40f), CornerRadius(8f))
            drawCircle(ink, 8f, Offset(48f, 52f))
            drawRoundRect(ink, Offset(74f, 62f), Size(14f, 36f), CornerRadius(7f))
            drawCircle(ink, 7f, Offset(81f, 56f))
        }
    }

    /** Hills under a grey sky for the Continue watching card, in a 260 by 128 box. */
    fun DrawScope.hillsScene() {
        scale(size.width / 260f, size.height / 128f, Offset.Zero) {
            drawRect(Color(0xFF26303A), size = Size(260f, 128f))
            val hills = Path().apply {
                moveTo(0f, 92f)
                lineTo(70f, 60f)
                lineTo(130f, 84f)
                lineTo(200f, 46f)
                lineTo(260f, 70f)
                lineTo(260f, 128f)
                lineTo(0f, 128f)
                close()
            }
            drawPath(hills, Color(0xFF1A2229))
        }
    }

    /** One of the two episode stills of the continue page, in a 96 by 54 box. */
    fun DrawScope.episodeStill(second: Boolean) {
        scale(size.width / 96f, size.height / 54f, Offset.Zero) {
            if (second) {
                drawRect(Color(0xFF22303A), size = Size(96f, 54f))
                drawRoundRect(Color(0xFF4A6276), Offset(18f, 14f), Size(24f, 30f), CornerRadius(4f))
                drawRect(Color(0xFF16212A), Offset(0f, 40f), Size(96f, 14f))
            } else {
                drawRect(Color(0xFF3A3026), size = Size(96f, 54f))
                drawCircle(Color(0xFF8C7356), 12f, Offset(64f, 22f))
                drawRect(Color(0xFF241C15), Offset(0f, 34f), Size(96f, 20f))
            }
        }
    }
}

/** A drawn placeholder poster. */
@Composable
internal fun PlaceholderPoster(design: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) { with(TourArt) { poster(design) } }
}
