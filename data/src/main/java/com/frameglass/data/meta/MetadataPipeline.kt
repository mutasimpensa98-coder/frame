package com.frameglass.data.meta

import android.app.LocaleManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.withTransaction
import com.frameglass.data.api.MatchChoice
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.RatingSource
import com.frameglass.data.api.RefreshSkip
import com.frameglass.data.api.SegmentKind
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.AnimeMap
import com.frameglass.data.db.CollectionEntity
import com.frameglass.data.db.Episode
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.Genre
import com.frameglass.data.db.IntroSegment
import com.frameglass.data.db.LookupJob
import com.frameglass.data.db.LookupQueue
import com.frameglass.data.db.LookupState
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.Person
import com.frameglass.data.db.Rating
import com.frameglass.data.db.Title
import com.frameglass.data.db.TitleGenre
import com.frameglass.data.db.TitlePerson
import com.frameglass.data.logic.CachePolicy
import com.frameglass.data.logic.ClipRules
import com.frameglass.data.logic.Clock
import com.frameglass.data.logic.ImdbRules
import com.frameglass.data.logic.MetaLanguage
import com.frameglass.data.logic.MetaRoute
import com.frameglass.data.logic.MetaRoutes
import com.frameglass.data.logic.ParsePaths
import com.frameglass.data.logic.RetryPolicy
import com.frameglass.data.logic.SegmentRules
import com.frameglass.data.repo.NetConfigImpl
import com.frameglass.data.repo.SettingsStore
import com.frameglass.data.scan.CoverColors
import com.frameglass.data.scan.ImageStore
import com.frameglass.net.EpisodeMeta
import com.frameglass.net.ImdbMetadata
import com.frameglass.net.MatchCandidate
import com.frameglass.net.MatchOutcome
import com.frameglass.net.MetaKind
import com.frameglass.net.MetadataClient
import com.frameglass.net.NetResult
import com.frameglass.net.OMDB_DAILY_LIMIT
import com.frameglass.net.OmdbCandidate
import com.frameglass.net.OmdbEpisode
import com.frameglass.net.OmdbTitle
import com.frameglass.net.PersonMeta
import com.frameglass.net.TitleMeta
import com.frameglass.net.isDailyLimit
import com.frameglass.parser.NameKind
import com.frameglass.parser.NameParser
import com.frameglass.parser.ParsedName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Serializable
internal data class CreditJson(val job: String, val name: String)

internal object Credits {
    private val serializer = ListSerializer(CreditJson.serializer())

    fun encode(items: List<CreditJson>): String? = if (items.isEmpty()) null else Json.encodeToString(serializer, items)

    fun decode(text: String?): List<Pair<String, String>> {
        if (text == null) return emptyList()
        return runCatching { Json.decodeFromString(serializer, text) }.getOrDefault(emptyList()).map { it.job to it.name }
    }
}

