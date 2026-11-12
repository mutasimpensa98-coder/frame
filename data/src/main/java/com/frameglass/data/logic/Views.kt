package com.frameglass.data.logic

import com.frameglass.data.api.ContinueItem
import com.frameglass.data.api.EpisodeRow
import com.frameglass.data.api.FileFacts
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.FolderView
import com.frameglass.data.api.HeroItem
import com.frameglass.data.api.HomeFeed
import com.frameglass.data.api.HomeRow
import com.frameglass.data.api.HomeRowKind
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibrarySort
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.RatingInfo
import com.frameglass.data.api.RatingSource
import com.frameglass.data.api.RootFolderInfo
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.EpisodeLite
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.RootFolder
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Text the pure builders need. The implementation reads string resources. */
internal interface Labels {
    /** "S1 E2" in the device language. */
    fun episode(season: Int, number: Int): String
    /** "S1 E4 • Otchigin". */
    fun withTitle(label: String, title: String?): String
}

internal fun TitleInfo.card(): TitleCard = TitleCard(
    id = title.id,
    kind = title.kind,
    name = title.name,
    year = title.year,
    coverPath = cover,
    rating = rating,
    progress = progress,
    unavailable = !available,
    watched = watched,
)

internal fun Playable.ref(): PlayRef = PlayRef(playableKey = key)

internal object TitleLists {
    private val BY_NAME = Comparator<TitleInfo> { a, b -> NaturalOrder.compare(a.title.sortKey, b.title.sortKey) }
    private val BY_RATING = compareByDescending<TitleInfo> { it.rating?.value ?: -1.0 }.then(BY_NAME)
    private val BY_RECENT = compareByDescending<TitleInfo> { it.addedAt }.then(BY_NAME)

    // Real titles first, so Library opens on posters and not on the camera roll.
    private val BY_RECENT_TITLES_FIRST = compareBy<TitleInfo> { it.isClip }.then(BY_RECENT)

    fun library(s: Snapshot, filter: LibraryFilter, sort: LibrarySort): List<TitleCard> {
        val cinema = s.settings.cinemaMode
        val pool = s.infos.values.filter(s::inLibrary).filter { info ->
            when (filter) {
                LibraryFilter.ALL -> !cinema || info.isRealTitle
                LibraryFilter.MOVIES -> info.isFilm
                LibraryFilter.SERIES -> info.title.kind == TitleKind.SERIES && !info.isClip
                LibraryFilter.ANIME -> info.title.kind == TitleKind.ANIME && !info.isClip
                // With All narrowed, this is the only list left for what All no longer holds.
                LibraryFilter.VIDEOS -> if (cinema) !info.isRealTitle else info.isClip
                LibraryFilter.FAVORITES -> info.title.favorite
                LibraryFilter.WATCHLIST -> info.title.watchlistAt != null
                LibraryFilter.FOLDERS -> false
            }
        }
        val order = when (sort) {
            LibrarySort.RECENT -> BY_RECENT_TITLES_FIRST
            LibrarySort.NAME -> BY_NAME
            LibrarySort.RATING -> BY_RATING
        }
        return pool.sortedWith(order).map { it.card() }
    }

    fun genre(s: Snapshot, name: String): List<TitleCard> =
        s.infos.values.filter { s.inLibrary(it) && name in it.genres }.sortedWith(BY_RATING).map { it.card() }

    fun ranked(items: List<TitleInfo>): List<TitleInfo> = items.sortedWith(BY_RATING)
    fun named(items: List<TitleInfo>): List<TitleInfo> = items.sortedWith(BY_NAME)
    fun recent(items: List<TitleInfo>): List<TitleInfo> = items.sortedWith(BY_RECENT)
}

/**
 * The wide art behind Hero and Details, best first. A matched title with a poster and no backdrop, as every OMDb
 * match is, gets none, so the screens show its poster whole instead of a frame from the video. [cover] is the
 * title's own cover, never its frame.
 */
internal object WideArt {
    private val MATCHED = setOf(MatchState.AUTO, MatchState.MANUAL)

    fun candidates(backdrop: String?, frame: String?, cover: String?, state: MatchState): List<String> {
        val posterInstead = cover != null && state in MATCHED
        return listOfNotNull(backdrop, frame?.takeUnless { posterInstead })
    }
}

/** F-U5, F-U6, F-S1. Clips stay out of the hero and the rows while a real title exists, and get one row of their own. */
internal object HomeBuilder {
    const val MIN_ROW_TITLES = 4
    const val ROW_TITLES = 10
    const val MAX_ROWS = HomeFeed.MAX_AUTOMATIC_ROWS
    const val RECENT_HEROES = 4

