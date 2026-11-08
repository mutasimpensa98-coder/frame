package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import java.util.Collections
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val DEFAULT_TMDB_BASE = "https://api.themoviedb.org"

/** F-M1: search and score in English, because `language=ar` may return neither English nor romaji. */
internal const val ENGLISH = "en-US"

private const val ANIMATION_GENRE = 16
private const val ABSOLUTE_GROUP_TYPE = 2
private const val ABSOLUTE_CACHE_SIZE = 8

/** Accepts a bare host or a URL with or without `/3`. Blank never stops metadata (old proxy bug). */
internal fun tmdbApiRoot(raw: String): HttpUrl? {
    val text = raw.trim().ifEmpty { DEFAULT_TMDB_BASE }
    val url = (if ("://" in text) text else "https://$text").toHttpUrlOrNull() ?: return null
    val builder = url.newBuilder().query(null).fragment(null)
    if (url.pathSegments.lastOrNull(String::isNotEmpty) != "3") builder.addPathSegment("3")
    return builder.build()
}

/** Not a data class: a generated toString would print the key. */
internal class TmdbCredential private constructor(val value: String, val isBearer: Boolean) {
    companion object {
        /** v4 tokens are JWTs, three dot-separated parts. Anything else goes out as a v3 key. */
        fun detect(raw: String?): TmdbCredential? {
            val key = raw?.trim()?.removePrefix("Bearer ")?.trim().orEmpty()
            if (key.isEmpty()) return null
            val parts = key.split('.')
            return TmdbCredential(key, parts.size == 3 && parts.all(String::isNotEmpty))
        }
    }
}

/** F-M5 sizes. */
internal object TmdbImages {
    private const val ROOT = "https://image.tmdb.org/t/p"

    fun poster(path: String?) = url("w500", path)
    fun backdrop(path: String?) = url("w1280", path)
    fun still(path: String?) = url("w300", path)
    fun profile(path: String?) = url("w185", path)

    /** The app has no SVG decoder. image.tmdb.org serves a PNG under the same name. */
    fun logo(path: String?) = url("w500", path?.replace(Regex("\\.svg$", RegexOption.IGNORE_CASE), ".png"))

    private fun url(size: String, path: String?): String? =
        path?.takeIf(String::isNotBlank)?.let { "$ROOT/$size/${it.trimStart('/')}" }
}

/** F-M14: the device language, then English. A logo in any other language reads as wrong. */
internal fun pickLogo(logos: List<TmdbImage>, language: String): String? {
    fun best(lang: String) = logos
        .filter { it.language.equals(lang, ignoreCase = true) && !it.filePath.isNullOrBlank() }
        .maxWithOrNull(compareBy<TmdbImage> { it.voteAverage }.thenBy { it.voteCount })
    return (best(languageCode(language)) ?: best("en"))?.filePath
}

/**
 * The trailer to open on YouTube: a YouTube Trailer in the app's language, then English, then any, official ones
 * first, then the newest. Teasers and clips are not trailers.
 */
internal fun pickTrailer(videos: List<TmdbVideo>, language: String): String? {
    val trailers = videos.filter {
        it.site.equals("YouTube", ignoreCase = true) && it.type.equals("Trailer", ignoreCase = true) && !it.key.isNullOrBlank()
    }
    val code = languageCode(language)
    val rank = compareBy<TmdbVideo> {
        when (it.language?.lowercase()) {
            code -> 0
            "en" -> 1
            else -> 2
        }
    }.thenByDescending { it.official }.thenByDescending { it.publishedAt.orEmpty() }
    return trailers.minWithOrNull(rank)?.key
}

private val FULL_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/** The next episode TMDB lists. A date with no day cannot be worded, so it counts as unknown. */
internal fun TmdbNextEpisode.toMeta(): NextEpisodeMeta? {
    val date = airDate?.takeIf(FULL_DATE::matches) ?: return null
    if (season < 0 || number <= 0) return null
    return NextEpisodeMeta(season, number, date)
}

internal fun languageCode(language: String): String = language.substringBefore('-').lowercase()

internal fun isEnglish(language: String): Boolean = languageCode(language) == "en"

/** F-M9: genre 16 and origin JP. Movie search results carry no country, so language stands in. */
internal fun isAnimeWork(genreIds: List<Int>, countries: List<String>, originalLanguage: String?): Boolean =
    ANIMATION_GENRE in genreIds && ("JP" in countries || originalLanguage == "ja")

internal fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()

