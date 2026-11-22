package com.frameglass.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Proportions measured on reference images 01 and 02.
private val BarShape = RoundedCornerShape(percent = 50)
private val BarSideMargin = 24.dp
private val BarBottomMargin = 8.dp
private val BarInnerPadding = 10.dp
private val PillInset = 4.dp

// A slot of the short capsule is never wider than this, so two tabs on a wide screen stay a capsule.
private val ShortSlotMax = 96.dp
private val LabelGutter = 3.dp
private val TabIcon = 24.dp
private val IconLabelGap = 2.dp

// The size a tab grows from as its section is switched on.
private const val ArrivingScale = 0.6f

// How long the pill waits on a tab the bar asked for before it gives up and returns to the selected one.
private const val PendingMillis = 500L

private const val CollapsedTitleScale = 20f / 34f

/** One tab of [LiquidTabBar]. [key] tells tabs apart when the list changes, so it outlives a change of language. */
@Immutable
data class GlassTab(val label: String, val icon: ImageVector, val key: Any = label)

/** One tab on screen: at rest, arriving, or on its way out after its section was switched off. */
@Stable
private class TabSlot(tab: GlassTab, shown: Boolean) {
    val key = tab.key
    var tab by mutableStateOf(tab)
    var leaving by mutableStateOf(false)

    /** The share of a whole slot this tab takes: 1 at rest. */
    val presence = Animatable(if (shown) 1f else 0f)

    /** Its centre in units ([TabBarMath]), written while measuring and read while drawing. */
    var center by mutableFloatStateOf(0f)
}

@Stable
private class TabBarState(
    selectedIndex: Int,
    /** The selected tab and the place of the pages now, both read from state. */
    private val selectedNow: () -> Int,
    private val pageNow: () -> Float?,
) {
    /** Every tab on screen in bar order, the leaving ones still in their place. */
    var slots by mutableStateOf<List<TabSlot>>(emptyList())
        private set

    // The pill's centre in units, which stay continuous while a tab arrives or leaves. It is plain numbers, and
    // [frame] is what tells the drawing that they have changed.
    private val motion = PillMotion(selectedIndex + 0.5f)
    private var frame by mutableIntStateOf(0)

    /** Worked out once however many read it: the pill, and every tab for its colour, on every frame of a drag. */
    val hold: State<PillHold> = derivedStateOf { holdNow() }

    /** What the last [step] saw, so that the pill can sleep until it changes. */
    var stepped: PillHold? = null
        private set

    /** Where the finger holds the pill, or NaN while no finger drags the bar. */
    var dragUnit by mutableFloatStateOf(Float.NaN)
        private set

    /** The tab the bar itself asked for, until the caller has made it the selected one. */
    var pendingKey by mutableStateOf<Any?>(null)

    /** The scale all labels share ([TabBarMath.labelScale]), written while measuring and read while drawing. */
    var labelScale by mutableFloatStateOf(1f)

    // The last layout, for the drag.
    var slotPx = 0f
    var paddingPx = 0f

    private val present: List<TabSlot> get() = slots.filter { !it.leaving }

    /** Brings the slots in line with [tabs]. New tabs start at no width and old ones stay until they have shrunk away. */
    fun sync(tabs: List<GlassTab>) {
        val current = slots
        val first = current.isEmpty()
        val wanted = tabs.map { it.key }
        val next = ArrayList<TabSlot>(tabs.size + 1)
        var cursor = 0
        for (slot in current) {
            val index = wanted.indexOf(slot.key)
            if (index < 0) {
                if (!slot.leaving) slot.leaving = true
                next += slot
            } else {
                while (cursor < index) next += TabSlot(tabs[cursor++], shown = first)
                if (slot.leaving) slot.leaving = false
                if (slot.tab != tabs[index]) slot.tab = tabs[index]
                next += slot
                cursor = max(cursor, index + 1)
            }
        }
        while (cursor < tabs.size) next += TabSlot(tabs[cursor++], shown = first)
        // A changed order is not animated: the bar starts again from the new list.
        val ordered = next.filter { !it.leaving }.map { it.key } == wanted
        val result = if (ordered) next else tabs.map { TabSlot(it, shown = true) }
        if (result != current) slots = result
    }

    /** A tab that has shrunk away leaves the bar. */
    fun drop(slot: TabSlot) {
        if (slot.leaving) slots = slots - slot
    }

    /** Centres of the tabs that are staying, in units. The leaving ones still take the room they have left. */
    fun centers(): FloatArray {
        val all = slots
        val centers = TabBarMath.centers(FloatArray(all.size) { all[it].presence.value.coerceIn(0f, 1f) })
        val kept = all.indices.filter { !all[it].leaving }
        return FloatArray(kept.size) { centers[kept[it]] }
    }

    private fun indexOf(key: Any?): Int = if (key == null) -1 else present.indexOfFirst { it.key == key }

    /** The index among the staying tabs the pill rests on when no hand moves it. */
    fun restIndex(selected: Int): Int = indexOf(pendingKey).takeIf { it >= 0 } ?: selected

    /** One source of truth for the pill: the finger on the bar, then the tab just asked for, then the pages, then the selection. */
    private fun holdNow(): PillHold {
        val staying = present
        val pending = indexOf(pendingKey)
        val rest = if (pending >= 0) pending else selectedNow()
        return PillHold.of(
            centers = centers(),
            rest = rest,
            restKey = staying.getOrNull(rest)?.key,
            drag = dragUnit,
            pending = pending >= 0,
            page = pageNow(),
            resizing = slots.any { it.presence.isRunning },
        )
    }

    /**
     * The pill's centre now, for drawing. Held by a finger or by the pages it is exactly where they are at this
     * moment, with nothing in between ([PillMotion]).
     */
    fun pillUnit(): Float {
        watchSteps()
        return motion.at(hold.value)
    }

    /** The pill's speed in units per second, for its stretch. */
    fun pillSpeed(): Float {
        watchSteps()
        return motion.speed
    }

    // Reading the count of steps is what makes a drawing follow them.
    private fun watchSteps(): Int = frame

    /** One frame of the pill. True while the next frame has more to do with nothing else changing. */
    fun step(seconds: Float, instant: Boolean): Boolean {
        val hold = hold.value
        stepped = hold
        val more = motion.step(seconds, hold.tie, hold.unit, hold.goal, instant, hold.anchor)
        frame++
        return more
    }

    /** The pill's unit while a hand moves it, by the bar or by the pages, and NaN otherwise. */
    fun handUnit(page: Float?): Float {
        val drag = dragUnit
        if (!drag.isNaN()) return drag
        if (pendingKey != null || page == null) return Float.NaN
        return TabBarMath.unitAt(page, centers())
    }

    fun dragTo(x: Float, width: Int, rtl: Boolean) {
        val unit = TabBarMath.unit(TabBarMath.fromStart(x, width.toFloat(), rtl), slotPx, paddingPx)
        pendingKey = null
        dragUnit = TabBarMath.clamp(unit, centers())
    }

    /** Ends a drag. With [select] it returns the index of the nearest tab, which the pill then waits on; else -1. */
    fun release(select: Boolean): Int {
        val unit = dragUnit
        dragUnit = Float.NaN
        if (!select || unit.isNaN()) return -1
        val index = TabBarMath.nearest(unit, centers())
        pendingKey = present.getOrNull(index)?.key
        return if (pendingKey == null) -1 else index
    }
}