    /** [onDisk] guards the hero, which fills the screen: a row is written before a file or an image can vanish. */
    fun build(s: Snapshot, labels: Labels, onDisk: (String) -> Boolean = { true }): HomeFeed {
        val live = s.infos.values.filter { it.available }
        val shown = s.forHome(live)
        val titles = shown.filter { !it.isClip }
        val pool = titles.ifEmpty { shown }
        val fits = { info: TitleInfo -> fitsHero(s, info, onDisk) }
        val lastPlayed = pool.filter { it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt }.firstOrNull(fits)
        val recent = pool.filter { it !== lastPlayed }
            .sortedWith(compareByDescending<TitleInfo> { it.addedAt }.thenBy { it.id })
            .asSequence().filter(fits).take(RECENT_HEROES).toList()
        val heroKey = lastPlayed?.let { heroPlayable(it)?.key }
        val allContinuing = continueWatching(shown, labels, EpisodeRows.today(s.now))
        // The app drops a hero whose art does not decode, and then needs this card back.
        val heroCard = allContinuing.firstOrNull { it.play.playableKey == heroKey }
        val continuing = allContinuing.filter { it !== heroCard }
        val first = lastPlayed?.let { hero(it, onDisk).copy(continueItem = heroCard) }
        val heroes = listOfNotNull(first) + recent.map { hero(it, onDisk) }
        val slots = MAX_ROWS - if (continuing.isEmpty()) 0 else 1
        val empty = live.isEmpty() || (s.roots.isEmpty() && !s.settings.scanWholePhone)
        return HomeFeed(
            heroes = heroes,
            continueWatching = continuing,
            rows = listOfNotNull(watchlistRow(shown)) + rows(titles, s).take(slots) + listOfNotNull(videosRow(shown)),
            empty = empty,
            cinemaEmpty = !empty && s.settings.cinemaMode && shown.isEmpty(),
        )
    }

    private fun heroPlayable(info: TitleInfo): Playable? =
        ResumeLogic.pick(info.playables) ?: info.playables.firstOrNull { it.available }

    private fun fitsHero(s: Snapshot, info: TitleInfo, onDisk: (String) -> Boolean): Boolean {
        val playable = heroPlayable(info) ?: return false
        if (playable.broken || s.absolutePath(playable.best)?.let(onDisk) == false) return false
        return wideImage(info, onDisk) != null || info.cover?.let(onDisk) == true
    }

    private fun wideImage(info: TitleInfo, onDisk: (String) -> Boolean): String? = info.title.let {
        WideArt.candidates(it.backdropPath, it.framePath, it.coverPath, it.matchState).firstOrNull(onDisk)
    }

    private fun hero(info: TitleInfo, onDisk: (String) -> Boolean): HeroItem {
        val playable = heroPlayable(info)
        return HeroItem(
            titleId = info.id,
            kind = info.title.kind,
            name = info.title.name,
            logoPath = info.title.logoPath,
            backdropPath = wideImage(info, onDisk),
            coverPath = info.cover?.takeIf(onDisk),
            genre = info.genres.firstOrNull(),
            year = info.title.year,
            dominantColor = info.title.dominantColor,
            resumable = if (info.isMovie) playable?.inProgress == true else info.started && ResumeLogic.pick(info.playables) != null,
            play = playable?.ref(),
            clip = info.isClip,
        )
    }

    private class Continuing(val at: Long, val item: ContinueItem)

    private fun continueWatching(live: List<TitleInfo>, labels: Labels, today: String): List<ContinueItem> =
        live.mapNotNull { info ->
            val playable = if (info.isMovie) {
                info.playables.firstOrNull { it.inProgress }
            } else if (info.started) {
                ResumeLogic.pick(info.playables)
            } else {
                null
            }
            if (playable == null) null else Continuing(info.lastPlayedAt, item(info, playable, labels, today))
        }.sortedByDescending { it.at }.map { it.item }

