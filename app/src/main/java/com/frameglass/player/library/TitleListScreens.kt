package com.frameglass.player.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.frameglass.player.ui.TitleGrid
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.statusInset

@Composable
private fun TitleListScreen(title: String, titles: List<TitleCard>, source: String, frames: Boolean = false) {
    val grid = rememberLazyGridState()
    Box(Modifier.fillMaxSize().background(FrameColors.Canvas)) {
        TitleGrid(
            titles = titles,
            source = source,
            state = grid,
            top = statusInset() + FrameSizes.TopBarHeight,
            bottom = navigationInset() + FrameSpacing.S24,
            frames = frames,
        )
        TopGlassBar(
            title = title,
            scrolled = { grid.glassScrolled() },
            navigation = { BackButton() },
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
    TitleListScreen(title, titles, "row:$kind:$label", frames = kind == HomeRowKind.VIDEOS)
}