internal class TmdbApi(private val http: Http, private val config: NetConfig) {
    private val absoluteOrders = Collections.synchronizedMap(
        object : LinkedHashMap<Long, List<TmdbGroupEpisode>>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<TmdbGroupEpisode>>?) =
                size > ABSOLUTE_CACHE_SIZE
        },
    )

    fun hasKey(): Boolean = TmdbCredential.detect(config.key(ApiKey.TMDB)) != null

    suspend fun search(kind: MetaKind, query: String, year: Int?): NetResult<List<TmdbItem>> {
        val params = buildList {
            add("query" to query)
            add("language" to ENGLISH)
            add("include_adult" to "false")
            if (year != null) add("year" to year.toString())
        }
        return get<TmdbPage>("search/${kind.path}", params).map(TmdbPage::results)
    }

    suspend fun searchMulti(query: String, language: String): NetResult<List<TmdbItem>> {
        val params = listOf("query" to query, "language" to language, "include_adult" to "false")
        return get<TmdbPage>("search/multi", params).map(TmdbPage::results)
    }

    suspend fun alternativeTitles(kind: MetaKind, id: Long): NetResult<List<String>> =
        get<TmdbAltTitles>("${kind.path}/$id/alternative_titles").map { reply ->
            (reply.titles + reply.results).mapNotNull(TmdbAltTitle::title)
        }

    /** F-M14 logos come from the `images` endpoint, appended to save a request. */
    suspend fun details(kind: MetaKind, id: Long, language: String, full: Boolean): NetResult<TmdbDetails> {
        val params = buildList {
            add("language" to language)
            if (full) {
                val extras = if (kind == MetaKind.TV) "credits,images,external_ids,videos" else "credits,images,videos"
                add("append_to_response" to extras)
                add("include_image_language" to listOf(languageCode(language), "en").distinct().joinToString(","))
                // Trailers in the app's language, then English, then those with no language set.
                add("include_video_language" to (listOf(languageCode(language), "en").distinct() + "null").joinToString(","))
            }
        }
        return get("${kind.path}/$id", params)
    }

    suspend fun season(id: Long, season: Int, language: String): NetResult<List<TmdbEpisode>> =
        get<TmdbSeason>("tv/$id/season/$season", listOf("language" to language)).map(TmdbSeason::episodes)

    suspend fun findByImdb(imdbId: String): NetResult<TmdbFind> =
        get("find/$imdbId", listOf("external_source" to "imdb_id", "language" to ENGLISH))

    /** F-M9 step 4, the fallback once anibridge ranges have no answer. */
    suspend fun absoluteEpisode(show: Long, absolute: Int): NetResult<AnimeEpisodeMap> {
        val order = absoluteOrders[show] ?: when (val fetched = fetchAbsoluteOrder(show)) {
            is Failure -> return fetched
            is Ok -> fetched.value.also { absoluteOrders[show] = it }
        }
        val episode = order.getOrNull(absolute - 1) ?: return Failure(Reason.NOT_FOUND, "absolute order")
        return Ok(AnimeEpisodeMap(show, episode.season, episode.episode))
    }

    private suspend fun fetchAbsoluteOrder(show: Long): NetResult<List<TmdbGroupEpisode>> {
        val groups = when (val listed = get<TmdbGroupList>("tv/$show/episode_groups")) {
            is Failure -> return listed
            is Ok -> listed.value.results
        }
        val group = groups.filter { it.type == ABSOLUTE_GROUP_TYPE }.maxByOrNull(TmdbGroupRef::episodeCount)
            ?: return Ok(emptyList())
        return get<TmdbGroupDetail>("tv/episode_group/${group.id}").map { detail ->
            detail.groups.sortedBy(TmdbGroup::order).flatMap { it.episodes.sortedBy(TmdbGroupEpisode::order) }
        }
    }

    private suspend inline fun <reified T> get(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
    ): NetResult<T> {
        val credential = TmdbCredential.detect(config.key(ApiKey.TMDB)) ?: return Failure(Reason.NO_KEY)
        val root = tmdbApiRoot(config.tmdbBaseUrl) ?: return Failure(Reason.BAD_RESPONSE, "tmdb base url")
        val url = root.newBuilder().addPathSegments(path)
        params.forEach { (name, value) -> url.addQueryParameter(name, value) }
        if (!credential.isBearer) url.addQueryParameter("api_key", credential.value)
        val bearer = credential.value.takeIf { credential.isBearer }
        return http.get(url.build(), "tmdb", keyed = true, bearer = bearer).decoded("tmdb")
    }

    private val MetaKind.path: String get() = if (this == MetaKind.MOVIE) "movie" else "tv"
}

@Serializable
internal data class TmdbPage(val results: List<TmdbItem> = emptyList())

