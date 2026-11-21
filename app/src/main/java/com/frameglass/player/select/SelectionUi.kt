package com.frameglass.player.select

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassKind
import com.frameglass.glass.GlassMenu
import com.frameglass.glass.GlassRow
import com.frameglass.glass.GlassSurface
import com.frameglass.glass.PressableGlass
import com.frameglass.glass.SegmentTickHaptic
import com.frameglass.player.R

// The selected chip of FilterChips: a 12% wash of the accent under a rim of it.
private const val RowWash = 0.12f
private const val RowRim = 0.56f
private val RowRimWidth = 1.5.dp

// Art dims toward the canvas under the mark, so the check reads on a white still and on a black one.
private const val ArtWash = 0.45f
private val ArtRimWidth = 2.5.dp
private val CheckRadius = 14.dp
private val CheckRing = 2.dp
private val CheckIcon = 18.dp

private val ActionIcon = 24.dp
private val ActionMinHeight = 56.dp
private const val DimmedAlpha = 0.4f
private val BarEdge = RoundedCornerShape(0.dp)

/** List bottom padding that keeps the last row clear of [SelectionActionBar]; add the navigation bar inset. */
internal val SelectionBarClearance = 96.dp

/**
 * The selection of the screen a card sits on, or null where cards cannot be picked: Home, and any row that is
 * only a preview. Cards read it through [selectableItem] and [selectionMark], never directly.
 */
internal val LocalSelection = staticCompositionLocalOf<SelectionState<SelectItem>?> { null }

/** Cuts a part of a screen out of its selection, such as a row of related titles under an episode list. */
@Composable
internal fun NoSelection(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSelection provides null, content = content)
}

/**
 * Screens in selection mode say so here, and the tabs then take the tab bar away, because the action bar stands
 * in its place.
 */
@Stable
internal class SelectionChrome {
    private val owners = mutableStateListOf<Any>()

    val active: Boolean get() = owners.isNotEmpty()

    fun add(owner: Any) {
        owners += owner
    }

    fun remove(owner: Any) {
        owners -= owner
    }
}

internal val LocalSelectionChrome = staticCompositionLocalOf<SelectionChrome?> { null }

/** How a picked item is marked. */
internal enum class SelectionLook {
    /** For a picture: it dims, takes a rim in the accent and a check in its middle. */
    Art,

    /** For a whole list row: a light wash of the accent under a rim of it. Its picture takes [Art]. */
    Row,
}

/** What starts selection mode with [item] picked, for the "Select" row of a menu. Null where nothing can be picked. */
@Composable
internal fun selectionStarter(item: SelectItem?): (() -> Unit)? {
    val selection = LocalSelection.current
    if (selection == null || item == null) return null
    return { selection.start(item) }
}

/**
 * Draws the mark of a picked item over this layout and nothing else. Use it on the picture of a card whose title
 * sits below the picture, so the rim hugs the picture and not the text.
 */