internal class NetworkCheck(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * The language the app shows. A language picked for the app lives in LocaleManager, which answers for any context,
 * while the application context may still carry the phone's language in a process with no activity.
 */
internal class AppLanguage(context: Context) {
    private val locales = context.getSystemService(LocaleManager::class.java)

    fun current(): String {
        val chosen = locales.applicationLocales
        val list = if (chosen.isEmpty) locales.systemLocales else chosen
        return MetaLanguage.of(list[0]?.toLanguageTag())
    }
}

/** One episode to ask for skip times. */
internal data class SegmentTarget(val episodeId: Long, val season: Int, val number: Int, val durationMs: Long)

/**
 * Works through LookupQueue one title at a time. Each finished step is saved, so a stop anywhere loses at
 * most the title in hand. Manual choices are re-read inside the write transaction, so a background job
 * cannot overwrite a match or cover the owner set while its request was in flight.
 *
 * TMDB answers while it is usable. Otherwise OMDb matches and fills titles, on a daily allowance: once OMDb says the
 * day is spent, nothing more goes to it until the next day, and the rows that need it wait in the queue till then.
 */
internal class MetadataPipeline(
    private val db: FrameDb,
    private val clientProvider: Lazy<MetadataClient>,
    private val config: NetConfigImpl,
    private val settings: SettingsStore,
    private val images: ImageStore,
    private val ops: TitleOps,
    private val index: SearchIndexer,
    private val clock: Clock,
    private val network: NetworkCheck,
    private val language: AppLanguage,
) {
    private val lock = Mutex()
    private val client get() = clientProvider.value

    /** The one client implements it. Were it ever missing, nothing would go to OMDb. */
    private val imdb get() = client as? ImdbMetadata
    private val titles get() = db.titles()
    private val meta get() = db.meta()

    @Volatile
    private var imdbPausedUntil: Long? = null

    private val updated = AtomicInteger()

    /** Matched titles that got fresh data from a service since the process started, whichever run did it. */
    val updatedTitles: Int get() = updated.get()

    /**
     * Returns how long to wait before the next run, or null when nothing can run later. No key or Online off
     * parks the rows until the owner changes that. No network returns 0, for a job that waits for the network.
     */
    suspend fun drain(budgetMs: Long = BUDGET_MS): Long? = lock.withLock { drainLocked(deadlineAfter(budgetMs)) }

    /**
     * A swipe's share of the queue. Waiting for a run already in progress counts against [budgetMs], because that run
     * is draining the same rows and a swipe must end on time.
     */
    suspend fun drainWithin(budgetMs: Long) {
        val deadline = deadlineAfter(budgetMs)
        if (!lock.lockWithin(budgetMs)) return
        try {
            drainLocked(deadline)
        } finally {
            lock.unlock()
        }
    }

    private fun deadlineAfter(budgetMs: Long): Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs)

    private suspend fun drainLocked(deadline: Long): Long? {
        config.awaitLoaded()
        if (!config.tmdbUsable && !config.omdbUsable) return null
        resumeImdbIfDue()
        while (System.nanoTime() < deadline) {
            currentCoroutineContext().ensureActive()
            if (!network.isOnline()) return 0
            val row = meta.nextDue(clock.now()) ?: break
            if (process(row)) break
        }
        return meta.earliestRetry()?.let { (it - clock.now()).coerceAtLeast(MIN_RERUN_MS) }
    }

    /** Why no details could be fetched now, or null when they can. */
    suspend fun blocker(): RefreshSkip? {
        config.awaitLoaded()
        return when {
            !config.onlineEnabled -> RefreshSkip.ONLINE_OFF
            !config.tmdbUsable && !config.omdbUsable -> RefreshSkip.NO_SERVICE
            !network.isOnline() -> RefreshSkip.NO_NETWORK
            else -> null
        }
    }

    /** Rows due now, including matches still waiting for a file's duration. */
    suspend fun titlesDue(): Int = meta.dueCount(clock.now())

    /** A swipe to refresh: titles fetched a while ago come again, and titles with no match search again. */
    suspend fun queueRefresh() {
        config.awaitLoaded()
        val now = clock.now()
        meta.matchedFetchedBefore(now - REFRESH_GAP_MS).forEach { queueFetch(it, now) }
        meta.omdbOnlyFetchedBefore(now - ImdbRules.FRESH_MS).forEach { queueFetch(it, now) }
        // While only OMDb can match, a title it could not settle this week would spend calls on the same answer.
        val tmdb = config.tmdbUsable
        meta.notFoundTitles()
            .filter { tmdb || !ImdbRules.omdbGaveUp(it.matchState, it.omdbTriedAt, now) }
            .forEach { queueOnce(it.id, LookupJob.MATCH, now) }
    }

    /**
     * TMDB is on again, or has a key now. Titles matched through OMDb look for their TMDB record, and what waited for
     * TMDB is queued: titles never fetched, episode files with no data, and titles OMDb could not settle. The settings
     * are read from the store, because the switch that brought this was saved a moment ago.
     */
    suspend fun queueTmdbReturn() {
        config.awaitLoaded()
        if (!config.tmdbUsableIn(settings.current())) return
        val now = clock.now()
        meta.omdbOnlyFetchedBefore(Long.MAX_VALUE).forEach { queueFetch(it, now) }
        meta.matchedNeverFetched().forEach { queueFetch(it, now) }
        meta.matchedWithEpisodesWaiting().forEach { queueOnce(it, LookupJob.EPISODES, now) }
        meta.unsettledTitles().forEach { queueOnce(it, LookupJob.MATCH, now) }
    }

    /** OMDb is usable now: matched titles with no IMDb rating fetch again, however recently they were fetched. */
    suspend fun queueMissingImdb() {
        config.awaitLoaded()
        if (!config.omdbUsableIn(settings.current())) return
        val now = clock.now()
        meta.matchedWithoutImdb().forEach { queueFetch(it, now) }
    }

    /**
     * Matched titles fetched in another language come again, however recently they were fetched. Each title records
     * its own language, so a kill part way loses nothing and a title already done is not fetched twice. Returns how
     * many titles still wait.
     */
    suspend fun queueLanguageRefresh(): Int {
        config.awaitLoaded()
        // Only TMDB speaks the app's language. OMDb has English alone.
        if (!config.tmdbUsable) return 0
        val now = clock.now()
        val stale = meta.matchedNotIn(language.current())
        stale.forEach { queueFetch(it, now) }
        return stale.size
    }

    private suspend fun queueOnce(titleId: Long, job: LookupJob, now: Long) {
        if (meta.queued(titleId) == null) meta.upsertQueue(LookupQueue(titleId, LookupState.QUEUED, 0, now, job))
    }

    /** A queued EPISODES job stops short of the title's own text and ratings, so it gives way. */
    private suspend fun queueFetch(titleId: Long, now: Long) {
        val queued = meta.queued(titleId)
        if (queued == null || queued.job == LookupJob.EPISODES) {
            meta.upsertQueue(LookupQueue(titleId, LookupState.QUEUED, 0, now, LookupJob.REFRESH))
        }
    }

    /** Returns true when the whole queue should stop for now. */
    private suspend fun process(row: LookupQueue): Boolean {
        val title = titles.title(row.titleId)
        if (title == null || title.removed || isPrivateOnly(title.id)) {
            meta.deleteQueue(row.titleId)
            return false
        }
        val gaveUp = ImdbRules.omdbGaveUp(title.matchState, title.omdbTriedAt, clock.now())
        val route = MetaRoutes.of(row.job, title.tmdbId, title.imdbId, config.tmdbUsable, config.omdbUsable, gaveUp)
        if (route == MetaRoute.WAIT) return true
        if (route.omdb && imdbPaused()) {
            meta.upsertQueue(row.copy(state = LookupState.RETRY, retryAt = pausedUntil()))
            return false
        }
        var local = false
        val failure = when (route) {
            MetaRoute.TMDB_FETCH -> fetch(title)
            MetaRoute.TMDB_EPISODES -> episodes(title)
            MetaRoute.TMDB_MATCH, MetaRoute.OMDB_MATCH -> when {
                staysLocal(title) -> {
                    titles.setMatchState(title.id, MatchState.UNMATCHED)
                    local = true
                    null
                }
                route == MetaRoute.TMDB_MATCH -> match(title)
                else -> matchOmdb(title)
            }
            MetaRoute.TMDB_UPGRADE -> upgrade(title)
            MetaRoute.OMDB_FETCH -> fetchOmdb(title)
            MetaRoute.OMDB_EPISODES -> omdbEpisodes(title, waitingOnly = true)
            MetaRoute.IMDB_RATING -> imdbRating(title.id, title.imdbId, clock.now())
            MetaRoute.DROP, MetaRoute.WAIT -> null
        }
        if (failure == null) {
            meta.deleteQueue(row.titleId)
            // A match that found nothing, or a merge whose survivor queues again, is not an update yet.
            val fresh = !local && route != MetaRoute.DROP && titles.title(row.titleId)?.isMatched == true
            if (fresh) updated.incrementAndGet()
            return false
        }
        return onFailure(row, failure)
    }

    private suspend fun onFailure(row: LookupQueue, failure: NetResult.Failure): Boolean {
        val now = clock.now()
        if (failure.isDailyLimit()) {
            // Other rows still run, and those that need OMDb are parked to the same time without a call.
            meta.upsertQueue(row.copy(state = LookupState.RETRY, retryAt = pauseImdb()))
            return false
        }
        return when (failure.reason) {
            NetResult.Reason.OFFLINE_SETTING, NetResult.Reason.NO_KEY -> true
            NetResult.Reason.RATE_LIMIT -> {
                meta.upsertQueue(row.copy(state = LookupState.RETRY, retryAt = now + RATE_LIMIT_WAIT_MS))
                true
            }
            NetResult.Reason.NOT_FOUND -> {
                meta.deleteQueue(row.titleId)
                // Gone from TMDB in every language, so the next language check must not ask again.
                titles.setMetaLanguage(row.titleId, language.current())
                false
            }
            NetResult.Reason.NETWORK, NetResult.Reason.BAD_RESPONSE -> {
                // Losing the network mid-run is the offline queue, not a failed attempt.
                if (!network.isOnline()) return true
                val attempts = row.attempts + 1
                meta.upsertQueue(row.copy(state = LookupState.RETRY, attempts = attempts, retryAt = now + RetryPolicy.delayMs(attempts)))
                false
            }
        }
    }

    /** Nothing about a title left only in the private folder goes out, whatever queued it. */
    private suspend fun isPrivateOnly(titleId: Long): Boolean =
        db.vault().countOfTitle(titleId) > 0 && db.scan().filesOfTitle(titleId).isEmpty()

    /** Every automatic lookup passes here, so no clip reaches TMDB or OMDb, whatever queued it. */
    private suspend fun staysLocal(title: Title): Boolean {
        if (title.manualMatch) return false
        val files = db.scan().filesOfTitle(title.id)
        val clipName = files.any { it.nameKind == NameKinds.CLIP }
        val plainNumber = files.all(ClipRules::plainNumber) && episodeCount(title.id) < ClipRules.SERIES_RUN
        return ClipRules.skipsLookup(title.kind, title.localYear, clipName, files.maxOfOrNull { it.durationMs } ?: 0, plainNumber)
    }

    private suspend fun match(title: Title): NetResult.Failure? = when (val result = client.match(parsedFor(title))) {
        is NetResult.Failure -> if (result.reason == NetResult.Reason.NOT_FOUND) {
            titles.setMatchState(title.id, MatchState.NONE_FOUND)
            null
        } else {
            result
        }
        is NetResult.Ok -> applyOutcome(title, result.value)
    }

    private suspend fun applyOutcome(title: Title, outcome: MatchOutcome): NetResult.Failure? {
        val auto = outcome.auto
        if (auto == null) {
            titles.setMatchState(title.id, if (outcome.choices.isEmpty()) MatchState.NONE_FOUND else MatchState.NEEDS_CHOICE)
            return null
        }
        if (auto.kind == MetaKind.MOVIE && episodeCount(title.id) > 1) {
            // Several episodes cannot be one movie, whatever the score says.
            titles.setMatchState(title.id, MatchState.NEEDS_CHOICE)
            return null
        }
        val target = ops.attachMatch(title.id, auto, outcome.anilistId, outcome.malId) ?: return null
        if (target.id != title.id) {
            queueSurvivor(target)
            return null
        }
        return fetch(target)
    }

    /** A merge removed the title in hand and its queue row, so the survivor needs a row of its own. */
    private suspend fun queueSurvivor(target: Title) {
        val job = if (target.fetchedAt == null) LookupJob.REFRESH else LookupJob.EPISODES
        meta.upsertQueue(LookupQueue(target.id, LookupState.QUEUED, 0, clock.now(), job))
    }

    private suspend fun episodeCount(titleId: Long): Int =
        db.scan().filesOfTitle(titleId).filter { it.nameKind != NameKinds.EXTRA }.map { it.playableKey }.distinct().size

    private suspend fun parsedFor(title: Title): ParsedName {
        val scan = db.scan()
        val file = scan.filesOfTitle(title.id).minByOrNull { it.parsedEpisode ?: Int.MAX_VALUE }
        val base = file?.let { f ->
            val root = scan.roots()
                .filter { it.volumeUuid == f.volumeUuid && (it.relPath.isEmpty() || f.relPath.startsWith(it.relPath + "/")) }
                .maxByOrNull { it.relPath.length }
            val path = ParsePaths.forParser(root?.relPath.orEmpty(), f.relPath)
            runCatching { NameParser.parse(path) }.getOrNull()
        }
        val parsed = base ?: ParsedName(
            title = title.localName, year = title.localYear,
            kind = if (title.kind == TitleKind.MOVIE) NameKind.MOVIE else NameKind.EPISODE,
            season = null, episode = null, episodeEnd = null, absolute = null, revision = null,
            animeHint = title.kind == TitleKind.ANIME, subtitled = false, dubbed = false, releaseTags = emptyList(),
        )
        return parsed.copy(title = title.localName, year = title.localYear)
    }

    private suspend fun fetch(title: Title): NetResult.Failure? {
        val tmdbId = title.tmdbId ?: return null
        val isMovie = title.tmdbIsMovie ?: (title.kind == TitleKind.MOVIE)
        // Recorded as asked for, so a language change during the request leaves this title due again.
        val asked = language.current()
        val details = when (val r = client.details(if (isMovie) MetaKind.MOVIE else MetaKind.TV, tmdbId, asked)) {
            is NetResult.Ok -> r.value
            is NetResult.Failure -> return r
        }
        val stamp = clock.now()
        val dir = images.titleDir(title.id)
        var failure: NetResult.Failure? = null
        suspend fun image(url: String?, name: String): String? {
            if (url == null) return null
            val target = File(dir, "$name-$stamp.jpg")
            return when (val r = client.download(url, target)) {
                is NetResult.Ok -> target.path
                is NetResult.Failure -> {
                    failure = failure ?: r
                    null
                }
            }
        }
        val poster = if (title.manualCover) null else image(details.posterUrl, "poster")
        val backdrop = image(details.backdropUrl, "backdrop")
        val logo = image(details.logoUrl, "logo")
        val colors = poster?.let(CoverColors::fromFile)
        savePeople(details.cast.take(MAX_CAST), stamp)
        val stored = store(title, details, poster, backdrop, logo, colors?.dominant, colors?.luma, stamp, asked) ?: return failure
        saveImdb(stored, details, stamp)
        index.index(stored.id)
        prune(stored.id)
        if (failure != null) return failure
        return if (isMovie) null else episodes(stored)
    }

    private suspend fun savePeople(cast: List<PersonMeta>, stamp: Long) {
        for (person in cast) {
            val existing = meta.person(person.tmdbId)
            val stale = existing?.photoPath == null || CachePolicy.decide(existing.fetchedAt, stamp) != CachePolicy.Action.KEEP
            var photo = existing?.photoPath
            val url = person.photoUrl
            if (url != null && stale) {
                val target = images.personFile(person.tmdbId, stamp)
                if (client.download(url, target) is NetResult.Ok) {
                    images.delete(existing?.photoPath)
                    photo = target.path
                }
            }
            meta.upsertPerson(Person(person.tmdbId, person.name, Keys.normalize(person.name), photo, stamp))
        }
    }

    private suspend fun store(
        title: Title,
        details: TitleMeta,
        poster: String?,
        backdrop: String?,
        logo: String?,
        color: Int?,
        luma: Int?,
        stamp: Long,
        asked: String,
    ): Title? = db.withTransaction {
        val fresh = titles.title(title.id) ?: return@withTransaction null
        // Fix match may have changed the target while this request was in flight.
        if (fresh.tmdbId != title.tmdbId) return@withTransaction null
        val credits = details.crew.take(MAX_CREW).map { CreditJson(it.job, it.name) }
        val name = details.title.ifBlank { fresh.name }
        val updated = fresh.copy(
            name = name,
            originalName = details.originalTitle ?: fresh.originalName,
            year = details.year ?: fresh.year,
            overview = details.overview ?: fresh.overview,
            runtime = details.runtimeMinutes ?: fresh.runtime,
            imdbId = details.imdbId ?: fresh.imdbId,
            coverPath = if (fresh.manualCover) fresh.coverPath else poster ?: fresh.coverPath,
            backdropPath = backdrop ?: fresh.backdropPath,
            logoPath = logo ?: fresh.logoPath,
            dominantColor = if (fresh.manualCover) fresh.dominantColor else color ?: fresh.dominantColor,
            coverLuma = if (fresh.manualCover) fresh.coverLuma else luma ?: fresh.coverLuma,
            collectionId = details.collectionId,
            fetchedAt = stamp,
            metaLanguage = asked,
            sortKey = Keys.normalize(name),
            creditsJson = Credits.encode(credits),
        )
        titles.update(updated)
        val id = updated.id
        meta.deleteTitleGenres(id)
        for (genre in details.genres) {
            val genreId = meta.genreByName(genre)?.id ?: meta.insertGenre(Genre(name = genre))
            meta.upsertTitleGenre(TitleGenre(id, genreId))
        }
        meta.deleteTitlePersons(id)
        details.cast.take(MAX_CAST).forEach { person ->
            meta.upsertTitlePerson(TitlePerson(id, person.tmdbId, person.character, person.order, stamp))
        }
        val collectionId = details.collectionId
        val collectionName = details.collectionName
        if (collectionId != null && collectionName != null) {
            meta.upsertCollection(CollectionEntity(collectionId, collectionName))
        }
        details.tmdbRating?.let { meta.upsertRating(Rating(id, RatingSource.TMDB, it, null, stamp)) }
        // The cast OMDb named before TMDB took over.
        meta.deleteUnusedOmdbPersons()
        updated
    }

    private suspend fun saveImdb(title: Title, details: TitleMeta, stamp: Long) {
        if (config.omdbUsable) imdbRating(title.id, details.imdbId, stamp)
    }

    /** A failure matters only to a queue row whose whole job this is. A rating fetched this week costs no call. */
    private suspend fun imdbRating(titleId: Long, imdbId: String?, now: Long): NetResult.Failure? {
        if (imdbId == null) return null
        if (ImdbRules.fresh(meta.ratingFetchedAt(titleId, RatingSource.IMDB), now)) return null
        if (imdbPaused()) return DAILY_LIMIT
        return when (val result = client.imdbRating(imdbId)) {
            is NetResult.Ok -> {
                meta.upsertRating(Rating(titleId, RatingSource.IMDB, result.value, null, now))
                null
            }
            is NetResult.Failure -> when {
                result.isDailyLimit() -> {
                    pauseImdb()
                    result
                }
                result.reason == NetResult.Reason.NOT_FOUND -> null
                else -> result
            }
        }
    }

    private suspend fun pausedUntil(): Long = imdbPausedUntil ?: settings.imdbPausedUntil().also { imdbPausedUntil = it }

    private suspend fun imdbPaused(): Boolean = clock.now() < pausedUntil()

    private suspend fun pauseImdb(): Long {
        val until = ImdbRules.resumeAt(clock.now())
        imdbPausedUntil = until
        settings.setImdbPausedUntil(until)
        return until
    }

    /** OMDb's new day. Ratings skipped while it was spent come again. Parked rows are due by their own time. */
    private suspend fun resumeImdbIfDue() {
        val until = pausedUntil()
        if (until == 0L || clock.now() < until) return
        imdbPausedUntil = 0
        settings.setImdbPausedUntil(0)
        val now = clock.now()
        meta.imdbIdWithoutRating().forEach { queueFetch(it, now) }
    }

    /** OMDb matching, while TMDB is off or has no key. */
    private suspend fun matchOmdb(title: Title): NetResult.Failure? {
        val source = imdb ?: return null
        val outcome = when (val result = source.omdbMatch(parsedFor(title))) {
            is NetResult.Ok -> result.value
            is NetResult.Failure -> {
                if (result.reason != NetResult.Reason.NOT_FOUND) return result
                return omdbUnsettled(title.id, MatchState.NONE_FOUND)
            }
        }
        val auto = outcome.auto
            ?: return omdbUnsettled(title.id, if (outcome.choices.isEmpty()) MatchState.NONE_FOUND else MatchState.NEEDS_CHOICE)
        if (auto.kind == MetaKind.MOVIE && episodeCount(title.id) > 1) return omdbUnsettled(title.id, MatchState.NEEDS_CHOICE)
        val target = ops.attachImdbMatch(title.id, auto.imdbId, auto.kind, outcome.score) ?: return null
        if (target.id != title.id) {
            queueSurvivor(target)
            return null
        }
        return storeOmdb(target, auto)
    }

    /** Stamped, so a swipe or a queued row does not spend OMDb calls on the same answer for a week. */
    private suspend fun omdbUnsettled(titleId: Long, state: MatchState): NetResult.Failure? {
        db.withTransaction {
            titles.setMatchState(titleId, state)
            titles.setOmdbTried(titleId, clock.now())
        }
        return null
    }

    /** A title matched through OMDb, now that TMDB answers. One TMDB call by its IMDb id. */
    private suspend fun upgrade(title: Title): NetResult.Failure? {
        val imdbId = title.imdbId ?: return null
        val source = imdb ?: return null
        // The OMDb match settled movie or series, so TMDB's answer is read the same way.
        val name = parsedFor(title).copy(kind = if (title.kind == TitleKind.MOVIE) NameKind.MOVIE else NameKind.EPISODE)
        val outcome = when (val result = source.tmdbForImdb(imdbId, name)) {
            is NetResult.Ok -> result.value
            is NetResult.Failure -> {
                if (result.reason != NetResult.Reason.NOT_FOUND) return result
                // TMDB has no record of it, so it stays an OMDb match and its OMDb data stays current.
                return if (config.omdbUsable && !imdbPaused()) fetchOmdb(title) else null
            }
        }
        val auto = outcome.auto ?: return null
        if (auto.kind == MetaKind.MOVIE && episodeCount(title.id) > 1) return null
        val target = ops.attachTmdbForImdb(title.id, auto, outcome.anilistId, outcome.malId) ?: return null
        if (target.id != title.id) {
            queueSurvivor(target)
            return null
        }
        return fetch(target)
    }

    private suspend fun fetchOmdb(title: Title): NetResult.Failure? {
        val imdbId = title.imdbId ?: return null
        if (ImdbRules.fresh(title.fetchedAt, clock.now())) return omdbEpisodes(title, waitingOnly = true)
        val source = imdb ?: return null
        val record = when (val result = source.omdbTitle(imdbId)) {
            is NetResult.Ok -> result.value
            is NetResult.Failure -> return if (result.reason == NetResult.Reason.NOT_FOUND) null else result
        }
        return storeOmdb(title, record)
    }

    /** OMDb has no backdrop, logo, cast photo, character or collection, so those are left as they are. */
    private suspend fun storeOmdb(title: Title, record: OmdbTitle): NetResult.Failure? {
        val stamp = clock.now()
        // A poster that fails comes with the next refresh. Retrying the title for it would spend OMDb calls.
        val poster = if (title.manualCover) {
            null
        } else {
            firstImage(record.posterUrls, File(images.titleDir(title.id), "poster-$stamp.jpg"))
        }
        val colors = poster?.let(CoverColors::fromFile)
        val stored = db.withTransaction {
            val fresh = titles.title(title.id) ?: return@withTransaction null
            // Fix match, or a TMDB match, may have changed the target while this request was in flight.
            if (!ImdbRules.omdbOnly(fresh.tmdbId, fresh.imdbId) || fresh.imdbId != record.imdbId) return@withTransaction null
            val name = record.title.ifBlank { fresh.name }
            val updated = fresh.copy(
                name = name,
                year = record.year ?: fresh.year,
                overview = record.overview ?: fresh.overview,
                runtime = record.runtimeMinutes ?: fresh.runtime,
                coverPath = if (fresh.manualCover) fresh.coverPath else poster ?: fresh.coverPath,
                dominantColor = if (fresh.manualCover) fresh.dominantColor else colors?.dominant ?: fresh.dominantColor,
                coverLuma = if (fresh.manualCover) fresh.coverLuma else colors?.luma ?: fresh.coverLuma,
                fetchedAt = stamp,
                metaLanguage = ImdbRules.LANGUAGE,
                sortKey = Keys.normalize(name),
                creditsJson = Credits.encode(record.crew.take(MAX_CREW).map { CreditJson(it.job, it.name) }),
            )
            titles.update(updated)
            val id = updated.id
            meta.deleteTitleGenres(id)
            for (genre in record.genres) {
                val genreId = meta.genreByName(genre)?.id ?: meta.insertGenre(Genre(name = genre))
                meta.upsertTitleGenre(TitleGenre(id, genreId))
            }
            meta.deleteTitlePersons(id)
            record.cast.take(MAX_CAST).forEachIndexed { order, actor ->
                val personId = ImdbRules.personId(actor)
                meta.upsertPerson(Person(personId, actor, Keys.normalize(actor)))
                meta.upsertTitlePerson(TitlePerson(id, personId, null, order, null))
            }
            meta.deleteUnusedOmdbPersons()
            record.imdbRating?.let { meta.upsertRating(Rating(id, RatingSource.IMDB, it, record.imdbVotes, stamp)) }
            updated
        } ?: return null
        index.index(stored.id)
        prune(stored.id)
        return if (stored.kind == TitleKind.MOVIE) null else omdbEpisodes(stored, waitingOnly = false)
    }

    private suspend fun firstImage(urls: List<String>, target: File): String? {
        for (url in urls) if (client.download(url, target) is NetResult.Ok) return target.path
        return null
    }

    /** One call per season. [waitingOnly] asks only for seasons where a file has no episode data yet. */
    private suspend fun omdbEpisodes(title: Title, waitingOnly: Boolean): NetResult.Failure? {
        val imdbId = title.imdbId ?: return null
        if (title.kind == TitleKind.MOVIE) return null
        val source = imdb ?: return null
        val seasons = if (waitingOnly) titles.seasonsWaiting(title.id) else titles.ownedSeasons(title.id)
        for (season in seasons.filter { it > 0 }.sorted()) {
            if (imdbPaused()) return DAILY_LIMIT
            when (val result = source.omdbSeason(imdbId, season)) {
                is NetResult.Ok -> saveOmdbSeason(title.id, result.value)
                is NetResult.Failure -> if (result.reason != NetResult.Reason.NOT_FOUND) return result
            }
        }
        return null
    }

    private suspend fun saveOmdbSeason(titleId: Long, list: List<OmdbEpisode>) {
        val stamp = clock.now()
        for (item in list) {
            val existing = titles.episode(titleId, item.season, item.number)
            val row = (existing ?: Episode(titleId = titleId, season = item.season, number = item.number)).copy(
                name = item.title ?: existing?.name,
                airDate = item.airDate ?: existing?.airDate,
                imdbRating = item.imdbRating ?: existing?.imdbRating,
                fetchedAt = stamp,
            )
            if (existing == null) titles.insertEpisode(row) else titles.updateEpisode(row)
        }
    }

    private suspend fun prune(titleId: Long) {
        val title = titles.title(titleId) ?: return
        val keep = HashSet<String>()
        listOfNotNull(title.coverPath, title.backdropPath, title.logoPath).forEach { keep += it }
        titles.episodes(titleId).mapNotNullTo(keep) { it.stillPath }
        images.titleDir(titleId).listFiles()?.filter { it.path !in keep }?.forEach { it.delete() }
    }

    /** Remaps anime numbers, then fills in the seasons the owner has. */
    private suspend fun episodes(title: Title): NetResult.Failure? {
        val tmdbId = title.tmdbId ?: return null
        if (title.tmdbIsMovie == true) return null
        mapAnime(title)?.let { return it }
        for (season in titles.ownedSeasons(title.id)) {
            when (val r = client.season(tmdbId, season, language.current())) {
                is NetResult.Ok -> saveSeason(title, r.value)
                is NetResult.Failure -> if (r.reason != NetResult.Reason.NOT_FOUND) return r
            }
        }
        prune(title.id)
        return null
    }

    private suspend fun mapAnime(title: Title): NetResult.Failure? {
        val anilistId = title.anilistId ?: return null
        if (title.kind != TitleKind.ANIME) return null
        val scan = db.scan()
        for (file in scan.filesOfTitle(title.id)) {
            // An explicit season already reads the way TMDB numbers it.
            if (file.nameKind != NameKinds.EPISODE || file.parsedSeason != null) continue
            val number = file.parsedAbsolute ?: file.parsedEpisode ?: continue
            val src = number.toString()
            var mapped = meta.animeMap(anilistId, src)
            if (mapped == null) {
                when (val r = client.animeEpisode(anilistId, title.malId, number)) {
                    is NetResult.Failure -> {
                        if (r.reason == NetResult.Reason.NOT_FOUND) continue
                        return r
                    }
                    is NetResult.Ok -> {
                        val value = r.value
                        if (title.tmdbId != null && value.tmdbShowId != title.tmdbId) continue
                        mapped = AnimeMap(
                            anilistId = anilistId, malId = title.malId, tmdbShowId = value.tmdbShowId,
                            tmdbSeason = value.season, srcRange = src, dstRange = value.episode.toString(),
                        )
                        meta.upsertAnimeMap(mapped)
                    }
                }
            }
            val episode = mapped.dstRange.toIntOrNull() ?: continue
            relink(title.id, file.rowId, file.episodeId, mapped.tmdbSeason, episode, number)
        }
        return null
    }

    private suspend fun relink(titleId: Long, rowId: Long, oldEpisodeId: Long?, season: Int, number: Int, absolute: Int) {
        db.withTransaction {
            val target = titles.episode(titleId, season, number)?.id
                ?: titles.insertEpisode(Episode(titleId = titleId, season = season, number = number, absolute = absolute))
            if (target == oldEpisodeId) return@withTransaction
            db.scan().setEpisode(rowId, target)
            if (oldEpisodeId != null && titles.filesOfEpisode(oldEpisodeId) == 0) {
                val old = titles.episode(oldEpisodeId)
                if (old?.fetchedAt == null) titles.deleteEpisode(oldEpisodeId)
            }
        }
    }

    private suspend fun saveSeason(title: Title, list: List<EpisodeMeta>) {
        val stamp = clock.now()
        val dir = images.titleDir(title.id)
        for (item in list) {
            val existing = titles.episode(title.id, item.season, item.number)
            val owned = existing != null && titles.filesOfEpisode(existing.id) > 0
            var still = existing?.stillPath
            val stale = still == null || CachePolicy.decide(existing.fetchedAt, stamp) != CachePolicy.Action.KEEP
            val url = item.stillUrl
            if (owned && url != null && stale) {
                val target = File(dir, "s${item.season}e${item.number}-$stamp.jpg")
                if (client.download(url, target) is NetResult.Ok) still = target.path
            }
            val row = (existing ?: Episode(titleId = title.id, season = item.season, number = item.number)).copy(
                name = item.title ?: existing?.name,
                overview = item.overview ?: existing?.overview,
                stillPath = still,
                runtime = item.runtimeMinutes ?: existing?.runtime,
                airDate = item.airDate ?: existing?.airDate,
                // An OMDb rating left from before TMDB held the title would never be refreshed again.
                imdbRating = null,
                fetchedAt = stamp,
            )
            if (existing == null) titles.insertEpisode(row) else titles.updateEpisode(row)
        }
    }

    /** Stores skip times in the background, so the current and next episode have them on the next open. */
    suspend fun prefetchSegments(titleId: Long, targets: List<SegmentTarget>) {
        config.awaitLoaded()
        if (!config.onlineEnabled || !network.isOnline()) return
        val title = titles.title(titleId) ?: return
        val stored = meta.segments(titleId).filter { it.source != SegmentRules.MANUAL }.mapNotNullTo(HashSet()) { it.episodeId }
        for (target in targets) {
            if (target.episodeId in stored) continue
            val result = client.skipSegments(title.malId, title.tmdbId, target.season, target.number, target.durationMs)
            if (result !is NetResult.Ok) continue
            for (segment in result.value) {
                val kind = segmentKind(segment.kind) ?: continue
                meta.insertSegment(
                    IntroSegment(
                        titleId = titleId, season = target.season, episodeId = target.episodeId, kind = kind,
                        startMs = segment.startMs, endMs = segment.endMs, source = segment.source, confidence = 1.0,
                    ),
                )
            }
        }
    }

    private fun segmentKind(kind: String): SegmentKind? = when (kind.lowercase()) {
        "op", "intro", "mixed-op" -> SegmentKind.OP
        "ed", "outro", "credits", "mixed-ed" -> SegmentKind.ED
        "recap" -> SegmentKind.RECAP
        else -> null
    }

    suspend fun choices(titleId: Long, query: String?): List<MatchChoice> {
        config.awaitLoaded()
        val title = titles.title(titleId) ?: return emptyList()
        val typed = query?.trim()?.ifEmpty { null }
        if (!config.tmdbUsable) return if (config.omdbUsable) omdbChoices(title, typed) else emptyList()
        val candidates = if (typed == null && title.matchState == MatchState.NEEDS_CHOICE) {
            (client.match(parsedFor(title)) as? NetResult.Ok)?.value?.choices.orEmpty()
        } else {
            (client.searchManual(typed ?: title.localName, language.current()) as? NetResult.Ok)?.value.orEmpty()
        }
        return candidates.map(::choice)
    }

    /** Fix match while TMDB is off. Empty while OMDb's day is spent. */
    private suspend fun omdbChoices(title: Title, typed: String?): List<MatchChoice> {
        val source = imdb ?: return emptyList()
        if (imdbPaused()) return emptyList()
        val result: NetResult<List<OmdbCandidate>> = if (typed == null && title.matchState == MatchState.NEEDS_CHOICE) {
            when (val found = source.omdbMatch(parsedFor(title))) {
                is NetResult.Ok -> {
                    val auto = found.value.auto?.let {
                        OmdbCandidate(it.imdbId, it.kind, it.title, it.year, it.posterUrls.lastOrNull(), found.value.score)
                    }
                    NetResult.Ok(found.value.choices.ifEmpty { listOfNotNull(auto) })
                }
                is NetResult.Failure -> found
            }
        } else {
            source.omdbSearch(typed ?: title.localName)
        }
        return when (result) {
            is NetResult.Ok -> result.value.map { omdbChoice(it, title.kind) }
            is NetResult.Failure -> {
                if (result.isDailyLimit()) pauseImdb()
                emptyList()
            }
        }
    }

    private fun omdbChoice(candidate: OmdbCandidate, current: TitleKind) = MatchChoice(
        providerId = IMDB_PREFIX + candidate.imdbId,
        kind = when {
            candidate.kind == MetaKind.MOVIE -> TitleKind.MOVIE
            current == TitleKind.ANIME -> TitleKind.ANIME
            else -> TitleKind.SERIES
        },
        name = candidate.title,
        year = candidate.year,
        posterUrl = candidate.posterUrl,
        overview = null,
    )

    private fun choice(candidate: MatchCandidate) = MatchChoice(
        providerId = TMDB_PREFIX + candidate.tmdbId,
        kind = when {
            candidate.kind == MetaKind.MOVIE -> TitleKind.MOVIE
            candidate.isAnime -> TitleKind.ANIME
            else -> TitleKind.SERIES
        },
        name = candidate.title,
        year = candidate.year,
        posterUrl = candidate.posterUrl,
        overview = candidate.overview,
    )

    suspend fun applyChoice(titleId: Long, choice: MatchChoice) {
        val id = choice.providerId
        when {
            id.startsWith(TMDB_PREFIX) -> id.removePrefix(TMDB_PREFIX).toLongOrNull()?.let { ops.applyManual(titleId, choice.kind, it) }
            id.startsWith(IMDB_PREFIX) -> id.removePrefix(IMDB_PREFIX).takeIf(ImdbRules.IMDB_ID::matches)
                ?.let { ops.applyManualImdb(titleId, choice.kind, it) }
        }
    }

    private companion object {
        const val TMDB_PREFIX = "tmdb:"
        const val IMDB_PREFIX = "imdb:"
        val DAILY_LIMIT = NetResult.Failure(NetResult.Reason.RATE_LIMIT, OMDB_DAILY_LIMIT)
        const val MAX_CAST = 20
        const val MAX_CREW = 6
        val BUDGET_MS = TimeUnit.MINUTES.toMillis(8)
        val RATE_LIMIT_WAIT_MS = TimeUnit.MINUTES.toMillis(1)

        /** Swiping twice in a row must not download every cover twice. */
        val REFRESH_GAP_MS = TimeUnit.MINUTES.toMillis(10)
        val MIN_RERUN_MS = TimeUnit.MINUTES.toMillis(1)
    }
}

