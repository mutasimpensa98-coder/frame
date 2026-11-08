package com.frameglass.player.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibrarySort
import com.frameglass.glass.FilterChips
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassMenu
import com.frameglass.glass.GlassRow
import com.frameglass.glass.GlassSheetState
import com.frameglass.glass.glassSheetOrigin
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.glass.GlassSource
import com.frameglass.glass.Skeleton
import com.frameglass.glass.SortDropdown
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.glassScrolled
import com.frameglass.player.R
import com.frameglass.player.folders.AddFolderButton
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.Route
import com.frameglass.player.quick.TonightSheet
import com.frameglass.player.ui.GridColumnGap
import com.frameglass.player.ui.GridLineGap
import com.frameglass.player.ui.GridMessage
import com.frameglass.player.ui.RefreshBox
import com.frameglass.player.ui.bleed
import com.frameglass.player.ui.fullLine
import com.frameglass.player.ui.gridPadding
import com.frameglass.player.ui.libraryColumns
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.rememberUiModel
import com.frameglass.player.ui.statusInset
import com.frameglass.player.ui.titleItems

private const val SkeletonCount = 9
private val ListSkeletonHeight = 96.dp
private val SortOrder = listOf(LibrarySort.RECENT, LibrarySort.NAME, LibrarySort.RATING)

