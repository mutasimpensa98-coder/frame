package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import java.io.File
import java.net.URLEncoder
import java.util.TreeMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal const val PROVIDER_OPENSUBTITLES = "OpenSubtitles"

private const val LABEL = "opensubtitles"
private const val ROOT = "https://api.opensubtitles.com/api/v1"

/** The daily download limit. The quota lasts 24 h, so waiting here would not help. */
private const val QUOTA_REACHED = 406

private val MOVIE_HASH = Regex("[0-9a-fA-F]{16}")
private val IMDB_NUMBER = Regex("(?:tt)?0*([0-9]+)")

/**
 * OpenSubtitles REST v1, read from its official client and docs on 2026-11-07. No login, so one key
 * gets the 5 free downloads a day, and search spends nothing. Only [fetch] calls POST /download, and
 * the link it returns is fetched with no credentials.
 */
internal class OpenSubtitlesApi(private val http: Http, private val config: NetConfig) : SubtitleProvider {
    override val name = PROVIDER_OPENSUBTITLES
    override val keyKind = ApiKey.OPENSUBTITLES

    /** 5 downloads a day without login (F-S9), so only the owner's tap may spend them. */
    override val tapOnly = true

    override suspend fun search(query: SubtitleQuery, languages: List<String>): NetResult<List<SubtitleHit>> {
        val key = config.keyOf(keyKind) ?: return Failure(Reason.NO_KEY)
        return eachLanguage(languages) { language ->
            val params = searchParams(query, language) ?: return@eachLanguage Ok(emptyList())
            val request = apiRequest(key).url(searchUrl(params)).build()
            http.text(request, LABEL, keyed = true).decoded<OsSearchReply>(LABEL).map { reply ->
                reply.data.mapNotNull { it.hit(language) }
            }
        }
    }

    /**
     * The API answers 301 to anything but its canonical form, which costs a request: names sorted,
     * values lower case, IMDb IDs as plain numbers, a space as +, and defaults left out.
     */
    private fun searchUrl(params: Map<String, String>): HttpUrl {
        val builder = "$ROOT/subtitles".toHttpUrl().newBuilder()
        for ((name, value) in params) {
            builder.addEncodedQueryParameter(name, URLEncoder.encode(value, Charsets.UTF_8))
        }
        return builder.build()
    }

    /**
     * Null when the query names nothing. A movie's own ID differs from a show's parent ID. Only an
     * episode sets the type, because a file with no season may still be an episode nobody parsed.
     */
    private fun searchParams(query: SubtitleQuery, language: String): Map<String, String>? {
        val params = TreeMap<String, String>()
        params["languages"] = language
        if (query.isEpisodic) params["type"] = "episode"
        query.season?.let { params["season_number"] = it.toString() }
        query.episode?.let { params["episode_number"] = it.toString() }
        val hash = query.osHash?.takeIf(MOVIE_HASH::matches)?.lowercase()
        hash?.let { params["moviehash"] = it }

        val imdb = query.imdbId?.let { IMDB_NUMBER.matchEntire(it.trim()) }?.groupValues?.get(1)
        val prefix = if (query.isEpisodic) "parent_" else ""
        val text = query.releaseName?.takeIf(String::isNotBlank) ?: query.title.takeIf(String::isNotBlank)
        when {
            imdb != null -> params["${prefix}imdb_id"] = imdb
            query.tmdbId != null -> params["${prefix}tmdb_id"] = query.tmdbId.toString()
            text != null -> {
                params["query"] = text.trim().lowercase()
                if (!query.isEpisodic) query.year?.let { params["year"] = it.toString() }
            }
            hash == null -> return null
        }
        return params
    }

    override suspend fun fetch(hit: SubtitleHit, target: File): NetResult<Unit> {
        val key = config.keyOf(keyKind) ?: return Failure(Reason.NO_KEY)
        val fileId = hit.id.toLongOrNull() ?: return Failure(Reason.BAD_RESPONSE, LABEL)
        val body = """{"file_id":$fileId,"sub_format":"srt"}""".toRequestBody(JSON_MEDIA_TYPE)
        val ticket = apiRequest(key).url("$ROOT/download").post(body).build()
        val link = when (val reply = http.fetch(ticket, LABEL)) {
            is Failure -> return reply
            is Ok -> when (val found = linkOf(reply.value)) {
                is Failure -> return found
                is Ok -> found.value
            }
        }
        val request = Request.Builder().url(link).header("User-Agent", SUBTITLE_USER_AGENT).build()
        return http.download(request, target, LABEL, maxBytes = MAX_PAYLOAD_BYTES)
    }

    private fun linkOf(reply: HttpReply): NetResult<HttpUrl> {
        if (reply.code == QUOTA_REACHED) return Failure(Reason.RATE_LIMIT, "$LABEL quota")
        if (!reply.isOk) return failureFor(reply.code, LABEL, keyed = true)
        val ticket = Ok(reply.body).decoded<OsDownloadReply>(LABEL).valueOrNull()
            ?: return Failure(Reason.BAD_RESPONSE, LABEL)
        val link = ticket.link?.toHttpUrlOrNull()
        return when {
            link != null -> Ok(link)
            ticket.remaining != null && ticket.remaining <= 0 -> Failure(Reason.RATE_LIMIT, "$LABEL quota")
            else -> Failure(Reason.BAD_RESPONSE, LABEL)
        }
    }

    private fun apiRequest(key: String): Request.Builder = Request.Builder()
        .header("Api-Key", key)
        .header("User-Agent", SUBTITLE_USER_AGENT)
        .header("Accept", "application/json")
}

@Serializable
internal data class OsSearchReply(val data: List<OsItem> = emptyList())

@Serializable
internal data class OsItem(val attributes: OsAttributes = OsAttributes()) {
    /** A rare two-CD subtitle lists a file per disc. The first stands for it, as in other clients. */
    fun hit(language: String): SubtitleHit? {
        val file = attributes.files.firstOrNull() ?: return null
        return SubtitleHit(
            provider = PROVIDER_OPENSUBTITLES,
            id = file.fileId.toString(),
            language = language,
            release = attributes.release?.takeIf(String::isNotBlank) ?: file.fileName.orEmpty(),
            hashMatch = attributes.moviehashMatch,
            downloads = attributes.downloadCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
            format = attributes.format?.takeIf(String::isNotBlank),
            seasonPack = false,
        )
    }
}

@Serializable
internal data class OsAttributes(
    val release: String? = null,
    val format: String? = null,
    @SerialName("download_count") val downloadCount: Long? = null,
    @SerialName("moviehash_match") val moviehashMatch: Boolean = false,
    val files: List<OsFile> = emptyList(),
)

@Serializable
internal data class OsFile(
    @SerialName("file_id") val fileId: Long,
    @SerialName("file_name") val fileName: String? = null,
)

@Serializable
internal data class OsDownloadReply(val link: String? = null, val remaining: Int? = null)
