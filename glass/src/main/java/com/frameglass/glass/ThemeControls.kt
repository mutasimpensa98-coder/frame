package com.frameglass.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

// The controls of the custom theme: three sliders whose tracks show what they choose, and the hue and
// saturation field of the "any colour" accent. All drawn here; nothing comes from a colour picker library.

private const val MaxHue = 360f
private const val HueStops = 12
private const val ToneStops = 12

/** The hue circle, closed, for rainbow tracks and rings. */
internal val HueWheel: List<Color> = List(HueStops + 1) { Color.hsv(MaxHue * it / HueStops % MaxHue, 0.78f, 1f) }

private val SliderHeight = 44.dp
private val TrackHeight = 16.dp
private val KnobRadius = 11.dp
private val KnobRing = 3.dp
private val FieldHeight = 180.dp
private val FieldCorner = 16.dp
private val FieldKnobRadius = 12.dp

/** The background hue of a custom theme, 0 to 360, on a rainbow track. */
@Composable
fun HueSlider(
    hue: Float,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    GradientSlider(
        value = hue / MaxHue,
        onChange = { onChange(it * MaxHue) },
        onChangeFinished = onChangeFinished,
        stops = remember { HueWheel.mapIndexed { index, color -> index.toFloat() / HueStops to color } },
        label = label,
        modifier = modifier,
    )
}

/**
 * The shade of [theme], from pure black to the lightest, on a track of the canvases it would give. The track
 * shows the step between the dark and the light shades as it is.
 */
@Composable
fun ShadeSlider(
    theme: CustomTheme,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val stops = remember(theme.hue) {
        val split = CustomTheme.DarkShades
        val dark = List(ToneStops + 1) { split * it / ToneStops }
        val light = List(ToneStops + 1) { split + (1f - split) * it / ToneStops }
        val lightStart = FramePalettes.customSpec(theme.copy(shade = split + 1f / 1024f)).canvas
        dark.map { it to FramePalettes.customSpec(theme.copy(shade = it)).canvas } +
            (split to lightStart) +
            light.drop(1).map { it to FramePalettes.customSpec(theme.copy(shade = it)).canvas }
    }
    GradientSlider(theme.shade, onChange, onChangeFinished, stops, label, modifier)
}

/** The surface tone of [theme], on a track of the card colours it would give over that canvas. */
@Composable
fun SurfaceSlider(
    theme: CustomTheme,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val stops = remember(theme.hue, theme.shade) {
        List(ToneStops + 1) { index ->
            val at = index.toFloat() / ToneStops
            at to FramePalettes.customSpec(theme.copy(surface = at)).card
        }
    }
    GradientSlider(theme.surface, onChange, onChangeFinished, stops, label, modifier)
}

/**
 * A slider from 0 to 1 whose track is a gradient of [stops]. It mirrors in RTL, as layout does. A drag only
 * starts once it is clearly sideways, so a page still scrolls from a finger that lands on it.
 */
@Composable
private fun GradientSlider(
    value: Float,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    stops: List<Pair<Float, Color>>,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = FrameTheme.colors
    val current = value.coerceIn(0f, 1f)
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    val rtl by rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    val inset = with(LocalDensity.current) { KnobRadius.toPx() }
    val finger = remember { SliderFinger() }
    fun moveTo(x: Float) {
        val span = max(1f, finger.width - 2f * inset)
        val fraction = ((x - inset) / span).coerceIn(0f, 1f)
        change(if (rtl) 1f - fraction else fraction)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .onSizeChanged { finger.width = it.width.toFloat() }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(current, 0f..1f)
                setProgress { wanted ->
                    change(wanted.coerceIn(0f, 1f))
                    finished()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    moveTo(position.x)
                    finished()
                }
            }
            .draggable(
                state = rememberDraggableState { delta ->
                    finger.x += delta
                    moveTo(finger.x)
                },
                orientation = Orientation.Horizontal,
                onDragStarted = { start ->
                    finger.x = start.x
                    moveTo(finger.x)
                },
                onDragStopped = { finished() },
            ),
    ) {
        val track = TrackHeight.toPx()
        val top = (size.height - track) / 2f
        val start = if (rtl) size.width - inset else inset
        val end = if (rtl) inset else size.width - inset
        drawRoundRect(
            brush = Brush.horizontalGradient(*stops.toTypedArray(), startX = start, endX = end),
            topLeft = Offset(0f, top),
            size = Size(size.width, track),
            cornerRadius = CornerRadius(track / 2f),
        )
        drawRoundRect(
            color = colors.outlineVariant,
            topLeft = Offset(0f, top),
            size = Size(size.width, track),
            cornerRadius = CornerRadius(track / 2f),
            style = Stroke(1.dp.toPx()),
        )
        val centre = Offset(start + (end - start) * current, size.height / 2f)
        drawCircle(KnobEdge, inset, centre)
        drawCircle(Color.White, inset - KnobRing.toPx(), centre)
    }
}

