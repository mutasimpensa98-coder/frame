package com.frameglass.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.RatingSource
import com.frameglass.data.api.TitleKind
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM Volume")
    suspend fun volumes(): List<Volume>

    @Upsert
    suspend fun upsertVolume(volume: Volume)

    @Query("SELECT * FROM RootFolder WHERE enabled = 1 ORDER BY volumeUuid, relPath")
    suspend fun roots(): List<RootFolder>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoot(root: RootFolder): Long

    @Query("DELETE FROM RootFolder WHERE volumeUuid = :volume AND relPath = :relPath")
    suspend fun deleteRoot(volume: String, relPath: String)

    @Query("SELECT * FROM RootFolder")
    suspend fun allRoots(): List<RootFolder>

    @Query("UPDATE RootFolder SET enabled = :enabled WHERE id IN (:ids)")
    suspend fun setRootsEnabled(ids: List<Long>, enabled: Boolean)

    @Query("UPDATE RootFolder SET enabled = 1 WHERE volumeUuid = :volume AND relPath = :relPath")
    suspend fun enableRoot(volume: String, relPath: String)

    @Query("SELECT * FROM ExcludedFolder")
    suspend fun excluded(): List<ExcludedFolder>

    @Query(
        "SELECT e.volumeUuid AS volumeUuid, e.relPath AS relPath, v.lastPath AS base FROM ExcludedFolder e " +
            "LEFT JOIN Volume v ON v.uuid = e.volumeUuid ORDER BY e.volumeUuid, e.relPath",
    )
    fun excludedPaths(): Flow<List<ExcludedPath>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExcluded(row: ExcludedFolder)

    @Query("DELETE FROM ExcludedFolder WHERE volumeUuid = :volume AND relPath = :relPath")
    suspend fun deleteExcluded(volume: String, relPath: String)

    @Query(
        "SELECT rowId, fileId, playableKey, relPath, size, mtime, available, hasSidecarSub FROM MediaFile " +
            "WHERE volumeUuid = :volume AND relPath >= :low AND relPath < :high",
    )
    suspend fun cacheRows(volume: String, low: String, high: String): List<CachedFile>

    @Query("SELECT * FROM MediaFile WHERE fileId = :fileId")
    suspend fun byFileId(fileId: String): List<MediaFile>

    @Query("SELECT * FROM MediaFile WHERE rowId = :rowId")
    suspend fun file(rowId: Long): MediaFile?

    @Query("SELECT * FROM MediaFile WHERE volumeUuid = :volume AND relPath = :relPath")
    suspend fun byPath(volume: String, relPath: String): MediaFile?

    @Query("SELECT * FROM MediaFile WHERE playableKey = :key")
    suspend fun byPlayable(key: String): List<MediaFile>

    @Query("SELECT * FROM MediaFile WHERE titleId = :titleId")
    suspend fun filesOfTitle(titleId: Long): List<MediaFile>

    @Query("SELECT * FROM MediaFile WHERE volumeUuid = :volume AND relPath >= :low AND relPath < :high")
    suspend fun filesUnder(volume: String, low: String, high: String): List<MediaFile>

    @Query("SELECT * FROM MediaFile")
    suspend fun allFiles(): List<MediaFile>

    @Insert
    suspend fun insertFile(file: MediaFile): Long

    @Query("UPDATE MediaFile SET available = 1, lastSeenAt = :now WHERE rowId IN (:ids)")
    suspend fun markAvailable(ids: List<Long>, now: Long)

    @Query("UPDATE MediaFile SET available = 0 WHERE rowId IN (:ids)")
    suspend fun markUnavailable(ids: List<Long>)

    @Query("UPDATE MediaFile SET available = 0 WHERE volumeUuid = :volume AND available = 1")
    suspend fun markVolumeUnavailable(volume: String)

    @Query("UPDATE MediaFile SET hasSidecarSub = :value WHERE rowId IN (:ids)")
    suspend fun setSidecar(ids: List<Long>, value: Boolean)

    @Query(
        "UPDATE MediaFile SET volumeUuid = :volume, relPath = :relPath, size = :size, mtime = :mtime, " +
            "available = 1, lastSeenAt = :now, hasSidecarSub = :hasSidecar WHERE rowId = :rowId",
    )
    suspend fun updatePath(rowId: Long, volume: String, relPath: String, size: Long, mtime: Long, now: Long, hasSidecar: Boolean)

    @Query(
        "UPDATE MediaFile SET fileId = :fileId, osHash = :osHash, size = :size, mtime = :mtime, available = 1, " +
            "lastSeenAt = :now, hasSidecarSub = :hasSidecar, playableKey = :playableKey, framePath = NULL, " +
            "enrichState = 0, enrichTries = 0 WHERE rowId = :rowId",
    )
    suspend fun updateContent(
        rowId: Long, fileId: String, osHash: String, size: Long, mtime: Long, now: Long, hasSidecar: Boolean, playableKey: String,
    )

    @Query("UPDATE MediaFile SET titleId = :titleId, episodeId = :episodeId, playableKey = :playableKey WHERE rowId = :rowId")
    suspend fun relink(rowId: Long, titleId: Long, episodeId: Long?, playableKey: String)

    @Query("UPDATE MediaFile SET episodeId = :episodeId WHERE rowId = :rowId")
    suspend fun setEpisode(rowId: Long, episodeId: Long?)

    /** The numbers of a clip were counters and dates, so nothing may read them as an episode again. */
    @Query(
        "UPDATE MediaFile SET titleId = :titleId, episodeId = NULL, playableKey = :playableKey, nameKind = :nameKind, " +
            "parsedSeason = NULL, parsedEpisode = NULL, parsedEpisodeEnd = NULL, parsedAbsolute = NULL WHERE rowId = :rowId",
    )
    suspend fun setClip(rowId: Long, titleId: Long, playableKey: String, nameKind: String)

    @Query("UPDATE MediaFile SET releaseTags = :releaseTags, parsedAbsolute = :parsedAbsolute WHERE rowId = :rowId")
    suspend fun setReading(rowId: Long, releaseTags: String?, parsedAbsolute: Int?)

    @Query("UPDATE MediaFile SET playableKey = :playableKey WHERE rowId = :rowId")
    suspend fun setPlayableKey(rowId: Long, playableKey: String)

    @Query("DELETE FROM MediaFile WHERE rowId IN (:ids)")
    suspend fun deleteFiles(ids: List<Long>)

    // Enrichment. Each row is claimed before work starts, so a native crash cannot loop forever.
    @Query("UPDATE MediaFile SET enrichState = 3 WHERE enrichState = 1 AND enrichTries >= :maxTries")
    suspend fun failStaleClaims(maxTries: Int)

    @Query("UPDATE MediaFile SET enrichState = 0 WHERE enrichState = 1")
    suspend fun releaseClaims()

    /** Real titles go first: matching can wait on them, and a phone can hold thousands of clips. */
    @Query(
        "SELECT * FROM MediaFile WHERE enrichState = 0 AND available = 1 " +
            "ORDER BY nameKind = 'CLIP', firstSeenAt DESC, rowId LIMIT :limit",
    )
    suspend fun nextToEnrich(limit: Int): List<MediaFile>

    @Query("UPDATE MediaFile SET enrichState = 1, enrichTries = enrichTries + 1 WHERE rowId IN (:ids)")
    suspend fun claim(ids: List<Long>)

    @Query(
        "UPDATE MediaFile SET durationMs = :durationMs, width = :width, height = :height, videoCodec = :videoCodec, " +
            "hdrType = :hdrType, audioSummary = :audioSummary, hasEmbeddedSub = :hasEmbeddedSub, framePath = :framePath, " +
            "enrichState = :state WHERE rowId = :rowId",
    )
    suspend fun saveProbe(
        rowId: Long, durationMs: Long, width: Int, height: Int, videoCodec: String?, hdrType: String?,
        audioSummary: String?, hasEmbeddedSub: Boolean, framePath: String?, state: Int,
    )

    @Query("UPDATE MediaFile SET durationMs = :durationMs WHERE rowId = :rowId AND durationMs = 0")
    suspend fun fillDuration(rowId: Long, durationMs: Long)

    @Insert
    suspend fun insertLog(log: ScanLog): Long

    @Query("SELECT * FROM ScanLog WHERE root = :root AND finishedAt IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun openLog(root: String): ScanLog?

    @Query("UPDATE ScanLog SET checkpoint = :checkpoint, counts = :counts WHERE id = :id")
    suspend fun checkpoint(id: Long, checkpoint: String?, counts: String)

    @Query("UPDATE ScanLog SET finishedAt = :finishedAt, stopReason = :stopReason, counts = :counts WHERE id = :id")
    suspend fun finishLog(id: Long, finishedAt: Long, stopReason: String?, counts: String)

    @Query("UPDATE ScanLog SET stopReason = :stopReason WHERE finishedAt IS NULL")
    suspend fun stampOpenLogs(stopReason: String)

    @Query("INSERT INTO ScanLog (startedAt, root, stopReason, finishedAt) VALUES (:now, :root, :stopReason, :now)")
    suspend fun logEvent(now: Long, root: String, stopReason: String)

    @Query("DELETE FROM ScanLog WHERE startedAt < :before")
    suspend fun trimLogs(before: Long)
}

