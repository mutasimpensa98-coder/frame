package com.frameglass.glass

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush

// The only hand-written AGSL in the app (brief 7.3). Refraction comes from Haze.
// AGSL needs RuntimeShader, API 33. Below it nothing here is compiled or called: the press light is a plain radial
// gradient and the tilt sheen is never asked for, because glass there is tint and rim only (waiver of 2026-11-19).

// Soft light under the finger, as premultiplied ink: white on dark glass, a soft shade on light glass.
private const val FingerHighlight = """
uniform float2 touch;
uniform float radius;
uniform float strength;
uniform float3 ink;

half4 main(float2 p) {
    float falloff = clamp(1.0 - distance(p, touch) / radius, 0.0, 1.0);
    float level = strength * falloff * falloff;
    return half4(half3(ink * level), half(level));
}
"""

// The rim gradient between two premultiplied colours, plus a soft white light on the top edge. Tilt
// moves both by shift.
private const val TiltSheen = """
uniform float2 size;
uniform float2 shift;
uniform float4 rimTop;
uniform float4 rimBottom;

half4 main(float2 p) {
    float across = clamp((p.y - shift.y) / size.y, 0.0, 1.0);
    float light = distance(p, float2(size.x * 0.35 + shift.x, shift.y)) / (size.x * 0.4);
    float sheen = 0.16 * clamp(1.0 - light, 0.0, 1.0);
    return half4(mix(rimTop, rimBottom, across) + float4(sheen));
}
"""

/** Everything that touches RuntimeShader is in this class, so a phone below API 33 never has to load it. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslBrush(source: String) : ShaderBrush() {
    private val shader = RuntimeShader(source)

    override fun createShader(size: Size): Shader = shader

    fun set(name: String, a: Float) = shader.setFloatUniform(name, a)

    fun set(name: String, a: Float, b: Float) = shader.setFloatUniform(name, a, b)

    fun set(name: String, a: Float, b: Float, c: Float) = shader.setFloatUniform(name, a, b, c)

    fun setPremultiplied(name: String, color: Color) {
        val alpha = color.alpha
        shader.setFloatUniform(name, color.red * alpha, color.green * alpha, color.blue * alpha, alpha)
    }
}

private fun compile(source: String): AgslBrush? = try {
    if (GlassSupport.liquid) AgslBrush(source) else null
} catch (error: IllegalArgumentException) {
    // A broken shader must cost the highlight, not the app.
    Log.e("FrameGlass", "AGSL did not compile", error)
    null
}

/** The squared falloff of FingerHighlight as gradient stops, for phones with no AGSL. */
private val FalloffStops = listOf(0f to 1f, 0.25f to 0.5625f, 0.5f to 0.25f, 0.75f to 0.0625f, 1f to 0f)

/**
 * One brush of each kind, used from the draw phase on the main thread. Sharing is safe: a change
 * to the uniforms makes a new native shader, and an already recorded draw keeps its own.
 */
internal object GlassBrushes {
    private val highlightBrush by lazy(LazyThreadSafetyMode.NONE) { compile(FingerHighlight) }
    private val sheenBrush by lazy(LazyThreadSafetyMode.NONE) { compile(TiltSheen) }

    fun highlight(touch: Offset, radius: Float, strength: Float, ink: Color): Brush? {
        if (!GlassSupport.liquid) return plainHighlight(touch, radius, strength, ink)
        return highlightBrush?.apply {
            set("touch", touch.x, touch.y)
            set("radius", radius)
            set("strength", strength)
            set("ink", ink.red, ink.green, ink.blue)
        }
    }

    /** The same light with no shader. */
    private fun plainHighlight(touch: Offset, radius: Float, strength: Float, ink: Color): Brush? {
        if (radius <= 0f) return null
        val stops = FalloffStops.map { (at, level) -> at to ink.copy(alpha = (strength * level).coerceIn(0f, 1f)) }
        return Brush.radialGradient(*stops.toTypedArray(), center = touch, radius = radius)
    }

    /** Asked for only by blurred glass with tilt, which does not exist below API 33. */
    fun sheen(size: Size, shift: Offset, top: Color, bottom: Color): Brush? {
        if (!GlassSupport.liquid) return null
        return sheenBrush?.apply {
            set("size", size.width, size.height)
            set("shift", shift.x, shift.y)
            setPremultiplied("rimTop", top)
            setPremultiplied("rimBottom", bottom)
        }
    }
}
