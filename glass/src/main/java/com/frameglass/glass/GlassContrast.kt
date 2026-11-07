package com.frameglass.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The contrast rule from brief 7.3: text on glass keeps 4.5:1 on any backdrop. */
object GlassContrast {
    /** The lowest contrast allowed for text and icons. */
    const val MinRatio = 4.5f

    /** Highest level behind white text, of 255. */
    const val WhiteTextLimit = 118f

    /** Highest level behind 70% white text, of 255. */
    const val SecondaryTextLimit = 87f

    /** Lowest level behind the light theme's near-black text, of 255. */
    const val DarkTextLimit = 126f

    /** Lowest level behind that text at 70%, of 255. */
    const val SecondaryDarkTextLimit = 174f

    /** Level of the tint colour, #0D0D0D. */
    const val TintLevel = 13f

    /** Level of the light tint colour, #F5F4F1. */
    const val LightTintLevel = 244f

    private val Ink = Color(0xFF0D0D0D)

    /**
     * Tint alpha over a backdrop [level]: a = max(baseAlpha, (B - L) / (B - T)). The same line serves both
     * themes: [limit] is a ceiling over a dark tint and a floor under a light one.
     */
    fun tintAlpha(baseAlpha: Float, level: Float, limit: Float, tintLevel: Float = TintLevel): Float {
        val safe = if (tintLevel < limit) level <= limit else level >= limit
        return if (safe) baseAlpha else max(baseAlpha, (level - limit) / (level - tintLevel))
    }

    /** The contrast ratio of two opaque colours. */
    fun ratio(first: Color, second: Color): Float {
        val a = first.luminance() + 0.05f
        val b = second.luminance() + 0.05f
        return max(a, b) / min(a, b)
    }

    /** Near-black or white, whichever reads better on [fill]. */
    fun readableOn(fill: Color): Color =
        if (ratio(Ink, fill) >= ratio(Color.White, fill)) Ink else Color.White

    private val Linear = FloatArray(256) { index ->
        val channel = index / 255f
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)
    }

    // The grey level with the same relative luminance as this pixel, which is what the limits mean.
    internal fun pixelLevel(argb: Int): Float {
        val luminance = 0.2126f * Linear[(argb shr 16) and 255] +
            0.7152f * Linear[(argb shr 8) and 255] +
            0.0722f * Linear[argb and 255]
        val encoded = if (luminance <= 0.0031308f) {
            12.92f * luminance
        } else {
            1.055f * luminance.pow(1f / 2.4f) - 0.055f
        }
        return encoded * 255f
    }

    // Undoes the sRGB tint blend, to recover the level behind a pixel that already shows the tint.
    internal fun backdropLevel(tinted: Float, tintAlpha: Float): Float =
        ((tinted - TintLevel * tintAlpha) / (1f - tintAlpha)).coerceIn(0f, 255f)
}
