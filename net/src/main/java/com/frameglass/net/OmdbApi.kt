package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

private const val OMDB_URL = "https://www.omdbapi.com/"

internal const val OMDB_POSTER_HOST = "m.media-amazon.com"

/** OMDb answers 200 with Response "False" for a miss, in these words. */
private val NOT_FOUND_TEXT = Regex("not found|incorrect imdb id|too many results", RegexOption.IGNORE_CASE)

/** F-M10, and the OMDb source used while TMDB is off. OMDb puts its error in the body, even of a 401. */
internal class OmdbApi(private val http: Http, private val config: NetConfig) {
    fun hasKey(): Boolean = key() != null

    /** Sends the id and the key only. */
    suspend fun rating(imdbId: String): NetResult<Double> = when (val found = call<OmdbRecord>(listOf("i" to imdbId))) {
        is Failure -> found
        is Ok -> found.value.imdbRating.real()?.toDoubleOrNull()?.let { Ok(it) } ?: Failure(Reason.NOT_FOUND, "omdb")
    }

    suspend fun byId(imdbId: String): NetResult<OmdbRecord> = call(listOf("i" to imdbId, "plot" to "full"))

    /** OMDb's single answer for an exact title, narrowed by type and year. */
    suspend fun byTitle(title: String, kind: MetaKind, year: Int?): NetResult<OmdbRecord> = call(
        buildList {
            add("t" to title)
            add("type" to kind.omdbType)
            if (year != null) add("y" to year.toString())
            add("plot" to "full")
        },
    )

    /** One page, at most 10 rows. Episodes and games are left out. */
    suspend fun search(query: String, kind: MetaKind?, year: Int?): NetResult<List<OmdbHit>> {
        val params = buildList {
            add("s" to query)
            if (kind != null) add("type" to kind.omdbType)
            if (year != null) add("y" to year.toString())
        }
        return when (val found = call<OmdbSearchReply>(params)) {
            is Failure -> found
            is Ok -> Ok(found.value.search.filter { it.kind != null && !it.imdbId.isNullOrBlank() })
        }
    }

    suspend fun season(imdbId: String, season: Int): NetResult<List<OmdbEpisode>> =
        when (val found = call<OmdbSeasonReply>(listOf("i" to imdbId, "Season" to season.toString()))) {
            is Failure -> found
            is Ok -> Ok(
                found.value.episodes.mapNotNull { row ->
                    val number = row.episode.real()?.toIntOrNull() ?: return@mapNotNull null
                    OmdbEpisode(
                        season = season,
                        number = number,
                        title = row.title.real()?.takeUnless(PLACEHOLDER_EPISODE::matches),
                        airDate = row.released.real()?.takeIf(ISO_DATE::matches),
                        imdbRating = row.imdbRating.real()?.toDoubleOrNull(),
                    )
                }.distinctBy(OmdbEpisode::number),
            )
        }

    private fun key(): String? = config.key(ApiKey.OMDB)?.trim()?.takeIf(String::isNotEmpty)

    private suspend inline fun <reified T : OmdbBody> call(params: List<Pair<String, String>>): NetResult<T> {
        val key = key() ?: return Failure(Reason.NO_KEY)
        val url = OMDB_URL.toHttpUrl().newBuilder()
            .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
            .addQueryParameter("apikey", key)
            .build()
        return when (val reply = http.fetch(Request.Builder().url(url).build(), "omdb")) {
            is Failure -> reply
            is Ok -> read(reply.value)
        }
    }

    private inline fun <reified T : OmdbBody> read(reply: HttpReply): NetResult<T> {
        val parsed = Ok(reply.body).decoded<T>("omdb").valueOrNull()
            ?: return if (reply.isOk) Failure(Reason.BAD_RESPONSE, "omdb") else failureFor(reply.code, "omdb", keyed = true)
        val error = parsed.error.orEmpty()
        return when {
            error.contains("limit", ignoreCase = true) -> Failure(Reason.RATE_LIMIT, OMDB_DAILY_LIMIT)
            reply.code == 401 -> Failure(Reason.NO_KEY, "omdb rejected the key")
            !reply.isOk -> failureFor(reply.code, "omdb", keyed = true)
            parsed.response.equals("False", ignoreCase = true) ->
                Failure(if (NOT_FOUND_TEXT.containsMatchIn(error)) Reason.NOT_FOUND else Reason.BAD_RESPONSE, "omdb")
            else -> Ok(parsed)
        }
    }

    private val MetaKind.omdbType: String get() = if (this == MetaKind.MOVIE) "movie" else "series"
}

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/** IMDb names an untitled episode "Episode #1.3", which says less than the episode number already does. */
private val PLACEHOLDER_EPISODE = Regex("""^Episode\s*#\s*\d+(\.\d+)?$""", RegexOption.IGNORE_CASE)
private val FIRST_YEAR = Regex("""\d{4}""")
private val MINUTES = Regex("""^(\d+)\s*min""", RegexOption.IGNORE_CASE)
private val CREDIT_NOTE = Regex("""\s*\([^)]*\)""")

