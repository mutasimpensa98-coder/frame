package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.RatingInfo
import com.frameglass.data.api.RatingSource
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.EpisodeLite
import com.frameglass.data.db.GenreLink
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.RatingRow
import com.frameglass.data.db.RootFolder
import com.frameglass.data.db.TitleRow
import com.frameglass.data.db.Volume
import com.frameglass.data.db.WatchState

/** One thing to play: one episode or one movie, with every version of it. */
internal class Playable(
    val key: String,
    val titleId: Long,
    /** Display numbers. They follow metadata when an anime is remapped. The key does not. */
    val season: Int,
    val number: Int,
    val numberEnd: Int?,
    val numbered: Boolean,
    val episode: EpisodeLite?,
    val files: List<MediaFile>,
    val watch: WatchState?,
) {
    val available: Boolean get() = files.any { it.available }
    val best: MediaFile get() = Versions.pick(files)
    val watched: Boolean get() = watch?.watched == true
    val lastPlayedAt: Long get() = watch?.lastPlayedAt ?: 0
    val inProgress: Boolean get() = watch != null && !watch.watched && watch.positionMs > 0
    val started: Boolean get() = watched || inProgress
    val firstSeenAt: Long get() = files.minOf { it.firstSeenAt }
    val durationMs: Long
        get() = best.durationMs.takeIf { it > 0 } ?: watch?.durationMs?.takeIf { it > 0 }
            ?: episode?.runtime?.let { it * 60_000L } ?: 0

    /** 0 to 1 while in progress, null otherwise. */
    val partial: Float?
        get() {
            val w = watch ?: return null
            if (!inProgress) return null
            val total = w.durationMs.takeIf { it > 0 } ?: durationMs
            if (total <= 0) return null
            return (w.positionMs.toFloat() / total).coerceIn(0f, 1f)
        }
}

internal object Assembler {
    private val ORDER: Comparator<Playable> =
        compareBy<Playable>({ it.season == 0 }, { it.season }, { !it.numbered }, { it.number })
            .thenComparator { a, b -> NaturalOrder.compare(a.files.first().relPath, b.files.first().relPath) }

    /** Extras such as NCOP1 are files, not episodes, so they are left out. */
    fun playables(
        titleId: Long,
        files: List<MediaFile>,
        episodes: List<EpisodeLite>,
        watch: (String) -> WatchState?,
    ): List<Playable> {
        val byId = episodes.associateBy { it.id }
        return files.filter { it.nameKind != NameKinds.EXTRA }
            .groupBy { it.playableKey }
            .map { (key, group) -> build(key, titleId, group, byId, watch(key)) }
            .sortedWith(ORDER)
    }

    private fun build(
        key: String,
        titleId: Long,
        group: List<MediaFile>,
        episodes: Map<Long, EpisodeLite>,
        watch: WatchState?,
    ): Playable {
        val first = group.first()
        val episode = group.firstNotNullOfOrNull { f -> f.episodeId?.let(episodes::get) }
        val parsedNumber = first.parsedEpisode ?: first.parsedAbsolute
        val number = episode?.number ?: parsedNumber
        val season = episode?.season ?: first.parsedSeason
            ?: if (number == null) 0 else PlayableKeys.seasonOf(first.nameKind, null)
        val span = if (first.parsedEpisodeEnd != null && first.parsedEpisode != null) first.parsedEpisodeEnd - first.parsedEpisode else 0
        return Playable(
            key = key,
            titleId = titleId,
            season = season,
            number = number ?: Int.MAX_VALUE,
            numberEnd = if (number != null && span > 0) number + span else null,
            numbered = number != null,
            episode = episode,
            files = group,
            watch = watch,
        )
    }
}

