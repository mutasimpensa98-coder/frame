package com.frameglass.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * The colours of the window in front, as plain values that also work in top-level code and draw lambdas.
 * Prefer [FrameTheme.colors] in new code: it is scoped to its composition. A value kept in a top-level
 * `val` stays the colour it was on first use, so it misses a theme change.
 *
 * The names come from the dark look: [White] is the main content colour and turns near-black on light.
 */
object FrameColors {
    /** The page background. */
    val Canvas: Color get() = ThemeBridge.palette.canvas

    /** Raised surface, such as chips and dropdowns. */
    val Elevated: Color get() = ThemeBridge.palette.elevated

    /** Card surface, such as Settings groups. */
    val Card: Color get() = ThemeBridge.palette.card

    /** Main text and icons on the canvas and on glass. */
    val White: Color get() = ThemeBridge.palette.content

    /** Secondary text on the canvas and on glass. */
    val SecondaryText: Color get() = ThemeBridge.palette.secondaryContent

    /** Text and icons on [AccentFill]. */
    val OnWhite: Color get() = ThemeBridge.palette.onAccent

    /** The accent as text and lines. */
    val Accent: Color get() = ThemeBridge.palette.accent

    /** The accent as a fill, such as the primary pill. */
    val AccentFill: Color get() = ThemeBridge.palette.accentFill

    /** The accent for marks drawn on art. */
    val MediaAccent: Color get() = ThemeBridge.palette.mediaAccent

    /** The faintest edge and divider. */
    val Divider: Color get() = ThemeBridge.palette.hairline

    /** Main text and icons over art and dark scrims, white in both themes. */
    val OnMedia: Color get() = MediaColors.Content

    /** Secondary text over art and dark scrims, in both themes. */
    val OnMediaSecondary: Color get() = MediaColors.SecondaryContent

    /** Status colour for errors only. */
    val Error: Color get() = ThemeBridge.palette.error

    /** Status colour for warnings only. */
    val Warning: Color get() = ThemeBridge.palette.warning
}

private const val MaxGlowAlpha = 0.16f
private const val LightGlowLift = 0.4f

/**
 * A soft radial glow from a cover's dominant colour, capped at 16% so the canvas keeps its own tone. On
 * light the colour lifts toward the canvas, because a dark cover would otherwise read as a smudge.
 */
@Composable
fun Modifier.coverGlow(argb: Int?): Modifier {
    if (argb == null) return this
    val colors = FrameTheme.colors
    val spread = LocalCoverTint.current
    val glow = remember(argb, colors) {
        val cover = Color(argb)
        (if (colors.isLight) lerp(cover, colors.canvas, LightGlowLift) else cover).copy(alpha = MaxGlowAlpha)
    }
    return drawWithCache {
        val brush = Brush.radialGradient(
            colors = listOf(glow, Color.Transparent),
            center = Offset(size.width / 2f, size.height * if (spread) 0.75f else 0.4f),
            radius = size.maxDimension * if (spread) 1.1f else 0.7f,
        )
        onDrawBehind { drawRect(brush) }
    }
}

/** Corner shapes from brief 7.2. */
object FrameShapes {
    /** Posters, and the 16:9 cards that sit beside them in the reference. */
    val Poster = RoundedCornerShape(12.dp)

    /** Grouped cards. */
    val Card = RoundedCornerShape(24.dp)

    /** Sheets and menus. */
    val Sheet = RoundedCornerShape(24.dp)

    /** Dialogs. */
    val Dialog = RoundedCornerShape(28.dp)

    /** Chips and pills. */
    val Chip = RoundedCornerShape(percent = 50)
}

/** The spacing scale from brief 7.2. */
object FrameSpacing {
    /** 4 dp. */
    val S4 = 4.dp

    /** 8 dp. */
    val S8 = 8.dp

    /** 12 dp. */
    val S12 = 12.dp

    /** 16 dp. */
    val S16 = 16.dp

    /** 24 dp. */
    val S24 = 24.dp

    /** 32 dp. */
    val S32 = 32.dp

    /** 48 dp. */
    val S48 = 48.dp
}

/** Fixed proportions and sizes shared by the screens. */
object FrameSizes {
    /** Width over height of a poster. */
    const val PosterRatio = 2f / 3f

    /** Width over height of a landscape card. */
    const val LandscapeRatio = 1.77f

    /** Poster width in a horizontal row. */
    val PosterWidth = 124.dp

    /** Landscape card width in a horizontal row, about 1.64 times the poster. */
    val LandscapeWidth = 204.dp

    /** Height of [LiquidTabBar] without its margins. */
    val TabBarHeight = 64.dp

    /** List bottom padding that clears [LiquidTabBar]; add the navigation bar inset to it. */
    val TabBarClearance = 88.dp

    /** Height of [TopGlassBar] without the status bar. */
    val TopBarHeight = 64.dp

    /** How far a page scrolls while [TopGlassBar] turns from clear to glass. */
    val TopBarCollapse = 64.dp
}