@Dao
interface TitleDao {
    @Query("SELECT * FROM Title WHERE id = :id")
    suspend fun title(id: Long): Title?

    @Query("SELECT * FROM Title")
    suspend fun allTitles(): List<Title>

    @Query("SELECT * FROM Title WHERE groupKey = :key")
    suspend fun byGroupKey(key: String): Title?

    @Query("SELECT t.* FROM Title t JOIN GroupAlias a ON a.titleId = t.id WHERE a.groupKey = :key")
    suspend fun byAlias(key: String): Title?

    @Query("SELECT * FROM Title WHERE groupName = :name")
    suspend fun byGroupName(name: String): List<Title>

    @Query("SELECT * FROM Title WHERE tmdbId = :tmdbId AND tmdbIsMovie = :isMovie AND id != :exclude ORDER BY id LIMIT 1")
    suspend fun byTmdb(tmdbId: Long, isMovie: Boolean, exclude: Long): Title?

    /** A title TMDB matched comes first, because it holds more than OMDb can give. */
    @Query("SELECT * FROM Title WHERE imdbId = :imdbId AND id != :exclude ORDER BY (tmdbId IS NULL), id LIMIT 1")
    suspend fun byImdb(imdbId: String, exclude: Long): Title?

    @Query("SELECT COUNT(*) FROM Title WHERE imdbId = :imdbId AND id != :exclude")
    suspend fun countByImdb(imdbId: String, exclude: Long): Int

