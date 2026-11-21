package com.frameglass.player.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.ChipOrder
import com.frameglass.data.api.FoldersMode
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibrarySort
import com.frameglass.data.api.ViewPlace
import com.frameglass.data.api.ViewSize
import com.frameglass.data.api.foldersView
import com.frameglass.data.api.frameServices
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassMenu
import com.frameglass.glass.GlassRow
import com.frameglass.glass.GlassSheetState
import com.frameglass.glass.glassSheetOrigin
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.glass.GlassSource
import com.frameglass.glass.ReorderableFilterChips
import com.frameglass.glass.Skeleton
import com.frameglass.glass.SortDropdown
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.glassScrolled
import com.frameglass.player.R
import com.frameglass.player.folders.AddFolderButton
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.LocalTabSwipeLock
import com.frameglass.player.nav.Route
import com.frameglass.player.quick.TonightSheet
import com.frameglass.player.search.SearchAction
import com.frameglass.player.search.SearchBack
import com.frameglass.player.search.SearchBar
import com.frameglass.player.search.SearchBarSpace
import com.frameglass.player.search.SearchRules
import com.frameglass.player.search.TitleSearchBody
import com.frameglass.player.search.rememberSearch
import com.frameglass.player.search.rememberSearchMode
import com.frameglass.player.select.SelectItem
import com.frameglass.player.select.SelectionPlace
import com.frameglass.player.select.SelectionScaffold
import com.frameglass.player.select.SelectionState
import com.frameglass.player.select.clearance
import com.frameglass.player.select.selectItem
import com.frameglass.player.ui.GridMessage
import com.frameglass.player.ui.RefreshBox
import com.frameglass.player.ui.bleed
import com.frameglass.player.ui.fullLine
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.rememberUiModel
import com.frameglass.player.ui.statusInset
import com.frameglass.player.views.ViewButton
import com.frameglass.player.views.rememberViewSize
import com.frameglass.player.views.ViewGrid
import com.frameglass.player.views.titleItems

private const val SkeletonCount = 9
private val ListSkeletonHeight = 96.dp
private val CompactSkeletonHeight = 42.dp
private val SortOrder = listOf(LibrarySort.RECENT, LibrarySort.NAME, LibrarySort.RATING)

/**
 * F-U2 and F-U3: chips, a sort dropdown and a 3-column grid, or 2 columns of 16:9 frames for Videos. Since
 * 2026-11-22 the top bar can turn into a search of the whole library, the chips are in the owner's order and can
 * be dragged into another, and Folders list every folder at once unless the owner chose the tree.
 */
