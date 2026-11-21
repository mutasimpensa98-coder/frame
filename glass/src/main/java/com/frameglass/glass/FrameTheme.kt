package com.frameglass.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val LocalFramePalette = staticCompositionLocalOf { FramePalettes.Default }
private val DefaultLook = FrameLook()
internal val LocalCoverTint = staticCompositionLocalOf { false }

/** The theme in force here. */
object FrameTheme {
    /** The colours of this theme and accent, scoped to the [FrameTheme] around the caller. */
    val colors: FramePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalFramePalette.current
}

private fun FramePalette.materialColors(): ColorScheme =
    (if (isLight) lightColorScheme() else darkColorScheme()).copy(
        primary = accent,
        onPrimary = GlassContrast.readableOn(accent),
        primaryContainer = card,
        onPrimaryContainer = content,
        secondary = content,
        onSecondary = canvas,
        secondaryContainer = card,
        onSecondaryContainer = content,
        tertiary = content,
        onTertiary = canvas,
        tertiaryContainer = card,
        onTertiaryContainer = content,
        background = canvas,
        onBackground = content,
        surface = canvas,
        onSurface = content,
        surfaceVariant = elevated,
        onSurfaceVariant = secondaryContent,
        surfaceTint = Color.Transparent,
        inverseSurface = content,
        inverseOnSurface = canvas,
        inversePrimary = canvas,
        error = error,
        onError = onError,
        errorContainer = card,
        onErrorContainer = error,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = Color.Black,
        surfaceBright = card,
        surfaceDim = canvas,
        surfaceContainerLowest = canvas,
        surfaceContainerLow = elevated,
        surfaceContainer = elevated,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
    )

// Material's own slots all map onto the six styles, so its tracked labels can never appear.
private val FrameTypography = Typography(
    displayLarge = FrameType.Display,
    displayMedium = FrameType.Display,
    displaySmall = FrameType.Display,
    headlineLarge = FrameType.Display,
    headlineMedium = FrameType.TitleLarge,
    headlineSmall = FrameType.TitleLarge,
    titleLarge = FrameType.TitleLarge,
    titleMedium = FrameType.Title,
    titleSmall = FrameType.Label,
    bodyLarge = FrameType.Body,
    bodyMedium = FrameType.Body,
    bodySmall = FrameType.Caption,
    labelLarge = FrameType.Label,
    labelMedium = FrameType.Label,
    labelSmall = FrameType.Caption,
)

private val FrameMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = FrameShapes.Poster,
    medium = FrameShapes.Card,
    large = FrameShapes.Card,
    extraLarge = FrameShapes.Dialog,
)

// Keeps plain FrameColors reads on this window's palette while it is in front (see ThemeBridge).
@Composable
private fun TrackThemeWindow(palette: FramePalette) {
    val window = remember { ThemeWindow(palette).also(ThemeBridge::enter) }
    // Written here, not in an effect, so the content composed below already reads it.
    window.palette = palette
    SideEffect { ThemeBridge.published(window) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, window) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Both, because leaving picture-in-picture resumes a window that never stopped.
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> ThemeBridge.enter(window)
                Lifecycle.Event.ON_STOP -> ThemeBridge.stopped(window)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            ThemeBridge.leave(window)
        }
    }
}

/**
 * The dark look with the white accent for one part of a screen, whatever the app's theme or accent, such as the
 * feature tour. Unlike a nested [FrameTheme] it opens no theme window: it changes what [FrameTheme.colors] and Material
 * read below it, and plain [FrameColors] reads keep following the app. Read colours through [FrameTheme.colors]
 * inside it.
 */
@Composable
fun DarkLook(content: @Composable () -> Unit) {
    val palette = FramePalettes.Default
    CompositionLocalProvider(LocalFramePalette provides palette) {
        MaterialTheme(
            colorScheme = remember(palette) { palette.materialColors() },
            shapes = FrameMaterialShapes,
            typography = FrameTypography,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides palette.content,
                LocalTextStyle provides FrameType.Body,
                content = content,
            )
        }
    }
}

private class PaletteFade(var settled: FramePalette) {
    val progress = Animatable(1f)

    /** The palette of the frame on screen while a fade runs. */
    var frame by mutableStateOf<FramePalette?>(null)
}

// A theme change fades from the colours on screen to the new ones over the tint time, in the same window:
// nothing is recreated. Each frame of the fade is a palette of its own, and the last one is the target itself.
@Composable
private fun fading(target: FramePalette, animate: Boolean): FramePalette {
    val fade = remember { PaletteFade(target) }
    if (!animate) {
        fade.settled = target
        SideEffect { fade.frame = null }
        return target
    }
    LaunchedEffect(target) {
        val from = fade.frame ?: fade.settled
        if (from !== target) {
            fade.progress.snapTo(0f)
            fade.progress.animateTo(1f, tween(FrameMotion.TintMillis)) { fade.frame = from.toward(target, value) }
        }
        fade.settled = target
        fade.frame = null
    }
    return fade.frame ?: fade.settled
}

/**
 * The Frame theme, glass state and overlay layer. It paints no background of its own.
 *
 * With no arguments it is the dark look, which is also what the player keeps in every theme. [look] is the
 * owner's theme and accent. A change of it fades in over [FrameMotion.TintMillis] without recreating anything,
 * unless [animate] is off or the phone is in degraded mode (brief 7.9), where it is a plain cut. [coverTint]
 * carries the cover colour of [coverGlow] down to where the art meets the canvas.
 */
@Composable
fun FrameTheme(
    look: FrameLook = DefaultLook,
    coverTint: Boolean = false,
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shown = if (look.followSystem) look.shownWhen(isSystemInDarkTheme()) else look
    val effects = observeEffectsLevel()
    val palette = fading(FramePalettes.of(shown), animate = animate && effects == EffectsLevel.Full)
    TrackThemeWindow(palette)
    // Null below API 33: with no state no surface blurs, and Haze is never touched (GlassSupport).
    val glass = remember { if (GlassSupport.liquid) GlassState() else null }
    val overlays = remember { GlassOverlayHost() }
    val tilt = rememberGlassTilt()
    CompositionLocalProvider(
        LocalFramePalette provides palette,
        LocalCoverTint provides coverTint,
        LocalEffectsLevel provides effects,
        LocalGlassState provides glass,
        LocalGlassTilt provides tilt,
        LocalGlassOverlayHost provides overlays,
    ) {
        ProvideGlassEffects(effects, palette.canvas) {
            MaterialTheme(
                colorScheme = remember(palette) { palette.materialColors() },
                shapes = FrameMaterialShapes,
                typography = FrameTypography,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides palette.content,
                    LocalTextStyle provides FrameType.Body,
                ) {
                    if (glass != null) GlassSampling(glass)
                    FrameSystemBars()
                    Box(propagateMinConstraints = true) {
                        content()
                        GlassOverlayLayer(overlays)
                    }
                }
            }
        }
    }
}
