package com.frameglass.player.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.ViewPlace
import com.frameglass.data.api.ViewSize
import com.frameglass.data.api.viewSize
import com.frameglass.data.api.withViewSize
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassRow
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.SegmentTickHaptic
import com.frameglass.glass.glassSheetOrigin
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.ui.gridPadding
import com.frameglass.player.ui.rememberUiModel

// The gap the grid has always had between its lines; headers keep it at every size.
private val RoomyLine = 20.dp
private val CompactLine = FrameSpacing.S4
private val SmallPosterGap = FrameSpacing.S8

/**
 * Space a full-width line of a grid adds under itself. Compact rows sit close together, and the chips, the sort
 * button and the section headers above them must not: they keep the gap they have at the roomy sizes.
 */
internal val LocalFullLinePad = compositionLocalOf { 0.dp }

/** The spacing of a grid at one view size. */
internal object ViewLayout {
    fun lineGap(size: ViewSize, frames: Boolean): Dp = when (size) {
        ViewSize.COMPACT_LIST -> CompactLine
        // Posters with nothing written under them sit like tiles. Frames and folders keep room for their names.
        ViewSize.SMALL_GRID -> if (frames) FrameSpacing.S12 else SmallPosterGap
        else -> RoomyLine
    }

    fun columnGap(size: ViewSize): Dp = if (size == ViewSize.SMALL_GRID) SmallPosterGap else FrameSpacing.S12

    fun headerPad(size: ViewSize, frames: Boolean): Dp = (RoomyLine - lineGap(size, frames)).coerceAtLeast(0.dp)
}

internal fun viewIcon(size: ViewSize): ImageVector = when (size) {
    ViewSize.COMPACT_LIST -> Icons.Rounded.ViewAgenda
    ViewSize.LIST -> Icons.AutoMirrored.Rounded.ViewList
    ViewSize.SMALL_GRID -> Icons.Rounded.Apps
    ViewSize.MEDIUM_GRID -> Icons.Rounded.GridView
    ViewSize.LARGE_GRID -> Icons.Rounded.ViewModule
}

internal fun viewLabel(size: ViewSize): Int = when (size) {
    ViewSize.COMPACT_LIST -> R.string.app_view_compact
    ViewSize.LIST -> R.string.app_view_list
    ViewSize.SMALL_GRID -> R.string.app_view_small
    ViewSize.MEDIUM_GRID -> R.string.app_view_medium
    ViewSize.LARGE_GRID -> R.string.app_view_large
}

/** The size [place] shows now: what the owner chose there, or the default of the place. */
@Composable
internal fun rememberViewSize(place: ViewPlace): ViewSize {
    val settings by rememberUiModel().settings.collectAsStateWithLifecycle()
    return settings?.viewSize(place) ?: place.default
}

/**
 * The view button of a top bar. Its icon is the size in use, and it opens the sheet of the five sizes, smallest
 * first, with a check on the one in use. The choice is saved for [place] at once and outlives the app.
 */
@Composable
internal fun ViewButton(place: ViewPlace, modifier: Modifier = Modifier) {
    val model = rememberUiModel()
    val current = rememberViewSize(place)
    val sheet = rememberGlassSheetState()
    val haptics = LocalHapticFeedback.current
    GlassIconButton(
        icon = viewIcon(current),
        contentDescription = stringResource(R.string.app_view_button, stringResource(viewLabel(current))),
        onClick = sheet::open,
        modifier = modifier.glassSheetOrigin(sheet),
    )
    GlassSheet(sheet, title = stringResource(R.string.app_view_title)) {
        ViewSize.entries.forEach { size ->
            GlassRow(
                text = stringResource(viewLabel(size)),
                icon = viewIcon(size),
                selected = size == current,
                onClick = {
                    sheet.close()
                    if (size != current) {
                        haptics.performHapticFeedback(SegmentTickHaptic)
                        model.updateSettings { it.withViewSize(place, size) }
                    }
                },
            )
        }
    }
}

/**
 * The one grid every sized list is drawn in. [frames] says the pictures are 16:9. The state is the caller's, and
 * items keep their keys, so a change of size leaves the list at the item it was showing.
 */
@Composable
internal fun ViewGrid(
    size: ViewSize,
    frames: Boolean,
    state: LazyGridState,
    top: Dp,
    bottom: Dp,
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit,
) {
    CompositionLocalProvider(LocalFullLinePad provides ViewLayout.headerPad(size, frames)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(ViewRules.columns(size, frames)),
            state = state,
            modifier = modifier.fillMaxSize(),
            contentPadding = gridPadding(top, bottom),
            horizontalArrangement = Arrangement.spacedBy(ViewLayout.columnGap(size)),
            verticalArrangement = Arrangement.spacedBy(ViewLayout.lineGap(size, frames)),
            content = content,
        )
    }
}
