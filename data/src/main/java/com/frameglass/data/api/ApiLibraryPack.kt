package com.frameglass.data.api

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/*
 * The owner's library package: watching stats, the cleanup assistant and automatic backup. Each one reaches the
 * screens through an extension, as in ApiExtras.kt, so FrameServices and its fakes stay as they are.
 */

/** Watched time on one local day. [date] is yyyy-MM-dd. */
data class DayTotal(val date: String, val watchedMs: Long)

/** A series or anime and the time watched of it since the log began. Never a private-folder video. */
data class SeriesTotal(val titleId: Long, val name: String, val watchedMs: Long)

/**
 * The stats page. The log starts with the version that added it, so time watched before then is not in it. Films
 * finished counts every film marked watched, from the start. Nothing here ever leaves the phone (R2).
 */
data class WatchStats(
    val weekMs: Long,
    val monthMs: Long,
    val allTimeMs: Long,
    /** Seven days, oldest first, ending today. */
    val lastSevenDays: List<DayTotal>,
    val topSeries: List<SeriesTotal>,
    val filmsFinished: Int,
) {
    val empty: Boolean get() = allTimeMs == 0L && filmsFinished == 0
}

/** Implemented by the object behind [LibraryRepository]. */
interface StatsSource {
    val watchStats: Flow<WatchStats>
}

val LibraryRepository.watchStats: Flow<WatchStats>
    get() = (this as? StatsSource)?.watchStats ?: emptyFlow()

enum class CleanupReason {
    /** Watched to the end, F-S3. */
    WATCHED,

    /** Another file holds the same episode or film in a better version, F-L9. */
    EXTRA_VERSION,
}

/**
 * One suggested file. [entry] is a plain Media Manager file entry, so it goes to MediaManager.planDelete and the
 * confirmation sheet like any other. [keptName] is the version that stays, for an extra version.
 */
data class CleanupItem(val entry: MediaEntry, val reason: CleanupReason, val keptName: String? = null)

/**
 * The Suggestions section of Media Manager. A file is listed once, as an extra version when it is one. Clips and
 * private-folder videos are never listed. [totalBytes] adds up every listed file.
 */
data class CleanupSuggestions(val versions: List<CleanupItem>, val watched: List<CleanupItem>) {
    val all: List<CleanupItem> get() = versions + watched
    val totalBytes: Long get() = all.sumOf { it.entry.sizeBytes }
}

/** Implemented by the object behind [MediaManager]. */
interface CleanupSource {
    val cleanupSuggestions: Flow<CleanupSuggestions>
}

val MediaManager.cleanupSuggestions: Flow<CleanupSuggestions>
    get() = (this as? CleanupSource)?.cleanupSuggestions ?: flowOf(CleanupSuggestions(emptyList(), emptyList()))

enum class AutoBackupMode { OFF, WEEKLY, DAILY }

/** Why the last automatic backup did not save. */
enum class AutoBackupFailure {
    /** No folder was picked. */
    NO_FOLDER,

    /** The folder is gone, or the permission to write there was taken back. */
    FOLDER_LOST,

    /** The folder was there, and writing the file failed. */
    WRITE_FAILED,
}

data class AutoBackupState(
    val mode: AutoBackupMode,
    /** The picked folder's name as the system shows it, or null when none is picked. */
    val folderName: String?,
    val lastBackupAt: Long?,
    /** Set only when the newest attempt failed. A later success clears it. */
    val lastFailureAt: Long?,
    val failure: AutoBackupFailure?,
)

/**
 * Automatic backup to a folder the owner picks once. It writes the same zip as a manual backup, so keys stay out
 * (R4), keeps the newest [KEEP] files of its own name pattern and deletes only older files of that pattern.
 */
interface AutoBackupSource {
    val autoBackup: Flow<AutoBackupState>

    /** A tree from ACTION_OPEN_DOCUMENT_TREE. The persistable permission is taken here. False if it could not be. */
    suspend fun setAutoBackupFolder(tree: Uri): Boolean
    suspend fun setAutoBackupMode(mode: AutoBackupMode)

    /** One backup now into the picked folder, with the same retention. True when it saved. */
    suspend fun backUpNow(): Boolean

    companion object {
        const val KEEP = 5
    }
}

private object NoAutoBackup : AutoBackupSource {
    override val autoBackup: Flow<AutoBackupState> = emptyFlow()
    override suspend fun setAutoBackupFolder(tree: Uri) = false
    override suspend fun setAutoBackupMode(mode: AutoBackupMode) = Unit
    override suspend fun backUpNow() = false
}

val LibraryRepository.autoBackups: AutoBackupSource
    get() = (this as? AutoBackupSource) ?: NoAutoBackup
