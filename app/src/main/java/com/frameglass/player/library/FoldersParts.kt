package com.frameglass.player.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.FolderSort
import com.frameglass.data.api.FolderView
import com.frameglass.data.api.ViewPlace
import com.frameglass.data.api.ViewSize
import com.frameglass.glass.GlassSource
import com.frameglass.glass.SortDropdown
import com.frameglass.player.R
import com.frameglass.player.nav.Navigator
import com.frameglass.player.nav.Route
import com.frameglass.player.search.SearchBar
import com.frameglass.player.search.SearchLook
import com.frameglass.player.search.SearchMode
import com.frameglass.player.search.SearchRules
import com.frameglass.player.search.Searched
import com.frameglass.player.ui.CenteredMessage
import com.frameglass.player.ui.GridMessage
import com.frameglass.player.ui.fullLine
import com.frameglass.player.views.ViewButton
import com.frameglass.player.views.ViewGrid
import com.frameglass.player.views.folderItems

/*
 * What Library's Folders chip, the Folders screen and a folder page share (owner requests of 2026-11-22), so the
 * three list folders the same way.
 */

private val FolderSorts = listOf(FolderSort.NAME, FolderSort.SIZE, FolderSort.COUNT, FolderSort.NEWEST)

/** The names of the orders of the flat list, in the order the dropdown lists them. */
@Composable
internal fun folderSortLabels(): List<String> = listOf(
    stringResource(R.string.app_sort_name),
    stringResource(R.string.app_folder_sort_size),
    stringResource(R.string.app_folder_sort_count),
    stringResource(R.string.app_folder_sort_newest),
)

/** Opens a folder of a list as a page over the tabs. A row of the flat list opens on its own videos. */
internal fun Navigator.openFolder(entry: FolderEntry) = push(Route.Folder(entry.path, entry.direct))

/**
 * Where Folders start: every folder at once with a sort over them when [flat], or the top of the tree. [view] is
 * null while it loads. One folder needs no sort, and neither does the tree, which is read like a file manager.
 */
internal fun LazyGridScope.foldersRoot(
    view: FolderView?,
    flat: Boolean,
    sort: FolderSort,
    sortLabels: List<String>,
    onSort: (FolderSort) -> Unit,
    size: ViewSize,
    open: (FolderEntry) -> Unit,
) {
    val entries = view?.entries.orEmpty()
    if (flat && entries.size > 1) {
        fullLine("folderSort") {
            SortDropdown(
                options = sortLabels,
                selectedIndex = FolderSorts.indexOf(sort),
                onSelect = { onSort(FolderSorts[it]) },
            )
        }
    }
    if (view != null && entries.isEmpty()) {
        fullLine("empty") { GridMessage(stringResource(R.string.app_library_no_folders)) }
    }
    folderItems(entries, size, open)
}

/** The top bar of a folder list while it is searched. The view button stays, because the results are the same cells. */
@Composable
internal fun FolderSearchBar(search: SearchMode) {
    SearchBar(
        query = search.words,
        onChange = { search.words = it },
        placeholder = stringResource(R.string.app_folders_search),
        onClose = search::close,
        autoFocus = true,
    ) {
        ViewButton(ViewPlace.FOLDERS)
    }
}

/**
 * What a folder search shows under its field: the folders, then the videos, whose names hold the words typed, as
 * the same cells as the list, so the view size, the long-press menu and selection all work on them.
 */
@Composable
internal fun FolderSearchBody(
    query: String,
    searched: Searched<FolderView>,
    top: Dp,
    bottom: Dp,
    size: ViewSize,
    open: (FolderEntry) -> Unit,
) {
    // A list of its own, so the folders under the search keep their place for when it closes.
    val grid = rememberLazyGridState()
    val found = searched.found
    when (SearchRules.folders(query, searched)) {
        SearchLook.HINT -> CenteredMessage(
            title = stringResource(R.string.app_folders_search_title),
            body = stringResource(R.string.app_folders_search_hint),
        )
        SearchLook.NOTHING -> CenteredMessage(stringResource(R.string.app_search_nothing))
        SearchLook.WAITING -> Unit
        SearchLook.RESULTS -> if (found != null) {
            GlassSource(Modifier.fillMaxSize()) {
                ViewGrid(size, frames = true, grid, top, bottom) {
                    folderItems(found.entries, size, open)
                }
            }
        }
    }
}
