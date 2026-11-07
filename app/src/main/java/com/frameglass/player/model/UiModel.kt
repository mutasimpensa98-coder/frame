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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val KeepAliveMillis = 5_000L

// Longer than the 400 ms throttle of SnapshotSource, so the count after a scan reads the new rows.
private const val SnapshotSettleMillis = 900L
private const val NewCountMillis = 3_000L
private const val CountWaitMillis = 2_000L

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
internal class UiModel(private val services: FrameServices, private val scope: CoroutineScope) {
    private val library = services.library
    private val sharing = SharingStarted.WhileSubscribed(KeepAliveMillis)

    val settings: StateFlow<AppSettings?> = services.settings.settings.stateIn(scope, SharingStarted.Eagerly, null)
    val home: StateFlow<HomeFeed?> = library.home.stateIn(scope, sharing, null)
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

    private val refreshingNow = MutableStateFlow(false)
    private val newCount = MutableStateFlow<Int?>(null)

    /** True while a swipe to refresh runs. */
    val refreshing: StateFlow<Boolean> = refreshingNow.asStateFlow()

    /** How many videos the last swipe found, for a few seconds after it ends. */
    val newVideos: StateFlow<Int?> = newCount.asStateFlow()

    // Bounded, so a library view that never loads cannot leave the refresh spinning.
    private suspend fun videoCount(): Int =
        withTimeoutOrNull(CountWaitMillis) { services.media.summary.first().videoCount } ?: 0

    /** rescan() returns nothing, so the count is the library before and after it. */
    fun refresh() {
        if (!refreshingNow.compareAndSet(expect = false, update = true)) return
        scope.launch {
            newCount.value = null
            val before = videoCount()
            runCatching { library.rescan() }
            delay(SnapshotSettleMillis)
            val found = (videoCount() - before).coerceAtLeast(0)
            refreshingNow.value = false
            newCount.value = found
            delay(NewCountMillis)
            newCount.value = null
        }
    }
}