/** OMDb writes N/A for every missing field. */
internal fun String?.real(): String? = this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }

internal fun omdbKind(type: String?): MetaKind? = when (type?.trim()?.lowercase()) {
    "movie" -> MetaKind.MOVIE
    "series" -> MetaKind.TV
    else -> null
}

/** "2008–2013", "2019–" and "2010" all start with the first year. */
internal fun omdbYear(text: String?): Int? = text.real()?.let { FIRST_YEAR.find(it)?.value?.toIntOrNull() }

internal fun omdbList(text: String?): List<String> =
    text.real()?.split(',')?.mapNotNull { it.real() }.orEmpty()

internal object OmdbImages {
    private const val WIDTH = 500
    private val SIZE = Regex("""\._V1_SX\d+\.jpg$""")

    /** OMDb links a 300 px poster. The same image service scales it, so the F-M5 poster width is tried first. */
    fun posters(raw: String?): List<String> {
        val url = raw.real()?.replaceFirst("http://", "https://")?.takeIf { it.startsWith("https://") }
            ?: return emptyList()
        return listOf(url.replace(SIZE, "._V1_SX$WIDTH.jpg"), url).distinct()
    }
}

/** OMDb's English genre names, renamed where TMDB's English name for the same genre differs. */
private val GENRE_NAMES = mapOf("Sci-Fi" to "Science Fiction")

internal interface OmdbBody {
    val response: String?
    val error: String?
}

@Serializable
internal data class OmdbRecord(
    @SerialName("Response") override val response: String? = null,
    @SerialName("Error") override val error: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("Year") val year: String? = null,
    @SerialName("Runtime") val runtime: String? = null,
    @SerialName("Genre") val genre: String? = null,
    @SerialName("Director") val director: String? = null,
    @SerialName("Writer") val writer: String? = null,
    @SerialName("Actors") val actors: String? = null,
    @SerialName("Plot") val plot: String? = null,
    @SerialName("Poster") val poster: String? = null,
    @SerialName("Type") val type: String? = null,
    val imdbRating: String? = null,
    val imdbVotes: String? = null,
    @SerialName("imdbID") val imdbId: String? = null,
) : OmdbBody {
    /** Null for an episode, a game, or a reply with no id or name. */
    fun toTitle(): OmdbTitle? {
        val id = imdbId.real() ?: return null
        val name = title.real() ?: return null
        val kind = omdbKind(type) ?: return null
        val crew = omdbList(director).map { CrewMeta("Director", it) } +
            omdbList(writer).mapNotNull { it.replace(CREDIT_NOTE, "").real() }.map { CrewMeta("Writer", it) }
        return OmdbTitle(
            imdbId = id,
            kind = kind,
            title = name,
            year = omdbYear(year),
            overview = plot.real(),
            runtimeMinutes = runtime.real()?.let { MINUTES.find(it)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it > 0 },
            genres = omdbList(genre).map { GENRE_NAMES[it] ?: it }.distinct(),
            cast = omdbList(actors).distinct(),
            crew = crew.distinct(),
            imdbRating = imdbRating.real()?.toDoubleOrNull(),
            imdbVotes = imdbVotes.real()?.replace(",", "")?.toIntOrNull(),
            posterUrls = OmdbImages.posters(poster),
        )
    }
}

@Serializable
internal data class OmdbSearchReply(
    @SerialName("Response") override val response: String? = null,
    @SerialName("Error") override val error: String? = null,
    @SerialName("Search") val search: List<OmdbHit> = emptyList(),
) : OmdbBody

@Serializable
internal data class OmdbHit(
    @SerialName("Title") val title: String? = null,
    @SerialName("Year") val year: String? = null,
    @SerialName("imdbID") val imdbId: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Poster") val poster: String? = null,
) {
    val kind: MetaKind? get() = omdbKind(type)

    fun candidate(queries: List<String>, queryYear: Int?): OmdbCandidate? {
        val id = imdbId.real() ?: return null
        val name = title.real() ?: return null
        val itemYear = omdbYear(year)
        return OmdbCandidate(
            imdbId = id,
            kind = kind ?: return null,
            title = name,
            year = itemYear,
            posterUrl = OmdbImages.posters(poster).lastOrNull(),
            score = Scoring.score(queries, listOf(name), queryYear, itemYear),
        )
    }
}

@Serializable
internal data class OmdbSeasonReply(
    @SerialName("Response") override val response: String? = null,
    @SerialName("Error") override val error: String? = null,
    @SerialName("Episodes") val episodes: List<OmdbEpisodeRow> = emptyList(),
) : OmdbBody

@Serializable
internal data class OmdbEpisodeRow(
    @SerialName("Title") val title: String? = null,
    @SerialName("Released") val released: String? = null,
    @SerialName("Episode") val episode: String? = null,
    val imdbRating: String? = null,
)
