package com.frameglass.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// Proportions measured on reference images 01 and 02.
private val BarShape = RoundedCornerShape(percent = 50)
private val BarSideMargin = 24.dp
private val BarBottomMargin = 8.dp
private val BarInnerPadding = 10.dp
private val PillInset = 4.dp
private const val PillSlotRatio = 1.16f

// Tabs per second at which the pill reaches its full stretch. A one-tab move peaks near 10.
private const val FullStretchSpeed = 8f

private const val CollapsedTitleScale = 20f / 34f

/** One tab of [LiquidTabBar]. */
@Immutable
data class GlassTab(val label: String, val icon: ImageVector)

/** The floating glass tab bar, with a selection pill that slides and stretches between tabs. */
@Composable
fun LiquidTabBar(
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val reduced = rememberEffectsLevel() == EffectsLevel.Degraded
    val selected by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)
    val position = remember { Animatable(selectedIndex.toFloat()) }
    val overlays = LocalGlassOverlayHost.current
    val colors = FrameTheme.colors

    LaunchedEffect(selectedIndex, reduced) {
        position.animateTo(
            selectedIndex.toFloat(),
            if (reduced) FrameMotion.effects() else FrameMotion.defaultSpatial(),
        )
    }

    GlassSurface(
        kind = GlassKind.TabBar,
        shape = BarShape,
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = BarSideMargin, end = BarSideMargin, bottom = BarBottomMargin)
            .fillMaxWidth()
            .height(FrameSizes.TabBarHeight)
            // The sheet takes over this edge of the screen, so the bar leaves as it rises.
            .graphicsLayer { alpha = 1f - (overlays?.sheetProgress ?: 0f) },
    ) {
        Layout(
            content = {
                Box(Modifier.background(colors.tabPill, BarShape).border(0.5.dp, colors.tabPillRim, BarShape))
                tabs.forEachIndexed { index, tab ->
                    TabItem(tab, index == selectedIndex, reduced) {
                        if (index != selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            select(index)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize().selectableGroup(),
        ) { measurables, constraints ->
            val padding = BarInnerPadding.roundToPx()
            val inset = PillInset.roundToPx()
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val slot = (width - 2 * padding) / measurables.size.minus(1).coerceAtLeast(1)
            val pillWidth = min((slot * PillSlotRatio).roundToInt(), width - 2 * inset)
            val pill = measurables.first().measure(Constraints.fixed(pillWidth, height - 2 * inset))
            val items = measurables.drop(1).map { it.measure(Constraints.fixed(slot, height)) }
            val rtl = layoutDirection == LayoutDirection.Rtl

            layout(width, height) {
                // place is raw and ignores the layout direction, so the offset is flipped by hand.
                pill.placeWithLayer(0, inset) {
                    val fromStart = padding + slot * (position.value + 0.5f)
                    val center = if (rtl) width - fromStart else fromStart
                    translationX = center - pillWidth / 2f
                    val speed = if (position.isRunning && !reduced) abs(position.velocity) else 0f
                    scaleX = 1f + FrameMotion.PillStretch * min(speed / FullStretchSpeed, 1f)
                }
                items.forEachIndexed { index, item ->
                    item.placeRelative(padding + slot * index, 0)
                }
            }
        }
    }
}

@Composable
private fun TabItem(tab: GlassTab, selected: Boolean, reduced: Boolean, onClick: () -> Unit) {
    val colors = FrameTheme.colors
    // Moves with the pill, so a label never turns before the fill it needs has arrived under it.
    val content by animateColorAsState(
        targetValue = if (selected) colors.onTabPill else colors.content,
        animationSpec = if (reduced) FrameMotion.effects() else FrameMotion.defaultSpatial(),
        label = "tabContent",
    )
    PressableGlass(
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
        shape = BarShape,
        role = Role.Tab,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Icon(tab.icon, null, Modifier.size(24.dp), tint = content)
            Text(
                text = tab.label,
                style = FrameType.Caption,
                color = content,
                maxLines = 1,
                softWrap = false,
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
