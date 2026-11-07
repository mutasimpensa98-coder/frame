package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import com.frameglass.parser.ArabicNormalizer
import java.io.File
import java.util.Locale
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

internal const val PROVIDER_SUBSOURCE = "SubSource"

private const val LABEL = "subsource"
private const val ROOT = "https://api.subsource.net/api/v1"
private const val PAGE_SIZE = 100

/** A film's listed year and a file's year can sit either side of a new year. */
private const val YEAR_SLACK = 1

private val SUBTITLE_ID = Regex("[0-9]+")
private val IMDB_ID = Regex("(?:tt)?([0-9]+)")

/**
 * SubSource API v1, from subsource.net/api-docs and working clients on 2026-11-07. A title is found
 * first, one "movie" per season, and its subtitles are then listed one language at a time. Downloads
 * come from the API host as a zip. The key rides in a header, so it stays out of every URL.
 */
internal class SubSourceApi(private val http: Http, private val config: NetConfig) : SubtitleProvider {
    override val name = PROVIDER_SUBSOURCE
    override val keyKind = ApiKey.SUBSOURCE

    override suspend fun search(query: SubtitleQuery, languages: List<String>): NetResult<List<SubtitleHit>> {
        val key = config.keyOf(keyKind) ?: return Failure(Reason.NO_KEY)
        val movieId = when (val found = movieIdOf(query, key)) {
            is Failure -> return found
            is Ok -> found.value ?: return Ok(emptyList())
        }
        return eachLanguage(languages) { language -> subtitles(movieId, query, language, key) }
    }

    /** IMDb first, because SubSource has no TMDB search. Text catches titles with no IMDb link. */
    private suspend fun movieIdOf(query: SubtitleQuery, key: String): NetResult<Long?> {
        val imdb = query.imdbId?.let { IMDB_ID.matchEntire(it.trim()) }?.groupValues?.get(1)
        if (imdb != null) {
            val url = lookupRoot().addQueryParameter("searchType", "imdb").addQueryParameter("imdb", "tt$imdb").build()
            when (val found = lookup(url, key)) {
                is Failure -> return found
                is Ok -> pick(found.value, query, byImdb = true)?.let { return Ok(it) }
            }
        }
        if (query.title.isBlank()) return Ok(null)
        val url = lookupRoot().apply {
            addQueryParameter("searchType", "text")
            addQueryParameter("q", query.title.trim())
            if (query.isEpisodic) {
                query.season?.let { addQueryParameter("season", it.toString()) }
            } else {
                query.year?.let { addQueryParameter("year", it.toString()) }
            }
        }.build()
        return lookup(url, key).map { pick(it, query, byImdb = false) }
    }

    private fun lookupRoot() = "$ROOT/movies/search".toHttpUrl().newBuilder()

    private suspend fun lookup(url: HttpUrl, key: String): NetResult<List<SsMovie>> =
        emptyOn404(http.text(request(url, key), LABEL, keyed = true).decoded<SsMovies>(LABEL).map { it.data })

    /** An exact ID wins. Next comes what the search itself vouches for: no ID at all, or the name. */
    private fun pick(movies: List<SsMovie>, query: SubtitleQuery, byImdb: Boolean): Long? {
        val pool = if (query.isEpisodic) {
            movies.filter { it.season == query.season }.ifEmpty { movies.filter { it.season == null } }
        } else {
            movies
        }
        val next = pool.filter { if (byImdb) it.imdbId.isNullOrBlank() else it.isNamed(query) }
        return pool.filter { it.hasIdOf(query) }.ifEmpty { next }.firstOrNull()?.movieId
    }

