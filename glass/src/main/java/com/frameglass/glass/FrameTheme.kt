package com.frameglass.glass

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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val LocalFramePalette = staticCompositionLocalOf { FramePalettes.Default }
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
 * The Frame Glass theme, glass state and overlay layer. It paints no background of its own.
 *
 * With no arguments it is the dark look, which is also what the player keeps in every theme. [coverTint]
 * carries the cover colour of [coverGlow] down to where the art meets the canvas.
 */
@Composable
fun FrameTheme(
    mode: FrameThemeMode = FrameThemeMode.Dark,
    accent: FrameAccent = FrameAccent.White,
    coverTint: Boolean = false,
    content: @Composable () -> Unit,
) {
    val light = when (mode) {
        FrameThemeMode.System -> !isSystemInDarkTheme()
        FrameThemeMode.Dark -> false
        FrameThemeMode.Light -> true
    }
    val palette = FramePalettes.of(light, accent)
    TrackThemeWindow(palette)
    val effects = observeEffectsLevel()
    val glass = remember { GlassState() }
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
                    GlassSampling(glass)
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