internal class TitleInfo(
    val title: TitleRow,
    val playables: List<Playable>,
    /** A gone file on a removable drive stays visible, because the drive can return. */
    val onRemovable: Boolean,
    val tmdbRating: Double?,
    val imdbRating: Double?,
    val genres: List<String>,
) {
    val id: Long get() = title.id
    val isMovie: Boolean get() = title.kind == TitleKind.MOVIE
    val available: Boolean = playables.any { it.available }
    val addedAt: Long = playables.maxOfOrNull { p -> p.files.maxOf { it.firstSeenAt } } ?: title.createdAt
    val lastPlayedAt: Long = playables.maxOfOrNull { it.lastPlayedAt } ?: 0
    val started: Boolean = playables.any { it.started }
    val watched: Boolean = playables.isNotEmpty() && playables.all { it.watched }
    val cover: String? get() = title.coverPath ?: title.framePath
    val matched: Boolean = title.matchState == MatchState.AUTO || title.matchState == MatchState.MANUAL
    val durationMs: Long = playables.maxOfOrNull { it.durationMs } ?: 0
    private val clipName: Boolean = playables.any { p -> p.files.any { it.nameKind == NameKinds.CLIP } }

    /** A personal or social video: it has a card, a frame and progress, but no poster to fetch. */
    val isClip: Boolean = ClipRules.isClip(title.kind, matched, clipName, durationMs)
    val isFilm: Boolean = ClipRules.isFilm(title.kind, matched, isClip, durationMs)

    /** The card rating: TMDB by default per F-M10, IMDb only when it is all there is. */
    val rating: RatingInfo?
        get() = when {
            tmdbRating != null -> RatingInfo(RatingSource.TMDB, tmdbRating)
            imdbRating != null -> RatingInfo(RatingSource.IMDB, imdbRating)
            else -> null
        }

    val progress: Float?
        get() {
            if (!started || watched) return null
            if (isMovie) return playables.firstOrNull()?.partial
            val done = playables.sumOf { p -> if (p.watched) 1.0 else (p.partial ?: 0f).toDouble() }
            return (done / playables.size).toFloat().coerceIn(0f, 1f)
        }
}

internal class Snapshot(
    val titleRows: List<TitleRow>,
    val files: List<MediaFile>,
    val episodes: List<EpisodeLite>,
    val watch: Map<String, WatchState>,
    val ratings: List<RatingRow>,
    val genreLinks: List<GenreLink>,
    val collections: Map<Long, String>,
    val volumes: List<Volume>,
    val roots: List<RootFolder>,
    val settings: AppSettings,
    val now: Long,
    /** Volumes mounted right now, by uuid. */
    val mounted: Set<String> = emptySet(),
    /** Titles hidden with removeFromLibrary. */
    val hiddenRows: List<TitleRow> = emptyList(),
) {
    val volumesById: Map<String, Volume> by lazy { volumes.associateBy { it.uuid } }

    val filesByTitle: Map<Long, List<MediaFile>> by lazy { files.filter { it.titleId != null }.groupBy { it.titleId!! } }

    /** What is on storage right now, hidden titles included. */
    val liveFiles: List<MediaFile> by lazy { files.filter { it.available } }

    val liveByRow: Map<Long, MediaFile> by lazy { liveFiles.associateBy { it.rowId } }

    /** Files of titles the owner did not remove. */
    val visibleFiles: List<MediaFile> by lazy {
        val shown = titleRows.mapTo(HashSet()) { it.id }
        files.filter { it.titleId in shown }
    }

    val playablesByKey: Map<String, Playable> by lazy {
        infos.values.flatMap { it.playables }.associateBy { it.key }
    }

    val infos: Map<Long, TitleInfo> by lazy { build(titleRows) }

    /** A hidden title with no file left, such as one moved to the private folder, has nothing to bring back. */
    val hiddenInfos: Map<Long, TitleInfo> by lazy { build(hiddenRows) }

    private val episodesByTitle by lazy { episodes.groupBy { it.titleId } }

    private fun build(rows: List<TitleRow>): Map<Long, TitleInfo> {
        val tmdb = ratings.filter { it.source == RatingSource.TMDB }.associate { it.titleId to it.value }
        val imdb = ratings.filter { it.source == RatingSource.IMDB }.associate { it.titleId to it.value }
        val genres = genreLinks.groupBy({ it.titleId }, { it.name })
        return rows.mapNotNull { row ->
            val titleFiles = filesByTitle[row.id] ?: return@mapNotNull null
            val playables = Assembler.playables(row.id, titleFiles, episodesByTitle[row.id].orEmpty()) { watch[it] }
            if (playables.isEmpty()) return@mapNotNull null
            row.id to TitleInfo(
                title = row,
                playables = playables,
                onRemovable = titleFiles.any { volumesById[it.volumeUuid]?.isRemovable == true },
                tmdbRating = tmdb[row.id],
                imdbRating = imdb[row.id],
                genres = genres[row.id].orEmpty(),
            )
        }.toMap()
    }

    fun absolutePath(file: MediaFile): String? = volumesById[file.volumeUuid]?.lastPath?.let { "$it/${file.relPath}" }

    /** F-L3: titles from a gone drive stay dimmed, and gone internal titles wait behind the toggle. */
    fun inLibrary(info: TitleInfo): Boolean = info.available || info.onRemovable || settings.showUnavailable
}