    @Query("UPDATE Title SET omdbTriedAt = :at WHERE id = :id")
    suspend fun setOmdbTried(id: Long, at: Long)

    /** OMDb's episode ratings belong to an OMDb match. Once TMDB holds the title they would never be refreshed. */
    @Query("UPDATE Episode SET imdbRating = NULL WHERE titleId = :titleId AND imdbRating IS NOT NULL")
    suspend fun clearEpisodeImdbRatings(titleId: Long)

    @Insert
    suspend fun insert(title: Title): Long

    @Update
    suspend fun update(title: Title)

    @Query("UPDATE Title SET kind = :kind WHERE id = :id")
    suspend fun setKind(id: Long, kind: TitleKind)

    @Query("UPDATE Title SET removed = :removed WHERE id = :id")
    suspend fun setRemoved(id: Long, removed: Boolean)

    @Query("UPDATE Title SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE Title SET watchlistAt = :at WHERE id = :id")
    suspend fun setWatchlist(id: Long, at: Long?)

    @Query("UPDATE Title SET matchState = :state WHERE id = :id")
    suspend fun setMatchState(id: Long, state: MatchState)

    @Query("UPDATE Title SET metaLanguage = :language WHERE id = :id")
    suspend fun setMetaLanguage(id: Long, language: String)

    @Query("UPDATE Title SET framePath = :path WHERE id = :id AND framePath IS NULL")
    suspend fun fillFrame(id: Long, path: String)

    /** A title's colours come from its frame only until a cover exists. */
    @Query("UPDATE Title SET dominantColor = :color, coverLuma = :luma WHERE id = :id AND coverPath IS NULL AND dominantColor IS NULL")
    suspend fun fillColors(id: Long, color: Int, luma: Int)

    @Query("UPDATE Title SET dominantColor = :color, coverLuma = :luma WHERE id = :id AND manualCover = 0")
    suspend fun setColors(id: Long, color: Int?, luma: Int?)

