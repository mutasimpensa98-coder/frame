package com.frameglass.player.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.FoldersMode
import com.frameglass.data.api.ViewPlace
import com.frameglass.data.api.foldersView
import com.frameglass.data.api.frameServices
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.GlassSource
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.glassScrolled
import com.frameglass.player.R
import com.frameglass.player.search.SearchAction
import com.frameglass.player.search.SearchBack
import com.frameglass.player.search.SearchBarSpace
import com.frameglass.player.search.SearchRules
import com.frameglass.player.search.rememberSearch
import com.frameglass.player.search.rememberSearchMode
import com.frameglass.player.select.SelectItem
import com.frameglass.player.select.SelectionPlace
import com.frameglass.player.select.SelectionScaffold
import com.frameglass.player.select.SelectionState
import com.frameglass.player.select.clearance
import com.frameglass.player.select.selectItem
import com.frameglass.player.ui.RefreshBox
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.rememberUiModel
import com.frameglass.player.ui.statusInset
import com.frameglass.player.views.ViewButton
import com.frameglass.player.views.ViewGrid
import com.frameglass.player.views.rememberViewSize

/** A folder opened inside the Folders screen. [direct] as on Route.Folder. */
private data class OpenFolder(val path: String, val direct: Boolean) {
    /** One line for the saved state, and the name of this page's own saved state. */
    val saved: String get() = (if (direct) DirectMark else TreeMark) + path

    companion object {
        private const val DirectMark = "d:"
        private const val TreeMark = "t:"

        fun of(saved: String) = OpenFolder(saved.drop(DirectMark.length), saved.startsWith(DirectMark))
    }
}

private val OpenFoldersSaver = listSaver<SnapshotStateList<OpenFolder>, String>(
    save = { opened -> opened.map { it.saved } },
    restore = { saved -> mutableStateListOf<OpenFolder>().apply { addAll(saved.map(OpenFolder::of)) } },
)

private const val RootPage = "root"

/**
 * Folders as a screen of its own, for a tab (owner request of 2026-11-22). It lists what the Folders chip of
 * Library lists, under the same setting: every folder that holds videos at once, or the tree. A folder opens
 * inside this screen, with a back arrow, and Back steps out of it before it leaves the tab, so the tab bar stays
 * where it is. It carries the view button, a search of folder and video names, and selection mode.
 */
@Composable
internal fun FoldersScreen() {
    val opened = rememberSaveable(saver = OpenFoldersSaver) { mutableStateListOf<OpenFolder>() }
    val pages = rememberSaveableStateHolder()
    val stepBack: () -> Unit = { if (opened.isNotEmpty()) opened.removeAt(opened.lastIndex) }
    val open: (FolderEntry) -> Unit = { entry ->
        val folder = OpenFolder(entry.path, entry.direct)
        // A second tap while the page fades in must not open the folder twice.
        if (opened.lastOrNull() != folder) opened.add(folder)
    }
    // Declared before the pages, so their own handlers, such as an open search or selection mode, close first.
    BackHandler(enabled = opened.isNotEmpty(), onBack = stepBack)

    AnimatedContent(
        targetState = opened.lastOrNull(),
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { fadeIn(FrameMotion.effects()) togetherWith fadeOut(FrameMotion.effects()) },
        label = "folders",
    ) { folder ->
        // Each page keeps its own scroll place, so stepping back lands where the folder was opened from.
        pages.SaveableStateProvider(folder?.saved ?: RootPage) {
            if (folder == null) {
                FoldersRoot(open)
            } else {
                FolderPage(
                    path = folder.path,
                    direct = folder.direct,
                    onBack = stepBack,
                    open = open,
                    restBottom = FrameSizes.TabBarClearance,
                )
            }
        }
    }
}

/** Where the Folders screen starts. */
@Composable
private fun FoldersRoot(open: (FolderEntry) -> Unit) {
    val model = rememberUiModel()
    val library = LocalContext.current.frameServices.library
    val root by model.foldersRoot.collectAsStateWithLifecycle()
    val sort by model.folderSort.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val search = rememberSearchMode()
    val searched = rememberSearch({ search.asked }) { library.searchFolders(it) }
    val selection = remember { SelectionState<SelectItem> { it.key } }
    val listed = remember(search.open, searched, root) {
        if (search.open) SearchRules.listed(searched.found) else root?.entries?.mapNotNull { it.selectItem() }
    }
    // Picks belong to the list they were made in.
    LaunchedEffect(search.open) { selection.clear() }
    SearchBack(search)
    val size = rememberViewSize(ViewPlace.FOLDERS)
    val sortLabels = folderSortLabels()
    val flat = settings?.foldersView != FoldersMode.TREE
    val top = statusInset() + FrameSizes.TopBarHeight
    val bottom = navigationInset() + selection.clearance(FrameSizes.TabBarClearance)

    SelectionScaffold(
        selection = selection,
        place = SelectionPlace.FOLDERS,
        listed = listed,
        selectingActions = { ViewButton(ViewPlace.FOLDERS) },
        topBar = {
            if (search.open) {
                FolderSearchBar(search)
            } else {
                TopGlassBar(
                    title = stringResource(R.string.app_folders_title),
                    scrolled = { grid.glassScrolled() },
                    actions = {
                        SearchAction(search, stringResource(R.string.app_folders_search))
                        ViewButton(ViewPlace.FOLDERS)
                    },
                )
            }
        },
    ) {
        if (search.open) {
            FolderSearchBody(search.words, searched, statusInset() + SearchBarSpace, bottom, size, open)
        } else {
            RefreshBox(indicatorTop = top, Modifier.fillMaxSize()) {
                GlassSource(Modifier.fillMaxSize()) {
                    ViewGrid(size, frames = true, grid, top, bottom) {
                        foldersRoot(root, flat, sort, sortLabels, model::sortFolders, size, open)
                    }
                }
            }
        }
    }
}