@Composable
internal fun LibraryScreen() {
    val model = rememberUiModel()
    val navigator = LocalNavigator.current
    val library = LocalContext.current.frameServices.library
    val query by model.libraryQuery.collectAsStateWithLifecycle()
    val titles by model.titles.collectAsStateWithLifecycle()
    val hasAnime by model.hasAnime.collectAsStateWithLifecycle()
    val hasWatchlist by model.hasWatchlist.collectAsStateWithLifecycle()
    val root by model.foldersRoot.collectAsStateWithLifecycle()
    val folderSort by model.folderSort.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val tonight = rememberGlassSheetState()
    val search = rememberSearchMode()
    val searched = rememberSearch({ search.asked }, library::search)
    val chipOrder = settings?.libraryChips

    // Library opens on the first chip of the owner's order. It waits while that chip is Anime or Watchlist and
    // their lists have not loaded, so the screen never opens on one filter and jumps to another.
    val opening = if (settings == null) null else ChipOrder.opening(chipOrder, hasAnime, hasWatchlist)
    LaunchedEffect(opening) { opening?.let(model::openLibraryOn) }

    val filter = query.filter
    val folders = filter == LibraryFilter.FOLDERS
    val selection = remember { SelectionState<SelectItem> { it.key } }
    // What "Select all" picks: the titles found, the rows of Folders, or every title of the filter in view.
    val listed = remember(search.open, searched, folders, titles, root) {
        when {
            search.open -> SearchRules.listed(searched.found)
            folders -> root?.entries?.mapNotNull { it.selectItem() }
            else -> titles?.map { it.selectItem() }
        }
    }
    val place = when {
        search.open -> SelectionPlace.TITLES
        filter == LibraryFilter.FOLDERS -> SelectionPlace.FOLDERS
        filter == LibraryFilter.FAVORITES -> SelectionPlace.FAVORITES
        filter == LibraryFilter.WATCHLIST -> SelectionPlace.WATCHLIST
        else -> SelectionPlace.TITLES
    }
    // Picks belong to the list they were made in: a filter, or the results of a search.
    LaunchedEffect(filter, search.open) { selection.clear() }
    SearchBack(search)
    // Folders keep a size of their own, because a list of places is not read like a wall of posters. What a search
    // finds are titles, shown at the size of Library.
    val viewPlace = if (folders && !search.open) ViewPlace.FOLDERS else ViewPlace.LIBRARY
    val size = rememberViewSize(viewPlace)
    // Videos and folders show 16:9 frames; every other filter shows posters.
    val frames = folders || filter == LibraryFilter.VIDEOS

    // R13: Anime and Watchlist have a chip only while they hold something. Each keeps its place in the order.
    val filters = ChipOrder.shown(chipOrder, hasAnime == true, hasWatchlist == true, current = filter)
    LaunchedEffect(hasAnime) {
        // The chip leaves when the last anime does, so the view cannot stay on it.
        if (hasAnime == false && filter == LibraryFilter.ANIME) {
            val next = ChipOrder.shown(chipOrder, hasAnime = false, hasWatchlist = hasWatchlist == true).first()
            model.changeQuery { it.copy(filter = next) }
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
                LibraryFilter.WATCHLIST -> R.string.app_filter_watchlist
                LibraryFilter.FOLDERS -> R.string.app_filter_folders
            },
        )
    }
    val sortLabels = listOf(
        stringResource(R.string.app_sort_recent),
        stringResource(R.string.app_sort_name),
        stringResource(R.string.app_sort_rating),
    )
    val folderSortLabels = folderSortLabels()
    val flatFolders = settings?.foldersView != FoldersMode.TREE
    val top = statusInset() + FrameSizes.TopBarHeight
    val bottom = navigationInset() + selection.clearance(FrameSizes.TabBarClearance)

    SelectionScaffold(
        selection = selection,
        place = place,
        listed = listed,
        selectingActions = { ViewButton(viewPlace) },
        topBar = {
            if (search.open) {
                // The search field stands where the bar was. The chips and the list under it wait as they are.
                SearchBar(
                    query = search.words,
                    onChange = { search.words = it },
                    onClose = search::close,
                    autoFocus = true,
                ) {
                    if (SearchRules.showsView(search.words, searched.found)) ViewButton(ViewPlace.LIBRARY)
                }
            } else {
                TopGlassBar(
                    title = stringResource(R.string.app_tab_library),
                    scrolled = { grid.glassScrolled() },
                    actions = {
                        SearchAction(search, stringResource(R.string.app_library_search))
                        ViewButton(viewPlace)
                        LibraryMenu(tonight)
                        AddFolderButton()
                    },
                )
            }
        },
    ) {
        if (search.open) {
            // The same search as the Search tab: titles, people and genres. Its list is its own, so the grid of
            // Library keeps its filter and its place, and is back as it was when the search closes.
            TitleSearchBody(
                query = search.words,
                searched = searched,
                source = "librarySearch",
                grid = rememberLazyGridState(),
                top = statusInset() + SearchBarSpace,
                bottom = bottom,
                size = size,
            )
        } else RefreshBox(indicatorTop = top, Modifier.fillMaxSize()) {
            GlassSource(Modifier.fillMaxSize()) {
                ViewGrid(size, frames, grid, top, bottom) {
                    fullLine("chips") {
                        LibraryChips(
                            filters = filters,
                            labels = labels,
                            selected = filter,
                            onSelect = { picked -> model.changeQuery { it.copy(filter = picked) } },
                            onMove = { from, to ->
                                model.updateSettings { it.copy(libraryChips = ChipOrder.move(it.libraryChips, filters, from, to)) }
                            },
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
                        folders -> foldersRoot(
                            view = root,
                            flat = flatFolders,
                            sort = folderSort,
                            sortLabels = folderSortLabels,
                            onSort = model::sortFolders,
                            size = size,
                            open = navigator::openFolder,
                        )
                        titles == null -> repeat(SkeletonCount) { index ->
                            item(key = "skeleton$index", contentType = "skeleton") {
                                val shape = when {
                                    size == ViewSize.COMPACT_LIST -> Modifier.height(CompactSkeletonHeight)
                                    size == ViewSize.LIST -> Modifier.height(ListSkeletonHeight)
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
                                            filter == LibraryFilter.FAVORITES -> R.string.app_favorites_empty
                                            filter == LibraryFilter.WATCHLIST -> R.string.app_watchlist_empty
                                            filter == LibraryFilter.ALL && cinema -> R.string.app_cinema_library_empty
                                            else -> R.string.app_library_empty
                                        },
                                    ),
                                )
                            }
                        }
                        else -> titleItems(titles.orEmpty(), "library", size, frames)
                    }
                }
            }
        }
    }
    TonightSheet(tonight)
}

/**
 * The filter chips of Library, in the owner's order (owner request of 2026-11-22). A tap picks a filter. A long
 * press lifts a chip, and dragging it along the row moves it to a new place, which [onMove] saves. While a chip is
 * in the air the tabs must not swipe, so the lock of the tab pager is held for that long.
 */
@Composable
private fun LibraryChips(
    filters: List<LibraryFilter>,
    labels: List<String>,
    selected: LibraryFilter?,
    onSelect: (LibraryFilter) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val lock = LocalTabSwipeLock.current
    // Whether this row is the one holding the lock, so it never opens a lock something else closed.
    val holding = remember { booleanArrayOf(false) }
    // A chip still in the air when the row leaves, as when another tab is tapped, must not leave the tabs locked.
    DisposableEffect(lock) {
        onDispose { if (holding[0]) lock?.locked = false }
    }
    ReorderableFilterChips(
        keys = filters.map { it.name },
        labels = labels,
        selectedKey = selected?.name,
        onSelect = { name -> filters.firstOrNull { it.name == name }?.let(onSelect) },
        onMove = onMove,
        modifier = Modifier.bleed(FrameSpacing.S16),
        onDragChange = { dragging ->
            holding[0] = dragging
            lock?.locked = dragging
        },
    )
}

/** R13: the random pick lives behind this menu, so the bar keeps its two buttons in view. */
@Composable
private fun LibraryMenu(tonight: GlassSheetState) {
    val navigator = LocalNavigator.current
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
            GlassRow(
                text = stringResource(R.string.app_stats),
                icon = Icons.Rounded.BarChart,
                onClick = {
                    open = false
                    navigator.push(Route.Stats)
                },
            )
        }
    }
}
