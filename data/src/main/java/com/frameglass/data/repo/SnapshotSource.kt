package com.frameglass.data.repo

import android.content.Context
import android.util.Log
import com.frameglass.data.R
import com.frameglass.data.db.FrameDb
import com.frameglass.data.logic.Clock
import com.frameglass.data.logic.Labels
import com.frameglass.data.logic.Snapshot
import com.frameglass.data.scan.Volumes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext

/**
 * One in-memory view of the library, rebuilt when a table changes. Home, Library, Search and Folders all
 * read it, so each screen is plain list work with no per-screen SQL, and a scan cannot flood the UI: the
 * first load is immediate and later reloads wait at least [THROTTLE_MS].
 */
internal class SnapshotSource(
    private val db: FrameDb,
    private val settings: SettingsStore,
    private val volumes: Volumes,
    private val clock: Clock,
    scope: CoroutineScope,
) {
    private val changes: Flow<Unit> = merge(
        db.invalidationTracker.createFlow(*TABLES).map { },
        settings.settings.map { },
    )

    val flow: SharedFlow<Snapshot> = changes
        .conflate()
        .transform {
            // A failed read must not end the shared flow, or every screen would stop updating for good.
            val snapshot = try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "snapshot load failed", e)
                null
            }
            if (snapshot != null) emit(snapshot)
            delay(THROTTLE_MS)
        }
        .flowOn(Dispatchers.IO)
        .shareIn(scope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), replay = 1)

    /** Straight from the database. A plan to delete files must not run on a view that may be seconds old. */
    suspend fun fresh(): Snapshot = withContext(Dispatchers.IO) { load() }

    private suspend fun load(): Snapshot {
        val titles = db.titles()
        val meta = db.meta()
        val scan = db.scan()
        return Snapshot(
            titleRows = titles.titleRows(),
            files = scan.allFiles(),
            episodes = titles.episodeRows(),
            watch = db.watch().all().associateBy { it.playableKey },
            ratings = meta.ratingRows(),
            genreLinks = meta.genreLinks(),
            collections = meta.collections().associate { it.id to it.name },
            volumes = scan.volumes(),
            roots = scan.roots(),
            settings = settings.current(),
            now = clock.now(),
            mounted = volumes.mounted().mapTo(HashSet()) { it.uuid },
            hiddenRows = titles.hiddenRows(),
        )
    }

    private companion object {
        const val TAG = "FrameData"
        val TABLES = arrayOf(
            "Title", "MediaFile", "Episode", "WatchState", "Rating", "TitleGenre", "Genre", "Collection", "Volume", "RootFolder",
        )
        const val THROTTLE_MS = 400L
        const val KEEP_ALIVE_MS = 5_000L
    }
}

internal class ResourceLabels(private val context: Context) : Labels {
    override fun episode(season: Int, number: Int): String =
        if (season == 0) {
            context.getString(R.string.data_special_label, number)
        } else {
            context.getString(R.string.data_episode_label, season, number)
        }

    /** The episode title goes in bidi isolates, so an English title cannot pull Arabic punctuation around. */
    override fun withTitle(label: String, title: String?): String =
        if (title.isNullOrBlank()) label else context.getString(R.string.data_episode_with_title, label, "\u2068$title\u2069")
}