    private fun item(info: TitleInfo, p: Playable, labels: Labels, today: String): ContinueItem {
        val total = p.durationMs
        val position = if (p.inProgress) p.watch?.positionMs ?: 0 else 0
        // Only the newest episode the owner has gets the hint, so a card deep in a season stays as it was.
        val newest = !info.isMovie && info.playables.lastOrNull { it.season != 0 && it.numbered }?.key == p.key
        return ContinueItem(
            titleId = info.id,
            play = p.ref(),
            name = info.title.name,
            episodeLabel = if (info.isMovie || !p.numbered) null else labels.episode(p.season, p.number),
            episodeTitle = p.episode?.name,
            imagePath = p.episode?.stillPath ?: info.title.backdropPath ?: p.best.framePath ?: info.cover,
            progress = p.partial ?: 0f,
            timeLeftMs = (total - position).coerceAtLeast(0),
            newEpisode = !p.inProgress && !info.isMovie && p.firstSeenAt > info.lastPlayedAt && info.lastPlayedAt > 0,
            upcoming = if (newest) Upcoming.of(info, today) else null,
        )
    }

    private fun row(kind: HomeRowKind, label: String?, items: List<TitleInfo>): HomeRow? {
        if (items.size < MIN_ROW_TITLES) return null
        return HomeRow(kind, label, items.take(ROW_TITLES).map { it.card() }, (items.size - ROW_TITLES).coerceAtLeast(0))
    }

    /** With no key there are no ratings or genres, so Recently added keeps films and series on Home as cards. */
    private fun rows(live: List<TitleInfo>, s: Snapshot): List<HomeRow> = listOfNotNull(
        row(HomeRowKind.TOP_RATED, null, topRated(live)),
        topGenre(live)?.let { row(HomeRowKind.GENRE, it, ofGenre(live, it)) },
        topCollection(live)?.let { id -> row(HomeRowKind.COLLECTION, s.collections[id], ofCollection(live, id)) },
    ).ifEmpty { listOfNotNull(row(HomeRowKind.RECENT, null, TitleLists.recent(live))) }

    private fun clips(live: List<TitleInfo>) = TitleLists.recent(live.filter { it.isClip })

    /**
     * The owner's watchlist, newest first. A started title is already in Continue watching, so only unstarted ones
     * show here. Unlike the automatic rows it shows with a single title.
     */
    private fun watchlist(live: List<TitleInfo>): List<TitleInfo> =
        live.filter { it.title.watchlistAt != null && !it.started && !it.watched }
            .sortedWith(compareByDescending<TitleInfo> { it.title.watchlistAt }.thenBy { it.id })

    private fun watchlistRow(live: List<TitleInfo>): HomeRow? {
        val items = watchlist(live)
        if (items.isEmpty()) return null
        return HomeRow(HomeRowKind.WATCHLIST, null, items.take(ROW_TITLES).map { it.card() }, (items.size - ROW_TITLES).coerceAtLeast(0))
    }

    private fun videosRow(live: List<TitleInfo>): HomeRow? = row(HomeRowKind.VIDEOS, null, clips(live))

    private fun topRated(live: List<TitleInfo>) = TitleLists.ranked(live.filter { it.rating != null })
    private fun ofGenre(live: List<TitleInfo>, name: String) = TitleLists.ranked(live.filter { name in it.genres })
    private fun ofCollection(live: List<TitleInfo>, id: Long) = TitleLists.named(live.filter { it.title.collectionId == id })

    private fun topGenre(live: List<TitleInfo>): String? =
        live.flatMap { it.genres }.groupingBy { it }.eachCount().filterValues { it >= MIN_ROW_TITLES }.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key

    private fun topCollection(live: List<TitleInfo>): Long? =
        live.filter { it.isMovie }.mapNotNull { it.title.collectionId }.groupingBy { it }.eachCount()
            .filterValues { it >= MIN_ROW_TITLES }.entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key

    fun rowTitles(s: Snapshot, kind: HomeRowKind, label: String?): List<TitleCard> {
        val live = s.infos.values.filter { it.available }
        val shown = s.forHome(live)
        val items = when (kind) {
            HomeRowKind.TOP_RATED -> topRated(shown)
            HomeRowKind.GENRE -> label?.let { ofGenre(shown, it) }.orEmpty()
            HomeRowKind.COLLECTION -> s.collections.entries.firstOrNull { it.value == label }?.let { ofCollection(shown, it.key) }.orEmpty()
            HomeRowKind.VIDEOS -> clips(live)
            HomeRowKind.RECENT -> TitleLists.recent(shown.filter { !it.isClip })
            HomeRowKind.WATCHLIST -> watchlist(shown)
        }
        return items.map { it.card() }
    }
}

/** F-U9, F-L9, F-S5. */
internal object EpisodeRows {
    fun today(now: Long): String = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun released(e: EpisodeLite, today: String): Boolean =
        e.fetchedAt != null && e.airDate != null && e.airDate <= today

