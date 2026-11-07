package com.frameglass.playerui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Pixels at each screen edge that belong to system gestures. Touches starting there are left alone. */
internal data class GestureEdges(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Everything the player's touch surface can mean (F-P5). Coordinates are physical, so left stays left in
 * RTL, as decision D6 needs for horizontal seek.
 */
internal interface GestureTarget {
    val locked: Boolean
    fun tap()
    fun skip(forward: Boolean)
    fun holdStart()
    fun holdEnd()
    fun seekStart()

    /** [fraction] is the whole drag so far as a share of the screen width. */
    fun seekDrag(fraction: Float)
    fun seekEnd()

    /** [delta] is this step as a share of the screen height, positive upward. */
    fun level(leftSide: Boolean, delta: Float)
    fun levelEnd()
    fun pinch(zoomChange: Float, pan: Offset)
    fun pinchEnd()
}

private enum class TouchMode { UNDECIDED, SEEK, LEVEL, PINCH, HOLD }
private enum class Side { LEFT, MIDDLE, RIGHT }

private const val SIDE_SHARE = 0.4f
private const val SKIP_CHAIN_MILLIS = 700L

/**
 * One detector for all of it, because tap, drag, long press and pinch must each cancel the others.
 * A tap at the sides waits for a possible second tap, and one in the middle acts at once.
 */
internal suspend fun PointerInputScope.detectPlayerGestures(target: GestureTarget, edges: () -> GestureEdges) = coroutineScope {
    val scope = this
    var pendingTap: Job? = null
    var lastSide: Side? = null
    var chainEndsAt = 0L

    fun onTap(x: Float, time: Long) {
        if (target.locked) {
            target.tap()
            return
        }
        val side = when {
            x < size.width * SIDE_SHARE -> Side.LEFT
            x > size.width * (1f - SIDE_SHARE) -> Side.RIGHT
            else -> Side.MIDDLE
        }
        val second = pendingTap?.isActive == true && lastSide == side
        when {
            side == Side.MIDDLE -> {
                pendingTap?.cancel()
                target.tap()
            }
            second || (time < chainEndsAt && lastSide == side) -> {
                pendingTap?.cancel()
                chainEndsAt = time + SKIP_CHAIN_MILLIS
                target.skip(side == Side.RIGHT)
            }
            else -> {
                lastSide = side
                pendingTap?.cancel()
                pendingTap = scope.launch {
                    delay(viewConfiguration.doubleTapTimeoutMillis)
                    target.tap()
                }
            }
        }
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val start = down.position
        val blocked = edges()
        val onEdge = start.x < blocked.left || start.x > size.width - blocked.right ||
            start.y < blocked.top || start.y > size.height - blocked.bottom
        if (onEdge) return@awaitEachGesture

        val slop = viewConfiguration.touchSlop
        val holdAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
        var mode = TouchMode.UNDECIDED
        var time = down.uptimeMillis
        while (true) {
            val waiting = mode == TouchMode.UNDECIDED && !target.locked
            val event = if (waiting) {
                withTimeoutOrNull((holdAt - time).coerceAtLeast(1L)) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                mode = TouchMode.HOLD
                target.holdStart()
                continue
            }
            val pressed = event.changes.filter { it.pressed }
            time = event.changes.firstOrNull()?.uptimeMillis ?: time
            if (pressed.isEmpty()) break
            if (target.locked) continue
            val finger = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
            when (mode) {
                TouchMode.UNDECIDED -> {
                    val moved = finger.position - start
                    if (pressed.size >= 2) {
                        mode = TouchMode.PINCH
                    } else if (moved.getDistance() > slop) {
                        mode = if (abs(moved.x) > abs(moved.y)) TouchMode.SEEK else TouchMode.LEVEL
                        if (mode == TouchMode.SEEK) target.seekStart()
                    }
                }
                TouchMode.SEEK -> target.seekDrag((finger.position.x - start.x) / size.width)
                TouchMode.LEVEL -> target.level(start.x < size.width / 2f, -finger.positionChange().y / size.height)
                TouchMode.PINCH -> target.pinch(event.calculateZoom(), event.calculatePan())
                TouchMode.HOLD -> Unit
            }
            if (mode != TouchMode.UNDECIDED) event.changes.forEach { it.consume() }
        }
        when (mode) {
            TouchMode.UNDECIDED -> onTap(start.x, time)
            TouchMode.SEEK -> target.seekEnd()
            TouchMode.LEVEL -> target.levelEnd()
            TouchMode.PINCH -> target.pinchEnd()
            TouchMode.HOLD -> target.holdEnd()
        }
    }
}
