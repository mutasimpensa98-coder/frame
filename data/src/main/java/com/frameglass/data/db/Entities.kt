package com.frameglass.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomWarnings
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.RatingSource
import com.frameglass.data.api.SegmentKind
import com.frameglass.data.api.TitleKind

@Entity(tableName = "Volume")
data class Volume(
    @PrimaryKey val uuid: String,
    val label: String?,
    val isRemovable: Boolean,
    val lastSeenAt: Long,
    /** Mount path when last seen, so an unplugged drive still has a path to show. */
    val lastPath: String?,
)

@Entity(tableName = "RootFolder", indices = [Index(value = ["volumeUuid", "relPath"], unique = true)])
data class RootFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val volumeUuid: String,
    val relPath: String,
    val addedAt: Long,
    val enabled: Boolean = true,
)

object NameKinds {
    const val MOVIE = "MOVIE"
    const val EPISODE = "EPISODE"
    const val SPECIAL = "SPECIAL"
    const val EXTRA = "EXTRA"

    /** ParsedName.clipLike. A value of this column, so the schema stays at version 1. */
    const val CLIP = "CLIP"
}

object EnrichState {
    const val PENDING = 0
    const val CLAIMED = 1
    const val DONE = 2
    const val FAILED = 3
}

@Entity(
    tableName = "MediaFile",
    indices = [
        Index("fileId"),
        Index(value = ["volumeUuid", "relPath"], unique = true),
        Index("titleId"),
        Index("episodeId"),
        Index("playableKey"),
        Index("enrichState"),
    ],
)
data class MediaFile(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val fileId: String,
    val volumeUuid: String,
    val relPath: String,
    val size: Long,
    val mtime: Long,
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val videoCodec: String? = null,
    val hdrType: String? = null,
    val audioSummary: String? = null,
    val osHash: String,
    val available: Boolean = true,
    val firstSeenAt: Long,
    /** Updated when a scan records a change, so an unchanged rescan writes nothing. */
    val lastSeenAt: Long,
    val titleId: Long?,
    val episodeId: Long?,
    val releaseTags: String?,
    val hasSidecarSub: Boolean = false,
    val playableKey: String,
    /** The parser's own numbers. These are never remapped, because playableKey is built from them. */
    val parsedSeason: Int?,
    val parsedEpisode: Int?,
    val parsedEpisodeEnd: Int?,
    val parsedAbsolute: Int?,
    val nameKind: String,
    val hasEmbeddedSub: Boolean = false,
    val framePath: String? = null,
    val enrichState: Int = EnrichState.PENDING,
    val enrichTries: Int = 0,
)

@Entity(
    tableName = "Title",
    indices = [
        Index("groupKey", unique = true),
        Index("groupName"),
        Index("tmdbId"),
        Index("collectionId"),
    ],
)
data class Title(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: TitleKind,
    val groupKey: String,
    /** Normalised title without year and kind, used to find a group across kinds and missing years. */
    val groupName: String,
    /** What the parser read. Metadata never overwrites these, so a purge can fall back to them. */
    val localName: String,
    val localYear: Int?,
    val name: String,
    val originalName: String? = null,
    val year: Int?,
    val overview: String? = null,
    val runtime: Int? = null,
    val tmdbId: Long? = null,
    val tmdbIsMovie: Boolean? = null,
    val anilistId: Long? = null,
    val malId: Long? = null,
    val imdbId: String? = null,
    val matchState: MatchState,
    val matchConfidence: Double? = null,
    val manualMatch: Boolean = false,
    val coverPath: String? = null,
    val manualCover: Boolean = false,
    val backdropPath: String? = null,
    val logoPath: String? = null,
    /** The local frame per F-M7, kept when a cover exists so a purge can fall back to it. */
    val framePath: String? = null,
    val dominantColor: Int? = null,
    val coverLuma: Int? = null,
    val collectionId: Long? = null,
    val fetchedAt: Long? = null,
    val sortKey: String,
    val creditsJson: String? = null,
    val removed: Boolean = false,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** The language of [name], [overview] and the genres. Null means unknown, so the next open fetches them again. */
    val metaLanguage: String? = null,
)

@Entity(tableName = "Episode", indices = [Index(value = ["titleId", "season", "number"], unique = true)])
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleId: Long,
    val season: Int,
    val number: Int,
    val absolute: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    val stillPath: String? = null,
    val runtime: Int? = null,
    val airDate: String? = null,
    val fetchedAt: Long? = null,
)

@Entity(tableName = "WatchState", indices = [Index("titleId")])
data class WatchState(
    @PrimaryKey val playableKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val lastPlayedAt: Long,
    val titleId: Long? = null,
)

@Entity(tableName = "SeriesPrefs")
data class SeriesPrefs(
    @PrimaryKey val titleId: Long,
    val audioLang: String? = null,
    val subLang: String? = null,
    val subsOff: Boolean = false,
    val speed: Float = 1f,
    val aspectMode: String? = null,
)

@Entity(tableName = "FilePrefs")
data class FilePrefs(
    /** The file's fileId, or the playable key of an Open with item that has no file row. */
    @PrimaryKey val fileId: String,
    val audioTrack: String? = null,
    val subtitleTrack: String? = null,
    val subtitleUri: String? = null,
    val subtitleEncoding: String? = null,
    val subtitleDelayMs: Long = 0,
    /** These three are used only for items with no title, which have no SeriesPrefs row. */
    val subsOff: Boolean = false,
    val speed: Float = 1f,
    val aspectMode: String? = null,
)