    private suspend fun subtitles(
        movieId: Long,
        query: SubtitleQuery,
        language: String,
        key: String,
    ): NetResult<List<SubtitleHit>> {
        val url = "$ROOT/subtitles".toHttpUrl().newBuilder().apply {
            addQueryParameter("movieId", movieId.toString())
            addQueryParameter("language", Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH).lowercase())
            addQueryParameter("limit", PAGE_SIZE.toString())
            query.season?.let { addQueryParameter("seasonNumber", it.toString()) }
            query.episode?.let { addQueryParameter("episodeNumber", it.toString()) }
        }.build()
        val listed = http.text(request(url, key), LABEL, keyed = true).decoded<SsSubtitles>(LABEL)
        return emptyOn404(listed.map { reply ->
            if (reply.success == false) emptyList() else reply.data.mapNotNull { it.hit(query, language) }
        })
    }

    override suspend fun fetch(hit: SubtitleHit, target: File): NetResult<Unit> {
        val key = config.keyOf(keyKind) ?: return Failure(Reason.NO_KEY)
        if (!SUBTITLE_ID.matches(hit.id)) return Failure(Reason.BAD_RESPONSE, LABEL)
        val url = "$ROOT/subtitles/${hit.id}/download".toHttpUrl()
        return http.download(request(url, key), target, LABEL, keyed = true, maxBytes = MAX_PAYLOAD_BYTES)
    }

    private fun request(url: HttpUrl, key: String): Request = Request.Builder().url(url)
        .header("X-API-Key", key)
        .header("User-Agent", SUBTITLE_USER_AGENT)
        .build()

    private fun <T> emptyOn404(result: NetResult<List<T>>): NetResult<List<T>> =
        if (result is Failure && result.reason == Reason.NOT_FOUND) Ok(emptyList()) else result
}

@Serializable
internal data class SsMovies(val data: List<SsMovie> = emptyList())

@Serializable
internal data class SsMovie(
    val movieId: Long,
    val title: String? = null,
    val alternateTitle: String? = null,
    val releaseYear: Int? = null,
    val imdbId: String? = null,
    val tmdbId: Long? = null,
    val season: Int? = null,
) {
    fun hasIdOf(query: SubtitleQuery): Boolean =
        (tmdbId != null && tmdbId == query.tmdbId) ||
            (!imdbId.isNullOrBlank() && imdbId.removePrefix("tt") == query.imdbId?.trim()?.removePrefix("tt"))

    fun isNamed(query: SubtitleQuery): Boolean {
        val wanted = ArabicNormalizer.key(query.title)
        val sameName = listOfNotNull(title, alternateTitle).any { ArabicNormalizer.key(it) == wanted }
        val year = query.year
        // A show's listed year belongs to its season, so only a film's is compared.
        val sameYear = query.isEpisodic || year == null || releaseYear == null ||
            releaseYear in (year - YEAR_SLACK)..(year + YEAR_SLACK)
        return sameName && sameYear
    }
}

@Serializable
internal data class SsSubtitles(val success: Boolean? = null, val data: List<SsSubtitle> = emptyList())

@Serializable
internal data class SsSubtitle(
    val subtitleId: Long,
    val releaseInfo: List<String?> = emptyList(),
    val files: Int? = null,
    val downloads: Long? = null,
) {
    fun hit(query: SubtitleQuery, language: String): SubtitleHit? {
        val names = releaseInfo.filterNotNull().filter(String::isNotBlank)
        val fit = fitOf(names, query.season, query.episode)
        if (fit == EpisodeFit.OTHER) return null
        // One subtitle can list many releases, so the one for this episode is the one to show.
        val shown = names.firstOrNull { fitOf(listOf(it), query.season, query.episode) == EpisodeFit.EXACT }
        return SubtitleHit(
            provider = PROVIDER_SUBSOURCE,
            id = subtitleId.toString(),
            language = language,
            release = shown ?: names.firstOrNull().orEmpty(),
            hashMatch = false,
            downloads = downloads?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
            format = null,
            seasonPack = fit == EpisodeFit.PACK || (query.episode != null && (files ?: 0) > 1),
        )
    }
}