@Composable
internal fun Modifier.selectionMark(
    item: SelectItem?,
    look: SelectionLook = SelectionLook.Art,
    shape: Shape = FrameShapes.Poster,
): Modifier {
    val selection = LocalSelection.current
    if (selection == null || item == null) return this
    val picked = selection.active && selection.isPicked(item)
    val shown by animateFloatAsState(if (picked) 1f else 0f, FrameMotion.effects(), label = "selectionMark")
    val colors = FrameTheme.colors
    val check = rememberVectorPainter(Icons.Rounded.Check)
    return drawWithCache {
        val art = look == SelectionLook.Art
        val rimWidth = (if (art) ArtRimWidth else RowRimWidth).toPx()
        // The rim is drawn inside the bounds, so a clipping parent cannot cut half of it away.
        val inner = Size((size.width - rimWidth).coerceAtLeast(0f), (size.height - rimWidth).coerceAtLeast(0f))
        val outline = shape.createOutline(size, layoutDirection, this)
        val rim = shape.createOutline(inner, layoutDirection, this)
        val radius = CheckRadius.toPx()
        val ring = CheckRing.toPx()
        val icon = CheckIcon.toPx()
        onDrawWithContent {
            drawContent()
            val strength = shown
            if (strength <= 0f) return@onDrawWithContent
            if (art) {
                drawOutline(outline, colors.canvas, alpha = ArtWash * strength)
            } else {
                drawOutline(outline, colors.accent, alpha = RowWash * strength)
            }
            translate(rimWidth / 2f, rimWidth / 2f) {
                drawOutline(rim, colors.accent, alpha = (if (art) 1f else RowRim) * strength, style = Stroke(rimWidth))
            }
            if (art && size.minDimension > 2f * (radius + ring)) {
                // A ring in the canvas colour keeps the check apart from whatever the picture holds.
                drawCircle(colors.canvas, radius + ring, center, alpha = strength)
                drawCircle(colors.accentFill, radius, center, alpha = strength)
                translate(center.x - icon / 2f, center.y - icon / 2f) {
                    with(check) { draw(Size(icon, icon), strength, ColorFilter.tint(colors.onAccent)) }
                }
            }
        }
    }
}

/**
 * Watches for a tap from outside the card, the way the long-press menu does. The card under it never sees the
 * press, so it opens nothing, and a drag still scrolls the list. A long press that ends on the card counts as a tap.
 */
private suspend fun PointerInputScope.selectionTaps(onTap: () -> Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        // A touch the list has already taken, such as the one that stops a fling, is not a tap on the card.
        val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
        down.consume()
        var up: PointerInputChange? = null
        while (up == null) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val moved = (change.position - down.position).getDistance() > slop
            if (change.isConsumed || moved || event.changes.size > 1) break
            if (!change.pressed) up = change
        }
        if (up != null) {
            up.consume()
            onTap()
        }
    }
}

/**
 * Makes any card pickable, whatever its shape or the layout it sits in. While its screen is selecting, a tap picks
 * or lets go of [item] instead of opening the card, and the card says so to a screen reader. With a [look] it also
 * draws the mark over its own bounds; pass null when [selectionMark] sits on a part of the card instead.
 * A null [item], and a card outside any [SelectionScaffold], are left exactly as they were.
 */
@Composable
internal fun Modifier.selectableItem(
    item: SelectItem?,
    look: SelectionLook? = SelectionLook.Art,
    shape: Shape = FrameShapes.Poster,
): Modifier {
    val selection = LocalSelection.current
    if (selection == null || item == null) return this
    val marked = if (look != null) selectionMark(item, look, shape) else this
    if (!selection.active) return marked
    val picked = selection.isPicked(item)
    val latest by rememberUpdatedState(item)
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.app_media_select)
    val toggle = {
        haptics.performHapticFeedback(SegmentTickHaptic)
        selection.toggle(latest)
    }
    return marked
        .semantics {
            selected = picked
            onClick(label) {
                toggle()
                true
            }
        }
        .pointerInput(selection) { selectionTaps(toggle) }
}

/** The glass pill at the bottom of a screen in selection mode. Media Manager's bar is the same pill. */
@Composable
internal fun SelectionBarSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    GlassSurface(
        kind = GlassKind.TabBar,
        shape = FrameShapes.Chip,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = FrameSpacing.S16, vertical = FrameSpacing.S8)
            .fillMaxWidth(),
        content = content,
    )
}

private class ActionFace(val icon: ImageVector, val short: Int, val full: Int)

