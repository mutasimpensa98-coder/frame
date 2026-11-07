package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

internal const val PROVIDER_SUBDL = "SubDL"

private const val LABEL = "subdl"
private const val DOWNLOAD_ROOT = "https://dl.subdl.com"

/** The API's own cap. */
private const val PAGE_SIZE = 30

private val SEARCH_ROOT: HttpUrl = "https://api.subdl.com/api/v1/subtitles".toHttpUrl()

/** The API answers 400 to these, and a space is what its index expects in their place. */
private val UNSAFE_TITLE = Regex("""[<>{}\[\]'"`´’;\\/]+""")
private val SPACES = Regex("[ \t]+")

/** Every segment has a character, so a path cannot start with // and leave the fixed host. */
private val DOWNLOAD_PATH = Regex("(?:/[A-Za-z0-9._~%-]+)+")

private val AUTH_ERRORS = listOf("not authorized", "api key", "invalid key")
private val EMPTY_ERRORS = listOf("can't find", "cannot find", "not found")

/** SubDL REST v1, read from its docs and working clients on 2026-11-07. The key rides in the URL. */
internal class SubDlApi(private val http: Http, private val config: NetConfig) : SubtitleProvider {
    override val name = PROVIDER_SUBDL
    override val keyKind = ApiKey.SUBDL

    override suspend fun search(query: SubtitleQuery, languages: List<String>): NetResult<List<SubtitleHit>> {
        val key = config.keyOf(keyKind) ?: return Failure(Reason.NO_KEY)
        if (query.tmdbId == null && query.imdbId.isNullOrBlank() && query.title.isBlank()) return Ok(emptyList())
        return eachLanguage(languages) { language ->
            val request = Request.Builder()
                .url(searchUrl(query, language, key))
                .header("User-Agent", SUBTITLE_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            when (val reply = http.text(request, LABEL, keyed = true).decoded<SubDlReply>(LABEL)) {
                is Failure -> reply
                is Ok -> read(reply.value, query, language)
            }
        }
    }

    private fun searchUrl(query: SubtitleQuery, language: String, key: String): HttpUrl =
        SEARCH_ROOT.newBuilder().apply {
            addQueryParameter("api_key", key)
            addQueryParameter("languages", language.uppercase())
            addQueryParameter("subs_per_page", PAGE_SIZE.toString())
            addQueryParameter("releases", "1")
            addQueryParameter("type", if (query.isEpisodic) "tv" else "movie")
            when {
                query.tmdbId != null -> addQueryParameter("tmdb_id", query.tmdbId.toString())
                !query.imdbId.isNullOrBlank() -> addQueryParameter("imdb_id", query.imdbId.trim())
                else -> {
                    addQueryParameter("film_name", safeTitle(query.title))
                    if (!query.isEpisodic) query.year?.let { addQueryParameter("year", it.toString()) }
                }
            }
            query.season?.let { addQueryParameter("season_number", it.toString()) }
            query.episode?.let { addQueryParameter("episode_number", it.toString()) }
        }.build()

    private fun safeTitle(title: String): String = title.replace(UNSAFE_TITLE, " ").replace(SPACES, " ").trim()

    /** A status of false on a 200 can mean "nothing found", which is not a failure. */
    private fun read(reply: SubDlReply, query: SubtitleQuery, language: String): NetResult<List<SubtitleHit>> {
        if (reply.status == false) {
            val error = reply.error.orEmpty().lowercase()
            return when {
                AUTH_ERRORS.any { it in error } -> Failure(Reason.NO_KEY, "$LABEL rejected the key")
                error.isEmpty() || EMPTY_ERRORS.any { it in error } -> Ok(emptyList())
                else -> Failure(Reason.BAD_RESPONSE, LABEL)
            }
        }
        return Ok(reply.subtitles.mapNotNull { it.hit(query, language) })
    }

    override suspend fun fetch(hit: SubtitleHit, target: File): NetResult<Unit> {
        val path = hit.id.takeIf(::isDownloadPath) ?: return Failure(Reason.BAD_RESPONSE, LABEL)
        val request = Request.Builder().url(DOWNLOAD_ROOT + path).header("User-Agent", SUBTITLE_USER_AGENT).build()
        return http.download(request, target, LABEL, maxBytes = MAX_PAYLOAD_BYTES)
    }
}

/** Never a full URL: a reply must not be able to pick the host a download comes from. */
internal fun isDownloadPath(path: String): Boolean = DOWNLOAD_PATH.matches(path) && ".." !in path

@Serializable
internal data class SubDlReply(
    val status: Boolean? = null,
    val error: String? = null,
    val subtitles: List<SubDlItem> = emptyList(),
)

/** Season and episode fields are 0 or null when the uploader recorded none. */
@Serializable
internal data class SubDlItem(
    @SerialName("release_name") val releaseName: String? = null,
    val name: String? = null,
    val url: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("episode_from") val episodeFrom: Int? = null,
    @SerialName("episode_end") val episodeEnd: Int? = null,
    @SerialName("full_season") val fullSeason: Boolean = false,
    val releases: List<String?> = emptyList(),
) {
    private val releaseNames: List<String>
        get() = (releases + releaseName).filterNotNull().filter(String::isNotBlank).distinct()

    fun hit(query: SubtitleQuery, language: String): SubtitleHit? {
        val path = url?.takeIf(::isDownloadPath) ?: return null
        val fit = fit(query)
        if (fit == EpisodeFit.OTHER) return null
        return SubtitleHit(
            provider = PROVIDER_SUBDL,
            id = path,
            language = language,
            release = releaseName?.takeIf(String::isNotBlank) ?: releaseNames.firstOrNull() ?: name.orEmpty(),
            hashMatch = false,
            downloads = null,
            format = null,
            seasonPack = fit == EpisodeFit.PACK,
        )
    }

    private fun fit(query: SubtitleQuery): EpisodeFit {
        val wanted = query.episode ?: return EpisodeFit.EXACT
        val ownSeason = season?.takeIf { it > 0 }
        if (ownSeason != null && query.season != null && ownSeason != query.season) return EpisodeFit.OTHER
        val from = episodeFrom?.takeIf { it > 0 }
        val end = episodeEnd?.takeIf { it > 0 }
        return when {
            fullSeason -> EpisodeFit.PACK
            from != null && end != null && end > from -> if (wanted in from..end) EpisodeFit.PACK else EpisodeFit.OTHER
            else -> {
                val own = (episode ?: from)?.takeIf { it > 0 }
                when {
                    own == null -> fitOf(releaseNames, query.season, wanted)
                    own == wanted -> EpisodeFit.EXACT
                    else -> EpisodeFit.OTHER
                }
            }
        }
    }
}
