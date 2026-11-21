package com.frameglass.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

private const val LiftScale = 1.08f
private val LiftShadow = 10.dp

// A chip pushed past an edge of the row scrolls it: at full speed once it is this far out.
private val EdgeZone = 24.dp
private val EdgeSpeed = 720.dp

/**
 * Where a chip being dragged along a row goes next, with no layout. Every position runs along the row from its
 * start, whatever the reading direction, so the same numbers serve Arabic and English.
 */
object ChipDragRules {
    /**
     * +1 to trade places with the chip after, -1 with the chip before, 0 to stay. [start] and [size] are the chip
     * as it is drawn under the finger; [beforeMiddle] and [afterMiddle] are the middles of its neighbours, or null
     * at an end of the row. A chip trades with the next one when its far edge passes that chip's middle, and with
     * the one before when its near edge passes that one's middle. After a trade the chip is clear of the middle it
     * crossed by at least the gap between chips, so chips of different widths never trade back and forth.
     */
    fun step(start: Float, size: Float, beforeMiddle: Float?, afterMiddle: Float?): Int = when {
        afterMiddle != null && start + size > afterMiddle -> 1
        beforeMiddle != null && start < beforeMiddle -> -1
        else -> 0
    }

    /** How far the drawn chip sticks out of the row: positive past its end, negative before its start, 0 inside. */
    fun overhang(start: Float, size: Float, viewportStart: Float, viewportEnd: Float): Float = when {
        start + size > viewportEnd -> start + size - viewportEnd
        start < viewportStart -> start - viewportStart
        else -> 0f
    }

    /** The scroll speed for an [overhang], along the row: it grows with the overhang up to [fullSpeed] at [zone]. */
    fun edgeSpeed(overhang: Float, zone: Float, fullSpeed: Float): Float =
        if (zone <= 0f) 0f else (overhang / zone).coerceIn(-1f, 1f) * fullSpeed

    /** The order after the item at [from] moved to [to]. Anything out of range changes nothing. */
    fun <T> moved(order: List<T>, from: Int, to: Int): List<T> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }
}

/** The chip in the air over a [LazyRow]. Positions run along the row from the start of its content. */
@Stable
private class ChipDrag(private val list: LazyListState) {
    /** The key of the chip being dragged, or null. */
    var key by mutableStateOf<String?>(null)
        private set

    /** Its place in the row now, which the row's layout follows a frame later. */
    var index by mutableIntStateOf(-1)

    /** Its place when it was lifted. */
    var from = -1
        private set

    private var travel by mutableFloatStateOf(0f)
    private var origin by mutableIntStateOf(0)

    private val slot: LazyListItemInfo?
        get() = key?.let { lifted -> list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == lifted } }

    /** Where the chip is drawn: where it lay when lifted, plus how far the finger went since. */
    private val drawnStart: Float get() = origin + travel

    /** How far that is from where the row laid the chip out. The row may have scrolled or reordered meanwhile. */
    val shift: Float get() = slot?.let { drawnStart - it.offset } ?: 0f

    /** Lifts the chip under [position], or returns false when the press landed between chips. */
    fun lift(position: Float): Boolean {
        val item = list.layoutInfo.visibleItemsInfo
            .firstOrNull { position >= it.offset && position <= it.offset + it.size } ?: return false
        val lifted = item.key as? String ?: return false
        origin = item.offset
        travel = 0f
        index = item.index
        from = item.index
        key = lifted
        return true
    }

    fun move(by: Float) {
        travel += by
    }

    /** The place the chip should trade to now, or -1. It waits while the row has not laid out the last trade. */
    fun target(): Int {
        val me = slot ?: return -1
        if (me.index != index) return -1
        val items = list.layoutInfo.visibleItemsInfo
        val before = items.firstOrNull { it.index == me.index - 1 }?.let { it.offset + it.size / 2f }
        val after = items.firstOrNull { it.index == me.index + 1 }?.let { it.offset + it.size / 2f }
        return when (ChipDragRules.step(drawnStart, me.size.toFloat(), before, after)) {
            1 -> me.index + 1
            -1 -> me.index - 1
            else -> -1
        }
    }

    fun overhang(): Float {
        val me = slot ?: return 0f
        val info = list.layoutInfo
        return ChipDragRules.overhang(
            drawnStart,
            me.size.toFloat(),
            info.viewportStartOffset.toFloat(),
            info.viewportEndOffset.toFloat(),
        )
    }

    fun drop() {
        key = null
        index = -1
        travel = 0f
    }
}

