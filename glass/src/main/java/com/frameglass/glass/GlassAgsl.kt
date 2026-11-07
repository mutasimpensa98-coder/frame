package com.frameglass.glass

import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush

// The only hand-written AGSL in the app (brief 7.3). Refraction comes from Haze.

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

private class AgslBrush(val shader: RuntimeShader) : ShaderBrush() {
    override fun createShader(size: Size): Shader = shader
}

private fun compile(source: String): AgslBrush? = try {
    AgslBrush(RuntimeShader(source))
} catch (error: IllegalArgumentException) {
    // A broken shader must cost the highlight, not the app.
    Log.e("FrameGlass", "AGSL did not compile", error)
    null
}

/**
 * One brush of each kind, used from the draw phase on the main thread. Sharing is safe: a change
 * to the uniforms makes a new native shader, and an already recorded draw keeps its own.
 */
internal object GlassBrushes {
    private val highlightBrush by lazy(LazyThreadSafetyMode.NONE) { compile(FingerHighlight) }
    private val sheenBrush by lazy(LazyThreadSafetyMode.NONE) { compile(TiltSheen) }

    fun highlight(touch: Offset, radius: Float, strength: Float, ink: Color): Brush? = highlightBrush?.apply {
        shader.setFloatUniform("touch", touch.x, touch.y)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("ink", ink.red, ink.green, ink.blue)
    }

    fun sheen(size: Size, shift: Offset, top: Color, bottom: Color): Brush? = sheenBrush?.apply {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("shift", shift.x, shift.y)
        shader.setPremultiplied("rimTop", top)
        shader.setPremultiplied("rimBottom", bottom)
    }

    private fun RuntimeShader.setPremultiplied(name: String, color: Color) {
        val alpha = color.alpha
        setFloatUniform(name, color.red * alpha, color.green * alpha, color.blue * alpha, alpha)
    }
}
