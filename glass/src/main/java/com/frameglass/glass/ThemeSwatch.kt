package com.frameglass.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
private val AnyColourRing = 5.dp
private const val CheckDisc = 0.36f

/** The name of this theme as the owner reads it. */
@Composable
fun FrameThemeId.label(): String = stringResource(
    when (this) {
        FrameThemeId.Black -> R.string.glass_theme_black
        FrameThemeId.PureBlack -> R.string.glass_theme_pure_black
        FrameThemeId.Midnight -> R.string.glass_theme_midnight
        FrameThemeId.Violet -> R.string.glass_theme_violet
        FrameThemeId.Forest -> R.string.glass_theme_forest
        FrameThemeId.Coffee -> R.string.glass_theme_coffee
        FrameThemeId.Light -> R.string.glass_theme_light
        FrameThemeId.Paper -> R.string.glass_theme_paper
    },
)

/** The name of this accent as the owner sees it: the neutral reads black on a light theme. */
@Composable
fun FrameAccent.label(): String = stringResource(
    when (this) {
        FrameAccent.White -> if (FrameTheme.colors.isLight) R.string.glass_accent_black else R.string.glass_accent_white
        FrameAccent.Blue -> R.string.glass_accent_blue
        FrameAccent.Red -> R.string.glass_accent_red
        FrameAccent.Green -> R.string.glass_accent_green
        FrameAccent.Amber -> R.string.glass_accent_amber
        FrameAccent.Violet -> R.string.glass_accent_violet
        FrameAccent.Pink -> R.string.glass_accent_pink
        FrameAccent.Cyan -> R.string.glass_accent_cyan
    },
)

/**
 * A small picture of Home in [look], for the Appearance screen: a ready theme or a custom one, with the accent
 * it would get. It shows that look whatever the app is using now. [name] is what a screen reader says.
 */
@Composable
fun ThemeSwatch(
    look: FrameLook,
    name: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = FrameTheme.colors
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
            HomePicture(FramePalettes.of(look.copy(followSystem = false)))
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
    val tone = FramePalettes.withAccent(FrameTheme.colors, accent)
    AccentDot(accent.label(), tone, selected, onClick, modifier) {
        Box(Modifier.fillMaxSize().background(tone.accentFill, CircleShape))
    }
}

/**
 * The choice of the theme's own accent, such as blue on Midnight: half the theme's raised surface and half its
 * accent, so it reads as "this theme's colour" beside the plain dots.
 */
@Composable
fun ThemeAccentSwatch(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = FramePalettes.withAccent(FrameTheme.colors, accent = null)
    AccentDot(stringResource(R.string.glass_accent_theme), tone, selected, onClick, modifier) {
        Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
            drawRect(tone.elevated)
            // The split leans the same way in both directions, so the accent half is always the lower one.
            drawArc(tone.accentFill, startAngle = -45f, sweepAngle = 180f, useCenter = true)
            // The check is drawn in the colour that reads on the accent, so it gets the accent under all of it.
            if (selected) drawCircle(tone.accentFill, radius = size.minDimension * CheckDisc)
        }
    }
}

/** The "any colour" choice: a rainbow ring around [argb], the colour picked so far. A tap opens the picker. */
@Composable
fun AnyAccentSwatch(
    argb: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = FramePalettes.withAccent(FrameTheme.colors, accent = null, accentArgb = argb)
    AccentDot(stringResource(R.string.glass_accent_any), tone, selected, onClick, modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.sweepGradient(HueWheel), CircleShape)
                .padding(AnyColourRing)
                .background(tone.accentFill, CircleShape),
        )
    }
}

@Composable
private fun AccentDot(
    name: String,
    tone: FramePalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    fill: @Composable BoxScope.() -> Unit,
) {
    val colors = FrameTheme.colors
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
                .border(0.5.dp, colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            fill()
            if (selected) {
                Icon(Icons.Rounded.Check, null, Modifier.size(DotCheck), tint = tone.onAccent)
            }
        }
    }
}

@Composable
private fun HomePicture(palette: FramePalette) {
    val rim = FrameTheme.colors.hairline
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(SwatchRatio)
            .clip(PictureShape)
            .border(0.5.dp, rim, PictureShape),
    ) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) { miniHome(palette) }
    }
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
    // The block that stands out from this canvas: the cards on a light one, the raised surface on a dark one.
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