    @Query("DELETE FROM Title WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "SELECT id, kind, name, year, coverPath, framePath, backdropPath, logoPath, dominantColor, collectionId, " +
            "matchState, sortKey, createdAt, favorite, seriesStatus, nextAirDate, nextSeason, nextEpisode, watchlistAt FROM Title " +
            "WHERE removed = 0",
    )
    suspend fun titleRows(): List<TitleRow>

    @Query(
        "SELECT id, kind, name, year, coverPath, framePath, backdropPath, logoPath, dominantColor, collectionId, " +
            "matchState, sortKey, createdAt, favorite, seriesStatus, nextAirDate, nextSeason, nextEpisode, watchlistAt FROM Title " +
            "WHERE removed = 1",
    )
    suspend fun hiddenRows(): List<TitleRow>

    @Query("SELECT id, titleId, season, number, name, stillPath, runtime, airDate, fetchedAt, imdbRating FROM Episode")
    suspend fun episodeRows(): List<EpisodeLite>

    @Query(
        "SELECT id, titleId, season, number, name, stillPath, runtime, airDate, fetchedAt, imdbRating FROM Episode " +
            "WHERE titleId = :titleId",
    )
    suspend fun episodeRows(titleId: Long): List<EpisodeLite>

    @Query("SELECT * FROM Episode WHERE titleId = :titleId")
    suspend fun episodes(titleId: Long): List<Episode>

    @Query("SELECT * FROM Episode WHERE id = :id")
    suspend fun episode(id: Long): Episode?

    @Query("SELECT * FROM Episode WHERE titleId = :titleId AND season = :season AND number = :number")
    suspend fun episode(titleId: Long, season: Int, number: Int): Episode?

    @Insert
    suspend fun insertEpisode(episode: Episode): Long

    @Update
    suspend fun updateEpisode(episode: Episode)

    @Query("DELETE FROM Episode WHERE titleId = :titleId")
    suspend fun deleteEpisodes(titleId: Long)

    @Query("DELETE FROM Episode WHERE id = :id")
    suspend fun deleteEpisode(id: Long)

    @Query("SELECT COUNT(*) FROM MediaFile WHERE episodeId = :episodeId")
    suspend fun filesOfEpisode(episodeId: Long): Int

    @Query("SELECT DISTINCT e.season FROM Episode e JOIN MediaFile f ON f.episodeId = e.id WHERE e.titleId = :titleId")
    suspend fun ownedSeasons(titleId: Long): List<Int>

    /** Seasons where a file waits for its episode data, so new files cost one call per season. */
    @Query(
        "SELECT DISTINCT e.season FROM Episode e JOIN MediaFile f ON f.episodeId = e.id " +
            "WHERE e.titleId = :titleId AND e.fetchedAt IS NULL",
    )
    suspend fun seasonsWaiting(titleId: Long): List<Int>

    @Upsert
    suspend fun upsertAlias(alias: GroupAlias)

    @Query("UPDATE GroupAlias SET titleId = :to WHERE titleId = :from")
    suspend fun repointAliases(from: Long, to: Long)

    @Query("SELECT * FROM GroupAlias")
    suspend fun aliases(): List<GroupAlias>

    @Insert
    suspend fun insertIndex(row: SearchIndex)

    @Query("DELETE FROM SearchIndex WHERE rowid = :titleId")
    suspend fun deleteIndex(titleId: Long)

    @Query("SELECT rowid FROM SearchIndex WHERE SearchIndex MATCH :match LIMIT :limit")
    suspend fun searchIds(match: String, limit: Int): List<Long>
}

@Dao
interface WatchDao {
    @Query("SELECT * FROM WatchState WHERE playableKey = :key")
    suspend fun get(key: String): WatchState?

    @Query("SELECT * FROM WatchState")
    suspend fun all(): List<WatchState>

    @Query("SELECT * FROM WatchState WHERE titleId = :titleId")
    suspend fun ofTitle(titleId: Long): List<WatchState>

    @Upsert
    suspend fun upsert(state: WatchState)

    @Query("DELETE FROM WatchState WHERE playableKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM SeriesPrefs WHERE titleId = :titleId")
    suspend fun seriesPrefs(titleId: Long): SeriesPrefs?

