package com.frameglass.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** Which look the app shows. The player keeps the default and stays dark in every theme. */
enum class FrameThemeMode {
    /** Follows the phone. */
    System,

    /** The near-black canvas. */
    Dark,

    /** The soft off-white canvas. */
    Light,
}

/** The colour of the primary pill, the tab selection pill, progress lines and chips. */
enum class FrameAccent {
    /** The neutral: white on dark and near-black on light, where the primary pill turns dark. */
    White,

    /** Blue. */
    Blue,

    /** Red. */
    Red,

    /** Green. */
    Green,

    /** Amber. */
    Amber,

    /** Violet. */
    Violet,
}

/** Every colour of one theme and accent. Read the one in force through [FrameTheme.colors]. */
@Immutable
class FramePalette internal constructor(
    /** True on the light canvas, for the few things that differ in kind and not only in colour. */
    val isLight: Boolean,
    /** Page background. */
    val canvas: Color,
    /** Chips, fields, dropdowns and image placeholders. */
    val elevated: Color,
    /** Grouped cards, such as Settings. */
    val card: Color,
    /** Main text and icons on the canvas and on glass. */
    val content: Color,
    /** Secondary text on the canvas and on glass. */
    val secondaryContent: Color,
    /** The accent as text and lines, tuned to read on every surface of this theme. */
    val accent: Color,
    /** The accent as a fill: the primary pill, the tab pill and selected rows. */
    val accentFill: Color,
    /** Text and icons on [accentFill]. */
    val onAccent: Color,
    /** Secondary text on [accentFill]. */
    val onAccentSecondary: Color,
    /** The accent for marks drawn straight on art and over its dark scrims, in either theme. */
    val mediaAccent: Color,
    /** Text and icons on a [mediaAccent] fill. */
    val onMediaAccent: Color,
    /** Status colour for errors only. */
    val error: Color,
    /** Text and icons on an [error] fill. */
    val onError: Color,
    /** Status colour for warnings only. */
    val warning: Color,
    /** The faintest edge and divider. */
    val hairline: Color,
    /** The edge of outlined controls. */
    val outline: Color,
    /** A weaker [outline]. */
    val outlineVariant: Color,
    internal val rowFill: Color,
    internal val track: Color,
    internal val shimmer: Color,
    internal val scrim: Color,
    internal val tabPill: Color,
    internal val tabPillRim: Color,
    internal val onTabPill: Color,
    internal val pressInk: Color,
    internal val pressStrength: Float,
)

/** Text and icons over art and its dark scrims, which keep their look in both themes. */
internal object MediaColors {
    val Content = Color.White
    val SecondaryContent = Color.White.copy(alpha = 0.70f)
}

internal object FramePalettes {
    private val DarkCanvas = Color(0xFF0D0D0D)
    private val LightCanvas = Color(0xFFF5F4F1)
    private val LightContent = Color(0xFF141414)
    private val LightError = Color(0xFFB3261E)
    private val LightWarning = Color(0xFF8A5300)
    private val DarkError = Color(0xFFFF6B5E)
    private val DarkWarning = Color(0xFFFFC14D)

    private const val SecondaryAlpha = 0.70f
    private const val SecondaryAlphaStep = 0.05f

    // Above the glass under it on light, as the dark pill is below it on dark: both add contrast.
    private val LightTabPill = Color.White.copy(alpha = 0.64f)
    private val DarkTabPill = Color.Black.copy(alpha = 0.46f)

    private val dark = FrameAccent.entries.map { build(light = false, it) }
    private val light = FrameAccent.entries.map { build(light = true, it) }

    /** The look of a plain `FrameTheme { }`, and of any read outside one. */
    val Default = of(light = false, FrameAccent.White)

    /** The one palette of this theme and accent, so identity means equality. */
    fun of(light: Boolean, accent: FrameAccent): FramePalette =
        (if (light) this.light else dark)[accent.ordinal]