/**
 * F-M11. Every title, episode, person and rating carries fetchedAt. From day 150 a refresh is queued, and at
 * day 175 whatever could not be refreshed is deleted and the title falls back to its local frame.
 * Provider ids, matches and manual covers are not TMDB content and stay.
 */
internal class CacheSweeper(
    private val db: FrameDb,
    private val images: ImageStore,
    private val index: SearchIndexer,
    private val clock: Clock,
) {
    suspend fun sweep() {
        val now = clock.now()
        val meta = db.meta()
        for (title in meta.titlesFetchedBefore(CachePolicy.refreshCutoff(now))) {
            // F-M11 is TMDB's rule. A title matched only through OMDb refreshes on the same clock and is never purged.
            if (ImdbRules.omdbOnly(title.tmdbId, title.imdbId)) {
                if (CachePolicy.decide(title.fetchedAt, now) != CachePolicy.Action.KEEP) queueRefresh(title.id, now)
                continue
            }
            when (CachePolicy.decide(title.fetchedAt, now)) {
                CachePolicy.Action.PURGE -> purgeTitle(title)
                CachePolicy.Action.REFRESH -> queueRefresh(title.id, now)
                CachePolicy.Action.KEEP -> Unit
            }
        }
        val cutoff = CachePolicy.purgeCutoff(now)
        meta.episodesFetchedBefore(cutoff).forEach { clearEpisode(it) }
        for (person in meta.personsFetchedBefore(cutoff)) removePerson(person)
        meta.deleteTmdbRatingsBefore(cutoff)
    }

    /** For the day the TMDB licence ends. What came from OMDb stays. */
    suspend fun purgeAll() {
        db.titles().allTitles().filter { it.fetchedAt != null && !ImdbRules.omdbOnly(it.tmdbId, it.imdbId) }.forEach { purgeTitle(it) }
        db.meta().allPersons().filter { it.id > 0 }.forEach { removePerson(it) }
    }

    private suspend fun removePerson(person: Person) {
        val meta = db.meta()
        val affected = meta.titlesOfPerson(person.id)
        images.delete(person.photoPath)
        db.withTransaction {
            meta.deletePersonLinks(person.id)
            meta.deletePerson(person.id)
        }
        affected.forEach { index.index(it) }
    }

    private suspend fun queueRefresh(titleId: Long, now: Long) {
        if (db.scan().filesOfTitle(titleId).isEmpty()) return
        val meta = db.meta()
        val queued = meta.queued(titleId)
        if (queued == null || queued.job != LookupJob.REFRESH) {
            meta.upsertQueue(LookupQueue(titleId, LookupState.QUEUED, 0, now, LookupJob.REFRESH))
        }
    }

    private suspend fun clearEpisode(episode: Episode) {
        images.delete(episode.stillPath)
        db.titles().updateEpisode(
            episode.copy(
                name = null, overview = null, stillPath = null, runtime = null, airDate = null, fetchedAt = null,
                imdbRating = null,
            ),
        )
    }

    private suspend fun purgeTitle(title: Title) {
        val titles = db.titles()
        val meta = db.meta()
        db.withTransaction {
            val fresh = titles.title(title.id) ?: return@withTransaction
            titles.update(
                fresh.copy(
                    name = fresh.localName,
                    originalName = null,
                    year = fresh.localYear,
                    overview = null,
                    runtime = null,
                    coverPath = if (fresh.manualCover) fresh.coverPath else null,
                    backdropPath = null,
                    logoPath = null,
                    dominantColor = if (fresh.manualCover) fresh.dominantColor else null,
                    coverLuma = if (fresh.manualCover) fresh.coverLuma else null,
                    collectionId = null,
                    fetchedAt = null,
                    metaLanguage = null,
                    sortKey = Keys.normalize(fresh.localName),
                    creditsJson = null,
                ),
            )
            meta.deleteTitleGenres(title.id)
            meta.deleteTitlePersons(title.id)
            meta.deleteRatings(title.id)
            titles.episodes(title.id).forEach {
                titles.updateEpisode(
                    it.copy(
                        name = null, overview = null, stillPath = null, runtime = null, airDate = null, fetchedAt = null,
                        imdbRating = null,
                    ),
                )
            }
        }
        images.deleteTitleDir(title.id)
        val frame = titles.title(title.id)?.takeIf { !it.manualCover }?.framePath
        val colors = frame?.let(CoverColors::fromFile)
        if (colors != null) titles.setColors(title.id, colors.dominant, colors.luma)
        index.index(title.id)
        if (title.tmdbId != null) queueRefresh(title.id, clock.now())
    }
}

private val Title.isMatched: Boolean get() = matchState == MatchState.AUTO || matchState == MatchState.MANUAL
