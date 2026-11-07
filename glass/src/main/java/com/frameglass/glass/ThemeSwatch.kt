package com.frameglass.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val SwatchShape = RoundedCornerShape(16.dp)
private val PictureShape = RoundedCornerShape(12.dp)
private const val SwatchRatio = 0.72f
private val RingWidth = 2.dp
private val RingGap = 4.dp
private val SwatchCheck = 20.dp
private val SwatchCheckIcon = 14.dp
private val DotSlot = 44.dp
private val DotCheck = 18.dp

/** The name of this theme as the owner reads it. */
@Composable
fun FrameThemeMode.label(): String = stringResource(
    when (this) {
        FrameThemeMode.System -> R.string.glass_theme_system
        FrameThemeMode.Dark -> R.string.glass_theme_dark
        FrameThemeMode.Light -> R.string.glass_theme_light
    },
)

/** The name of this accent as the owner sees it: the neutral reads black on the light theme. */
@Composable
fun FrameAccent.label(): String = stringResource(
    when (this) {
        FrameAccent.White -> if (FrameTheme.colors.isLight) R.string.glass_accent_black else R.string.glass_accent_white
        FrameAccent.Blue -> R.string.glass_accent_blue
        FrameAccent.Red -> R.string.glass_accent_red
        FrameAccent.Green -> R.string.glass_accent_green
        FrameAccent.Amber -> R.string.glass_accent_amber
        FrameAccent.Violet -> R.string.glass_accent_violet
    },
)

/**
 * A small picture of Home in a theme and accent, for the Appearance screen. It shows that theme whatever
 * the app is using now, and [FrameThemeMode.System] shows both, split corner to corner.
 */
@Composable
fun ThemeSwatch(
    mode: FrameThemeMode,
    accent: FrameAccent,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = FrameTheme.colors
    val name = mode.label()
    val ring by animateColorAsState(
        if (selected) colors.accent else Color.Transparent,
        FrameMotion.effects(),
        label = "swatchRing",
    )
    val semantics = Modifier.semantics {
        contentDescription = name
        this.selected = selected
    }
    val picture: @Composable () -> Unit = {
        Box(Modifier.border(RingWidth, ring, SwatchShape).padding(RingGap)) {
            HomePicture(mode, accent)
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(FrameSpacing.S4)
                        .size(SwatchCheck)
                        .background(colors.accentFill, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(SwatchCheckIcon), tint = colors.onAccent)
                }
            }
        }
    }
    if (onClick != null) {
        PressableGlass(
            onClick = onClick,
            modifier = modifier.then(semantics),
            shape = SwatchShape,
            role = Role.RadioButton,
            highlight = false,
        ) { picture() }
    } else {
        Box(modifier.then(semantics)) { picture() }
    }
}

/** A round accent choice in its tone for the current theme, with a check when [selected]. */
@Composable
fun AccentSwatch(
    accent: FrameAccent,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FrameTheme.colors
    val tone = FramePalettes.of(colors.isLight, accent)
    val name = accent.label()
    val ring by animateColorAsState(
        if (selected) tone.accent else Color.Transparent,
        FrameMotion.effects(),
        label = "accentRing",
    )
    PressableGlass(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(DotSlot)
            .semantics {
                contentDescription = name
                this.selected = selected
            },
        role = Role.RadioButton,
        highlight = false,
    ) {
        Box(
            modifier = Modifier
                .border(RingWidth, ring, CircleShape)
                .padding(RingGap)
                .background(tone.accentFill, CircleShape)
                .border(0.5.dp, colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, null, Modifier.size(DotCheck), tint = tone.onAccent)
            }
        }
    }
}

@Composable
private fun HomePicture(mode: FrameThemeMode, accent: FrameAccent) {
    val dark = FramePalettes.of(light = false, accent)
    val light = FramePalettes.of(light = true, accent)
    val rim = FrameTheme.colors.hairline
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(SwatchRatio)
            .clip(PictureShape)
            .border(0.5.dp, rim, PictureShape),
    ) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) {
            when (mode) {
                FrameThemeMode.Dark -> miniHome(dark)
                FrameThemeMode.Light -> miniHome(light)
                FrameThemeMode.System -> {
                    miniHome(dark)
                    clipPath(lowerCorner(size)) { miniHome(light) }
                }
            }
        }
    }
}

private fun lowerCorner(size: Size) = Path().apply {
    moveTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

// Home in miniature, in the same order as the real one: art fading into the canvas, title and meta
// lines, the primary pill, a row of posters with a progress line, and the floating tab bar.
private fun DrawScope.miniHome(colors: FramePalette) {
    val w = size.width
    val h = size.height
    val unit = w / 24f
    fun bar(color: Color, x: Float, y: Float, width: Float, height: Float) {
        drawRoundRect(
            color = color,
            topLeft = Offset(x * w, y * h),
            size = Size(width * w, height),
            cornerRadius = CornerRadius(height / 2f),
        )
    }

    drawRect(colors.canvas)
    drawRect(
        brush = Brush.verticalGradient(
            0f to colors.elevated,
            1f to colors.canvas,
            endY = h * 0.5f,
        ),
        size = Size(w, h * 0.5f),
    )
    bar(colors.content, x = 0.24f, y = 0.27f, width = 0.52f, height = unit * 1.1f)
    bar(colors.secondaryContent, x = 0.32f, y = 0.34f, width = 0.36f, height = unit * 0.6f)
    bar(colors.accentFill, x = 0.30f, y = 0.40f, width = 0.40f, height = unit * 2.4f)
    bar(colors.onAccent, x = 0.40f, y = 0.40f + unit * 0.95f / h, width = 0.20f, height = unit * 0.5f)

    val posterTop = h * 0.56f
    val posterSize = Size(w * 0.26f, h * 0.2f)
    val corner = CornerRadius(unit * 0.8f)
    // The block that stands out from this canvas: lighter than it on both themes.
    val poster = if (colors.isLight) colors.card else colors.elevated
    repeat(3) { index ->
        drawRoundRect(
            color = poster,
            topLeft = Offset(w * (0.08f + 0.30f * index), posterTop),
            size = posterSize,
            cornerRadius = corner,
        )
    }
    bar(colors.accent, x = 0.11f, y = 0.73f, width = 0.13f, height = unit * 0.4f)

    val tabTop = h * 0.84f
    val tabHeight = h * 0.1f
    val tabRadius = CornerRadius(tabHeight / 2f)
    drawRoundRect(
        color = colors.elevated,
        topLeft = Offset(w * 0.08f, tabTop),
        size = Size(w * 0.84f, tabHeight),
        cornerRadius = tabRadius,
    )
    drawRoundRect(
        color = colors.tabPill,
        topLeft = Offset(w * 0.1f, tabTop + unit * 0.3f),
        size = Size(w * 0.3f, tabHeight - unit * 0.6f),
        cornerRadius = tabRadius,
    )
    bar(colors.onTabPill, x = 0.2f, y = 0.885f, width = 0.1f, height = unit * 0.5f)
    bar(colors.secondaryContent, x = 0.5f, y = 0.885f, width = 0.1f, height = unit * 0.5f)
    bar(colors.secondaryContent, x = 0.72f, y = 0.885f, width = 0.1f, height = unit * 0.5f)
}