/** F-U2 and F-U3: chips, a sort dropdown and a 3-column grid, or 2 columns of 16:9 frames for Videos. */
@Composable
internal fun LibraryScreen() {
    val model = rememberUiModel()
    val navigator = LocalNavigator.current
    val query by model.libraryQuery.collectAsStateWithLifecycle()
    val titles by model.titles.collectAsStateWithLifecycle()
    val hasAnime by model.hasAnime.collectAsStateWithLifecycle()
    val root by model.rootFolder.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val tonight = rememberGlassSheetState()
    val folders = query.filter == LibraryFilter.FOLDERS
    val frames = query.filter == LibraryFilter.VIDEOS && !query.asList

    val filters = buildList {
        add(LibraryFilter.ALL)
        add(LibraryFilter.MOVIES)
        add(LibraryFilter.SERIES)
        if (hasAnime) add(LibraryFilter.ANIME)
        add(LibraryFilter.VIDEOS)
        add(LibraryFilter.FAVORITES)
        add(LibraryFilter.FOLDERS)
    }
    LaunchedEffect(hasAnime) {
        // The chip leaves when the last anime does, so the view cannot stay on it.
        if (!hasAnime && query.filter == LibraryFilter.ANIME) {
            model.changeQuery { it.copy(filter = LibraryFilter.ALL) }
        }
    }
    val labels = filters.map {
        stringResource(
            when (it) {
                LibraryFilter.ALL -> R.string.app_filter_all
                LibraryFilter.MOVIES -> R.string.app_filter_movies
                LibraryFilter.SERIES -> R.string.app_filter_series
                LibraryFilter.ANIME -> R.string.app_filter_anime
                LibraryFilter.VIDEOS -> R.string.app_section_videos
                LibraryFilter.FAVORITES -> R.string.app_filter_favorites
                LibraryFilter.FOLDERS -> R.string.app_filter_folders
            },
        )
    }
    val sortLabels = listOf(
        stringResource(R.string.app_sort_recent),
        stringResource(R.string.app_sort_name),
        stringResource(R.string.app_sort_rating),
    )
    val asList = query.asList || folders
    val top = statusInset() + FrameSizes.TopBarHeight
    val bottom = navigationInset() + FrameSizes.TabBarClearance

    Box(Modifier.fillMaxSize()) {
        RefreshBox(indicatorTop = top, Modifier.fillMaxSize()) {
            GlassSource(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = libraryColumns(asList, frames),
                    state = grid,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = gridPadding(top, bottom),
                    horizontalArrangement = Arrangement.spacedBy(GridColumnGap),
                    verticalArrangement = Arrangement.spacedBy(GridLineGap),
                ) {
                    fullLine("chips") {
                        FilterChips(
                            labels = labels,
                            selectedIndex = filters.indexOf(query.filter).coerceAtLeast(0),
                            onSelect = { index -> model.changeQuery { it.copy(filter = filters[index]) } },
                            modifier = Modifier.bleed(FrameSpacing.S16),
                        )
                    }
                    if (!folders) {
                        fullLine("sort") {
                            SortDropdown(
                                options = sortLabels,
                                selectedIndex = SortOrder.indexOf(query.sort),
                                onSelect = { index -> model.changeQuery { it.copy(sort = SortOrder[index]) } },
                            )
                        }
                    }
                    when {
                        folders -> {
                            val entries = root?.entries.orEmpty()
                            if (root != null && entries.isEmpty()) {
                                fullLine("empty") { GridMessage(stringResource(R.string.app_library_no_folders)) }
                            }
                            folderLines(entries) { navigator.push(Route.Folder(it.path)) }
                        }
                        titles == null -> repeat(SkeletonCount) { index ->
                            item(key = "skeleton$index") {
                                val shape = when {
                                    query.asList -> Modifier.height(ListSkeletonHeight)
                                    frames -> Modifier.aspectRatio(FrameSizes.LandscapeRatio)
                                    else -> Modifier.aspectRatio(FrameSizes.PosterRatio)
                                }
                                Skeleton(Modifier.fillMaxWidth().then(shape))
                            }
                        }
                        titles.orEmpty().isEmpty() -> {
                            fullLine("empty") {
                                val cinema = settings?.cinemaMode == true
                                GridMessage(
                                    stringResource(
                                        when {
                                            query.filter == LibraryFilter.FAVORITES -> R.string.app_favorites_empty
                                            query.filter == LibraryFilter.ALL && cinema -> R.string.app_cinema_library_empty
                                            else -> R.string.app_library_empty
                                        },
                                    ),
                                )
                            }
                        }
                        else -> titleItems(titles.orEmpty(), "library", query.asList, frames)
                    }
                }
            }
        }
        TopGlassBar(
            title = stringResource(R.string.app_tab_library),
            scrolled = { grid.glassScrolled() },
            actions = {
                if (!folders) {
                    GlassIconButton(
                        icon = if (query.asList) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                        contentDescription = stringResource(
                            if (query.asList) R.string.app_view_grid else R.string.app_view_list,
                        ),
                        onClick = { model.changeQuery { it.copy(asList = !it.asList) } },
                    )
                }
                LibraryMenu(tonight)
                AddFolderButton()
            },
        )
    }
    TonightSheet(tonight)
}

/** R13: the random pick lives behind this menu, so the bar keeps its two buttons in view. */
@Composable
private fun LibraryMenu(tonight: GlassSheetState) {
    Box {
        var open by remember { mutableStateOf(false) }
        GlassIconButton(
            icon = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.app_more),
            onClick = { open = true },
            modifier = Modifier.glassSheetOrigin(tonight),
        )
        GlassMenu(expanded = open, onDismiss = { open = false }) {
            GlassRow(
                text = stringResource(R.string.app_tonight),
                icon = Icons.Rounded.Shuffle,
                onClick = {
                    open = false
                    tonight.open()
                },
            )
        }
    }
}

/** Lines of a folder listing, shared by the Folders chip and the folder screen. */
internal fun LazyGridScope.folderLines(
    entries: List<FolderEntry>,
    openFolder: (FolderEntry) -> Unit,
) {
    entries.forEach { entry ->
        fullLine(entry.path) {
            val navigator = LocalNavigator.current
            FolderRow(
                entry = entry,
                onClick = {
                    if (entry.isDirectory) openFolder(entry) else entry.play?.let(navigator::play)
                },
            )
        }
    }
}
