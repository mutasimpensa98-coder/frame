package com.frameglass.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.frameglass.data.api.ApiKeyKind
import com.frameglass.data.api.CastMember
import com.frameglass.data.api.EpisodeRow
import com.frameglass.data.api.FileVersion
import com.frameglass.data.api.FolderView
import com.frameglass.data.api.HomeFeed
import com.frameglass.data.api.HomeRowKind
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibraryRepository
import com.frameglass.data.api.LibrarySort
import com.frameglass.data.api.MatchChoice
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.PersonView
import com.frameglass.data.api.PickOption
import com.frameglass.data.api.ResumeEntry
import com.frameglass.data.api.RatingInfo
import com.frameglass.data.api.RatingSource
import com.frameglass.data.api.RefreshProgress
import com.frameglass.data.api.RefreshSkip
import com.frameglass.data.api.RefreshSource
import com.frameglass.data.api.RootFolderInfo
import com.frameglass.data.api.ScanState
import com.frameglass.data.api.SearchResults
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.TitleDetail
import com.frameglass.data.api.VersionSource
import com.frameglass.data.api.WatchTarget
import com.frameglass.data.api.AutoBackupSource
import com.frameglass.data.api.StatsSource
import com.frameglass.data.api.WatchStats
import com.frameglass.data.backup.AutoBackupManager
import com.frameglass.data.backup.BackupManager
import com.frameglass.data.db.ExcludedFolder
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.LookupJob
import com.frameglass.data.db.LookupQueue
import com.frameglass.data.db.LookupState
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.RootFolder
import com.frameglass.data.db.Volume
import com.frameglass.data.logic.Assembler
import com.frameglass.data.logic.AutoReach
import com.frameglass.data.logic.Clock
import com.frameglass.data.logic.EpisodeRows
import com.frameglass.data.logic.FileFactsBuilder
import com.frameglass.data.logic.FolderBuilder
import com.frameglass.data.logic.HomeBuilder
import com.frameglass.data.logic.Labels
import com.frameglass.data.logic.ParsePaths
import com.frameglass.data.logic.PathRange
import com.frameglass.data.logic.QuickResume
import com.frameglass.data.logic.TonightPool
import com.frameglass.data.logic.Upcoming
import com.frameglass.data.logic.PathScope
import com.frameglass.data.logic.PlayableKeys
import com.frameglass.data.logic.ResumeLogic
import com.frameglass.data.logic.SearchText
import com.frameglass.data.logic.Snapshot
import com.frameglass.data.logic.TitleInfo
import com.frameglass.data.logic.TitleLists
import com.frameglass.data.logic.VaultRefs
import com.frameglass.data.logic.Versions
import com.frameglass.data.logic.WatchRules
import com.frameglass.data.logic.WideArt
import com.frameglass.data.logic.WatchStatsBuilder
import com.frameglass.data.logic.card
import com.frameglass.data.logic.ref
import com.frameglass.data.meta.Credits
import com.frameglass.data.meta.Keys
import com.frameglass.data.meta.MetadataPipeline
import com.frameglass.data.scan.CatchUp
import com.frameglass.data.scan.CoverColors
import com.frameglass.data.scan.ImageStore
import com.frameglass.data.scan.ScanProgress
import com.frameglass.data.scan.ScanTally
import com.frameglass.data.scan.Scanner
import com.frameglass.data.scan.SwipeRefresh
import com.frameglass.data.scan.SwipeSteps
import com.frameglass.data.scan.Volumes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

