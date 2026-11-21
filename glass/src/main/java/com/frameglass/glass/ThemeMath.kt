package com.frameglass.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.cbrt
import kotlin.math.roundToInt

/**
 * Colour arithmetic for the themes. Lightness here is CIE L*, 0 to 100, which follows what the eye and the
 * contrast rule see: two colours of one L* have one luminance, whatever their hue.
 */
internal object ThemeMath {
    private const val MaxLightness = 100f
    private const val LinearBelow = 8f
    private const val LinearSlope = 903.3f
    private const val LinearLuminance = 0.008856f
    private const val SearchSteps = 24
    private const val FullCircle = 360f

    /** The luminance, 0 to 1, of a CIE [lightness]. */
    fun luminanceOf(lightness: Float): Float {
        val l = lightness.coerceIn(0f, MaxLightness)
        if (l <= LinearBelow) return l / LinearSlope
        val root = (l + 16f) / 116f
        return root * root * root
    }

    /** The CIE lightness of [color]. */
    fun lightness(color: Color): Float {
        val y = color.luminance()
        return if (y <= LinearLuminance) y * LinearSlope else 116f * cbrt(y) - 16f
    }

    /** [hue] brought into 0 until 360. */
    fun wrapHue(hue: Float): Float = if (hue.isFinite()) ((hue % FullCircle) + FullCircle) % FullCircle else 0f

    /** [color] on the 8-bit grid, so a derived colour is exactly the ARGB the screen shows. */
    fun snap(color: Color): Color = Color(
        red = (color.red * 255f).roundToInt().coerceIn(0, 255),
        green = (color.green * 255f).roundToInt().coerceIn(0, 255),
        blue = (color.blue * 255f).roundToInt().coerceIn(0, 255),
    )

    /**
     * The colour of this [hue] and HSL [saturation] whose CIE lightness is [lightness]. Luminance rises with
     * HSL lightness at a fixed hue and saturation, so a plain bisection finds it, the same way every time.
     */
    fun tone(hue: Float, saturation: Float, lightness: Float): Color {
        val wanted = luminanceOf(lightness)
        val h = wrapHue(hue)
        val s = saturation.coerceIn(0f, 1f)
        var low = 0f
        var high = 1f
        repeat(SearchSteps) {
            val middle = (low + high) / 2f
            if (Color.hsl(h, s, middle).luminance() < wanted) low = middle else high = middle
        }
        return snap(Color.hsl(h, s, (low + high) / 2f))
    }

    /**
     * [color] moved toward [toward] in equal steps until [holds] accepts it, or [toward] itself when nothing
     * on the way does. A colour that already holds comes back untouched.
     */
    inline fun fit(color: Color, toward: Color, steps: Int = 20, holds: (Color) -> Boolean): Color {
        if (holds(color)) return color
        for (step in 1..steps) {
            val candidate = snap(lerp(color, toward, step.toFloat() / steps))
            if (holds(candidate)) return candidate
        }
        return toward
    }
}