    @Query("SELECT * FROM SeriesPrefs")
    suspend fun allSeriesPrefs(): List<SeriesPrefs>

    @Upsert
    suspend fun upsertSeriesPrefs(prefs: SeriesPrefs)

    @Query("DELETE FROM SeriesPrefs WHERE titleId = :titleId")
    suspend fun deleteSeriesPrefs(titleId: Long)

    @Query("SELECT * FROM FilePrefs WHERE fileId = :fileId")
    suspend fun filePrefs(fileId: String): FilePrefs?

    @Query("SELECT * FROM FilePrefs")
    suspend fun allFilePrefs(): List<FilePrefs>

    @Upsert
    suspend fun upsertFilePrefs(prefs: FilePrefs)

    @Query("DELETE FROM FilePrefs WHERE fileId = :fileId")
    suspend fun deleteFilePrefs(fileId: String)

    @Query("SELECT * FROM AudioDeviceDelay WHERE deviceKey = :key")
    suspend fun deviceDelay(key: String): AudioDeviceDelay?

    @Query("SELECT * FROM AudioDeviceDelay")
    suspend fun allDeviceDelays(): List<AudioDeviceDelay>

    @Upsert
    suspend fun upsertDeviceDelay(delay: AudioDeviceDelay)

    @Query("SELECT * FROM PlaySession WHERE id = 1")
    suspend fun session(): PlaySession?

    @Upsert
    suspend fun upsertSession(session: PlaySession)

    @Query("DELETE FROM PlaySession")
    suspend fun clearSession()

    @Query("SELECT * FROM WatchLog WHERE day = :day AND titleId = :titleId")
    suspend fun logRow(day: String, titleId: Long): WatchLog?

    @Upsert
    suspend fun upsertLog(row: WatchLog)

    @Query("SELECT * FROM WatchLog")
    suspend fun allLog(): List<WatchLog>

    @Query("SELECT * FROM WatchLog WHERE titleId = :titleId")
    suspend fun logOfTitle(titleId: Long): List<WatchLog>

    @Query("DELETE FROM WatchLog WHERE titleId = :titleId")
    suspend fun deleteLogOfTitle(titleId: Long)
}

@Dao
interface MetaDao {
    @Query("SELECT COUNT(*) FROM LookupQueue")
    fun pendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM LookupQueue WHERE retryAt <= :now")
    suspend fun dueCount(now: Long): Int

    /** A match waits for the file's duration, because a short video with a plain name must not reach TMDB. */
    @Query(
        "SELECT q.* FROM LookupQueue q WHERE q.retryAt <= :now AND NOT (q.job = 'MATCH' AND EXISTS (" +
            "SELECT 1 FROM MediaFile f WHERE f.titleId = q.titleId AND f.available = 1 AND f.durationMs = 0 " +
            "AND f.enrichState IN (0, 1))) ORDER BY q.retryAt, q.titleId LIMIT 1",
    )
    suspend fun nextDue(now: Long): LookupQueue?

    @Query("SELECT MIN(retryAt) FROM LookupQueue")
    suspend fun earliestRetry(): Long?

    @Query("SELECT * FROM LookupQueue WHERE titleId = :titleId")
    suspend fun queued(titleId: Long): LookupQueue?

    @Upsert
    suspend fun upsertQueue(row: LookupQueue)

    @Query("DELETE FROM LookupQueue WHERE titleId = :titleId")
    suspend fun deleteQueue(titleId: Long)

    /** A new key makes every parked row due again. */
    @Query("UPDATE LookupQueue SET retryAt = :now WHERE retryAt > :now")
    suspend fun makeAllDue(now: Long)

    /** A swipe to refresh fetches these again. */
    @Query("SELECT id FROM Title WHERE removed = 0 AND tmdbId IS NOT NULL AND (fetchedAt IS NULL OR fetchedAt <= :before)")
    suspend fun matchedFetchedBefore(before: Long): List<Long>

    /** Series whose announced next episode has aired by [today], a yyyy-MM-dd date. */
    @Query(
        "SELECT id FROM Title WHERE removed = 0 AND tmdbId IS NOT NULL AND nextAirDate IS NOT NULL AND nextAirDate <= :today",
    )
    suspend fun nextEpisodeAiredBy(today: String): List<Long>

