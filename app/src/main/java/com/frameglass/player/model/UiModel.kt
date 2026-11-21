package com.frameglass.player.model

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.FolderSort
import com.frameglass.data.api.FolderView
import com.frameglass.data.api.FoldersMode
import com.frameglass.data.api.FrameServices
import com.frameglass.data.api.HomeFeed
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibrarySort
import com.frameglass.data.api.RootFolderInfo
import com.frameglass.data.api.ScanState
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.foldersView
import com.frameglass.data.api.refreshWithProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KeepAliveMillis = 5_000L

/**
 * What the Library tab shows, kept for the session. [filter] is null until Library has opened once: it opens on
 * the first chip of the owner's order (owner request of 2026-11-22), which is known only when the settings are.
 */
internal data class LibraryQuery(
    val filter: LibraryFilter? = null,
    val sort: LibrarySort = LibrarySort.RECENT,
)

/**
 * The feeds the main screens read. They outlive the screens, so going back to Home or Library shows the
 * last result at once instead of a skeleton while the snapshot flow starts again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class UiModel(
    private val services: FrameServices,
    private val scope: CoroutineScope,
    private val artDecodes: suspend (String) -> Boolean = { true },
) {
    private val library = services.library
    private val sharing = SharingStarted.WhileSubscribed(KeepAliveMillis)

    val settings: StateFlow<AppSettings?> = services.settings.settings.stateIn(scope, SharingStarted.Eagerly, null)
    val home: StateFlow<HomeFeed?> = library.home
        .mapLatest { feed -> feed.withDrawableHeroes(artDecodes) }
        .stateIn(scope, sharing, null)
    val scan: StateFlow<ScanState> = library.scanState.stateIn(scope, sharing, ScanState(false, 0, 0))
    val folders: StateFlow<List<RootFolderInfo>?> = library.folders.stateIn(scope, sharing, null)

    private val folderOrder = MutableStateFlow(FolderSort.NAME)

    /** The order of the flat list of folders, kept for the session like the sort of Library. */
    val folderSort: StateFlow<FolderSort> = folderOrder.asStateFlow()

    fun sortFolders(sort: FolderSort) {
        folderOrder.value = sort
    }

    /**
     * Where Folders start, for Library's chip and for the Folders screen alike: every folder at once, or the top of
     * the tree, as the owner set it (owner request of 2026-11-22).
     */
    val foldersRoot: StateFlow<FolderView?> = settings
        .filterNotNull()
        .map { it.foldersView }
        .distinctUntilChanged()
        .combine(folderOrder) { mode, sort -> mode to sort }
        .flatMapLatest { (mode, sort) -> if (mode == FoldersMode.FLAT) library.flatFolders(sort) else library.folder(null) }
        .stateIn(scope, sharing, null)

    private val query = MutableStateFlow(LibraryQuery())
    val libraryQuery: StateFlow<LibraryQuery> = query.asStateFlow()

    val titles: StateFlow<List<TitleCard>?> = query
        .map { it.filter to it.sort }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sort) -> if (filter == null) flowOf(null) else library.titles(filter, sort) }
        .stateIn(scope, sharing, null)

    /** Null until the list has loaded, so a chip that depends on it is neither shown nor ruled out too early. */
    val hasAnime: StateFlow<Boolean?> = library.titles(LibraryFilter.ANIME, LibrarySort.RECENT)
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
        .stateIn(scope, sharing, null)

    val hasWatchlist: StateFlow<Boolean?> = library.titles(LibraryFilter.WATCHLIST, LibrarySort.RECENT)
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
        .stateIn(scope, sharing, null)

    fun changeQuery(change: (LibraryQuery) -> LibraryQuery) = query.update(change)

    /** Library opens on [filter]. A filter already in view, such as one Home asked for, stays. */
    fun openLibraryOn(filter: LibraryFilter) = query.update { if (it.filter == null) it.copy(filter = filter) else it }

    /** Starts a scan that belongs to the app, so leaving the screen that asked for it does not stop the walk. */
    fun scanNow() = library.startCatchUp()

    /** Runs in the scope of the app, so a setting saves even if its page closes at once. */
    fun updateSettings(change: (AppSettings) -> AppSettings) {
        scope.launch { services.settings.update(change) }
    }

    /** Work that must finish after the screen that asked for it has gone. */
    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    val deletes = DeleteFlow(services, scope)
    val vault = VaultFlow(services, scope)

    /** The delete inside the private folder, which is for good and has a confirmation of its own. */
    val vaultDeletes = VaultDeleteFlow(services, scope)

    /** The swipe to refresh of Home and Library, with live progress and then its summary. */
    val refreshes = RefreshFlow(scope, start = { library.refreshWithProgress() })
}
