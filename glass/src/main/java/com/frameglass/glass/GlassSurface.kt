package com.frameglass.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.glass.GlassAccessibilitySettings
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.LocalGlassAccessibilitySettings
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import kotlin.math.max

// This file is the only caller of Haze (brief 7.3). Swapping the blur library touches nothing else.

/** The surfaces that may be glass (brief 7.3). Never use glass on a card. */
enum class GlassKind {
    /** The floating tab bar. */
    TabBar,

    /** The top bar. */
    TopBar,

    /** Sheets. */
    Sheet,

    /** Menus. */
    Menu,

    /** Small badges and chips over art. */
    Badge,

    /** Round controls and player control clusters. */
    Control,
}

private const val MaxBlurredLayers = 3
private const val DegradedTint = 0.72f

private val FullAlpha: () -> Float = { 1f }

private val Refraction = RefractionProfile.Edge(20.dp)
private val NoRefraction = RefractionProfile.Edge(0.dp)

/** One surface asking for real blur. */
internal class GlassLayer(var recipe: GlassRecipe) {
    var coordinates: LayoutCoordinates? = null
    var alpha: () -> Float = FullAlpha
    var tint: State<Float>? = null
    var level by mutableFloatStateOf(0f)
}

/** The window's blur state: its sources and which surfaces hold one of the blur slots. */
@Stable
internal class GlassState {
    val haze = HazeState()
    val sampler = GlassSampler()
    var sourceCount by mutableIntStateOf(0)
    private val layers = mutableStateListOf<GlassLayer>()

    fun add(layer: GlassLayer) {
        if (layer !in layers) layers += layer
    }

    fun remove(layer: GlassLayer) {
        layers -= layer
    }

    /** The highest priority [MaxBlurredLayers] surfaces blur. The rest fall back to tint and rim. */
    fun isGranted(layer: GlassLayer): Boolean =
        layers.sortedBy { it.recipe.priority }.take(MaxBlurredLayers).any { it === layer }
}

internal val LocalGlassState = compositionLocalOf<GlassState?> { null }
internal val LocalInsideGlassSource = compositionLocalOf { false }

/** Marks this layout as what glass surfaces in the window blur. */
@Composable
fun Modifier.glassSource(zIndex: Float = 0f): Modifier {
    val glass = LocalGlassState.current ?: return this
    DisposableEffect(glass) {
        glass.sourceCount++
        onDispose { glass.sourceCount-- }
    }
    return hazeSource(glass.haze, zIndex)
}

/** Holds content that glass blurs, such as a scrolling list; glass placed inside it stays unblurred. */
@Composable
fun GlassSource(
    modifier: Modifier = Modifier,
    zIndex: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glassSource(zIndex), propagateMinConstraints = true) {
        CompositionLocalProvider(LocalInsideGlassSource provides true) { content() }
    }
}

/**
 * The one glass surface. With no source, no free blur slot or [blur] off it keeps tint and rim, and
 * sheets and menus turn solid in that case.
 */
@Composable
fun GlassSurface(
    kind: GlassKind,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    blur: Boolean = true,
    alpha: () -> Float = FullAlpha,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = FrameTheme.colors
    val recipe = kind.recipe(colors.isLight)
    val glass = if (blur && !LocalInsideGlassSource.current) LocalGlassState.current else null
    val degraded = rememberEffectsLevel() == EffectsLevel.Degraded
    // Outlives a theme change: a new layer would have no coordinates, because the bar does not move.
    val layer = remember { GlassLayer(recipe) }
    layer.recipe = recipe
    val visible by remember(alpha) { derivedStateOf { alpha() > 0f } }
    val wantsBlur = glass != null && visible && glass.sourceCount > 0
    if (glass != null) {
        DisposableEffect(glass, layer, wantsBlur) {
            if (wantsBlur) glass.add(layer)
            onDispose { glass.remove(layer) }
        }
    }
    val blurred = glass != null && wantsBlur && glass.isGranted(layer)
    val sampled = glass != null && blurred && recipe.sampled
    if (glass != null) {
        DisposableEffect(glass, layer, sampled) {
            if (sampled) glass.sampler.add(layer)
            onDispose { glass.sampler.remove(layer) }
        }
    }

    val level = if (sampled) layer.level else recipe.worstLevel
    val contrastTint = GlassContrast.tintAlpha(recipe.baseTint, level, recipe.textLimit, recipe.tintLevel)
    val wantedTint = when {
        recipe.solidWithoutBlur && !blurred -> 1f
        degraded -> max(contrastTint, DegradedTint)
        else -> contrastTint
    }
    val tint = if (recipe.sampled) {
        animateFloatAsState(wantedTint, tween(FrameMotion.TintMillis), label = "glassTint")
    } else {
        rememberUpdatedState(wantedTint)
    }
    SideEffect {
        layer.alpha = alpha
        layer.tint = tint
    }

    val refraction = recipe.refraction && !degraded
    val tilt = if (blurred && !degraded) LocalGlassTilt.current else null
    if (tilt != null) {
        DisposableEffect(tilt) {
            tilt.acquire()
            onDispose { tilt.release() }
        }
    }

    Box(
        modifier
            .onPlaced { layer.coordinates = it }
            .then(if (recipe.shadow != null) Modifier.dropShadow(shape, recipe.shadow) else Modifier)
            .then(
                if (blurred) {
                    val canvas = colors.canvas
                    val style = remember(recipe, shape, refraction, tint, canvas) {
                        glassStyle(recipe, shape, refraction, tint, canvas) { layer.alpha() }
                    }
                    Modifier.glassEffect(glass, style, refraction)
                } else {
                    Modifier.glassTint(recipe, shape, tint, colors.canvas) { layer.alpha() }
                },
            ),
    ) {
        if (blurred) {
            // A layer of its own, so a tilt redraw does not touch the blurred material under it.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { }
                    .drawWithCache {
                        val rim = rimDrawer(recipe, shape, tilt) { layer.alpha() }
                        onDrawBehind { rim() }
                    },
            )
        }
        content()
    }
}