    /** Titles matched only through OMDb, which hold an IMDb id and no TMDB id, as ImdbRules.omdbOnly says. */
    @Query(
        "SELECT t.id FROM Title t WHERE t.removed = 0 AND t.tmdbId IS NULL AND t.imdbId IS NOT NULL " +
            "AND (t.fetchedAt IS NULL OR t.fetchedAt <= :before) AND EXISTS (SELECT 1 FROM MediaFile f WHERE f.titleId = t.id)",
    )
    suspend fun omdbOnlyFetchedBefore(before: Long): List<Long>

    /** TMDB is back, so these fill in what waited for it. */
    @Query(
        "SELECT t.id FROM Title t WHERE t.removed = 0 AND t.tmdbId IS NOT NULL AND t.fetchedAt IS NULL " +
            "AND EXISTS (SELECT 1 FROM MediaFile f WHERE f.titleId = t.id)",
    )
    suspend fun matchedNeverFetched(): List<Long>

    @Query(
        "SELECT DISTINCT t.id FROM Title t JOIN Episode e ON e.titleId = t.id JOIN MediaFile f ON f.episodeId = e.id " +
            "WHERE t.removed = 0 AND t.tmdbId IS NOT NULL AND t.tmdbIsMovie IS NOT 1 AND e.fetchedAt IS NULL",
    )
    suspend fun matchedWithEpisodesWaiting(): List<Long>

    /** The ratings skipped while OMDb's day was spent. */
    @Query(
        "SELECT t.id FROM Title t WHERE t.removed = 0 AND t.tmdbId IS NOT NULL AND t.imdbId IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM MediaFile f WHERE f.titleId = t.id) " +
            "AND NOT EXISTS (SELECT 1 FROM Rating r WHERE r.titleId = t.id AND r.source = 'IMDB')",
    )
    suspend fun imdbIdWithoutRating(): List<Long>

    /** A new OMDb key fetches these again. A title with no file left waits until it has one. */
    @Query(
        "SELECT t.id FROM Title t WHERE t.removed = 0 AND t.tmdbId IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM MediaFile f WHERE f.titleId = t.id) " +
            "AND NOT EXISTS (SELECT 1 FROM Rating r WHERE r.titleId = t.id AND r.source = 'IMDB')",
    )
    suspend fun matchedWithoutImdb(): List<Long>

    /** A new app language fetches these again. A title with no file left waits until it has one. */
    @Query(
        "SELECT t.id FROM Title t WHERE t.removed = 0 AND t.tmdbId IS NOT NULL " +
            "AND (t.metaLanguage IS NULL OR t.metaLanguage != :language) " +
            "AND EXISTS (SELECT 1 FROM MediaFile f WHERE f.titleId = t.id)",
    )
    suspend fun matchedNotIn(language: String): List<Long>

    /** A swipe to refresh searches for these again. Clips are UNMATCHED, so they stay out. */
    @Query("SELECT id, matchState, omdbTriedAt FROM Title WHERE removed = 0 AND tmdbId IS NULL AND matchState = 'NONE_FOUND'")
    suspend fun notFoundTitles(): List<UnsettledTitle>

    /** TMDB is back, so it gets a try at what OMDb could not settle. Clips are UNMATCHED, so they stay out. */
    @Query(
        "SELECT id FROM Title WHERE removed = 0 AND tmdbId IS NULL AND imdbId IS NULL " +
            "AND matchState IN ('NONE_FOUND', 'NEEDS_CHOICE')",
    )
    suspend fun unsettledTitles(): List<Long>

    @Query("SELECT * FROM Title WHERE fetchedAt IS NOT NULL AND fetchedAt <= :before")
    suspend fun titlesFetchedBefore(before: Long): List<Title>

    /** F-M11 is TMDB's rule, so episodes of a title matched only through OMDb stay out. */
    @Query(
        "SELECT e.* FROM Episode e JOIN Title t ON t.id = e.titleId WHERE e.fetchedAt IS NOT NULL AND e.fetchedAt <= :before " +
            "AND NOT (t.tmdbId IS NULL AND t.imdbId IS NOT NULL)",
    )
    suspend fun episodesFetchedBefore(before: Long): List<Episode>

