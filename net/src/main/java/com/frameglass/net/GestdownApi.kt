package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import com.frameglass.parser.ArabicNormalizer
import java.io.File
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

internal const val PROVIDER_GESTDOWN = "Gestdown"

private const val LABEL = "gestdown"
private val ROOT: HttpUrl = "https://api.gestdown.info".toHttpUrl()

/** Show and subtitle ids in live replies were 22 base62 characters. Anything else never reaches a path. */
private val GESTDOWN_ID = Regex("[A-Za-z0-9]{1,64}")

/** "Doctor Who (2005)" and "The Office (US)" are how the site tells shows apart, and a file never says that. */
private val NAME_SUFFIX = Regex("""\s*\([^)]*\)\s*$""")

private const val JSON_TYPE = "application/json"

/**
 * [UNVERIFIED] Downloads are plain SRT, so asking for JSON could draw a 406 from a server that negotiates. No download
 * has been tried live with either header; any type is the safe ask.
 */
private const val ANY_TYPE = "*/*"

/** [UNVERIFIED] Answered with no body to a language tag the site does not know, on 2026-11-08. */
private const val UNKNOWN_LANGUAGE = 423

/**
 * Gestdown, a public API in front of Addic7ed that needs no key, read from live replies on 2026-11-08. Podnapisi was
 * the first choice, but its hosts had no DNS records that day. Gestdown holds TV episodes only, so a film or a file
 * with no episode number never reaches it. A show is found by name, and the show's TMDB id, when known, picks among
 * the shows that answer. Only the show name, season, episode and language go out: never the release name or the hash.
 * Downloads are plain SRT from the API host, with no redirect.
 */
internal class GestdownApi(private val http: Http) : SubtitleProvider {
    override val name = PROVIDER_GESTDOWN
    override val keyKind: ApiKey? = null
    override val episodesOnly = true

    override fun serves(query: SubtitleQuery): Boolean = query.episode != null && query.title.isNotBlank()

    override suspend fun search(query: SubtitleQuery, languages: List<String>): NetResult<List<SubtitleHit>> {
        val episode = query.episode ?: return Ok(emptyList())
        if (query.title.isBlank()) return Ok(emptyList())
        // A file with no season is almost always the first one, as in Show - 05.mkv.
        val season = query.season ?: 1
        val show = when (val found = showOf(query, season)) {
            is Failure -> return found
            is Ok -> found.value ?: return Ok(emptyList())
        }
        return eachLanguage(languages) { language -> episodeSubtitles(show, season, episode, language) }
    }

    private suspend fun showOf(query: SubtitleQuery, season: Int): NetResult<String?> {
        val url = ROOT.newBuilder().addPathSegments("shows/search").addPathSegment(query.title.trim()).build()
        return emptyOn404(get(url).decoded<GdShows>(LABEL)).map { reply -> GdShows.pick(reply?.shows, query, season) }
    }

    private suspend fun episodeSubtitles(show: String, season: Int, episode: Int, language: String): NetResult<List<SubtitleHit>> {
        val url = ROOT.newBuilder()
            .addPathSegments("subtitles/get")
            .addPathSegment(show)
            .addPathSegment(season.toString())
            .addPathSegment(episode.toString())
            .addPathSegment(language)
            .build()
        val reply = when (val fetched = http.fetch(request(url), LABEL)) {
            is Failure -> return fetched
            is Ok -> fetched.value
        }
        if (reply.code == UNKNOWN_LANGUAGE || reply.code == 404) return Ok(emptyList())
        if (!reply.isOk) return failureFor(reply.code, LABEL, keyed = false)
        return Ok(reply.body).decoded<GdEpisodeReply>(LABEL).map { it.hits(language) }
    }

    override suspend fun fetch(hit: SubtitleHit, target: File): NetResult<Unit> {
        if (!GESTDOWN_ID.matches(hit.id)) return Failure(Reason.BAD_RESPONSE, LABEL)
        val url = ROOT.newBuilder().addPathSegments("subtitles/download").addPathSegment(hit.id).build()
        return http.download(request(url, accept = ANY_TYPE), target, LABEL, maxBytes = MAX_PAYLOAD_BYTES)
    }

    private suspend fun get(url: HttpUrl): NetResult<String> = http.text(request(url), LABEL, keyed = false)

    private fun request(url: HttpUrl, accept: String = JSON_TYPE): Request = Request.Builder().url(url)
        .header("User-Agent", SUBTITLE_USER_AGENT)
        .header("Accept", accept)
        .build()

    private fun <T> emptyOn404(result: NetResult<T>): NetResult<T?> =
        if (result is Failure && result.reason == Reason.NOT_FOUND) Ok(null) else result
}

@Serializable
internal data class GdShows(val shows: List<GdShow> = emptyList()) {
    companion object {
        /**
         * The show's TMDB id wins. Without one, the name must match a single show that has the season, because the
         * wrong show is worse than none.
         */
        fun pick(shows: List<GdShow>?, query: SubtitleQuery, season: Int): String? {
            val usable = shows.orEmpty().filter { GESTDOWN_ID.matches(it.id) }
            query.tmdbId?.let { id -> usable.firstOrNull { it.tmdbId == id }?.let { return it.id } }
            val wanted = ArabicNormalizer.key(query.title)
            val named = usable.filter { show ->
                (query.tmdbId == null || show.tmdbId == null) &&
                    ArabicNormalizer.key(show.name.replace(NAME_SUFFIX, "")) == wanted &&
                    (show.seasons.isEmpty() || season in show.seasons)
            }
            return named.singleOrNull()?.id
        }
    }
}

@Serializable
internal data class GdShow(
    val id: String,
    val name: String = "",
    val seasons: List<Int> = emptyList(),
    val tmdbId: Long? = null,
)

@Serializable
internal data class GdEpisodeReply(val matchingSubtitles: List<GdSubtitle> = emptyList()) {
    fun hits(language: String): List<SubtitleHit> = matchingSubtitles.mapNotNull { it.hit(language) }
}

/** [completed] is false while a translation is still under way on the site. */
@Serializable
internal data class GdSubtitle(
    val subtitleId: String,
    val version: String? = null,
    val release: String? = null,
    val completed: Boolean = true,
    val downloadCount: Long? = null,
) {
    fun hit(language: String): SubtitleHit? {
        if (!completed || !GESTDOWN_ID.matches(subtitleId)) return null
        return SubtitleHit(
            provider = PROVIDER_GESTDOWN,
            id = subtitleId,
            language = language,
            release = listOfNotNull(release, version).firstOrNull(String::isNotBlank)?.trim().orEmpty(),
            hashMatch = false,
            downloads = downloadCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
            format = "srt",
            seasonPack = false,
        )
    }
}
