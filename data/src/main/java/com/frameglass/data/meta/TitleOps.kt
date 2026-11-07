package com.frameglass.data.meta

import androidx.room.withTransaction
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.Episode
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.GroupAlias
import com.frameglass.data.db.LookupJob
import com.frameglass.data.db.LookupQueue
import com.frameglass.data.db.LookupState
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.SearchIndex
import com.frameglass.data.db.Title
import com.frameglass.data.db.WatchState
import com.frameglass.data.logic.Clock
import com.frameglass.data.logic.GroupCandidate
import com.frameglass.data.logic.GroupKeys
import com.frameglass.data.logic.ImdbRules
import com.frameglass.data.logic.PlayableKeys
import com.frameglass.data.logic.SearchText
import com.frameglass.data.logic.SegmentRules
import com.frameglass.data.logic.WatchRules
import com.frameglass.data.logic.titleKind
import com.frameglass.net.MatchCandidate
import com.frameglass.net.MetaKind
import com.frameglass.parser.ArabicNormalizer
import com.frameglass.parser.ParsedName

internal object Keys {
    /** A parser bug must not stop a scan. */
    fun normalize(text: String): String = runCatching { ArabicNormalizer.key(text) }.getOrDefault(text.lowercase())
}

/** Keeps the FTS table in step with a title, its people and its genres. */
internal class SearchIndexer(private val db: FrameDb) {
    private fun text(vararg parts: String?): String =
        SearchText.clean(Keys.normalize(parts.filterNotNull().joinToString(" ")))

    suspend fun index(titleId: Long) {
        val titles = db.titles()
        val title = titles.title(titleId)
        titles.deleteIndex(titleId)
        if (title == null) return
        val meta = db.meta()
        titles.insertIndex(
            SearchIndex(
                rowId = titleId,
                title = text(title.name, title.localName),
                original = text(title.originalName),
                people = text(*meta.peopleNames(titleId).toTypedArray()),
                genres = text(*meta.genreNames(titleId).toTypedArray()),
            ),
        )
    }
}

/** Finds or creates the title for a parsed file, by persisted groupKey per section 4.5. */
internal class TitleResolver(
    private val db: FrameDb,
    private val index: SearchIndexer,
    private val clock: Clock,
) {
    suspend fun resolve(parsed: ParsedName, fallbackName: String, fileId: String): Title =
        if (parsed.clipLike) clip(parsed, fallbackName, fileId) else titled(parsed, fallbackName)

    /** A clip has a title of its own, keyed by its content, and never enters the lookup queue. */
    suspend fun clip(parsed: ParsedName, fallbackName: String, fileId: String): Title {
        val titles = db.titles()
        val key = GroupKeys.clip(fileId)
        titles.byGroupKey(key)?.let { return it }
        val name = parsed.title.trim().ifEmpty { fallbackName }
        val title = Title(
            kind = TitleKind.MOVIE,
            groupKey = key,
            groupName = key,
            localName = name,
            localYear = null,
            name = name,
            year = null,
            matchState = MatchState.UNMATCHED,
            sortKey = Keys.normalize(name).ifEmpty { fallbackName.lowercase() },
            createdAt = clock.now(),
        )
        val id = titles.insert(title)
        index.index(id)
        return title.copy(id = id)
    }

    private suspend fun titled(parsed: ParsedName, fallbackName: String): Title {
        val titles = db.titles()
        val name = parsed.title.trim().ifEmpty { fallbackName }
        val normalized = Keys.normalize(name).ifEmpty { fallbackName.lowercase() }
        val kind = parsed.titleKind()
        val key = GroupKeys.key(kind, normalized, parsed.year)
        val found = titles.byGroupKey(key) ?: titles.byAlias(key) ?: run {
            val candidates = titles.byGroupName(normalized).map { GroupCandidate(it.id, it.kind, it.localYear) }
            val picked = GroupKeys.pick(kind, parsed.year, candidates) ?: return@run null
            titles.upsertAlias(GroupAlias(key, picked.id))
            titles.title(picked.id)
        }
        if (found != null) return refreshGroup(found, kind)
        val now = clock.now()
        val title = Title(
            kind = kind,
            groupKey = key,
            groupName = normalized,
            localName = name,
            localYear = parsed.year,
            name = name,
            year = parsed.year,
            matchState = MatchState.QUEUED,
            sortKey = normalized,
            createdAt = now,
        )
        val id = titles.insert(title)
        db.meta().upsertQueue(LookupQueue(id, LookupState.QUEUED, 0, now, LookupJob.MATCH))
        index.index(id)
        return title.copy(id = id)
    }

    private suspend fun refreshGroup(title: Title, kind: TitleKind): Title {
        val titles = db.titles()
        if (title.removed) titles.setRemoved(title.id, false)
        val anime = kind == TitleKind.ANIME && title.kind == TitleKind.SERIES && !title.manualMatch
        if (anime) titles.setKind(title.id, TitleKind.ANIME)
        // The short video rule parked this title. A new file can be the long one, so it gets asked again.
        val parked = title.matchState == MatchState.UNMATCHED && title.tmdbId == null
        if (parked) {
            titles.setMatchState(title.id, MatchState.QUEUED)
            db.meta().upsertQueue(LookupQueue(title.id, LookupState.QUEUED, 0, clock.now(), LookupJob.MATCH))
        }
        return title.copy(
            removed = false,
            kind = if (anime) TitleKind.ANIME else title.kind,
            matchState = if (parked) MatchState.QUEUED else title.matchState,
        )
    }

    /** Gives a numbered file its local Episode row. Metadata fills that row in later. */
    suspend fun episodeId(titleId: Long, kind: TitleKind, nameKind: String, season: Int?, episode: Int?, absolute: Int?): Long? {
        if (nameKind == NameKinds.EXTRA || kind == TitleKind.MOVIE) return null
        val number = episode ?: absolute ?: return null
        val titles = db.titles()
        val s = PlayableKeys.seasonOf(nameKind, season)
        titles.episode(titleId, s, number)?.let { return it.id }
        return titles.insertEpisode(Episode(titleId = titleId, season = s, number = number, absolute = absolute))
    }

    /** New files of a matched title need their episode data. */
    suspend fun queueEpisodes(title: Title) {
        val omdbSeries = ImdbRules.omdbOnly(title.tmdbId, title.imdbId) && title.kind != TitleKind.MOVIE
        if (!omdbSeries && (title.tmdbId == null || title.tmdbIsMovie == true)) return
        val meta = db.meta()
        if (meta.queued(title.id) == null) {
            meta.upsertQueue(LookupQueue(title.id, LookupState.QUEUED, 0, clock.now(), LookupJob.EPISODES))
        }
    }
}

