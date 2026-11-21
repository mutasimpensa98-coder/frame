package com.frameglass.player.select

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.frameglass.data.api.FileVersion
import com.frameglass.data.api.FrameServices
import com.frameglass.data.api.MediaEntry
import com.frameglass.data.api.MediaGroup
import com.frameglass.data.api.frameServices
import com.frameglass.data.api.versions
import com.frameglass.player.model.UiModel
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.Navigator
import com.frameglass.player.nav.Route
import com.frameglass.player.ui.CardMenuRules
import com.frameglass.player.ui.rememberUiModel
import kotlinx.coroutines.flow.first

/**
 * Carries out an action of the selection bar on everything picked, through the same calls the long-press menu
 * uses for one card. Every action leaves selection mode: at once, except Delete, which only opens the confirmation
 * sheet and leaves when that delete has run, so a cancelled sheet keeps the picks.
 */
@Composable
internal fun rememberSelectionActions(selection: SelectionState<SelectItem>): (SelectionAction) -> Unit {
    val model = rememberUiModel()
    val services = LocalContext.current.frameServices
    val navigator = LocalNavigator.current
    return remember(selection, model, services, navigator) {
        { action -> SelectionRunner(selection, model, services, navigator).run(action) }
    }
}

private class SelectionRunner(
    private val selection: SelectionState<SelectItem>,
    private val model: UiModel,
    private val services: FrameServices,
    private val navigator: Navigator,
) {
    private val library get() = services.library
    private val media get() = services.media

    fun run(action: SelectionAction) {
        val items = selection.items
        if (items.isEmpty()) return
        when (action) {
            SelectionAction.WATCHLIST_ADD, SelectionAction.WATCHLIST_REMOVE -> {
                val wanted = action == SelectionAction.WATCHLIST_ADD
                titles(items) { library.setWatchlist(it, wanted) }
            }
            SelectionAction.FAVORITE_ADD, SelectionAction.FAVORITE_REMOVE -> {
                val wanted = action == SelectionAction.FAVORITE_ADD
                titles(items) { library.setFavorite(it, wanted) }
            }
            SelectionAction.HIDE -> titles(items) { library.removeFromLibrary(it) }
            SelectionAction.MARK_WATCHED, SelectionAction.MARK_UNWATCHED -> {
                val watched = action == SelectionAction.MARK_WATCHED
                val targets = SelectionRules.watchTargets(items)
                selection.clear()
                model.launch { targets.forEach { library.setWatched(it, watched) } }
            }
            SelectionAction.MOVE_TO_PRIVATE -> model.launch {
                val folders = folderEntries(items)
                val inside = folders.mapNotNull { entry ->
                    val path = entry.folderPath ?: return@mapNotNull null
                    path to media.children(entry).first().mapNotNull { it.fileRowId }
                }.toMap()
                val move = SelectionRules.moveIn(items, inside, episodeFiles(items))
                if (move.isEmpty) {
                    // Nothing under the picks can move. The sheet says so, and the picks stay for another try.
                    model.vault.nothingToMove()
                } else {
                    selection.clear()
                    model.vault.moveIn(move.titleIds, move.fileRowIds) { navigator.push(Route.PrivateFolder) }
                }
            }
            SelectionAction.MOVE_OUT -> {
                val ids = SelectionRules.privateIds(items)
                selection.clear()
                model.vault.moveOut(ids)
            }
            // For good, so it only opens the private folder's own confirmation. The picks stay until that delete ran.
            SelectionAction.DELETE_PRIVATE ->
                model.vaultDeletes.ask(SelectionRules.privateIds(items), onDeleted = selection::clear)
            // Never without the sheet: this only asks, and the sheet names the files before anything goes.
            SelectionAction.DELETE -> model.launch {
                val entries = SelectionRules.deleteEntries(items, folderEntries(items), episodeFiles(items))
                // With nothing to delete the sheet says so instead of staying shut, and the picks stay.
                if (entries.isEmpty()) model.deletes.nothing() else model.deletes.ask(entries, onDeleted = selection::clear)
            }
        }
    }

    private fun titles(items: List<SelectItem>, change: suspend (Long) -> Unit) {
        val ids = SelectionRules.titleIds(items)
        selection.clear()
        model.launch { ids.forEach { change(it) } }
    }

    /**
     * Media Manager's entries for every picked folder: the folder itself when it holds videos, and every folder
     * under it that does. Its delete and its file lists are keyed on these.
     */
    private suspend fun folderEntries(items: List<SelectItem>): List<MediaEntry> {
        val paths = SelectionRules.foldersOnStorage(items)
        if (paths.isEmpty()) return emptyList()
        val folders = media.entries(MediaGroup.FOLDERS).first()
        return paths.flatMap { CardMenuRules.foldersUnder(it, folders) }.distinctBy { it.id }
    }

    private suspend fun episodeFiles(items: List<SelectItem>): Map<String, List<FileVersion>> =
        SelectionRules.episodesOnStorage(items).associateWith { library.versions(it) }
}
