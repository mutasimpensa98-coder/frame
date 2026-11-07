package com.frameglass.glass

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private val SheetMargin = 8.dp
private const val SheetMaxHeight = 0.85f
private const val SheetLandscapeWidth = 0.58f
private val SheetLandscapeMaxWidth = 560.dp
private val SheetEnterDistance = 48.dp
private val DragDismissDistance = 96.dp
private val DragDismissSpeed = 1200.dp
private const val BackShrink = 0.06f

private val MenuMinWidth = 180.dp
private val MenuMaxWidth = 300.dp
private val MenuGap = 8.dp
private const val MenuStartScale = 0.85f

private val RowShape = RoundedCornerShape(16.dp)

// Sheets and menus render here, above the tab bar, wherever they were declared. This stays in the
// same window, which keeps them under Compose's control and able to blur what is behind them.

internal class GlassOverlaySlot(val content: @Composable () -> Unit)

@Stable
internal class GlassOverlayHost {
    val slots = mutableStateListOf<GlassOverlaySlot>()
    private val sheets = mutableStateListOf<Animatable<Float, AnimationVector1D>>()

    /** How far the most open sheet has risen, from 0 to 1. Read it while drawing. */
    val sheetProgress: Float
        get() = sheets.maxOfOrNull { it.value.coerceIn(0f, 1f) } ?: 0f

    fun addSheet(progress: Animatable<Float, AnimationVector1D>) {
        sheets += progress
    }

    fun removeSheet(progress: Animatable<Float, AnimationVector1D>) {
        sheets -= progress
    }
}

internal val LocalGlassOverlayHost = staticCompositionLocalOf<GlassOverlayHost?> { null }

@Composable
internal fun BoxScope.GlassOverlayLayer(host: GlassOverlayHost) {
    if (host.slots.isEmpty()) return
    Box(Modifier.matchParentSize()) {
        host.slots.forEach { slot -> key(slot) { slot.content() } }
    }
}

@Composable
private fun GlassOverlay(content: @Composable () -> Unit) {
    val host = LocalGlassOverlayHost.current
    if (host == null) {
        content()
        return
    }
    val latestContent = rememberUpdatedState(content)
    val latestLocals = rememberUpdatedState(currentCompositionLocalContext)
    val slot = remember {
        GlassOverlaySlot {
            CompositionLocalProvider(latestLocals.value) {
                // Declared inside a source or not, it renders here, above every source.
                CompositionLocalProvider(LocalInsideGlassSource provides false) {
                    latestContent.value()
                }
            }
        }
    }
    DisposableEffect(host, slot) {
        host.slots += slot
        onDispose { host.slots -= slot }
    }
}

internal class Anchor {
    var coordinates: LayoutCoordinates? = null

    fun boundsIn(parent: LayoutCoordinates?): Rect? {
        val own = parent?.takeIf { it.isAttached } ?: return null
        val target = coordinates?.takeIf { it.isAttached } ?: return null
        return own.localBoundingBoxOf(target, clipBounds = false)
    }
}

/** Open or closed state of one [GlassSheet]. */
@Stable
class GlassSheetState internal constructor() {
    /** True while the sheet is open. */
    var isOpen by mutableStateOf(false)
        private set

    internal val origin = Anchor()

    /** Opens the sheet. */
    fun open() {
        isOpen = true
    }

    /** Closes the sheet. */
    fun close() {
        isOpen = false
    }
}

/** Creates and remembers a [GlassSheetState]. */
@Composable
fun rememberGlassSheetState(): GlassSheetState = remember { GlassSheetState() }

/** Marks the button a sheet morphs out of. Without one, the sheet rises into place. */
fun Modifier.glassSheetOrigin(state: GlassSheetState): Modifier =
    onPlaced { state.origin.coordinates = it }

private fun lowerBounded() = Animatable(0f).apply { updateBounds(lowerBound = 0f) }

