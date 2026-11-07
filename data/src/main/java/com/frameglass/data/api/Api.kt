package com.frameglass.data.api

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow

/*
 * The contract between :data and the screens in :app and :player.
 * All image fields are absolute local file paths, or null when there is no image yet.
 */

enum class TitleKind { MOVIE, SERIES, ANIME }
enum class LibraryFilter { ALL, MOVIES, SERIES, ANIME, VIDEOS, FAVORITES, FOLDERS }
enum class LibrarySort { RECENT, NAME, RATING }
enum class RatingSource { TMDB, IMDB }
enum class MatchState { UNMATCHED, QUEUED, AUTO, NEEDS_CHOICE, MANUAL, NONE_FOUND }
enum class SegmentKind { OP, ED, RECAP }
enum class ApiKeyKind { TMDB, OMDB, OPENSUBTITLES, SUBDL, SUBSOURCE }

data class RatingInfo(val source: RatingSource, val value: Double)

/** Identifies something to play. Exactly one field is set. */
data class PlayRef(
    val playableKey: String? = null,
    val fileRowId: Long? = null,
    val uri: String? = null,
)

data class TitleCard(
    val id: Long,
    val kind: TitleKind,
    val name: String,
    val year: Int?,
    val coverPath: String?,
    val rating: RatingInfo?,
    /** 0 to 1 for a started title, null otherwise. */
    val progress: Float?,
    val unavailable: Boolean,
    val watched: Boolean,
)

data class HeroItem(
    val titleId: Long,
    val kind: TitleKind,
    val name: String,
    val logoPath: String?,
    val backdropPath: String?,
    val coverPath: String?,
    val genre: String?,
    val year: Int?,
    val dominantColor: Int?,
    /** True for a started title, so the pill reads Resume, otherwise Play. */
    val resumable: Boolean,
    val play: PlayRef?,
    /** A personal or social video, which must not be labelled a movie. */
    val clip: Boolean = false,
)

data class ContinueItem(
    val titleId: Long,
    val play: PlayRef,
    val name: String,
    /** "S1 E2", or null for a movie. */
    val episodeLabel: String?,
    val episodeTitle: String?,
    val imagePath: String?,
    val progress: Float,
    val timeLeftMs: Long,
    val newEpisode: Boolean,
)

enum class HomeRowKind { TOP_RATED, GENRE, COLLECTION, VIDEOS, RECENT }

data class HomeRow(val kind: HomeRowKind, val label: String?, val titles: List<TitleCard>, val moreCount: Int)

data class HomeFeed(
    val heroes: List<HeroItem>,
    val continueWatching: List<ContinueItem>,
    val rows: List<HomeRow>,
    /** True when nothing on Home can be played: no video was found, or none is available now. */
    val empty: Boolean,
)

data class FolderEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val videoCount: Int,
    val sizeBytes: Long,
    val isNew: Boolean,
    val hasSubtitle: Boolean,
    val coverPath: String?,
    val progress: Float?,
    val unavailable: Boolean,
    val play: PlayRef?,
)

data class FolderView(val path: String?, val name: String?, val entries: List<FolderEntry>)

data class RootFolderInfo(val path: String, val label: String, val videoCount: Int, val available: Boolean)

data class CastMember(val personId: Long, val name: String, val character: String?, val photoPath: String?)

data class TitleDetail(
    val id: Long,
    val kind: TitleKind,
    val name: String,
    val originalName: String?,
    val year: Int?,
    val overview: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val coverPath: String?,
    val backdropPath: String?,
    val logoPath: String?,
    val dominantColor: Int?,
    val rating: RatingInfo?,
    val imdbRating: RatingInfo?,
    val credits: List<Pair<String, String>>,
    val cast: List<CastMember>,
    val collectionName: String?,
    val collection: List<TitleCard>,
    val matchState: MatchState,
    /** Resume when started, otherwise the first thing to play. */
    val play: PlayRef?,
    /** "S1 E2" or null, shown on the primary pill for a series. */
    val playLabel: String?,
    val resumable: Boolean,
    val seasons: List<Int>,
    val unavailable: Boolean,
    val favorite: Boolean = false,
    /** Facts about the files behind this title, shown on Details, most useful for unmatched videos. */
    val files: List<FileFacts> = emptyList(),
)

data class EpisodeRow(
    val season: Int,
    val number: Int,
    val label: String,
    val title: String,
    val imagePath: String?,
    val durationMs: Long,
    val progress: Float?,
    val watched: Boolean,
    val available: Boolean,
    /** Known from TMDB but no file exists. Shown dimmed. */
    val missing: Boolean,
    val play: PlayRef?,
)

data class PersonView(val personId: Long, val name: String, val photoPath: String?, val titles: List<TitleCard>)

data class SearchResults(val titles: List<TitleCard>, val people: List<CastMember>, val genres: List<String>)

data class MatchChoice(
    val providerId: String,
    val kind: TitleKind,
    val name: String,
    val year: Int?,
    val posterUrl: String?,
    val overview: String?,
)

data class ScanState(val running: Boolean, val found: Int, val matching: Int, val currentFolder: String? = null)

sealed interface WatchTarget {
    data class Playable(val playableKey: String) : WatchTarget
    data class Season(val titleId: Long, val season: Int) : WatchTarget
    data class Title(val titleId: Long) : WatchTarget
}

interface LibraryRepository {
    val home: Flow<HomeFeed>
    val scanState: Flow<ScanState>
    val folders: Flow<List<RootFolderInfo>>

