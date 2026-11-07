package com.frameglass.glass

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

private const val MinPressMillis = 90L

@Stable
private class GlassPress(private val scope: CoroutineScope) {
    val swell = Animatable(0f)
    var touch by mutableStateOf(Offset.Zero)

    private var downAt = 0L
    private var release: Job? = null

    fun down(position: Offset) {
        release?.cancel()
        touch = position
        downAt = SystemClock.uptimeMillis()
        scope.launch { swell.animateTo(1f, FrameMotion.fastSpatial()) }
    }

    fun up() {
        // A quick tap still shows the full press before it settles.
        val wait = MinPressMillis - (SystemClock.uptimeMillis() - downAt)
        release = scope.launch {
            delay(max(0L, wait))
            swell.animateTo(0f, FrameMotion.fastSpatial())
        }
    }
}

// Watches the pointer without consuming it, so clicks and parent scrolling behave as usual.
private suspend fun PointerInputScope.trackPress(press: GlassPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        press.down(down.position)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToDownIgnoreConsumed()) continue
            if (!change.pressed || change.isConsumed) break
            press.touch = change.position
        }
        press.up()
    }
}

/** A control that swells to 1.04 on press, with a soft highlight that follows the finger. */
@Composable
fun PressableGlass(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    highlight: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val press = remember(scope) { GlassPress(scope) }
    val interactions = remember { MutableInteractionSource() }
    val showHighlight = highlight && rememberEffectsLevel() == EffectsLevel.Full
    val colors = FrameTheme.colors

    Box(
        modifier
            .graphicsLayer {
                val scale = lerp(1f, FrameMotion.PressScale, press.swell.value)
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(enabled) { if (enabled) trackPress(press) }
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = role,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .then(
                if (showHighlight) {
                    Modifier.drawWithCache {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        val radius = size.maxDimension * 0.75f
                        onDrawWithContent {
                            drawContent()
                            val strength = colors.pressStrength * press.swell.value.coerceIn(0f, 1f)
                            if (strength > 0f) {
                                GlassBrushes.highlight(press.touch, radius, strength, colors.pressInk)
                                    ?.let { drawOutline(outline, it) }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
        content = content,
    )
}