private fun face(action: SelectionAction): ActionFace = when (action) {
    SelectionAction.WATCHLIST_ADD ->
        ActionFace(Icons.Rounded.BookmarkAdd, R.string.app_select_watchlist, R.string.app_menu_watchlist_add)
    SelectionAction.WATCHLIST_REMOVE ->
        ActionFace(Icons.Rounded.BookmarkRemove, R.string.app_select_remove, R.string.app_menu_watchlist_remove)
    SelectionAction.FAVORITE_ADD ->
        ActionFace(Icons.Rounded.FavoriteBorder, R.string.app_select_favorite, R.string.app_menu_favorite_add)
    SelectionAction.FAVORITE_REMOVE ->
        ActionFace(Icons.Rounded.Favorite, R.string.app_select_remove, R.string.app_menu_favorite_remove)
    SelectionAction.MARK_WATCHED ->
        ActionFace(Icons.Rounded.CheckCircle, R.string.app_select_watched, R.string.app_mark_watched)
    SelectionAction.MARK_UNWATCHED ->
        ActionFace(Icons.Rounded.RadioButtonUnchecked, R.string.app_select_unwatched, R.string.app_mark_unwatched)
    SelectionAction.HIDE -> ActionFace(Icons.Rounded.VisibilityOff, R.string.app_menu_hide, R.string.app_menu_hide)
    SelectionAction.MOVE_TO_PRIVATE ->
        ActionFace(Icons.Rounded.Lock, R.string.app_select_private, R.string.app_menu_private)
    SelectionAction.MOVE_OUT ->
        ActionFace(Icons.Rounded.Output, R.string.app_private_move_out, R.string.app_private_move_out)
    SelectionAction.DELETE -> ActionFace(Icons.Rounded.DeleteOutline, R.string.app_menu_delete, R.string.app_menu_delete)
}

