package com.frameglass.player.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.ViewPlace
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.GlassSource
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.glassScrolled
import com.frameglass.player.R
import com.frameglass.player.folders.AddFolderButton
import com.frameglass.player.select.SelectItem
import com.frameglass.player.select.SelectionPlace
import com.frameglass.player.select.SelectionScaffold
import com.frameglass.player.select.SelectionState
import com.frameglass.player.select.clearance
import com.frameglass.player.select.selectItem
import com.frameglass.player.ui.GridMessage
import com.frameglass.player.ui.RefreshBox
import com.frameglass.player.ui.fullLine
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.rememberUiModel
import com.frameglass.player.ui.statusInset
import com.frameglass.player.views.ViewButton
import com.frameglass.player.views.ViewGrid
import com.frameglass.player.views.folderItems
import com.frameglass.player.views.rememberViewSize

/**
 * The Folders tab (owner request of 2026-11-22).
 *
 * MERGE: replace the body with FoldersScreen() from the `library` branch, and keep BarExtrasSlot() first among
 * its top bar actions. Until then this shows what the Folders chip of Library shows, built from the same parts:
 * the folders that hold videos, the view sizes of ViewPlace.FOLDERS, selection, the long-press menu and pull to
 * refresh.
 */
@Composable
internal fun FoldersTabHost() {
    val model = rememberUiModel()
    val navigator = LocalNavigator.current
    val root by model.rootFolder.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val selection = remember { SelectionState<SelectItem> { it.key } }
    val listed = remember(root) { root?.entries?.mapNotNull { it.selectItem() } }
    val size = rememberViewSize(ViewPlace.FOLDERS)
    val top = statusInset() + FrameSizes.TopBarHeight
    val bottom = navigationInset() + selection.clearance(FrameSizes.TabBarClearance)

    SelectionScaffold(
        selection = selection,
        place = SelectionPlace.FOLDERS,
        listed = listed,
        selectingActions = { ViewButton(ViewPlace.FOLDERS) },
        topBar = {
            TopGlassBar(
                title = stringResource(R.string.app_tab_folders),
                scrolled = { grid.glassScrolled() },
                actions = {
                    BarExtrasSlot()
                    ViewButton(ViewPlace.FOLDERS)
                    AddFolderButton()
                },
            )
        },
    ) {
        RefreshBox(indicatorTop = top, Modifier.fillMaxSize()) {
            GlassSource(Modifier.fillMaxSize()) {
                ViewGrid(size, frames = true, grid, top, bottom) {
                    val entries = root?.entries.orEmpty()
                    if (root != null && entries.isEmpty()) {
                        fullLine("empty") { GridMessage(stringResource(R.string.app_library_no_folders)) }
                    }
                    folderItems(entries, size) { navigator.push(Route.Folder(it.path)) }
                }
            }
        }
    }
}