/**
 * A row of single-choice chips whose order the owner sets (owner request of 2026-11-22). A tap selects, as on
 * [FilterChips]. A long press lifts a chip: it grows a little, takes a shadow and follows the finger along the
 * row, the others slide out of its way, and the row scrolls when the chip is pushed past an edge. On release the
 * chip settles into its place and [onMove] is told once, with places in [keys]. Until the caller hands back the
 * new [keys], the row keeps the order of the drop.
 *
 * [onDragChange] says when a chip is in the air, so a pager around the row can stop swiping meanwhile.
 */
@Composable
fun ReorderableFilterChips(
    keys: List<String>,
    labels: List<String>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = FrameSpacing.S16),
    onDragChange: (Boolean) -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val colors = FrameTheme.colors
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduced by rememberUpdatedState(rememberEffectsLevel() == EffectsLevel.Degraded)
    val edgeZone = with(LocalDensity.current) { EdgeZone.toPx() }
    val edgeSpeed = with(LocalDensity.current) { EdgeSpeed.toPx() }
    val list = rememberLazyListState()
    val drag = remember(list) { ChipDrag(list) }
    val names = remember(keys, labels) { keys.zip(labels).toMap() }
    // The order of a drag, from the lift until the caller hands back the order of the drop. Null follows the
    // caller, which is read in the same pass, so a chip that comes or goes never shows a frame late.
    var dragged by remember { mutableStateOf<List<String>?>(null) }
    val order = dragged ?: keys
    val latestKeys by rememberUpdatedState(keys)
    val move by rememberUpdatedState(onMove)
    val dragChange by rememberUpdatedState(onDragChange)
    // The chip that was just dropped glides from under the finger to its place.
    var settling by remember { mutableStateOf<String?>(null) }
    val settle = remember { Animatable(0f) }
    val earlier = stringResource(R.string.glass_chip_move_start)
    val later = stringResource(R.string.glass_chip_move_end)

    // New keys from the caller end the drop's own order: they are that order saved, or something newer.
    LaunchedEffect(keys) {
        if (drag.key == null) dragged = null
    }
    LaunchedEffect(selectedKey) {
        val index = keys.indexOf(selectedKey)
        if (index < 0 || drag.key != null) return@LaunchedEffect
        val layout = list.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val hidden = item == null ||
            item.offset < layout.viewportStartOffset ||
            item.offset + item.size > layout.viewportEndOffset
        if (hidden) list.animateScrollToItem(index)
    }

    fun lift(position: Float): Boolean {
        if (latestKeys.size < 2 || !drag.lift(position)) return false
        dragged = latestKeys
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        dragChange(true)
        return true
    }

    fun trade(to: Int) {
        val now = dragged ?: return
        val at = drag.index
        if (at !in now.indices || to !in now.indices) return
        // The row keeps its first visible chip in place by its key, so a trade there would scroll the row.
        if (at == list.firstVisibleItemIndex || to == list.firstVisibleItemIndex) {
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        }
        dragged = ChipDragRules.moved(now, at, to)
        drag.index = to
        haptics.performHapticFeedback(SegmentTickHaptic)
    }

    fun drop() {
        val key = drag.key ?: return
        val from = drag.from
        val to = drag.index
        val shift = drag.shift
        drag.drop()
        dragChange(false)
        settling = key
        scope.launch {
            settle.snapTo(shift)
            settle.animateTo(0f, if (reduced) FrameMotion.effects() else FrameMotion.defaultSpatial())
            if (settling == key) settling = null
        }
        // The drop's order stays on screen until the caller hands it back. When the chips changed under the drag,
        // as when one appeared, or nothing moved, the caller's order is the one to show and nothing is saved.
        if (from != to && dragged == ChipDragRules.moved(latestKeys, from, to)) move(from, to) else dragged = null
    }

    // A chip left in the air when the row goes away must not leave the pager locked.
    DisposableEffect(drag) {
        onDispose { if (drag.key != null) dragChange(false) }
    }

    // While a chip is in the air: scroll the row when it is pushed past an edge, and trade places as it passes
    // its neighbours. Both are read each frame, so a finger held still at an edge keeps the row moving.
    LaunchedEffect(drag, drag.key != null) {
        if (drag.key == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            last = now
            val speed = ChipDragRules.edgeSpeed(drag.overhang(), edgeZone, edgeSpeed)
            if (speed != 0f) list.scrollBy(speed * seconds)
            val target = drag.target()
            if (target >= 0) trade(target)
        }
    }

    LazyRow(
        modifier = modifier
            .selectableGroup()
            .pointerInput(drag, rtl) {
                val timeout = viewConfiguration.longPressTimeoutMillis
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    // Watched before the chips and the row see the touch, and left alone until the long press: a
                    // tap still selects and a drag still scrolls.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val ended = withTimeoutOrNull(timeout) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val moved = (change.position - down.position).getDistance() > slop
                            if (!change.pressed || change.isConsumed || moved || event.changes.size > 1) break
                        }
                        true
                    }
                    if (ended != null) return@awaitEachGesture
                    // Along the row from the start of its content: the row starts at the right in Arabic.
                    val fromStart = if (rtl) size.width - down.position.x else down.position.x
                    if (!lift(fromStart + list.layoutInfo.viewportStartOffset)) return@awaitEachGesture
                    try {
                        // From here the touch is the drag's alone: no chip is tapped and nothing scrolls under it.
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null && change.pressed) {
                                val x = change.positionChangeIgnoreConsumed().x
                                drag.move(if (rtl) -x else x)
                            }
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        drop()
                    }
                }
            },
        state = list,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        itemsIndexed(order, key = { _, key -> key }) { index, key ->
            val lifted = key == drag.key
            val landing = key == settling
            val rise by animateFloatAsState(
                targetValue = if (lifted) 1f else 0f,
                animationSpec = if (reduced) FrameMotion.effects() else FrameMotion.fastSpatial(),
                label = "chipLift",
            )
            val raised = lifted || landing || rise > 0f
            Box(
                Modifier
                    .zIndex(if (raised) 1f else 0f)
                    // The chip in the air is placed by the finger, and the others slide to make room.
                    .then(
                        if (lifted || landing) {
                            Modifier
                        } else {
                            Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec = if (reduced) null else FrameMotion.defaultSpatial<IntOffset>(),
                                fadeOutSpec = null,
                            )
                        },
                    )
                    .graphicsLayer {
                        val along = if (lifted) drag.shift else if (landing) settle.value else 0f
                        // A layer offset is raw, so the side is flipped by hand for right-to-left.
                        translationX = if (rtl) -along else along
                        val scale = lerp(1f, LiftScale, rise.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        shadowElevation = LiftShadow.toPx() * rise.coerceIn(0f, 1f)
                        shape = FrameShapes.Chip
                        clip = false
                    }
                    // A selected chip is a wash of the accent, so a lifted one gets the canvas under it, or its
                    // own shadow would show through.
                    .background(if (raised) colors.canvas else Color.Transparent, FrameShapes.Chip),
            ) {
                // A drag is no use to a screen reader, so the chip itself offers a move of one place either way.
                val moves = Modifier.semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(earlier) {
                            move(index, index - 1)
                            true
                        }.takeIf { index > 0 },
                        CustomAccessibilityAction(later) {
                            move(index, index + 1)
                            true
                        }.takeIf { index < order.lastIndex },
                    )
                }
                FilterChip(names[key].orEmpty(), key == selectedKey, moves) {
                    if (key != selectedKey) {
                        haptics.performHapticFeedback(SegmentTickHaptic)
                        onSelect(key)
                    }
                }
            }
        }
    }
}