/** Every multi-row change to a title's identity. Each public call is one transaction. */
internal class TitleOps(
    private val db: FrameDb,
    private val resolver: TitleResolver,
    private val index: SearchIndexer,
    private val clock: Clock,
) {
    private fun kindFor(candidateKind: MetaKind, isAnime: Boolean, current: TitleKind): TitleKind = when {
        candidateKind == MetaKind.MOVIE -> TitleKind.MOVIE
        isAnime || current == TitleKind.ANIME -> TitleKind.ANIME
        else -> TitleKind.SERIES
    }

    /** Applies an automatic match. Returns the title that now holds it, which a merge may have changed. */
    suspend fun attachMatch(titleId: Long, candidate: MatchCandidate, anilistId: Long?, malId: Long?): Title? =
        db.withTransaction {
            val titles = db.titles()
            val title = titles.title(titleId) ?: return@withTransaction null
            if (title.manualMatch) return@withTransaction title
            val isMovie = candidate.kind == MetaKind.MOVIE
            val other = titles.byTmdb(candidate.tmdbId, isMovie, title.id)
            var target = title
            // Two AniList entries on one TMDB show are separate cours whose parser numbers overlap, so
            // merging them would turn episode 3 of each into two versions of one episode.
            val anilist = anilistId ?: title.anilistId
            val sameWork = anilist == null || other?.anilistId == null || anilist == other.anilistId
            if (other != null && !other.manualMatch && sameWork) {
                val survivor = if (other.id < title.id) other else title
                val loser = if (survivor === other) title else other
                merge(loser.id, survivor.id)
                target = titles.title(survivor.id) ?: return@withTransaction null
            }
            val kind = kindFor(candidate.kind, candidate.isAnime, target.kind)
            rekey(target, kind)
            val updated = (titles.title(target.id) ?: target).copy(
                kind = kind,
                tmdbId = candidate.tmdbId,
                tmdbIsMovie = isMovie,
                anilistId = anilistId ?: target.anilistId,
                malId = malId ?: target.malId,
                matchState = MatchState.AUTO,
                matchConfidence = candidate.score,
            )
            titles.update(updated)
            titles.clearEpisodeImdbRatings(updated.id)
            updated
        }

    /**
     * Fix match. Per section 4.5 the affected rows rekey in one transaction, and a manual match never
     * merges with another title even when both now point at one provider id.
     */
    suspend fun applyManual(titleId: Long, kind: TitleKind, tmdbId: Long) {
        db.withTransaction {
            val titles = db.titles()
            val meta = db.meta()
            val title = titles.title(titleId) ?: return@withTransaction
            val isMovie = kind == TitleKind.MOVIE
            rekey(title, kind)
            val current = titles.title(titleId) ?: title
            val sameTarget = title.tmdbId == tmdbId && title.tmdbIsMovie == isMovie
            if (!sameTarget) {
                meta.deleteTitlePersons(titleId)
                meta.deleteTitleGenres(titleId)
                meta.deleteRatings(titleId)
                meta.deleteSegmentsExcept(titleId, SegmentRules.MANUAL)
                rebuildEpisodes(titleId, kind)
            }
            titles.update(
                current.copy(
                    kind = kind,
                    tmdbId = tmdbId,
                    tmdbIsMovie = isMovie,
                    anilistId = if (sameTarget) current.anilistId else null,
                    malId = if (sameTarget) current.malId else null,
                    imdbId = if (sameTarget) current.imdbId else null,
                    manualMatch = true,
                    matchState = MatchState.MANUAL,
                    matchConfidence = 1.0,
                    fetchedAt = null,
                    metaLanguage = null,
                    collectionId = if (sameTarget) current.collectionId else null,
                ),
            )
            titles.clearEpisodeImdbRatings(titleId)
            meta.upsertQueue(LookupQueue(titleId, LookupState.QUEUED, 0, clock.now(), LookupJob.REFRESH))
            index.index(titleId)
        }
    }

    /**
     * An automatic match through OMDb. Titles on one IMDb id merge as they do on one TMDB id, unless
     * ImdbRules.mergesOnImdb says the id cannot tell the works apart, and a title TMDB already matched survives,
     * because it holds more. Returns the title that now holds the match.
     */
    suspend fun attachImdbMatch(titleId: Long, imdbId: String, kind: MetaKind, score: Double): Title? =
        db.withTransaction {
            val titles = db.titles()
            val title = titles.title(titleId) ?: return@withTransaction null
            if (title.manualMatch || title.tmdbId != null) return@withTransaction title
            var target = title
            val other = titles.byImdb(imdbId, title.id)
            val merges = other != null && ImdbRules.mergesOnImdb(
                kind = title.kind,
                otherKind = other.kind,
                otherAnilistId = other.anilistId,
                otherManual = other.manualMatch,
                holders = titles.countByImdb(imdbId, title.id),
            )
            if (other != null && merges) {
                val survivor = if (other.tmdbId != null || other.id < title.id) other else title
                val loser = if (survivor === other) title else other
                merge(loser.id, survivor.id)
                target = titles.title(survivor.id) ?: return@withTransaction null
                if (target.tmdbId != null) return@withTransaction target
            }
            val newKind = kindFor(kind, isAnime = false, current = target.kind)
            rekey(target, newKind)
            val updated = (titles.title(target.id) ?: target).copy(
                kind = newKind,
                imdbId = imdbId,
                matchState = MatchState.AUTO,
                matchConfidence = score,
            )
            titles.update(updated)
            updated
        }

    /**
     * TMDB's record of the IMDb id a title was matched to through OMDb. An automatic match merges as any TMDB match
     * does. The owner's own choice names the same work, so it gains the TMDB id and stays a manual match.
     */
    suspend fun attachTmdbForImdb(titleId: Long, candidate: MatchCandidate, anilistId: Long?, malId: Long?): Title? =
        db.withTransaction {
            val titles = db.titles()
            val title = titles.title(titleId) ?: return@withTransaction null
            if (title.tmdbId != null) return@withTransaction title
            if (!title.manualMatch) return@withTransaction attachMatch(titleId, candidate, anilistId, malId)
            val kind = kindFor(candidate.kind, candidate.isAnime, title.kind)
            rekey(title, kind)
            val updated = (titles.title(titleId) ?: title).copy(
                kind = kind,
                tmdbId = candidate.tmdbId,
                tmdbIsMovie = candidate.kind == MetaKind.MOVIE,
                anilistId = anilistId ?: title.anilistId,
                malId = malId ?: title.malId,
                fetchedAt = null,
                metaLanguage = null,
            )
            titles.update(updated)
            titles.clearEpisodeImdbRatings(titleId)
            updated
        }

    /**
     * Fix match to an OMDb choice, while TMDB is off. OMDb has no backdrop, logo or original title to replace those
     * of another work, so they go now, with the rest of what belonged to the old match.
     */
    suspend fun applyManualImdb(titleId: Long, kind: TitleKind, imdbId: String) {
        db.withTransaction {
            val titles = db.titles()
            val meta = db.meta()
            val title = titles.title(titleId) ?: return@withTransaction
            rekey(title, kind)
            val current = titles.title(titleId) ?: title
            val sameTarget = title.tmdbId == null && title.imdbId == imdbId
            if (!sameTarget) {
                meta.deleteTitlePersons(titleId)
                meta.deleteTitleGenres(titleId)
                meta.deleteRatings(titleId)
                meta.deleteSegmentsExcept(titleId, SegmentRules.MANUAL)
                rebuildEpisodes(titleId, kind)
            }
            val cleared = if (sameTarget) {
                current
            } else {
                current.copy(
                    name = current.localName,
                    originalName = null,
                    year = current.localYear,
                    overview = null,
                    runtime = null,
                    anilistId = null,
                    malId = null,
                    coverPath = if (current.manualCover) current.coverPath else null,
                    backdropPath = null,
                    logoPath = null,
                    dominantColor = if (current.manualCover) current.dominantColor else null,
                    coverLuma = if (current.manualCover) current.coverLuma else null,
                    sortKey = Keys.normalize(current.localName),
                    creditsJson = null,
                )
            }
            titles.update(
                cleared.copy(
                    kind = kind,
                    tmdbId = null,
                    tmdbIsMovie = null,
                    imdbId = imdbId,
                    manualMatch = true,
                    matchState = MatchState.MANUAL,
                    matchConfidence = 1.0,
                    fetchedAt = null,
                    metaLanguage = null,
                    collectionId = null,
                ),
            )
            meta.deleteUnusedOmdbPersons()
            meta.upsertQueue(LookupQueue(titleId, LookupState.QUEUED, 0, clock.now(), LookupJob.REFRESH))
            index.index(titleId)
        }
    }

    /** The word anime came from a file name that no longer earns it. A match or a choice made by hand stays. */
    /** [reread] holds the title's files with their parse read again, written before the keys are worked out. */
    suspend fun settleAsSeries(titleId: Long, reread: List<MediaFile> = emptyList()) {
        db.withTransaction {
            val title = db.titles().title(titleId) ?: return@withTransaction
            if (title.kind != TitleKind.ANIME || title.tmdbId != null || title.imdbId != null || title.manualMatch) return@withTransaction
            val scan = db.scan()
            for (file in reread) if (file.titleId == titleId) scan.setReading(file.rowId, file.releaseTags, file.parsedAbsolute)
            rekey(title, TitleKind.SERIES)
        }
    }

    /** Recomputes file keys when a title changes between movie and series, and carries watch state over. */
    private suspend fun rekey(title: Title, kind: TitleKind) {
        if (kind == title.kind) return
        val titles = db.titles()
        val scan = db.scan()
        for (file in scan.filesOfTitle(title.id)) {
            val key = keyOf(file, title.id, kind)
            if (key != file.playableKey) {
                scan.setPlayableKey(file.rowId, key)
                moveWatch(file.playableKey, key, title.id)
            }
        }
        val group = GroupKeys.key(kind, title.groupName, title.localYear)
        if (group != title.groupKey && titles.byGroupKey(group) == null) {
            titles.upsertAlias(GroupAlias(title.groupKey, title.id))
            titles.update((titles.title(title.id) ?: title).copy(groupKey = group, kind = kind))
        } else {
            titles.setKind(title.id, kind)
        }
    }

    private fun keyOf(file: MediaFile, titleId: Long, kind: TitleKind): String = PlayableKeys.of(
        titleId, kind, file.nameKind, file.parsedSeason, file.parsedEpisode, file.parsedAbsolute, file.fileId,
    )

    private suspend fun moveWatch(from: String, to: String, titleId: Long?) {
        if (from == to) return
        val watch = db.watch()
        val old = watch.get(from) ?: return
        watch.delete(from)
        watch.upsert(WatchRules.merge(watch.get(to), old.copy(playableKey = to, titleId = titleId)))
    }

    /** Local Episode rows only hold what the parser read. Provider data comes back with the next fetch. */
    private suspend fun rebuildEpisodes(titleId: Long, kind: TitleKind) {
        val scan = db.scan()
        db.titles().deleteEpisodes(titleId)
        for (file in scan.filesOfTitle(titleId)) {
            val id = resolver.episodeId(titleId, kind, file.nameKind, file.parsedSeason, file.parsedEpisode, file.parsedAbsolute)
            scan.setEpisode(file.rowId, id)
        }
    }

    /** Gives files without an episode link their local row, as after a restore. */
    suspend fun linkMissingEpisodes(titleId: Long, kind: TitleKind) {
        val scan = db.scan()
        for (file in scan.filesOfTitle(titleId)) {
            if (file.episodeId != null) continue
            val id = resolver.episodeId(titleId, kind, file.nameKind, file.parsedSeason, file.parsedEpisode, file.parsedAbsolute)
            if (id != null) scan.setEpisode(file.rowId, id)
        }
    }

    /**
     * Moves one file of an unmatched title onto a clip title of its own, with its progress. A title left with
     * no file stays as a row, like one whose folder was removed, but it must not reach TMDB any more.
     */
    suspend fun makeClip(file: MediaFile, parsed: ParsedName, fallbackName: String) {
        db.withTransaction {
            val titles = db.titles()
            val scan = db.scan()
            val clip = resolver.clip(parsed, fallbackName, file.fileId)
            val old = file.titleId?.let { titles.title(it) }
            val key = PlayableKeys.movie(clip.id)
            scan.setClip(file.rowId, clip.id, key, NameKinds.CLIP)
            moveWatch(file.playableKey, key, clip.id)
            file.framePath?.let { titles.fillFrame(clip.id, it) }
            val color = old?.dominantColor
            val luma = old?.coverLuma
            if (color != null && luma != null) titles.fillColors(clip.id, color, luma)
            if (old != null && scan.filesOfTitle(old.id).isEmpty()) db.meta().deleteQueue(old.id)
        }
    }

    /** Moves everything from [loserId] onto [survivorId]. Must run inside a transaction. */
    private suspend fun merge(loserId: Long, survivorId: Long) {
        val titles = db.titles()
        val scan = db.scan()
        val watch = db.watch()
        val meta = db.meta()
        val loser = titles.title(loserId) ?: return
        val survivor = titles.title(survivorId) ?: return

        for (file in scan.filesOfTitle(loserId)) {
            val episode = file.episodeId?.let { moveEpisode(it, survivorId) }
            scan.relink(file.rowId, survivorId, episode, PlayableKeys.rehome(file.playableKey, loserId, survivorId))
        }
        titles.deleteEpisodes(loserId)

        for (state in watch.ofTitle(loserId)) {
            val key = PlayableKeys.rehome(state.playableKey, loserId, survivorId)
            mergeWatch(state, key, survivorId)
        }

        val loserPrefs = watch.seriesPrefs(loserId)
        if (loserPrefs != null) {
            if (watch.seriesPrefs(survivorId) == null) watch.upsertSeriesPrefs(loserPrefs.copy(titleId = survivorId))
            watch.deleteSeriesPrefs(loserId)
        }

        // Stored skip times hang on episode rows that just went away, so only the owner's own times move.
        meta.deleteSegmentsExcept(loserId, SegmentRules.MANUAL)
        meta.moveSegments(loserId, survivorId)
        titles.upsertAlias(GroupAlias(loser.groupKey, survivorId))
        titles.repointAliases(loserId, survivorId)
        if (loser.manualCover && !survivor.manualCover) {
            titles.update(
                survivor.copy(
                    coverPath = loser.coverPath,
                    manualCover = true,
                    dominantColor = loser.dominantColor,
                    coverLuma = loser.coverLuma,
                ),
            )
        }
        if (loser.favorite) titles.setFavorite(survivorId, true)
        // Private files keep pointing at a title that exists, so moving them out finds it.
        val vault = db.vault()
        for (item in vault.ofTitle(loserId)) {
            vault.rehome(item.id, survivorId, PlayableKeys.rehome(item.file.playableKey, loserId, survivorId))
        }
        meta.deleteTitlePersons(loserId)
        meta.deleteTitleGenres(loserId)
        meta.deleteRatings(loserId)
        meta.deleteQueue(loserId)
        titles.delete(loserId)
        titles.deleteIndex(loserId)
    }

    private suspend fun mergeWatch(state: WatchState, key: String, survivorId: Long) {
        val watch = db.watch()
        watch.delete(state.playableKey)
        watch.upsert(WatchRules.merge(watch.get(key), state.copy(playableKey = key, titleId = survivorId)))
    }

    private suspend fun moveEpisode(episodeId: Long, survivorId: Long): Long? {
        val titles = db.titles()
        val episode = titles.episode(episodeId) ?: return null
        titles.episode(survivorId, episode.season, episode.number)?.let { return it.id }
        return titles.insertEpisode(episode.copy(id = 0, titleId = survivorId))
    }
}