    /** [imdb] is the IMDb switch. Off, no episode shows an IMDb rating. */
    fun build(
        info: TitleInfo,
        season: Int,
        episodes: List<EpisodeLite>,
        today: String,
        labels: Labels,
        imdb: Boolean = true,
    ): List<EpisodeRow> {
        fun ratingOf(e: EpisodeLite?) = e?.imdbRating?.takeIf { imdb }?.let { RatingInfo(RatingSource.IMDB, it) }
        val mine = info.playables.filter { it.season == season }
        if (mine.isEmpty()) return emptyList()
        val listed = episodes.filter { it.season == season }
        val byNumber = listed.associateBy { it.number }
        val covered = HashSet<Int>()
        val rows = ArrayList<EpisodeRow>()
        for (p in mine) {
            if (!p.numbered) {
                rows += row(info, p, 0, null, season, labels, null)
                continue
            }
            for (n in p.number..(p.numberEnd ?: p.number)) {
                covered += n
                val episode = byNumber[n] ?: p.episode.takeIf { n == p.number }
                rows += row(info, p, n, episode, season, labels, ratingOf(episode))
            }
        }
        listed.filter { it.number !in covered && released(it, today) }.forEach { e ->
            rows += EpisodeRow(
                season = season,
                number = e.number,
                label = labels.episode(season, e.number),
                title = e.name.orEmpty(),
                imagePath = e.stillPath,
                durationMs = (e.runtime ?: 0) * 60_000L,
                progress = null,
                watched = false,
                available = false,
                missing = true,
                play = null,
                rating = ratingOf(e),
            )
        }
        return rows.sortedWith(compareBy({ it.number == 0 }, { it.number }))
    }

    private fun row(
        info: TitleInfo,
        p: Playable,
        number: Int,
        e: EpisodeLite?,
        season: Int,
        labels: Labels,
        rating: RatingInfo?,
    ): EpisodeRow {
        val file = p.best
        return EpisodeRow(
            season = season,
            number = number,
            label = if (number == 0) "" else labels.episode(season, number),
            title = e?.name ?: CleanName.of(file.relPath, file.releaseTags),
            imagePath = e?.stillPath ?: file.framePath ?: info.title.backdropPath ?: info.cover,
            durationMs = p.durationMs,
            progress = p.partial,
            watched = p.watched,
            available = p.available,
            missing = false,
            play = if (p.available) p.ref() else null,
            rating = rating,
        )
    }
}

/** Every file of a title, extras too, from what the database holds. */
internal object FileFactsBuilder {
    fun of(s: Snapshot, titleId: Long): List<FileFacts> =
        s.filesByTitle[titleId].orEmpty().sortedWith { a, b -> NaturalOrder.compare(a.relPath, b.relPath) }.map { f ->
            val path = s.absolutePath(f) ?: f.relPath
            FileFacts(
                fileRowId = f.rowId,
                path = path,
                folder = path.substringBeforeLast('/', ""),
                name = f.relPath.substringAfterLast('/'),
                sizeBytes = f.size,
                durationMs = f.durationMs,
                width = f.width,
                height = f.height,
                videoCodec = f.videoCodec,
                hdrType = f.hdrType,
                audioSummary = f.audioSummary,
                modifiedAtMs = f.mtime,
                hasSubtitle = f.hasSidecarSub || f.hasEmbeddedSub,
                available = f.available,
            )
        }
}

internal object NextUp {
    /** The next available item after [key]. Specials follow only when a special is playing. */
    fun after(items: List<Playable>, key: String): Playable? {
        val index = items.indexOfFirst { it.key == key }
        if (index < 0) return null
        val current = items[index]
        return items.drop(index + 1).firstOrNull { it.available && (it.season != 0 || current.season == 0) }
    }
}

/** F-U2 Folders view and F-L10 chips. Built from the database, so a gone drive still lists. */
internal object FolderBuilder {
    val NEW_WINDOW: Long = TimeUnit.DAYS.toMillis(7)

    private fun rootPath(root: RootFolder, s: Snapshot): String? {
        val base = s.volumesById[root.volumeUuid]?.lastPath ?: return null
        return if (root.relPath.isEmpty()) base else "$base/${root.relPath}"
    }

    private fun rootName(root: RootFolder, s: Snapshot): String =
        root.relPath.substringAfterLast('/').ifEmpty { s.volumesById[root.volumeUuid]?.label.orEmpty() }