/** An in-window glass sheet over a scrim, from the bottom in portrait and the start side in landscape. */
@Composable
fun GlassSheet(
    state: GlassSheetState,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { lowerBounded() }
    val drag = remember { Animatable(0f) }
    val back = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(state.isOpen) {
        if (state.isOpen) {
            shown = true
            drag.snapTo(0f)
            back.snapTo(0f)
            progress.animateTo(1f, FrameMotion.defaultSpatial())
        } else {
            progress.animateTo(0f, FrameMotion.defaultSpatial())
            shown = false
        }
    }
    // Composed only while open, so it registers last. Back goes to the newest handler, and a screen composed
    // after a sheet that stays in composition, such as the one card menu at the root, would take Back from it.
    if (state.isOpen) {
        PredictiveBackHandler { events ->
            try {
                events.collect { back.snapTo(it.progress) }
                state.close()
            } catch (cancelled: CancellationException) {
                scope.launch { back.animateTo(0f, FrameMotion.effects()) }
                throw cancelled
            }
        }
    }
    if (shown) {
        val host = LocalGlassOverlayHost.current
        DisposableEffect(host, progress) {
            host?.addSheet(progress)
            onDispose { host?.removeSheet(progress) }
        }
        GlassOverlay { SheetLayer(state, progress, drag, back, modifier, title, content) }
    }
}

@Composable
private fun SheetLayer(
    state: GlassSheetState,
    progress: Animatable<Float, AnimationVector1D>,
    drag: Animatable<Float, AnimationVector1D>,
    back: Animatable<Float, AnimationVector1D>,
    modifier: Modifier,
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val reduced = rememberEffectsLevel() == EffectsLevel.Degraded
    val density = LocalDensity.current
    val enterDistance = with(density) { SheetEnterDistance.toPx() }
    val dismissDistance = with(density) { DragDismissDistance.toPx() }
    val dismissSpeed = with(density) { DragDismissSpeed.toPx() }
    val closeLabel = stringResource(R.string.glass_close)
    val panel = remember { Anchor() }
    val colors = FrameTheme.colors

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(colors.scrim)
                .pointerInput(state) { detectTapGestures { state.close() } }
                .semantics {
                    onClick(closeLabel) {
                        state.close()
                        true
                    }
                },
        )
        GlassSurface(
            kind = GlassKind.Sheet,
            shape = FrameShapes.Sheet,
            modifier = modifier
                .align(if (landscape) Alignment.CenterStart else Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        if (landscape) {
                            WindowInsetsSides.Start + WindowInsetsSides.Vertical
                        } else {
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        },
                    ),
                )
                .padding(SheetMargin)
                .then(
                    if (landscape) {
                        Modifier
                            .width(min(maxWidth * SheetLandscapeWidth, SheetLandscapeMaxWidth))
                            .fillMaxHeight()
                    } else {
                        Modifier.fillMaxWidth().heightIn(max = maxHeight * SheetMaxHeight)
                    },
                )
                .onPlaced { panel.coordinates = it }
                .graphicsLayer {
                    val open = progress.value
                    val shrink = 1f - back.value * BackShrink
                    val origin = if (reduced) null else state.origin.boundsIn(panel.coordinates)
                    alpha = (open * 3f).coerceIn(0f, 1f)
                    if (origin != null && size.width > 0f && size.height > 0f) {
                        // Raw offsets between two measured rectangles, so RTL needs no flip.
                        val start = max(origin.width / size.width, origin.height / size.height)
                        val scale = lerp(start.coerceAtMost(1f), 1f, open) * shrink
                        scaleX = scale
                        scaleY = scale
                        translationX = (origin.center.x - size.width / 2f) * (1f - open)
                        translationY = (origin.center.y - size.height / 2f) * (1f - open) + drag.value
                    } else {
                        scaleX = shrink
                        scaleY = shrink
                        translationY = (if (reduced) 0f else (1f - open) * enterDistance) + drag.value
                    }
                }
                .pointerInput(Unit) { }
                .semantics { title?.let { paneTitle = it } },
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Vertical,
                            enabled = !landscape,
                            state = rememberDraggableState { delta ->
                                scope.launch { drag.snapTo(max(0f, drag.value + delta)) }
                            },
                            onDragStopped = { velocity ->
                                if (drag.value > dismissDistance || velocity > dismissSpeed) {
                                    state.close()
                                } else {
                                    drag.animateTo(0f, FrameMotion.defaultSpatial())
                                }
                            },
                        )
                        .padding(
                            start = FrameSpacing.S24,
                            end = FrameSpacing.S24,
                            top = FrameSpacing.S24,
                            bottom = FrameSpacing.S12,
                        ),
                ) {
                    if (title != null) {
                        Text(
                            text = bidiIsolate(title),
                            style = FrameType.TitleLarge,
                            color = colors.content,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = FrameSpacing.S12,
                            end = FrameSpacing.S12,
                            bottom = FrameSpacing.S16,
                        ),
                    verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
                    content = content,
                )
            }
        }
    }
}

