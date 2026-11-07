package com.frameglass.net

import com.frameglass.parser.ParsedName
import java.io.File

/*
 * The contract between :net and :data. :data never sees HTTP or JSON.
 * Every call fails softly with NetResult.Failure. Nothing here may throw on a network error.
 */

enum class ApiKey { TMDB, OMDB, OPENSUBTITLES, SUBDL, SUBSOURCE }

/** Supplied by :data. Keys live encrypted in :data and are never logged. */
interface NetConfig {
    fun key(kind: ApiKey): String?
    val tmdbBaseUrl: String
    val onlineEnabled: Boolean
}

sealed interface NetResult<out T> {
    data class Ok<T>(val value: T) : NetResult<T>
    data class Failure(val reason: Reason, val message: String? = null) : NetResult<Nothing>

    enum class Reason { OFFLINE_SETTING, NO_KEY, NETWORK, RATE_LIMIT, NOT_FOUND, BAD_RESPONSE }
}

enum class MetaKind { MOVIE, TV }

data class MatchCandidate(
    val kind: MetaKind,
    val tmdbId: Long,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val posterUrl: String?,
    val overview: String?,
    val score: Double,
    val isAnime: Boolean,
)

data class MatchOutcome(
    /** Set when the rules in brief F-M2 allow an automatic match. */
    val auto: MatchCandidate?,
    /** At most 3 choices when no automatic match was allowed. */
    val choices: List<MatchCandidate>,
    val anilistId: Long? = null,
    val malId: Long? = null,
)

data class PersonMeta(val tmdbId: Long, val name: String, val character: String?, val photoUrl: String?, val order: Int)

data class CrewMeta(val job: String, val name: String)

data class TitleMeta(
    val kind: MetaKind,
    val tmdbId: Long,
    val imdbId: String?,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val overview: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val tmdbRating: Double?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val cast: List<PersonMeta>,
    val crew: List<CrewMeta>,
    val collectionId: Long?,
    val collectionName: String?,
    val seasons: List<Int>,
)

data class EpisodeMeta(
    val season: Int,
    val number: Int,
    val title: String?,
    val overview: String?,
    val stillUrl: String?,
    val runtimeMinutes: Int?,
    val airDate: String?,
)

/** One anime episode resolved to TMDB numbering. */
data class AnimeEpisodeMap(val tmdbShowId: Long, val season: Int, val episode: Int)

data class NetSegment(val kind: String, val startMs: Long, val endMs: Long, val source: String)

interface MetadataClient {
    /** Scores candidates with `language=en-US` and alternative titles, per F-M1, F-M2 and F-M9. */
    suspend fun match(name: ParsedName): NetResult<MatchOutcome>
    suspend fun searchManual(query: String, language: String): NetResult<List<MatchCandidate>>
    /** Display fields in [language] with English as a per-field fallback. */
    suspend fun details(kind: MetaKind, tmdbId: Long, language: String): NetResult<TitleMeta>
    suspend fun season(tmdbId: Long, season: Int, language: String): NetResult<List<EpisodeMeta>>
    suspend fun imdbRating(imdbId: String): NetResult<Double>
    suspend fun download(url: String, target: File): NetResult<Unit>
    suspend fun animeEpisode(anilistId: Long, malId: Long?, absoluteOrEpisode: Int): NetResult<AnimeEpisodeMap>
    suspend fun skipSegments(malId: Long?, tmdbId: Long?, season: Int?, episode: Int?, episodeLengthMs: Long): NetResult<List<NetSegment>>
}