    @Query("SELECT * FROM Person WHERE fetchedAt IS NOT NULL AND fetchedAt <= :before")
    suspend fun personsFetchedBefore(before: Long): List<Person>

    @Query("SELECT * FROM Rating WHERE fetchedAt <= :before")
    suspend fun ratingsFetchedBefore(before: Long): List<Rating>

    /** F-M11 is TMDB's rule. An IMDb rating is not TMDB content. */
    @Query("DELETE FROM Rating WHERE source = 'TMDB' AND fetchedAt <= :before")
    suspend fun deleteTmdbRatingsBefore(before: Long)

    @Query("SELECT fetchedAt FROM Rating WHERE titleId = :titleId AND source = :source")
    suspend fun ratingFetchedAt(titleId: Long, source: RatingSource): Long?

    @Upsert
    suspend fun upsertPerson(person: Person)

    @Query("SELECT * FROM Person WHERE id = :id")
    suspend fun person(id: Long): Person?

    @Query("SELECT * FROM Person")
    suspend fun allPersons(): List<Person>

    @Query("DELETE FROM Person WHERE id = :id")
    suspend fun deletePerson(id: Long)

    /** OMDb's cast, under ImdbRules.personId, once no title names them. */
    @Query("DELETE FROM Person WHERE id < 0 AND id NOT IN (SELECT personId FROM TitlePerson)")
    suspend fun deleteUnusedOmdbPersons()

    @Upsert
    suspend fun upsertTitlePerson(row: TitlePerson)

    @Query("DELETE FROM TitlePerson WHERE titleId = :titleId")
    suspend fun deleteTitlePersons(titleId: Long)

    @Query("DELETE FROM TitlePerson WHERE personId = :personId")
    suspend fun deletePersonLinks(personId: Long)

    @Query(
        "SELECT p.id AS personId, p.name AS name, tp.character AS character, p.photoPath AS photoPath " +
            "FROM TitlePerson tp JOIN Person p ON p.id = tp.personId WHERE tp.titleId = :titleId ORDER BY tp.castOrder",
    )
    suspend fun cast(titleId: Long): List<CastRow>

    @Query("SELECT p.name FROM TitlePerson tp JOIN Person p ON p.id = tp.personId WHERE tp.titleId = :titleId")
    suspend fun peopleNames(titleId: Long): List<String>

    @Query("SELECT titleId FROM TitlePerson WHERE personId = :personId")
    suspend fun titlesOfPerson(personId: Long): List<Long>

    @Query(
        "SELECT id AS personId, name, NULL AS character, photoPath FROM Person " +
            "WHERE nameKey LIKE :like ORDER BY name LIMIT :limit",
    )
    suspend fun searchPeople(like: String, limit: Int): List<CastRow>

    @Query("SELECT * FROM Genre WHERE name = :name")
    suspend fun genreByName(name: String): Genre?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenre(genre: Genre): Long

    @Upsert
    suspend fun upsertTitleGenre(row: TitleGenre)

    @Query("DELETE FROM TitleGenre WHERE titleId = :titleId")
    suspend fun deleteTitleGenres(titleId: Long)

    @Query("SELECT tg.titleId AS titleId, g.name AS name FROM TitleGenre tg JOIN Genre g ON g.id = tg.genreId")
    suspend fun genreLinks(): List<GenreLink>

    @Query("SELECT g.name FROM TitleGenre tg JOIN Genre g ON g.id = tg.genreId WHERE tg.titleId = :titleId")
    suspend fun genreNames(titleId: Long): List<String>

    @Upsert
    suspend fun upsertCollection(collection: CollectionEntity)

    @Query("SELECT * FROM Collection")
    suspend fun collections(): List<CollectionEntity>

    @Query("SELECT * FROM Collection WHERE id = :id")
    suspend fun collection(id: Long): CollectionEntity?

    @Upsert
    suspend fun upsertRating(rating: Rating)

    @Query("SELECT titleId, source, value FROM Rating")
    suspend fun ratingRows(): List<RatingRow>

    @Query("SELECT value FROM Rating WHERE titleId = :titleId AND source = :source")
    suspend fun rating(titleId: Long, source: RatingSource): Double?

    @Query("DELETE FROM Rating WHERE titleId = :titleId")
    suspend fun deleteRatings(titleId: Long)