/** An in-window glass menu. Put it in a Box beside its button and fill it with [GlassRow]. */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = remember { lowerBounded() }
    val anchor = remember { Anchor() }
    var shown by remember { mutableStateOf(false) }

    // Finds the parent layout, the same way a popup finds its anchor.
    Spacer(Modifier.onPlaced { anchor.coordinates = it.parentLayoutCoordinates })
    LaunchedEffect(expanded) {
        if (expanded) {
            shown = true
            progress.animateTo(1f, FrameMotion.defaultSpatial())
        } else {
            progress.animateTo(0f, FrameMotion.defaultSpatial())
            shown = false
        }
    }
    // Composed only while open, for the reason given in GlassSheet.
    if (expanded) BackHandler(onBack = onDismiss)
    if (shown) {
        GlassOverlay { MenuLayer(anchor, progress, onDismiss, modifier, content) }
    }
}

@Composable
private fun MenuLayer(
    anchor: Anchor,
    progress: Animatable<Float, AnimationVector1D>,
    onDismiss: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val closeLabel = stringResource(R.string.glass_close)
    val latestDismiss by rememberUpdatedState(onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { latestDismiss() } }
            .semantics {
                onClick(closeLabel) {
                    latestDismiss()
                    true
                }
            },
    )
    Layout(
        content = {
            GlassSurface(
                kind = GlassKind.Menu,
                shape = FrameShapes.Sheet,
                modifier = modifier
                    .widthIn(min = MenuMinWidth, max = MenuMaxWidth)
                    .width(IntrinsicSize.Max)
                    .pointerInput(Unit) { },
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(FrameSpacing.S8),
                    verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
                    content = content,
                )
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(FrameSpacing.S12),
    ) { measurables, constraints ->
        val gap = MenuGap.toPx()
        val menu = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val bounds = anchor.boundsIn(coordinates)
                ?: Rect(0f, 0f, width, 0f)

            // Raw coordinates: the menu lines up with the anchor's start edge in either direction.
            val wantedX = if (layoutDirection == LayoutDirection.Rtl) {
                bounds.right - menu.width
            } else {
                bounds.left
            }
            val x = wantedX.coerceIn(0f, max(0f, width - menu.width))
            val below = bounds.bottom + gap + menu.height <= height
            val y = if (below) bounds.bottom + gap else max(0f, bounds.top - gap - menu.height)
            val pivotX = ((bounds.center.x - x) / max(1, menu.width)).coerceIn(0f, 1f)

            menu.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                val open = progress.value
                val scale = lerp(MenuStartScale, 1f, open)
                alpha = open.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(pivotX, if (below) 0f else 1f)
            }
        }
    }
}

/** A rounded row for sheets and menus; the selected row takes the accent with a check, as in image 08. */
@Composable
fun GlassRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    supporting: String? = null,
    icon: ImageVector? = null,
) {
    val colors = FrameTheme.colors
    val contentColor = if (selected) colors.onAccent else colors.content
    PressableGlass(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected },
        shape = RowShape,
        highlight = !selected,
    ) {
        Row(
            modifier = Modifier
                .background(if (selected) colors.accentFill else colors.rowFill, RowShape)
                .heightIn(min = FrameSpacing.S48)
                .padding(horizontal = FrameSpacing.S16, vertical = FrameSpacing.S8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(RowIconSize), tint = contentColor)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = bidiIsolate(text),
                    style = FrameType.Body,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = bidiIsolate(supporting),
                        style = FrameType.Caption,
                        color = if (selected) colors.onAccentSecondary else colors.secondaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(Icons.Rounded.Check, null, Modifier.size(RowIconSize), tint = contentColor)
            }
        }
    }
}

private val RowIconSize = 20.dp
