package com.frameglass.net

import com.frameglass.parser.ParsedName

/*
 * Additions beside the fixed contract in NetApi.kt: one switch per metadata service, and OMDb as a second source of
 * matches and display data while TMDB is off. Every call fails softly, as in NetApi.kt.
 */

/**
 * Implemented by the NetConfig that :data supplies. A config without it has both services on. The one HTTP client
 * enforces both switches for every request, Coil's included, whoever makes it.
 */
interface ServiceSwitches {
    /** Off: nothing reaches TMDB, its images, the custom TMDB base URL, AniList or anibridge. */
    val tmdbEnabled: Boolean

    /** Off: nothing reaches OMDb or its poster host. */
    val imdbEnabled: Boolean
}

/** The message of the [NetResult.Reason.RATE_LIMIT] failure OMDb gives once a key's day is spent. */
const val OMDB_DAILY_LIMIT = "omdb daily limit"

/** OMDb's daily allowance is spent (about 1,000 calls for a free key). Asking again before the next day is wasted. */
fun NetResult.Failure.isDailyLimit(): Boolean = reason == NetResult.Reason.RATE_LIMIT && message == OMDB_DAILY_LIMIT

/** One OMDb search row, for the match-choice sheet. [posterUrl] is OMDb's own small poster. */
data class OmdbCandidate(
    val imdbId: String,
    val kind: MetaKind,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val score: Double,
)

/**
 * OMDb's record of one work. OMDb is English only and has no original title, backdrop, title logo, cast photo,
 * character name, collection or episode still, so those have no field here.
 */
data class OmdbTitle(
    val imdbId: String,
    val kind: MetaKind,
    val title: String,
    val year: Int?,
    val overview: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    /** Names only, in billing order. */
    val cast: List<String>,
    val crew: List<CrewMeta>,
    val imdbRating: Double?,
    val imdbVotes: Int?,
    /** The same poster, largest first. Try them in turn. */
    val posterUrls: List<String>,
)

data class OmdbOutcome(
    /** Set when the rules in brief F-M2 allow an automatic match. */
    val auto: OmdbTitle?,
    val score: Double,
    /** At most 3 choices when no automatic match was allowed. */
    val choices: List<OmdbCandidate>,
)

data class OmdbEpisode(val season: Int, val number: Int, val title: String?, val airDate: String?, val imdbRating: Double?)

/** Implemented by the client [Net.client] returns. */
interface ImdbMetadata {
    /** By the parsed title, year and type first, then a search. Scored by F-M2, like a TMDB match. */
    suspend fun omdbMatch(name: ParsedName): NetResult<OmdbOutcome>

    /** Movies and series only, best first, for Fix match. */
    suspend fun omdbSearch(query: String): NetResult<List<OmdbCandidate>>

    suspend fun omdbTitle(imdbId: String): NetResult<OmdbTitle>

    /** Titles, air dates and IMDb ratings of one season. OMDb has no season 0. */
    suspend fun omdbSeason(imdbId: String, season: Int): NetResult<List<OmdbEpisode>>

    /** TMDB's own record for an IMDb id, so a title OMDb matched gains full data. AniList ids come too for anime. */
    suspend fun tmdbForImdb(imdbId: String, name: ParsedName): NetResult<MatchOutcome>
}