    fun titles(filter: LibraryFilter, sort: LibrarySort): Flow<List<TitleCard>>
    fun folder(path: String?): Flow<FolderView>
    fun title(id: Long): Flow<TitleDetail?>
    fun episodes(titleId: Long, season: Int): Flow<List<EpisodeRow>>
    fun search(query: String): Flow<SearchResults>
    fun person(personId: Long): Flow<PersonView?>
    fun genre(name: String): Flow<List<TitleCard>>
    fun rowTitles(row: HomeRowKind, label: String?): Flow<List<TitleCard>>

    /** Called on every app open. Must return quickly and never delay the first screen. */
    fun startCatchUp()
    suspend fun addFolder(path: String)
    suspend fun removeFolder(path: String)
    suspend fun rescan()

    /** A swipe to refresh: scan the files, then fetch art, text and ratings again for what is due. */
    suspend fun refreshAll() = rescan()
    suspend fun setManualCover(titleId: Long, image: Uri)
    suspend fun matchChoices(titleId: Long, query: String? = null): List<MatchChoice>
    suspend fun applyMatch(titleId: Long, choice: MatchChoice)
    suspend fun removeFromLibrary(titleId: Long)
    suspend fun setWatched(target: WatchTarget, watched: Boolean)
    suspend fun setFavorite(titleId: Long, favorite: Boolean)
    /** Folders the owner excluded. Their videos leave the library and scanning skips them. */
    val excludedFolders: Flow<List<String>>
    suspend fun excludeFolder(path: String)
    suspend fun includeFolder(path: String)
    /** Titles hidden with removeFromLibrary, so they can be brought back. */
    val hiddenTitles: Flow<List<TitleCard>>
    suspend fun unhideTitle(titleId: Long)
    suspend fun exportBackup(target: Uri): Result<Unit>
    suspend fun importBackup(source: Uri): Result<Unit>
}

data class SidecarSubtitle(val path: String, val label: String, val language: String?, val mimeType: String)

data class SkipSegment(val kind: SegmentKind, val startMs: Long, val endMs: Long, val source: String)

data class TrackPrefs(
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val subtitlesOff: Boolean = false,
    val speed: Float = 1f,
    val subtitleUri: String? = null,
    val subtitleEncoding: String? = null,
    val subtitleDelayMs: Long = 0,
    /** Fit, Fill, 16:9, 4:3 or Crop as an enum name owned by :player. */
    val aspectMode: String? = null,
)

data class PlaybackItem(
    val ref: PlayRef,
    val playableKey: String,
    val fileRowId: Long?,
    val titleId: Long?,
    /** Absolute path for local files, or the content uri for Open with. */
    val uri: String,
    val title: String,
    /** "S1 E4 • Otchigin", or null for a movie. */
    val subtitle: String?,
    val season: Int?,
    val episode: Int?,
    val posterPath: String?,
    val resumeMs: Long,
    val durationMs: Long,
    val sidecars: List<SidecarSubtitle>,
    val segments: List<SkipSegment>,
    val prefs: TrackPrefs,
    val next: PlayRef?,
    val nextLabel: String?,
    /** In the private folder: block screenshots, keep the name out of notifications and recents. */
    val isPrivate: Boolean = false,
)

interface PlaybackRepository {
    suspend fun open(ref: PlayRef): PlaybackItem?
    suspend fun savePosition(item: PlaybackItem, positionMs: Long, durationMs: Long)
    suspend fun markFinished(item: PlaybackItem)
    suspend fun savePrefs(item: PlaybackItem, prefs: TrackPrefs)
    suspend fun saveManualIntro(item: PlaybackItem, startMs: Long, endMs: Long, wholeSeries: Boolean)
    suspend fun bluetoothDelayMs(deviceKey: String): Long
    suspend fun saveBluetoothDelay(deviceKey: String, ms: Long)
    /** Stored on every position save, for restore after the app is killed. */
    suspend fun lastSession(): PlayRef?
}

data class AppSettings(
    val onlineFeatures: Boolean = true,
    val doubleTapSeconds: Int = 10,
    val backgroundAudio: Boolean = false,
    val autoPlayNext: Boolean = true,
    val nightAudio: Boolean = false,
    val subtitleScale: Float = 1f,
    val subtitleColor: Int = 0xFFFFFFFF.toInt(),
    val subtitleBackground: Boolean = false,
    val subtitleFontPath: String? = null,
    val subtitleBottomOffset: Float = 0f,
    /** Finds every video on the phone with no folder choice. On by default. */
    val scanWholePhone: Boolean = true,
    val scanHiddenFolders: Boolean = false,
    val scanNomediaFolders: Boolean = false,
    val showUnavailable: Boolean = false,
    val tmdbBaseUrl: String = "https://api.themoviedb.org",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: AccentColor = AccentColor.WHITE,
    /** Soft colour from the cover behind Details and the hero. */
    val coverTint: Boolean = false,
    /** Decision D9: extra fields a tapped subtitle search may send. Both are on, and both can be turned off. */
    val sendReleaseName: Boolean = true,
    val sendFileHash: Boolean = true,
    /** Auto-lock the private folder this many seconds after leaving it. 0 locks at once. */
    val vaultLockSeconds: Int = 0,
    val neverSleepingHintDone: Boolean = false,
    val onboardingDone: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(change: (AppSettings) -> AppSettings)
    fun hasKey(kind: ApiKeyKind): Flow<Boolean>
    /** Stores the key encrypted with an AndroidKeyStore AES-GCM key. Null removes it. */
    suspend fun setKey(kind: ApiKeyKind, value: String?)
}

interface FrameServices {
    val library: LibraryRepository
    val playback: PlaybackRepository
    val settings: SettingsRepository
    val media: MediaManager
    val vault: PrivateVault
    val subtitles: OnlineSubtitles
}

/** The Application class implements this. Everything gets its services through it. */
interface FrameServicesOwner {
    val services: FrameServices
}

val Context.frameServices: FrameServices
    get() = (applicationContext as FrameServicesOwner).services