/**
 * The floating glass tab bar, with a selection pill that slides and stretches between tabs.
 *
 * A finger dragged along the bar carries the pill with it, and the nearest tab is selected when it lets go.
 * [pagePosition] is where the pages behind the bar are while a hand swipes them and while they rest, in tabs from
 * the start, and null while they follow the bar: the pill is drawn where the pages are, frame for frame, and goes
 * to its tab by itself only when neither a finger nor the pages hold it. Four tabs and more span the width; two or
 * three make a shorter centred capsule, and the bar changes between them as [tabs] changes.
 */
@Composable
fun LiquidTabBar(
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pagePosition: (() -> Float?)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val reduced = rememberEffectsLevel() == EffectsLevel.Degraded
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val selected by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)
    val page by rememberUpdatedState(pagePosition)
    val state = remember { TabBarState(selectedIndex, { selected }, { page?.invoke() }) }
    // Written before anything below reads it, as rememberUpdatedState does.
    state.sync(tabs)
    val overlays = LocalGlassOverlayHost.current
    val colors = FrameTheme.colors
    val selectedKey = tabs.getOrNull(selectedIndex)?.key

    // The pill is stepped frame by frame while it has somewhere to go, and sleeps until what holds it or where it
    // rests has changed. Nothing here is started again when a finger or a page moves: the one loop goes on.
    LaunchedEffect(state, reduced) {
        while (true) {
            var last = 0L
            do {
                val more = withFrameNanos { now ->
                    val seconds = if (last == 0L) PillMotion.FirstStep else (now - last) / 1_000_000_000f
                    last = now
                    state.step(seconds, instant = reduced)
                }
            } while (more)
            val seen = state.stepped
            snapshotFlow { state.hold.value }.first { it != seen }
        }
    }
    // A light tick each time the pill, carried by a hand, comes nearer to another tab.
    LaunchedEffect(state) {
        var last = selected
        snapshotFlow { state.handUnit(page?.invoke()) to state.restIndex(selected) }.collect { (unit, rest) ->
            if (unit.isNaN()) {
                last = rest
            } else {
                val next = TabBarMath.tick(last, unit, state.centers())
                if (next != last) {
                    last = next
                    haptics.performHapticFeedback(SegmentTickHaptic)
                }
            }
        }
    }
    val pending = state.pendingKey
    LaunchedEffect(pending, selectedKey) {
        if (pending == null) return@LaunchedEffect
        // A caller that does not take the selection gets the pill back where it was.
        if (pending != selectedKey) delay(PendingMillis)
        state.pendingKey = null
    }
    state.slots.forEach { slot ->
        key(slot.key) {
            LaunchedEffect(slot, slot.leaving, reduced) {
                val target = if (slot.leaving) 0f else 1f
                if (slot.presence.value != target) {
                    slot.presence.animateTo(target, if (reduced) FrameMotion.effects() else FrameMotion.defaultSpatial())
                }
                if (slot.leaving) state.drop(slot)
            }
        }
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = BarSideMargin, end = BarSideMargin, bottom = BarBottomMargin)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            kind = GlassKind.TabBar,
            shape = BarShape,
            modifier = Modifier
                .height(FrameSizes.TabBarHeight)
                // The sheet takes over this edge of the screen, so the bar leaves as it rises.
                .graphicsLayer { alpha = 1f - (overlays?.sheetProgress ?: 0f) },
        ) {
            Layout(
                content = {
                    Box(Modifier.background(colors.tabPill, BarShape).border(0.5.dp, colors.tabPillRim, BarShape))
                    state.slots.forEach { slot ->
                        key(slot.key) {
                            TabItem(slot, state, selected = slot.key == selectedKey) {
                                val index = tabs.indexOfFirst { it.key == slot.key }
                                if (index >= 0 && index != selected) {
                                    haptics.performHapticFeedback(SegmentTickHaptic)
                                    state.pendingKey = slot.key
                                    select(index)
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .selectableGroup()
                    // Starts only after the touch slop, along the bar, so a tap stays a tap. The drag then takes
                    // the pointer, which ends the press of the tab under it.
                    .pointerInput(state, rtl) {
                        detectHorizontalDragGestures(
                            onDragStart = { state.dragTo(it.x, size.width, rtl) },
                            onDragEnd = {
                                val index = state.release(select = true)
                                if (index >= 0 && index != selected) select(index) else state.pendingKey = null
                            },
                            onDragCancel = { state.release(select = false) },
                            onHorizontalDrag = { change, _ -> state.dragTo(change.position.x, size.width, rtl) },
                        )
                    },
            ) { measurables, constraints ->
                val slots = state.slots
                val items = measurables.drop(1).take(slots.size)
                val padding = BarInnerPadding.toPx()
                val inset = PillInset.roundToPx()
                val height = constraints.maxHeight
                val weights = FloatArray(items.size) { slots[it].presence.value.coerceIn(0f, 1f) }
                val count = weights.sum()
                val width = TabBarMath
                    .barWidth(count, constraints.maxWidth.toFloat(), padding, ShortSlotMax.toPx())
                    .roundToInt()
                    .coerceIn(constraints.minWidth, constraints.maxWidth)
                val slot = TabBarMath.slotWidth(count, width.toFloat(), padding)
                val centers = TabBarMath.centers(weights)
                state.slotPx = slot
                state.paddingPx = padding
                var natural = 0
                items.forEachIndexed { index, item ->
                    if (slots[index].center != centers[index]) slots[index].center = centers[index]
                    if (!slots[index].leaving) natural = max(natural, item.maxIntrinsicWidth(height))
                }
                val labelScale = TabBarMath.labelScale(slot, natural.toFloat(), LabelGutter.toPx())
                if (state.labelScale != labelScale) state.labelScale = labelScale

                val itemWidth = slot.roundToInt().coerceAtLeast(0)
                val pillWidth = min((slot * TabBarMath.PillSlots).roundToInt(), width - 2 * inset).coerceAtLeast(0)
                val pill = measurables.first()
                    .measure(Constraints.fixed(pillWidth, (height - 2 * inset).coerceAtLeast(0)))
                val placed = items.map { it.measure(Constraints.fixed(itemWidth, height)) }
                val mirrored = layoutDirection == LayoutDirection.Rtl

                layout(width, height) {
                    // place is raw and ignores the layout direction, so the offset is flipped by hand.
                    pill.placeWithLayer(0, inset) {
                        val fromStart = TabBarMath.pixel(state.pillUnit(), slot, padding)
                        val center = TabBarMath.fromStart(fromStart, width.toFloat(), mirrored)
                        translationX = center - pillWidth / 2f
                        scaleX = 1f + if (reduced) 0f else TabBarMath.stretch(state.pillSpeed())
                    }
                    placed.forEachIndexed { index, item ->
                        val start = TabBarMath.pixel(centers[index], slot, padding) - itemWidth / 2f
                        item.placeRelative(start.roundToInt(), 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(slot: TabSlot, state: TabBarState, selected: Boolean, onClick: () -> Unit) {
    val colors = FrameTheme.colors
    val painter = rememberVectorPainter(slot.tab.icon)
    val labelHeight = with(LocalDensity.current) { FrameType.Caption.lineHeight.toPx() + IconLabelGap.toPx() }
    // Read while drawing: the colour travels with the pill, by how much of the tab the pill covers, and a move
    // recomposes nothing.
    val tint = remember(colors, slot, state) {
        { lerp(colors.content, colors.onTabPill, TabBarMath.coverage(state.pillUnit(), slot.center)) }
    }
    PressableGlass(
        onClick = onClick,
        modifier = Modifier
            .semantics { this.selected = selected }
            .graphicsLayer {
                val shown = slot.presence.value.coerceIn(0f, 1f)
                val scale = lerp(ArrivingScale, 1f, shown)
                alpha = shown
                scaleX = scale
                scaleY = scale
            },
        shape = BarShape,
        enabled = !slot.leaving,
        role = Role.Tab,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(IconLabelGap, Alignment.CenterVertically),
        ) {
            Box(
                Modifier
                    .size(TabIcon)
                    .graphicsLayer {
                        // With the labels gone the icon stands in the middle of the bar.
                        translationY = (1f - TabBarMath.labelAlpha(state.labelScale)) * labelHeight / 2f
                    }
                    .drawBehind { with(painter) { draw(size, colorFilter = ColorFilter.tint(tint())) } },
            )
            // A label is never cut short. It is measured at its own width, and all labels shrink together until
            // the widest fits its slot; below a readable size they fade out and the icons stand alone.
            BasicText(
                text = slot.tab.label,
                style = FrameType.Caption,
                color = ColorProducer(tint),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .wrapContentWidth(unbounded = true)
                    .graphicsLayer {
                        val scale = max(state.labelScale, TabBarMath.MinLabelScale)
                        alpha = TabBarMath.labelAlpha(state.labelScale)
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}

/** Pixels scrolled from the top of this list, for [TopGlassBar]. */
fun LazyListState.glassScrolled(): Float =
    if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat()

/** Pixels scrolled from the top of this grid, for [TopGlassBar]. */
fun LazyGridState.glassScrolled(): Float =
    if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat()

/**
 * A 34 sp title that shrinks to 20 sp as glass fades in behind it, with [scrolled] read outside composition.
 * With [titleOnScroll], for a page that opens on art with its own title, a 20 sp title fades in with the glass.
 */
@Composable
fun TopGlassBar(
    title: String,
    scrolled: () -> Float,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    titleOnScroll: Boolean = false,
) {
    val range = with(LocalDensity.current) { FrameSizes.TopBarCollapse.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestScrolled by rememberUpdatedState(scrolled)
    val collapse = remember(range) {
        derivedStateOf { (latestScrolled() / range).coerceIn(0f, 1f) }
    }
    val glassAlpha = remember(collapse) { { collapse.value } }

    Box(modifier.fillMaxWidth()) {
        GlassSurface(
            kind = GlassKind.TopBar,
            shape = BarEdgeShape,
            modifier = Modifier.matchParentSize(),
            alpha = glassAlpha,
        ) { }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = FrameSizes.TopBarHeight)
                .padding(horizontal = FrameSpacing.S16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
        ) {
            navigation?.invoke()
            Text(
                text = bidiIsolate(title),
                style = if (titleOnScroll) FrameType.Title else FrameType.Display,
                color = FrameTheme.colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        if (titleOnScroll) {
                            alpha = collapse.value
                        } else {
                            val scale = lerp(1f, CollapsedTitleScale, collapse.value)
                            scaleX = scale
                            scaleY = scale
                            // The pivot is a raw fraction too, so the start edge is picked by hand.
                            transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                        }
                    },
            )
            actions?.invoke(this)
        }
    }
}

private val BarEdgeShape = RoundedCornerShape(0.dp)
