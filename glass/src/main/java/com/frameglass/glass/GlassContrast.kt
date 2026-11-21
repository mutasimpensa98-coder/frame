package com.frameglass.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

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

    /** The lowest contrast allowed for large text and for the parts of a control, such as a selection ring. */
    const val MinLargeRatio = 3f

    private val Ink = Color(0xFF0D0D0D)
    private const val LimitSteps = 2000
    private const val ColouredTintMargin = 3f

    /**
     * Tint alpha over a backdrop [level]: a = max(baseAlpha, (B - L) / (B - T)). The same line serves both
     * themes: [limit] is a ceiling over a dark tint and a floor under a light one.
     */
    fun tintAlpha(baseAlpha: Float, level: Float, limit: Float, tintLevel: Float = TintLevel): Float =
        tintAlpha(baseAlpha, level, limit, tintLevel, lightTint = tintLevel >= limit)

    /**
     * The same rule with the kind of tint said outright, for a theme whose canvas sits right at the limit of its
     * own text. There no blend with a worse backdrop holds, and only the plain tint does: the theme is built so
     * that its text reads on its canvas.
     */
    fun tintAlpha(baseAlpha: Float, level: Float, limit: Float, tintLevel: Float, lightTint: Boolean): Float {
        val safe = if (lightTint) level >= limit else level <= limit
        if (safe) return baseAlpha
        val tintHolds = if (lightTint) tintLevel > limit else tintLevel < limit
        if (!tintHolds) return 1f
        return max(baseAlpha, (level - limit) / (level - tintLevel)).coerceAtMost(1f)
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

    /** True when [front], which may be translucent, keeps [minimum] contrast drawn on the opaque [back]. */
    fun reads(front: Color, back: Color, minimum: Float = MinRatio): Boolean =
        ratio(if (front.alpha < 1f) front.compositeOver(back) else front, back) >= minimum

    /** The grey level with the same relative luminance as [color]: what the limits are measured in. */
    fun level(color: Color): Float = pixelLevel(color.toArgb())

    /** The level of a glass tint of this colour, as a whole number, which is how [limit] and [tintAlpha] share it. */
    fun tintLevel(tint: Color): Float = level(tint).roundToInt().toFloat()

    /**
     * The backdrop limit for [text] on glass tinted with [tint], in the terms of [tintAlpha]: the level the tinted
     * surface must stay at or below under a dark tint, or at or above under a light one ([light]). With [rowFill],
     * the text must also hold on a row of that fill laid on the glass.
     *
     * It is the stricter of two measures. One is taken on plain greys, which is what the rule means and what holds
     * over any backdrop. The other is taken on the real blend of the tint over the worst backdrop, pure white or
     * pure black, because translucent text on a coloured tint can read a touch worse than on the grey of the same
     * level. A coloured tint also blends and rounds to 8 bits channel by channel, which moves the result by a
     * hundredth or two either way, so its limit is held [ColouredTintMargin] levels to the safe side. A pure grey
     * tint keeps the exact numbers: 118 and 87 for the plain dark theme, as in the brief.
     */
    fun limit(text: Color, tint: Color, light: Boolean, rowFill: Color? = null): Float {
        fun holdsOn(surface: Color) =
            reads(text, surface) && (rowFill == null || reads(text, rowFill.compositeOver(surface)))

        val tintLevel = tintLevel(tint)
        val levels = if (light) 0..255 else 255 downTo 0
        val onGrey = levels.firstOrNull { holdsOn(Color(it, it, it)) }?.toFloat() ?: tintLevel

        val worst = if (light) Color.Black else Color.White
        val worstLevel = if (light) 0f else 255f
        var onTint = tintLevel
        for (step in 0..LimitSteps) {
            val alpha = step.toFloat() / LimitSteps
            if (holdsOn(tint.copy(alpha = alpha).compositeOver(worst))) {
                val level = worstLevel + alpha * (tintLevel - worstLevel)
                onTint = if (light) ceil(level) else floor(level)
                break
            }
        }
        val margin = if (isGrey(tint)) 0f else ColouredTintMargin
        return if (light) max(onGrey, onTint) + margin else min(onGrey, onTint) - margin
    }

    private fun isGrey(color: Color): Boolean = color.red == color.green && color.green == color.blue

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
    internal fun backdropLevel(tinted: Float, tintAlpha: Float, tintLevel: Float = TintLevel): Float =
        if (tintAlpha >= 1f) tintLevel else ((tinted - tintLevel * tintAlpha) / (1f - tintAlpha)).coerceIn(0f, 255f)
}