@Entity(tableName = "Person")
data class Person(
    @PrimaryKey val id: Long,
    val name: String,
    val nameKey: String,
    val photoPath: String? = null,
    val fetchedAt: Long? = null,
)

@Entity(
    tableName = "TitlePerson",
    primaryKeys = ["titleId", "personId"],
    indices = [Index("personId")],
)
data class TitlePerson(
    val titleId: Long,
    val personId: Long,
    val character: String? = null,
    @ColumnInfo(name = "castOrder") val order: Int = 0,
    val fetchedAt: Long? = null,
)

@Entity(tableName = "Genre", indices = [Index("name", unique = true)])
data class Genre(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "TitleGenre", primaryKeys = ["titleId", "genreId"], indices = [Index("genreId")])
data class TitleGenre(val titleId: Long, val genreId: Long)

@Entity(tableName = "Collection")
data class CollectionEntity(
    @PrimaryKey val id: Long,
    val name: String,
)

@Entity(tableName = "Rating", primaryKeys = ["titleId", "source"])
data class Rating(
    val titleId: Long,
    val source: RatingSource,
    val value: Double,
    val votes: Int? = null,
    val fetchedAt: Long,
)

@Entity(tableName = "IntroSegment", indices = [Index("titleId")])
data class IntroSegment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleId: Long,
    /** Null means every season. A row with a season overrides it. */
    val season: Int?,
    val episodeId: Long?,
    val kind: SegmentKind,
    val startMs: Long,
    val endMs: Long,
    val source: String,
    val confidence: Double,
)

@Entity(tableName = "AnimeMap", indices = [Index(value = ["anilistId", "srcRange"], unique = true)])
data class AnimeMap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val anilistId: Long,
    val malId: Long?,
    val tmdbShowId: Long,
    val tmdbSeason: Int,
    val srcRange: String,
    val dstRange: String,
)

enum class LookupState { QUEUED, RETRY }

/** MATCH finds a provider id, EPISODES adds new files of a matched title, REFRESH reloads everything. */
enum class LookupJob { MATCH, EPISODES, REFRESH }

@Entity(tableName = "LookupQueue", indices = [Index("retryAt")])
data class LookupQueue(
    @PrimaryKey val titleId: Long,
    val state: LookupState,
    val attempts: Int,
    val retryAt: Long,
    val job: LookupJob,
)

@Entity(tableName = "ScanLog", indices = [Index("root")])
data class ScanLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val root: String,
    val checkpoint: String? = null,
    val stopReason: String? = null,
    val counts: String? = null,
    val finishedAt: Long? = null,
)

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "SearchIndex")
data class SearchIndex(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val original: String,
    val people: String,
    val genres: String,
)

/** A group key that was merged into another title, so later files of that group find the survivor. */
@Entity(tableName = "GroupAlias", indices = [Index("titleId")])
data class GroupAlias(
    @PrimaryKey val groupKey: String,
    val titleId: Long,
)

@Entity(tableName = "AudioDeviceDelay")
data class AudioDeviceDelay(
    @PrimaryKey val deviceKey: String,
    val delayMs: Long,
)

/** One row, written on every position save. Cleared when the item finishes. */
@Entity(tableName = "PlaySession")
data class PlaySession(
    @PrimaryKey val id: Int = 1,
    val playableKey: String?,
    val fileRowId: Long?,
    val uri: String?,
    val watchKey: String,
    val updatedAt: Long,
)

/** A folder the owner excluded, relative to its volume. An empty path is the whole volume. */
@Entity(tableName = "ExcludedFolder", primaryKeys = ["volumeUuid", "relPath"])
data class ExcludedFolder(
    val volumeUuid: String,
    val relPath: String,
    val addedAt: Long,
)

object VaultRowState {
    const val STORED = 0

    /** Committed, and the original may still be on disk. A restart finishes the removal. */
    const val MOVING_IN = 1

    /** Back in the library, and the stored copy may still exist. A restart finishes the cleanup. */
    const val RESTORED = 2
}

/**
 * One file in the private folder. Its MediaFile row moved here whole, so no normal query can reach it and
 * moving it out restores everything. [file] keeps the original volume, path and name. Nothing reads this
 * table except the vault, and backups never do.
 */
@Suppress(RoomWarnings.PRIMARY_KEY_FROM_EMBEDDED_IS_DROPPED, RoomWarnings.INDEX_FROM_EMBEDDED_ENTITY_IS_DROPPED)
@Entity(tableName = "VaultItem", indices = [Index("f_titleId")])
data class VaultItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Random, inside the vault folder. */
    val storedName: String,
    val sha256: String,
    val state: Int,
    val movedAt: Long,
    val watchPositionMs: Long = 0,
    val watchDurationMs: Long = 0,
    val watched: Boolean = false,
    val lastPlayedAt: Long = 0,
    /** A half-written restore in the owner's folder, so a restart can remove it. */
    val tempPath: String? = null,
    @Embedded(prefix = "f_") val file: MediaFile,
) {
    /** What moveOut and the card go by: the title, or this file alone when it has none. */
    val groupId: Long get() = file.titleId ?: -id
}

/** A subtitle that moved with its video, because Arabic subtitles are the main way the owner watches. */
@Entity(tableName = "VaultSidecar", indices = [Index("itemId")])
data class VaultSidecar(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val storedName: String,
    /** Relative to the video's folder, such as Subs/Name.ar.srt. */
    val originalRel: String,
    val sizeBytes: Long,
    val sha256: String,
    val language: String?,
    val forced: Boolean,
    val signs: Boolean,
    /** Another video reads the same subtitle, so the original stayed where it was. */
    val keptOriginal: Boolean,
)