private fun Modifier.glassEffect(glass: GlassState, style: GlassStyle, refraction: Boolean): Modifier =
    hazeGlass(
        input = HazeInput.Sources(glass.haze),
        style = style,
        expandLayerBounds = refraction,
    )

// The block starts from Haze's Regular response for the phone's appearance, so every value that
// differs between light and dark is written here. The app's theme can differ from the phone's, and
// the contrast rule assumes a plain sRGB blend: no white point, contrast, ambient or specular lift
// over the tint. That leaves the canvas as the one thing the theme changes here.
private fun glassStyle(
    recipe: GlassRecipe,
    shape: RoundedCornerShape,
    refraction: Boolean,
    tint: State<Float>,
    canvas: Color,
    alpha: () -> Float,
): GlassStyle {
    val optics = GlassOptics.regular.copy(
        blurRadius = OpticalSizeValue.Fixed(recipe.blur),
        depth = OpticalSizeValue.Fixed(1f),
        refractionStrength = if (refraction) GlassOptics.regular.refractionStrength else 0f,
        refractionProfile = if (refraction) Refraction else NoRefraction,
    )
    return GlassStyle {
        shape(shape)
        optics(optics)
        // The tint only shows where the input has alpha, so gaps in a source need the canvas behind.
        backgroundColor(canvas)
        specularIntensity(0f)
        edgeShadow(Color.Transparent)
        ambientResponse(0f)
        chromaticAberrationStrength(0f)
        contrast(0f)
        whitePoint(0f)
        chromaMultiplier(1.1f)
        tint(canvas.copy(alpha = tint.value))
        alpha(alpha())
    }
}

private fun Modifier.glassTint(
    recipe: GlassRecipe,
    shape: RoundedCornerShape,
    tint: State<Float>,
    canvas: Color,
    alpha: () -> Float,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val rim = rimDrawer(recipe, shape, null, alpha)
    onDrawBehind {
        drawOutline(outline, canvas, alpha = tint.value * alpha())
        rim()
    }
}

private fun CacheDrawScope.rimDrawer(
    recipe: GlassRecipe,
    shape: RoundedCornerShape,
    tilt: GlassTilt?,
    alpha: () -> Float,
): DrawScope.() -> Unit {
    val width = recipe.rimWidth.toPx()
    if (recipe.bottomRimOnly) {
        return {
            drawRect(
                recipe.bottomRim,
                topLeft = Offset(0f, size.height - width),
                size = Size(size.width, width),
                alpha = alpha(),
            )
        }
    }
    val outline = shape.createOutline(Size(size.width - width, size.height - width), layoutDirection, this)
    val stroke = Stroke(width)
    val plain = Brush.verticalGradient(listOf(recipe.rimTop, recipe.rimBottom))
    val maxShift = FrameMotion.MaxTiltShift.toPx()
    return {
        val opacity = alpha()
        if (opacity > 0f) {
            val brush = if (tilt == null) {
                plain
            } else {
                GlassBrushes.sheen(size, Offset(tilt.x, tilt.y) * maxShift, recipe.rimTop, recipe.rimBottom)
                    ?: plain
            }
            translate(width / 2f, width / 2f) {
                drawOutline(outline, brush, opacity, stroke)
            }
        }
    }
}

/** Applies Haze's reduced-transparency path and lowest quality while degraded (brief 7.9). */
@Composable
internal fun ProvideGlassEffects(level: EffectsLevel, canvas: Color, content: @Composable () -> Unit) {
    val degraded = level == EffectsLevel.Degraded
    val settings = remember(degraded, canvas) {
        GlassAccessibilitySettings(
            reduceTransparency = degraded,
            reducedTransparencyFallbackColor = canvas,
        )
    }
    CompositionLocalProvider(
        LocalGlassAccessibilitySettings provides settings,
        LocalHazePerformanceMode provides
            if (degraded) HazePerformanceMode.Performance else HazePerformanceMode.Default,
        content = content,
    )
}