    private fun under(s: Snapshot, volume: String, rel: String): List<MediaFile> {
        val prefix = if (rel.isEmpty()) "" else "$rel/"
        return s.visibleFiles.filter { it.volumeUuid == volume && it.relPath.startsWith(prefix) }
    }

    fun roots(s: Snapshot): List<RootFolderInfo> = s.roots.mapNotNull { root ->
        val path = rootPath(root, s) ?: return@mapNotNull null
        RootFolderInfo(path, rootName(root, s), under(s, root.volumeUuid, root.relPath).size, root.volumeUuid in s.mounted)
    }

    private fun isNew(f: MediaFile, s: Snapshot): Boolean =
        s.now - f.firstSeenAt < NEW_WINDOW && (s.watch[f.playableKey]?.lastPlayedAt ?: 0) == 0L

    private fun cover(f: MediaFile, s: Snapshot): String? = f.framePath ?: f.titleId?.let { s.infos[it]?.cover }

    private fun folderEntry(path: String, name: String, files: List<MediaFile>, s: Snapshot) = FolderEntry(
        path = path,
        name = name,
        isDirectory = true,
        videoCount = files.size,
        sizeBytes = files.sumOf { it.size },
        isNew = files.any { isNew(it, s) },
        hasSubtitle = files.any { it.hasSidecarSub || it.hasEmbeddedSub },
        coverPath = files.minWithOrNull { a, b -> NaturalOrder.compare(a.relPath, b.relPath) }?.let { cover(it, s) },
        progress = null,
        unavailable = files.isNotEmpty() && files.none { it.available },
        play = null,
    )

    private fun fileEntry(path: String, f: MediaFile, s: Snapshot) = FolderEntry(
        path = path,
        name = f.relPath.substringAfterLast('/'),
        isDirectory = false,
        videoCount = 1,
        sizeBytes = f.size,
        isNew = isNew(f, s),
        hasSubtitle = f.hasSidecarSub || f.hasEmbeddedSub,
        coverPath = cover(f, s),
        progress = s.playablesByKey[f.playableKey]?.partial,
        unavailable = !f.available,
        play = if (f.available) PlayRef(fileRowId = f.rowId) else null,
    )

    private fun addedFolders(s: Snapshot): FolderView {
        val entries = s.roots.mapNotNull { root ->
            rootPath(root, s)?.let { folderEntry(it, rootName(root, s), under(s, root.volumeUuid, root.relPath), s) }
        }
        return FolderView(null, null, entries)
    }

    /** The start of a whole-phone library: one volume opens on its own folders, and several list each volume. */
    private fun volumeRoots(s: Snapshot): FolderView {
        val held = s.visibleFiles.mapTo(HashSet()) { it.volumeUuid }
        val shown = s.volumes.filter { it.uuid in held && it.lastPath != null }
        shown.singleOrNull()?.let { return build(it.lastPath, s).copy(path = null, name = null) }
        val entries = shown.map { volume ->
            val path = volume.lastPath.orEmpty()
            folderEntry(path, volume.label ?: path.substringAfterLast('/'), under(s, volume.uuid, ""), s)
        }
        return FolderView(null, null, entries)
    }

    fun build(path: String?, s: Snapshot): FolderView {
        if (path == null) return if (s.settings.scanWholePhone) volumeRoots(s) else addedFolders(s)
        val name = path.substringAfterLast('/')
        val volume = s.volumes.firstOrNull { v -> v.lastPath != null && (path == v.lastPath || path.startsWith(v.lastPath + "/")) }
            ?: return FolderView(path, name, emptyList())
        val rel = path.removePrefix(volume.lastPath!!).removePrefix("/")
        val prefix = if (rel.isEmpty()) "" else "$rel/"
        val folders = LinkedHashMap<String, MutableList<MediaFile>>()
        val files = ArrayList<MediaFile>()
        for (f in under(s, volume.uuid, rel)) {
            val rest = f.relPath.substring(prefix.length)
            val slash = rest.indexOf('/')
            if (slash < 0) files += f else folders.getOrPut(rest.substring(0, slash)) { ArrayList() } += f
        }
        val folderEntries = folders.entries
            .sortedWith { a, b -> NaturalOrder.compare(a.key, b.key) }
            .map { (child, group) -> folderEntry("$path/$child", child, group, s) }
        val fileEntries = files
            .sortedWith { a, b -> NaturalOrder.compare(a.relPath, b.relPath) }
            .map { fileEntry("$path/${it.relPath.substringAfterLast('/')}", it, s) }
        return FolderView(path, name, folderEntries + fileEntries)
    }
}
