package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.LookupJob
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/** OMDb as the second metadata source, used while TMDB is off or has no key. */
internal object ImdbRules {
    private val DAY = TimeUnit.DAYS.toMillis(1)
    private val RESUME_MARGIN_MS = TimeUnit.MINUTES.toMillis(10)
    private val SPACES = Regex("\\s+")

    /** The language OMDb writes in. It has no other. */
    const val LANGUAGE = "en"

    /**
     * A free key has about 1,000 calls a day, and a swipe must not spend them on data it already has. IMDb ratings
     * move slowly, so a week is soon enough.
     */
    val FRESH_MS: Long = TimeUnit.DAYS.toMillis(7)

    fun fresh(fetchedAt: Long?, now: Long): Boolean = fetchedAt != null && now - fetchedAt in 0 until FRESH_MS

    /** OMDb's day is taken to end at midnight UTC [UNVERIFIED]. The margin covers a phone clock a little fast. */
    fun resumeAt(now: Long): Long = (Math.floorDiv(now, DAY) + 1) * DAY + RESUME_MARGIN_MS

    /** Every TMDB path sets the TMDB id, so a title holding only an IMDb id was matched through OMDb. */
    fun omdbOnly(tmdbId: Long?, imdbId: String?): Boolean = tmdbId == null && imdbId != null

    /**
     * OMDb answered this title within the week and could not settle it. The same name and year would get the same
     * answer, so asking again only spends the day's calls. Fix match still asks, because the owner types a new name.
     */
    fun omdbGaveUp(state: MatchState, omdbTriedAt: Long?, now: Long): Boolean =
        state in UNSETTLED && fresh(omdbTriedAt, now)

    private val UNSETTLED = setOf(MatchState.NONE_FOUND, MatchState.NEEDS_CHOICE)

    /**
     * Whether an OMDb match may merge a title into another on the same IMDb id. Every cour of an anime shares one IMDb
     * id, and OMDb has no AniList id to tell them apart, so anime never merges here. Nor does a title whose IMDb id
     * already has two holders, because TMDB or the owner kept those apart. The TMDB upgrade decides these later, with
     * the AniList check of a TMDB match.
     */
    fun mergesOnImdb(kind: TitleKind, otherKind: TitleKind, otherAnilistId: Long?, otherManual: Boolean, holders: Int): Boolean =
        !otherManual && holders == 1 && otherAnilistId == null && kind != TitleKind.ANIME && otherKind != TitleKind.ANIME

    /**
     * OMDb names its cast with no id. A negative id cannot meet a TMDB person id, and the same name gives the same
     * person across titles, so the person page still lists them together.
     */
    fun personId(name: String): Long {
        val key = name.trim().lowercase(Locale.ROOT).replace(SPACES, " ")
        val digest = MessageDigest.getInstance("SHA-256").digest("omdb-person:$key".toByteArray())
        var bits = 0L
        for (i in 0 until Long.SIZE_BYTES) bits = (bits shl 8) or (digest[i].toLong() and 0xFF)
        val positive = bits and Long.MAX_VALUE
        return -(if (positive == 0L) 1L else positive)
    }

    val IMDB_ID = Regex("^tt\\d+$")
}

/**
 * A metadata service is in use when Online features and its own switch are both on, and a key is saved. The key is
 * not part of AppSettings, so these rules leave it to the caller.
 */
internal object ServiceUse {
    fun tmdb(s: AppSettings): Boolean = s.onlineFeatures && s.tmdbEnabled

    fun imdb(s: AppSettings): Boolean = s.onlineFeatures && s.imdbEnabled

    /** Which services a settings change just brought into use. Online turned on counts, so what waited is queued then. */
    fun cameOn(old: AppSettings, new: AppSettings): Pair<Boolean, Boolean> =
        (tmdb(new) && !tmdb(old)) to (imdb(new) && !imdb(old))
}

internal enum class MetaRoute(val omdb: Boolean = false) {
    TMDB_FETCH,
    TMDB_EPISODES,
    TMDB_MATCH,

    /** An OMDb match looks for TMDB's record of the same IMDb id. */
    TMDB_UPGRADE,
    OMDB_FETCH(omdb = true),
    OMDB_EPISODES(omdb = true),
    OMDB_MATCH(omdb = true),

    /** A TMDB match while TMDB is off: only its IMDb rating can be refreshed. */
    IMDB_RATING(omdb = true),

    /** Nothing can be done for this row now. A switch turned back on queues what is missing. */
    DROP,

    /** The service this row needs went off during the run. The row stays as it is. */
    WAIT,
}

/** The one place that decides which service answers a queued row. The HTTP client refuses a switched-off service anyway. */
internal object MetaRoutes {
    /** [omdbGaveUp] is ImdbRules.omdbGaveUp for the title. Only TMDB may then try to match it again. */
    fun of(
        job: LookupJob,
        tmdbId: Long?,
        imdbId: String?,
        tmdb: Boolean,
        omdb: Boolean,
        omdbGaveUp: Boolean = false,
    ): MetaRoute = when {
        tmdbId != null -> when {
            tmdb -> if (job == LookupJob.EPISODES) MetaRoute.TMDB_EPISODES else MetaRoute.TMDB_FETCH
            omdb && imdbId != null && job != LookupJob.EPISODES -> MetaRoute.IMDB_RATING
            else -> MetaRoute.DROP
        }
        imdbId != null -> when {
            tmdb -> MetaRoute.TMDB_UPGRADE
            omdb -> if (job == LookupJob.EPISODES) MetaRoute.OMDB_EPISODES else MetaRoute.OMDB_FETCH
            else -> MetaRoute.WAIT
        }
        job != LookupJob.MATCH -> MetaRoute.DROP
        tmdb -> MetaRoute.TMDB_MATCH
        omdb -> if (omdbGaveUp) MetaRoute.DROP else MetaRoute.OMDB_MATCH
        else -> MetaRoute.WAIT
    }
}