// Where the finger is during a drag, and how wide the slider was laid out. Plain fields: nothing draws from them.
private class SliderFinger {
    var x = 0f
    var width = 1f
}

// White with a dark ring, so a knob shows on every colour a track or the field can hold.
private val KnobEdge = Color(0xFF101014)

/** The colour of a point of [HueSaturationField]: a hue in degrees and a saturation from 0 to 1, as ARGB. */
fun accentArgb(hue: Float, saturation: Float): Int =
    Color.hsv(ThemeMath.wrapHue(hue).coerceAtMost(MaxHue), saturation.coerceIn(0f, 1f), 1f).toArgb()

/** The hue in degrees and the saturation of [argb], to place a stored accent back on [HueSaturationField]. */
fun accentHueSaturation(argb: Int): Pair<Float, Float> {
    val r = (argb shr 16 and 255) / 255f
    val g = (argb shr 8 and 255) / 255f
    val b = (argb and 255) / 255f
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val spread = high - low
    if (spread <= 0f) return 0f to 0f
    val sector = when (high) {
        r -> (g - b) / spread % 6f
        g -> (b - r) / spread + 2f
        else -> (r - g) / spread + 4f
    }
    return ThemeMath.wrapHue(sector * 60f) to spread / high
}

/**
 * The "any colour" field: hue along it and saturation up it, at full brightness. The theme then moves the
 * picked colour as far as it needs to read, so every point of the field is a usable accent.
 */
@Composable
fun HueSaturationField(
    hue: Float,
    saturation: Float,
    onChange: (hue: Float, saturation: Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = FrameTheme.colors
    val change by rememberUpdatedState(onChange)
    val rtl by rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                fun moveTo(position: Offset) {
                    val x = (position.x / size.width).coerceIn(0f, 1f)
                    val along = if (rtl) 1f - x else x
                    change(along * MaxHue, 1f - (position.y / size.height).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    moveTo(down.position)
                    drag(down.id) { moved ->
                        moved.consume()
                        moveTo(moved.position)
                    }
                }
            },
    ) {
        val corner = CornerRadius(FieldCorner.toPx())
        val wheel = List(HueStops + 1) { Color.hsv(MaxHue * it / HueStops % MaxHue, 1f, 1f) }
        drawRoundRect(
            brush = Brush.horizontalGradient(if (rtl) wheel.reversed() else wheel),
            cornerRadius = corner,
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.White)),
            cornerRadius = corner,
        )
        drawRoundRect(colors.outlineVariant, cornerRadius = corner, style = Stroke(1.dp.toPx()))
        val along = (hue / MaxHue).coerceIn(0f, 1f)
        val centre = Offset(
            x = size.width * if (rtl) 1f - along else along,
            y = size.height * (1f - saturation.coerceIn(0f, 1f)),
        )
        val knob = FieldKnobRadius.toPx()
        drawCircle(KnobEdge, knob, centre)
        drawCircle(Color.White, knob - KnobRing.toPx(), centre)
        drawCircle(Color(accentArgb(along * MaxHue, saturation)), knob - 2f * KnobRing.toPx(), centre)
    }
}