/** An icon over a short label. The full wording is what a screen reader says. */
@Composable
private fun ActionCell(
    icon: ImageVector,
    label: String,
    description: String,
    enabled: Boolean,
    danger: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FrameTheme.colors
    val ink = if (danger) colors.error else colors.content
    PressableGlass(
        onClick = onClick,
        modifier = modifier
            .alpha(if (enabled) 1f else DimmedAlpha)
            .semantics { contentDescription = description },
        shape = FrameShapes.Poster,
        enabled = enabled,
        highlight = false,
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = ActionMinHeight)
                .padding(horizontal = FrameSpacing.S4, vertical = FrameSpacing.S8)
                .clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, null, Modifier.size(ActionIcon), tint = ink)
            Text(
                text = label,
                style = FrameType.Caption,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionCell(entry: BarAction, onAction: (SelectionAction) -> Unit, modifier: Modifier = Modifier) {
    val face = face(entry.action)
    ActionCell(
        icon = face.icon,
        label = stringResource(face.short),
        description = stringResource(face.full),
        enabled = entry.enabled,
        danger = entry.action == SelectionAction.DELETE,
        onClick = { onAction(entry.action) },
        modifier = modifier,
    )
}

/** What did not fit in the row, with its full wording. Only what applies to the picks is listed. */
@Composable
private fun MoreCell(overflow: List<BarAction>, onAction: (SelectionAction) -> Unit, modifier: Modifier = Modifier) {
    val usable = overflow.filter { it.enabled }
    Box(modifier) {
        var open by remember { mutableStateOf(false) }
        ActionCell(
            icon = Icons.Rounded.MoreHoriz,
            label = stringResource(R.string.app_select_more),
            description = stringResource(R.string.app_more),
            enabled = usable.isNotEmpty(),
            danger = false,
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
        )
        GlassMenu(expanded = open, onDismiss = { open = false }) {
            usable.forEach { entry ->
                val face = face(entry.action)
                GlassRow(
                    text = stringResource(face.full),
                    icon = face.icon,
                    onClick = {
                        open = false
                        onAction(entry.action)
                    },
                )
            }
        }
    }
}

/**
 * The bottom action bar of selection mode: what [SelectionRules] offers for [place] and [items], icon over label,
 * with Delete at the end. It only reports the tap; [rememberSelectionActions] is what acts.
 */
@Composable
internal fun SelectionActionBar(
    place: SelectionPlace,
    items: List<SelectItem>,
    onAction: (SelectionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = remember(place, items) { SelectionRules.bar(SelectionRules.actions(place, items)) }
    SelectionBarSurface(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FrameSpacing.S16, vertical = FrameSpacing.S4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            layout.shown.forEach { ActionCell(it, onAction, Modifier.weight(1f)) }
            if (layout.overflow.isNotEmpty()) MoreCell(layout.overflow, onAction, Modifier.weight(1f))
            layout.pinned.forEach { ActionCell(it, onAction, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BarChip(text: String, onClick: () -> Unit) {
    val colors = FrameTheme.colors
    PressableGlass(
        onClick = onClick,
        modifier = Modifier.minimumInteractiveComponentSize(),
        shape = FrameShapes.Chip,
        highlight = false,
    ) {
        Text(
            text = text,
            style = FrameType.Label,
            color = colors.content,
            maxLines = 1,
            modifier = Modifier
                .background(colors.elevated, FrameShapes.Chip)
                .padding(horizontal = FrameSpacing.S12, vertical = 6.dp),
        )
    }
}

/**
 * The top bar of selection mode: close, how many are picked, and "Select all", which reads "Clear all" once
 * everything listed is picked. It is whole glass from the start, so the mode shows even on an unscrolled page.
 */
@Composable
internal fun SelectionTopBar(
    selection: SelectionState<SelectItem>,
    listed: List<SelectItem>,
    modifier: Modifier = Modifier,
) {
    val count = selection.count
    val picked = selection.picked
    val allPicked = remember(picked, listed) { SelectionRules.allPicked(picked.keys, listed.map { it.key }) }
    Box(modifier.fillMaxWidth()) {
        GlassSurface(kind = GlassKind.TopBar, shape = BarEdge, modifier = Modifier.matchParentSize()) { }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = FrameSizes.TopBarHeight)
                .padding(horizontal = FrameSpacing.S16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
        ) {
            GlassIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.app_media_select_done),
                onClick = selection::clear,
            )
            Text(
                text = pluralStringResource(R.plurals.app_media_selected, count, count),
                style = FrameType.Title,
                color = FrameTheme.colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (listed.isNotEmpty()) {
                BarChip(
                    text = stringResource(if (allPicked) R.string.app_select_none else R.string.app_select_all),
                    onClick = { if (allPicked) selection.clearPicks() else selection.selectAll(listed) },
                )
            }
        }
    }
}

/** The list bottom padding of a screen: [normal], or enough to clear the action bar while selecting. */
internal fun SelectionState<*>.clearance(normal: Dp): Dp = if (active) maxOf(normal, SelectionBarClearance) else normal

/**
 * Everything a screen needs around its list to select from it, whatever the list looks like: it hands the
 * selection to the cards inside [content], swaps [topBar] for the selection bar, raises the action bar, takes Back
 * first, and drops picks that leave the list. [listed] is what the list holds now, in order, for "Select all";
 * null while it loads. The screen keeps [selection] itself, so it can clear it when its filter changes.
 */
@Composable
internal fun SelectionScaffold(
    selection: SelectionState<SelectItem>,
    place: SelectionPlace,
    listed: List<SelectItem>?,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val act = rememberSelectionActions(selection)
    val chrome = LocalSelectionChrome.current
    LaunchedEffect(selection, listed) {
        if (listed != null) selection.retain(listed)
    }
    if (selection.active) {
        // Composed only while selecting, so it is the newest handler and Back closes the mode before the page.
        BackHandler(onBack = selection::clear)
        if (chrome != null) {
            DisposableEffect(chrome) {
                val owner = Any()
                chrome.add(owner)
                onDispose { chrome.remove(owner) }
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSelection provides selection) { content() }
        // Both bars are swapped in plainly, as Media Manager's are, so their glass is composed like any bar's.
        if (selection.active) {
            SelectionTopBar(selection, listed.orEmpty())
            SelectionActionBar(place, selection.items, act, Modifier.align(Alignment.BottomCenter))
        } else {
            topBar()
        }
    }
}
