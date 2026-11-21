package com.frameglass.player.library

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.HomeRowKind
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.frameServices
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.glassScrolled
import com.frameglass.player.R
import com.frameglass.player.select.SelectItem
import com.frameglass.player.select.SelectionPlace
import com.frameglass.player.select.SelectionScaffold
import com.frameglass.player.select.SelectionState
import com.frameglass.player.select.clearance
import com.frameglass.player.select.selectItem
import com.frameglass.player.ui.TitleGrid
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.statusInset

@Composable
private fun TitleListScreen(
    title: String,
    titles: List<TitleCard>,
    source: String,
    frames: Boolean = false,
    place: SelectionPlace = SelectionPlace.TITLES,
) {
    val grid = rememberLazyGridState()
    val selection = remember { SelectionState<SelectItem> { it.key } }
    val listed = remember(titles) { titles.map { it.selectItem() } }
    SelectionScaffold(
        selection = selection,
        place = place,
        listed = listed,
        modifier = Modifier.background(FrameColors.Canvas),
        topBar = {
            TopGlassBar(
                title = title,
                scrolled = { grid.glassScrolled() },
                navigation = { BackButton() },
            )
        },
    ) {
        TitleGrid(
            titles = titles,
            source = source,
            state = grid,
            top = statusInset() + FrameSizes.TopBarHeight,
            bottom = navigationInset() + selection.clearance(FrameSpacing.S24),
            frames = frames,
        )
    }
}

/** Every title of a genre, reached from Search. */
@Composable
internal fun GenreScreen(name: String) {
    val library = LocalContext.current.frameServices.library
    val titles by remember(name) { library.genre(name) }.collectAsStateWithLifecycle(emptyList())
    TitleListScreen(name, titles, "genre:$name")
}

/** The whole of a Home row, reached from View all. */
@Composable
internal fun RowScreen(kind: HomeRowKind, label: String?) {
    val library = LocalContext.current.frameServices.library
    val titles by remember(kind, label) { library.rowTitles(kind, label) }.collectAsStateWithLifecycle(emptyList())
    val title = when (kind) {
        HomeRowKind.TOP_RATED -> stringResource(R.string.app_top_rated)
        HomeRowKind.GENRE -> label.orEmpty()
        HomeRowKind.COLLECTION -> label ?: stringResource(R.string.app_collection)
        HomeRowKind.VIDEOS -> stringResource(R.string.app_section_videos)
        HomeRowKind.RECENT -> stringResource(R.string.app_sort_recent)
        HomeRowKind.WATCHLIST -> stringResource(R.string.app_filter_watchlist)
    }
    TitleListScreen(
        title = title,
        titles = titles,
        source = "row:$kind:$label",
        frames = kind == HomeRowKind.VIDEOS,
        // The whole of the watchlist row is the watchlist, so its bar takes titles off it.
        place = if (kind == HomeRowKind.WATCHLIST) SelectionPlace.WATCHLIST else SelectionPlace.TITLES,
    )
}
