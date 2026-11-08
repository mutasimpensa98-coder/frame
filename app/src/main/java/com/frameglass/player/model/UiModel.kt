package com.frameglass.player.model

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.FolderView
import com.frameglass.data.api.FrameServices
import com.frameglass.data.api.HomeFeed
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibrarySort
import com.frameglass.data.api.RootFolderInfo
import com.frameglass.data.api.ScanState
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.refreshWithProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KeepAliveMillis = 5_000L

/** What the Library tab shows, kept for the session. */
internal data class LibraryQuery(
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENT,
    val asList: Boolean = false,
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
    val rootFolder: StateFlow<FolderView?> = library.folder(null).stateIn(scope, sharing, null)

    private val query = MutableStateFlow(LibraryQuery())
    val libraryQuery: StateFlow<LibraryQuery> = query.asStateFlow()

    val titles: StateFlow<List<TitleCard>?> = query
        .map { it.filter to it.sort }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sort) -> library.titles(filter, sort) }
        .stateIn(scope, sharing, null)

    val hasAnime: StateFlow<Boolean> = library.titles(LibraryFilter.ANIME, LibrarySort.RECENT)
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
        .stateIn(scope, sharing, false)

    fun changeQuery(change: (LibraryQuery) -> LibraryQuery) = query.update(change)

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

    /** The swipe to refresh of Home and Library, with live progress and then its summary. */
    val refreshes = RefreshFlow(scope, start = { library.refreshWithProgress() })
}