    // Each tone clears 4.5:1 as text on all three surfaces of its theme, and over a 12% wash of itself
    // on a chip. ThemeContrastTest holds them to it.
    private fun tone(accent: FrameAccent, light: Boolean): Color = when (accent) {
        FrameAccent.White -> if (light) LightContent else Color.White
        FrameAccent.Blue -> if (light) Color(0xFF0A54C2) else Color(0xFF7AB8FF)
        FrameAccent.Red -> if (light) Color(0xFFAD2219) else Color(0xFFFF8A80)
        FrameAccent.Green -> if (light) Color(0xFF0A6636) else Color(0xFF5FD38D)
        FrameAccent.Amber -> if (light) Color(0xFF754B00) else Color(0xFFFFC14D)
        FrameAccent.Violet -> if (light) Color(0xFF6631C2) else Color(0xFFBFA2FF)
    }

    private fun build(light: Boolean, accent: FrameAccent): FramePalette {
        val tone = tone(accent, light)
        val mediaTone = tone(accent, light = false)
        // Amber dark enough to read as text is brown as a fill, so its pills stay amber and carry dark text.
        val fill = if (light && accent == FrameAccent.Amber) mediaTone else tone
        val onFill = GlassContrast.readableOn(fill)
        val neutral = accent == FrameAccent.White
        return if (light) {
            FramePalette(
                isLight = true,
                canvas = LightCanvas,
                elevated = Color(0xFFE9E7E1),
                card = Color.White,
                content = LightContent,
                secondaryContent = LightContent.copy(alpha = SecondaryAlpha),
                accent = tone,
                accentFill = fill,
                onAccent = onFill,
                onAccentSecondary = secondaryOn(fill, onFill),
                mediaAccent = mediaTone,
                onMediaAccent = GlassContrast.readableOn(mediaTone),
                error = LightError,
                onError = GlassContrast.readableOn(LightError),
                warning = LightWarning,
                hairline = Color.Black.copy(alpha = 0.07f),
                outline = Color.Black.copy(alpha = 0.24f),
                outlineVariant = Color.Black.copy(alpha = 0.12f),
                rowFill = Color.Black.copy(alpha = 0.05f),
                track = Color.Black.copy(alpha = 0.12f),
                shimmer = Color.White.copy(alpha = 0.60f),
                scrim = Color.Black.copy(alpha = 0.32f),
                tabPill = if (neutral) LightTabPill else fill,
                tabPillRim = if (neutral) Color.Black.copy(alpha = 0.06f) else Color.Transparent,
                onTabPill = if (neutral) LightContent else onFill,
                pressInk = Color.Black,
                pressStrength = 0.10f,
            )
        } else {
            FramePalette(
                isLight = false,
                canvas = DarkCanvas,
                elevated = Color(0xFF1A1A1A),
                card = Color(0xFF242424),
                content = Color.White,
                secondaryContent = Color.White.copy(alpha = SecondaryAlpha),
                accent = tone,
                accentFill = fill,
                onAccent = onFill,
                onAccentSecondary = secondaryOn(fill, onFill),
                mediaAccent = mediaTone,
                onMediaAccent = onFill,
                error = DarkError,
                onError = GlassContrast.readableOn(DarkError),
                warning = DarkWarning,
                hairline = Color.White.copy(alpha = 0.08f),
                outline = Color.White.copy(alpha = 0.24f),
                outlineVariant = Color.White.copy(alpha = 0.12f),
                rowFill = Color.White.copy(alpha = 0.06f),
                track = Color.White.copy(alpha = 0.12f),
                shimmer = Color.White.copy(alpha = 0.07f),
                scrim = Color.Black.copy(alpha = 0.55f),
                tabPill = if (neutral) DarkTabPill else fill,
                tabPillRim = if (neutral) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                onTabPill = if (neutral) Color.White else onFill,
                pressInk = Color.White,
                pressStrength = 0.22f,
            )
        }
    }

    // 70% where that reads, and stronger on tones where it would drop under the contrast rule.
    private fun secondaryOn(fill: Color, on: Color): Color {
        var alpha = SecondaryAlpha
        while (alpha < 1f && GlassContrast.ratio(on.copy(alpha = alpha).compositeOver(fill), fill) < GlassContrast.MinRatio) {
            alpha += SecondaryAlphaStep
        }
        return on.copy(alpha = alpha.coerceAtMost(1f))
    }
}