    @Query("SELECT * FROM IntroSegment WHERE titleId = :titleId")
    suspend fun segments(titleId: Long): List<IntroSegment>

    @Query("SELECT * FROM IntroSegment WHERE source = :source")
    suspend fun segmentsBySource(source: String): List<IntroSegment>

    @Insert
    suspend fun insertSegment(segment: IntroSegment): Long

    @Query("DELETE FROM IntroSegment WHERE titleId = :titleId AND source != :keepSource")
    suspend fun deleteSegmentsExcept(titleId: Long, keepSource: String)

    @Query("DELETE FROM IntroSegment WHERE titleId = :titleId AND season IS :season AND source = :source")
    suspend fun deleteSeasonSegments(titleId: Long, season: Int?, source: String)

    @Query("DELETE FROM IntroSegment WHERE titleId = :titleId AND episodeId IS :episodeId AND source = :source")
    suspend fun deleteEpisodeSegments(titleId: Long, episodeId: Long?, source: String)

    @Query("UPDATE IntroSegment SET titleId = :to, episodeId = NULL WHERE titleId = :from")
    suspend fun moveSegments(from: Long, to: Long)

    @Query("SELECT * FROM AnimeMap WHERE anilistId = :anilistId AND srcRange = :src")
    suspend fun animeMap(anilistId: Long, src: String): AnimeMap?

    @Upsert
    suspend fun upsertAnimeMap(row: AnimeMap)
}

/** The private folder. Only the vault and the backup filter use this. */
@Dao
interface VaultDao {
    @Insert
    suspend fun insert(item: VaultItem): Long

    @Insert
    suspend fun insertSidecars(rows: List<VaultSidecar>)

    @Query("SELECT * FROM VaultItem WHERE state = 0 ORDER BY id")
    fun storedFlow(): Flow<List<VaultItem>>

    @Query("SELECT * FROM VaultItem WHERE state = 0 ORDER BY id")
    suspend fun stored(): List<VaultItem>

    @Query("SELECT * FROM VaultItem WHERE id = :id AND state = 0")
    suspend fun item(id: Long): VaultItem?

    @Query("SELECT * FROM VaultItem WHERE state != 0 OR tempPath IS NOT NULL")
    suspend fun unfinished(): List<VaultItem>

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(f_size), 0) AS bytes FROM VaultItem WHERE state = 0")
    fun totals(): Flow<VaultTotals>

    @Query("SELECT * FROM VaultItem WHERE f_titleId = :titleId")
    suspend fun ofTitle(titleId: Long): List<VaultItem>

    @Query("SELECT COUNT(*) FROM VaultItem WHERE f_titleId = :titleId")
    suspend fun countOfTitle(titleId: Long): Int

    @Query("SELECT DISTINCT f_titleId FROM VaultItem WHERE f_titleId IS NOT NULL")
    suspend fun titleIds(): List<Long>

    @Query("SELECT DISTINCT f_fileId FROM VaultItem")
    suspend fun fileIds(): List<String>

    @Query("UPDATE VaultItem SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: Int)

    @Query("UPDATE VaultItem SET tempPath = :path WHERE id = :id")
    suspend fun setTemp(id: Long, path: String?)

    @Query(
        "UPDATE VaultItem SET watchPositionMs = :positionMs, watchDurationMs = :durationMs, watched = :watched, " +
            "lastPlayedAt = :lastPlayedAt WHERE id = :id",
    )
    suspend fun saveWatch(id: Long, positionMs: Long, durationMs: Long, watched: Boolean, lastPlayedAt: Long)

    /** A merge removed the title. Episode rows moved with it, so the link is worked out again on the way out. */
    @Query("UPDATE VaultItem SET f_titleId = :titleId, f_episodeId = NULL, f_playableKey = :playableKey WHERE id = :id")
    suspend fun rehome(id: Long, titleId: Long, playableKey: String)

    @Query("DELETE FROM VaultItem WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM VaultSidecar WHERE itemId = :itemId")
    suspend fun sidecars(itemId: Long): List<VaultSidecar>

    @Query("DELETE FROM VaultSidecar WHERE itemId = :itemId")
    suspend fun deleteSidecars(itemId: Long)
}