internal class LibraryRepositoryImpl(
    private val context: Context,
    private val db: FrameDb,
    private val snapshots: SnapshotSource,
    private val labels: Labels,
    private val settings: SettingsStore,
    private val volumes: Volumes,
    private val scanner: Scanner,
    private val progress: ScanProgress,
    private val catchUp: CatchUp,
    private val pipeline: MetadataPipeline,
    private val images: ImageStore,
    private val backup: BackupManager,
    private val clock: Clock,
    private val vault: Lazy<VaultPlayback>,
    private val backups: AutoBackupManager,
) : LibraryRepository, VersionSource, RefreshSource, StatsSource, AutoBackupSource by backups {

    private fun <T> view(block: suspend (Snapshot) -> T): Flow<T> =
        snapshots.flow.map(block).distinctUntilChanged().flowOn(Dispatchers.Default)

    override val home: Flow<HomeFeed> = view { HomeBuilder.build(it, labels) { path -> File(path).exists() } }

    override suspend fun quickResume(): List<ResumeEntry> = withContext(Dispatchers.Default) {
        QuickResume.build(snapshots.fresh(), labels)
    }

    override val resumeChanges: Flow<Unit> = db.invalidationTracker.createFlow("WatchState", "MediaFile", "Title").map { }

    override val tonightPool: Flow<List<PickOption>> = view { TonightPool.build(it) }

    /** The week starts on the day the phone's region starts it, Saturday for Libya. */
    override val watchStats: Flow<WatchStats> =
        combine(snapshots.flow, db.invalidationTracker.createFlow("WatchLog")) { s, _ -> s }
            .map { s ->
                val today = LocalDate.parse(EpisodeRows.today(clock.now()))
                WatchStatsBuilder.build(db.watch().allLog(), s, today, WeekFields.of(Locale.getDefault()).firstDayOfWeek)
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    override val scanState: Flow<ScanState> = combine(
        progress.state,
        db.meta().pendingCount(),
        settings.hasKey(ApiKeyKind.TMDB),
        settings.hasKey(ApiKeyKind.OMDB),
        settings.settings,
    ) { state, pending, tmdbKey, omdbKey, s ->
        val serviceOn = (s.tmdbEnabled && tmdbKey) || (s.imdbEnabled && omdbKey)
        state.copy(matching = if (serviceOn && s.onlineFeatures) pending else 0)
    }.distinctUntilChanged()

    override val folders: Flow<List<RootFolderInfo>> = view { FolderBuilder.roots(it) }

    override fun titles(filter: LibraryFilter, sort: LibrarySort): Flow<List<TitleCard>> =
        view { TitleLists.library(it, filter, sort) }

    override fun folder(path: String?): Flow<FolderView> = view { FolderBuilder.build(path, it) }

    override fun title(id: Long): Flow<TitleDetail?> = view { detail(id, it) }

    private suspend fun detail(id: Long, s: Snapshot): TitleDetail? {
        val info = s.infos[id] ?: return null
        val title = db.titles().title(id) ?: return null
        val meta = db.meta()
        val pick = ResumeLogic.pick(info.playables)
        val chosen = pick ?: info.playables.firstOrNull { it.available }
        val collection = title.collectionId?.let { collectionId ->
            s.infos.values.filter { it.title.collectionId == collectionId && it.id != id && s.inLibrary(it) }
        }.orEmpty()
        return TitleDetail(
            id = id,
            kind = title.kind,
            name = title.name,
            originalName = title.originalName,
            year = title.year,
            overview = title.overview,
            runtimeMinutes = title.runtime,
            genres = info.genres,
            coverPath = info.cover,
            backdropPath = WideArt.candidates(title.backdropPath, title.framePath, title.coverPath, title.matchState)
                .firstOrNull(),
            logoPath = title.logoPath,
            dominantColor = title.dominantColor,
            rating = info.tmdbRating?.let { RatingInfo(RatingSource.TMDB, it) },
            imdbRating = info.imdbRating?.let { RatingInfo(RatingSource.IMDB, it) },
            credits = Credits.decode(title.creditsJson),
            cast = meta.cast(id).map { CastMember(it.personId, it.name, it.character, it.photoPath) },
            collectionName = title.collectionId?.let { s.collections[it] },
            collection = TitleLists.named(collection).map { it.card() },
            matchState = title.matchState,
            play = chosen?.ref(),
            playLabel = chosen?.takeIf { !info.isMovie && it.numbered }?.let { labels.episode(it.season, it.number) },
            resumable = if (info.isMovie) chosen?.inProgress == true else info.started && pick != null,
            seasons = seasons(info),
            unavailable = !info.available,
            favorite = title.favorite,
            watchlist = title.watchlistAt != null,
            files = FileFactsBuilder.of(s, id),
            upcoming = Upcoming.of(info, EpisodeRows.today(s.now)),
            trailerKey = title.trailerKey,
        )
    }

    /** Specials sort last, as on every streaming service. */
    private fun seasons(info: TitleInfo): List<Int> {
        if (info.isMovie) return emptyList()
        return info.playables.map { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it }))
    }

    override fun episodes(titleId: Long, season: Int): Flow<List<EpisodeRow>> = view { s ->
        val info = s.infos[titleId]
        if (info == null) emptyList() else EpisodeRows.build(
            info, season, s.episodes.filter { it.titleId == titleId }, EpisodeRows.today(s.now), labels, s.settings.imdbEnabled,
            s.settings.hideUnseenStills,
        )
    }

    override fun search(query: String): Flow<SearchResults> {
        val cleaned = SearchText.clean(Keys.normalize(query))
        val match = SearchText.match(cleaned) ?: return flowOf(SearchResults(emptyList(), emptyList(), emptyList()))
        return view { s ->
            val found = db.titles().searchIds(match, MAX_TITLES).mapNotNull { s.infos[it] }.filter(s::inLibrary)
            val titles = found.sortedWith(
                compareByDescending<TitleInfo> { it.title.sortKey.startsWith(cleaned) }.thenBy { it.title.sortKey },
            )
            // A title in the private folder keeps its row, so its cast and genres must not speak for it here.
            val people = db.meta().searchPeople("%$cleaned%", MAX_PEOPLE)
                .filter { p -> db.meta().titlesOfPerson(p.personId).any { id -> s.infos[id]?.let(s::inLibrary) == true } }
                .map { CastMember(it.personId, it.name, null, it.photoPath) }
            val genres = s.genreLinks.filter { it.titleId in s.infos }.map { it.name }.distinct()
                .filter { SearchText.clean(Keys.normalize(it)).contains(cleaned) }
            SearchResults(titles.map { it.card() }, people, genres)
        }
    }

    override fun person(personId: Long): Flow<PersonView?> = view { s ->
        val meta = db.meta()
        val person = meta.person(personId)
        if (person == null) {
            null
        } else {
            val titles = meta.titlesOfPerson(personId).mapNotNull { s.infos[it] }.filter(s::inLibrary)
            PersonView(personId, person.name, person.photoPath, TitleLists.named(titles).map { it.card() })
        }
    }

    override fun genre(name: String): Flow<List<TitleCard>> = view { TitleLists.genre(it, name) }

    override fun rowTitles(row: HomeRowKind, label: String?): Flow<List<TitleCard>> = view { HomeBuilder.rowTitles(it, row, label) }

    override fun startCatchUp() {
        catchUp.onOpen()
        backups.onAppOpen()
    }

    override suspend fun addFolder(path: String) = withContext(Dispatchers.IO) {
        val (volume, rel) = volumes.locate(path) ?: return@withContext
        val now = clock.now()
        val scan = db.scan()
        scan.upsertVolume(Volume(volume.uuid, volume.label, volume.removable, now, volume.path))
        scan.insertRoot(RootFolder(volumeUuid = volume.uuid, relPath = rel, addedAt = now))
        scan.enableRoot(volume.uuid, rel)
        // Adding an excluded folder by hand is the owner taking it back.
        scan.deleteExcluded(volume.uuid, rel)
        // Adding a folder back is how a removed title returns.
        val titles = db.titles()
        scan.filesUnder(volume.uuid, PathRange.low(rel), PathRange.high(rel))
            .mapNotNull { it.titleId }.distinct().forEach { titles.setRemoved(it, false) }
        catchUp.onFoldersChanged()
    }

    override suspend fun removeFolder(path: String) = withContext(Dispatchers.IO) {
        val scan = db.scan()
        val known = db.scan().volumes().firstOrNull { v -> v.lastPath != null && (path == v.lastPath || path.startsWith(v.lastPath + "/")) }
            ?: return@withContext
        val rel = path.removePrefix(known.lastPath!!).trim('/')
        val opts = settings.current()
        val reach = if (opts.scanWholePhone) AutoReach(known.lastPath, opts) else null
        // File rows are derived data. Titles, matches and progress stay, so adding the folder back restores all of it.
        scanner.exclusive {
            db.withTransaction {
                scan.deleteRoot(known.uuid, rel)
                val others = scan.roots().filter { it.volumeUuid == known.uuid }.map { it.relPath }
                val orphans = scan.filesUnder(known.uuid, PathRange.low(rel), PathRange.high(rel)).filter { file ->
                    others.none { root -> root.isEmpty() || file.relPath.startsWith("$root/") } &&
                        reach?.reaches(ParsePaths.folderOf(file.relPath)) != true
                }
                orphans.map { it.rowId }.chunked(500).forEach { scan.deleteFiles(it) }
            }
        }
    }

    override suspend fun rescan() = withContext(Dispatchers.IO) { catchUp.scanNow() }

    override suspend fun refreshAll() = refreshProgress().collect()

    private val swipe = SwipeRefresh(object : SwipeSteps {
        override suspend fun scan(tally: ScanTally) = catchUp.scanNow(tally)
        override suspend fun queueRefresh() = pipeline.queueRefresh()
        override suspend fun blocker(): RefreshSkip? = pipeline.blocker()
        override suspend fun drain(budgetMs: Long) = pipeline.drainWithin(budgetMs)
        override suspend fun titlesLeft(): Int = pipeline.titlesDue()
        override val titlesUpdated: Int get() = pipeline.updatedTitles
        override fun continueInBackground() = catchUp.continueMetadata()
    })

    override fun refreshProgress(): Flow<RefreshProgress> = swipe.run().flowOn(Dispatchers.IO)

    override suspend fun setManualCover(titleId: Long, image: Uri) = withContext(Dispatchers.IO) {
        val target = images.coverFile(titleId, clock.now())
        val copied = runCatching {
            context.contentResolver.openInputStream(image)?.use { input -> target.outputStream().use { input.copyTo(it) } }
        }.getOrNull() != null
        val colors = if (copied) CoverColors.fromFile(target.path) else null
        if (colors == null) {
            // Not an image, so nothing changes.
            target.delete()
            return@withContext
        }
        val titles = db.titles()
        val old = db.withTransaction {
            val fresh = titles.title(titleId) ?: return@withTransaction null
            titles.update(fresh.copy(coverPath = target.path, manualCover = true, dominantColor = colors.dominant, coverLuma = colors.luma))
            fresh
        }
        if (old == null) target.delete() else if (old.manualCover) images.delete(old.coverPath)
    }

    override suspend fun matchChoices(titleId: Long, query: String?): List<MatchChoice> =
        withContext(Dispatchers.IO) { pipeline.choices(titleId, query) }

    override suspend fun applyMatch(titleId: Long, choice: MatchChoice) = withContext(Dispatchers.IO) {
        pipeline.applyChoice(titleId, choice)
        catchUp.onMetadataEnabled()
    }

    override suspend fun removeFromLibrary(titleId: Long) = withContext(Dispatchers.IO) {
        // The rows stay, so progress and the match survive. Queries hide the title, and addFolder brings it back.
        db.titles().setRemoved(titleId, true)
        db.meta().deleteQueue(titleId)
    }

    override suspend fun setFavorite(titleId: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        db.titles().setFavorite(titleId, favorite)
    }

    /** A title already watched stays for a rewatch, and leaves when all of it is finished again or marked watched. */
    override suspend fun setWatchlist(titleId: Long, onWatchlist: Boolean) = withContext(Dispatchers.IO) {
        db.titles().setWatchlist(titleId, if (onWatchlist) clock.now() else null)
    }

    override val excludedFolders: Flow<List<String>> = db.scan().excludedPaths()
        .map { rows -> rows.map { row -> row.base?.let { ParsePaths.join(it, row.relPath) } ?: row.relPath } }
        .distinctUntilChanged()

    /** The volume and relative path of [path], also on a drive that is unplugged right now. */
    private suspend fun locateKnown(path: String): Pair<String, String>? {
        volumes.locate(path)?.let { (volume, rel) -> return volume.uuid to rel }
        val known = db.scan().volumes()
            .filter { v -> v.lastPath != null && (path == v.lastPath || path.startsWith(v.lastPath + "/")) }
            .maxByOrNull { it.lastPath.orEmpty().length } ?: return null
        return known.uuid to path.removePrefix(known.lastPath.orEmpty()).trim('/')
    }

    private suspend fun rootIdsWithin(volume: String, rel: String): List<Long> =
        db.scan().allRoots().filter { it.volumeUuid == volume && PathScope.within(it.relPath, rel) }.map { it.id }

    /**
     * Saved first, so the choice holds even if the app dies while a running scan finishes. Folders added by hand
     * inside it stop too, and includeFolder starts them again.
     */
    override suspend fun excludeFolder(path: String) = withContext(Dispatchers.IO) {
        val (volume, rel) = locateKnown(path) ?: return@withContext
        val scan = db.scan()
        scan.insertExcluded(ExcludedFolder(volume, rel, clock.now()))
        scanner.exclusive {
            rootIdsWithin(volume, rel).chunked(ROOT_CHUNK).forEach { scan.setRootsEnabled(it, false) }
            scanner.applyExclusions()
        }
    }

    override suspend fun includeFolder(path: String) = withContext(Dispatchers.IO) {
        val (volume, rel) = locateKnown(path) ?: return@withContext
        val scan = db.scan()
        scan.deleteExcluded(volume, rel)
        rootIdsWithin(volume, rel).chunked(ROOT_CHUNK).forEach { scan.setRootsEnabled(it, true) }
        catchUp.onFoldersChanged()
    }

    override val hiddenTitles: Flow<List<TitleCard>> =
        view { s -> TitleLists.named(s.hiddenInfos.values.toList()).map { it.card() } }

    override suspend fun unhideTitle(titleId: Long) = withContext(Dispatchers.IO) {
        val titles = db.titles()
        val title = titles.title(titleId) ?: return@withContext
        titles.setRemoved(titleId, false)
        // Hiding took it out of the queue, so a title still waiting for its first match goes back in.
        if (title.tmdbId == null && title.matchState == MatchState.QUEUED) {
            db.meta().upsertQueue(LookupQueue(titleId, LookupState.QUEUED, 0, clock.now(), LookupJob.MATCH))
            catchUp.onMetadataEnabled()
        }
    }

    override suspend fun setWatched(target: WatchTarget, watched: Boolean) = withContext(Dispatchers.IO) {
        // The player marks by playable key, and a private item's state lives in the vault.
        if (target is WatchTarget.Playable && VaultRefs.isVault(target.playableKey)) {
            return@withContext vault.value.setWatched(target.playableKey, watched)
        }
        val keys = keysOf(target)
        val watch = db.watch()
        db.withTransaction {
            keys.forEach { (key, titleId) -> watch.upsert(WatchRules.onManual(watch.get(key), key, titleId, watched)) }
        }
        if (watched) {
            val marked = keys.mapTo(HashSet()) { it.first }
            keys.mapNotNull { it.second }.distinct().forEach { WatchlistKeeper.settle(db, it, marked) }
        }
    }

    private fun coveredKeys(file: MediaFile): List<Pair<String, Long?>> {
        val titleId = file.titleId ?: return listOf(file.playableKey to null)
        return PlayableKeys.covered(file.playableKey, titleId, file.nameKind, file.parsedSeason, file.parsedEpisode, file.parsedEpisodeEnd)
            .map { it to titleId }
    }

    private suspend fun keysOf(target: WatchTarget): List<Pair<String, Long?>> {
        val scan = db.scan()
        return when (target) {
            is WatchTarget.Playable -> {
                val file = scan.byPlayable(target.playableKey).firstOrNull()
                if (file == null) listOf(target.playableKey to null) else coveredKeys(file)
            }
            is WatchTarget.Season -> playablesOf(target.titleId).filter { it.season == target.season }.flatMap { coveredKeys(it.files.first()) }
            is WatchTarget.Title -> playablesOf(target.titleId).flatMap { coveredKeys(it.files.first()) }
        }
    }

    private suspend fun playablesOf(titleId: Long) =
        Assembler.playables(titleId, db.scan().filesOfTitle(titleId), db.titles().episodeRows(titleId)) { null }

    override suspend fun exportBackup(target: Uri): Result<Unit> = withContext(Dispatchers.IO) { backup.export(target) }

    override suspend fun importBackup(source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        scanner.exclusive { backup.import(source) }.onSuccess { catchUp.onFoldersChanged() }
    }

    override suspend fun versions(playableKey: String): List<FileVersion> = withContext(Dispatchers.IO) {
        val files = db.scan().byPlayable(playableKey)
        if (files.isEmpty()) return@withContext emptyList()
        val best = Versions.pick(files)
        val unplayed = (db.watch().get(playableKey)?.lastPlayedAt ?: 0L) == 0L
        val now = clock.now()
        files.sortedWith(compareByDescending<MediaFile> { it.available }.thenByDescending { it.height }.thenByDescending { it.size })
            .map { file ->
                FileVersion(
                    fileRowId = file.rowId,
                    name = file.relPath.substringAfterLast('/'),
                    width = file.width,
                    height = file.height,
                    sizeBytes = file.size,
                    videoCodec = file.videoCodec,
                    hdrType = file.hdrType,
                    audioSummary = file.audioSummary,
                    available = file.available,
                    isDefault = file.rowId == best.rowId,
                    isNew = unplayed && now - file.firstSeenAt < FolderBuilder.NEW_WINDOW,
                    hasSubtitle = file.hasSidecarSub || file.hasEmbeddedSub,
                )
            }
    }

    private companion object {
        const val MAX_TITLES = 200
        const val MAX_PEOPLE = 30
        const val ROOT_CHUNK = 500
    }
}

/** The absolute path of a row on a volume that is mounted now, or null. */
internal suspend fun FrameDb.absolutePath(file: MediaFile, volumes: Volumes): String? =
    volumes.pathMap()[file.volumeUuid]?.let { File(it, file.relPath).path } ?: scan().volumes()
        .firstOrNull { it.uuid == file.volumeUuid }?.lastPath?.let { ParsePaths.join(it, file.relPath) }