/** One shape for movie, TV and multi search rows. */
@Serializable
internal data class TmdbItem(
    val id: Long,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val overview: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
) {
    val names: List<String> get() = listOfNotNull(title, name, originalTitle, originalName)
    val year: Int? get() = yearOf(releaseDate ?: firstAirDate)

    fun candidate(kind: MetaKind, score: Double) = MatchCandidate(
        kind = kind,
        tmdbId = id,
        title = title ?: name ?: originalTitle ?: originalName.orEmpty(),
        originalTitle = originalTitle ?: originalName,
        year = year,
        posterUrl = TmdbImages.poster(posterPath),
        overview = overview?.takeIf(String::isNotBlank),
        score = score,
        isAnime = isAnimeWork(genreIds, originCountry, originalLanguage),
    )
}

@Serializable
internal data class TmdbAltTitles(
    val titles: List<TmdbAltTitle> = emptyList(),
    val results: List<TmdbAltTitle> = emptyList(),
)

@Serializable
internal data class TmdbAltTitle(val title: String? = null)

@Serializable
internal data class TmdbDetails(
    val id: Long,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("last_episode_to_air") val lastEpisode: TmdbRuntime? = null,
    @SerialName("next_episode_to_air") val nextEpisode: TmdbNextEpisode? = null,
    val status: String? = null,
    val videos: TmdbVideos? = null,
    val genres: List<TmdbNamed> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("belongs_to_collection") val collection: TmdbNamed? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("external_ids") val externalIds: TmdbExternalIds? = null,
    val seasons: List<TmdbSeasonRef> = emptyList(),
    val credits: TmdbCredits? = null,
    val images: TmdbImageSet? = null,
    @SerialName("created_by") val createdBy: List<TmdbNamed> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
) {
    val displayTitle: String? get() = (title ?: name)?.takeIf(String::isNotBlank)
    val original: String? get() = (originalTitle ?: originalName)?.takeIf(String::isNotBlank)
    val year: Int? get() = yearOf(releaseDate ?: firstAirDate)
    val runtimeMinutes: Int?
        get() = runtime?.takeIf { it > 0 }
            ?: episodeRunTime.firstOrNull { it > 0 }
            ?: lastEpisode?.runtime?.takeIf { it > 0 }

    fun candidate(kind: MetaKind, score: Double, isAnime: Boolean) = MatchCandidate(
        kind = kind,
        tmdbId = id,
        title = displayTitle ?: original.orEmpty(),
        originalTitle = original,
        year = year,
        posterUrl = TmdbImages.poster(posterPath),
        overview = overview?.takeIf(String::isNotBlank),
        score = score,
        isAnime = isAnime,
    )
}

@Serializable
internal data class TmdbRuntime(val runtime: Int? = null)

@Serializable
internal data class TmdbNextEpisode(
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("season_number") val season: Int = -1,
    @SerialName("episode_number") val number: Int = 0,
)

@Serializable
internal data class TmdbVideos(val results: List<TmdbVideo> = emptyList())

@Serializable
internal data class TmdbVideo(
    @SerialName("iso_639_1") val language: String? = null,
    val key: String? = null,
    val site: String? = null,
    val type: String? = null,
    val official: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
internal data class TmdbNamed(val id: Long = 0, val name: String? = null)

@Serializable
internal data class TmdbExternalIds(@SerialName("imdb_id") val imdbId: String? = null)

@Serializable
internal data class TmdbSeasonRef(@SerialName("season_number") val number: Int)

@Serializable
internal data class TmdbCredits(
    val cast: List<TmdbCast> = emptyList(),
    val crew: List<TmdbCrew> = emptyList(),
)

@Serializable
internal data class TmdbCast(
    val id: Long,
    val name: String? = null,
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = Int.MAX_VALUE,
)

@Serializable
internal data class TmdbCrew(val job: String? = null, val name: String? = null)

@Serializable
internal data class TmdbImageSet(val logos: List<TmdbImage> = emptyList())

@Serializable
internal data class TmdbImage(
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
)

@Serializable
internal data class TmdbSeason(val episodes: List<TmdbEpisode> = emptyList())

@Serializable
internal data class TmdbEpisode(
    @SerialName("episode_number") val number: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
internal data class TmdbGroupList(val results: List<TmdbGroupRef> = emptyList())

@Serializable
internal data class TmdbGroupRef(
    val id: String,
    val type: Int = 0,
    @SerialName("episode_count") val episodeCount: Int = 0,
)

@Serializable
internal data class TmdbGroupDetail(val groups: List<TmdbGroup> = emptyList())

@Serializable
internal data class TmdbGroup(val order: Int = 0, val episodes: List<TmdbGroupEpisode> = emptyList())

@Serializable
internal data class TmdbGroupEpisode(
    val order: Int = 0,
    @SerialName("season_number") val season: Int,
    @SerialName("episode_number") val episode: Int,
)

@Serializable
internal data class TmdbFind(
    @SerialName("movie_results") val movies: List<TmdbItem> = emptyList(),
    @SerialName("tv_results") val shows: List<TmdbItem> = emptyList(),
)
