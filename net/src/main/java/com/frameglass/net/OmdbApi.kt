package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

private const val OMDB_URL = "https://www.omdbapi.com/"

/** F-M10. OMDb puts its error in the body of a 401. The poster field is never read. */
internal class OmdbApi(private val http: Http, private val config: NetConfig) {
    suspend fun rating(imdbId: String): NetResult<Double> {
        val key = config.key(ApiKey.OMDB)?.trim()?.takeIf(String::isNotEmpty) ?: return Failure(Reason.NO_KEY)
        val url = OMDB_URL.toHttpUrl().newBuilder()
            .addQueryParameter("i", imdbId)
            .addQueryParameter("apikey", key)
            .build()
        return when (val reply = http.fetch(Request.Builder().url(url).build(), "omdb")) {
            is Failure -> reply
            is Ok -> read(reply.value)
        }
    }

    private fun read(reply: HttpReply): NetResult<Double> {
        val parsed = Ok(reply.body).decoded<OmdbReply>("omdb").valueOrNull()
            ?: return if (reply.isOk) Failure(Reason.BAD_RESPONSE, "omdb") else failureFor(reply.code, "omdb", keyed = true)
        val error = parsed.error.orEmpty()
        return when {
            error.contains("limit", ignoreCase = true) -> Failure(Reason.RATE_LIMIT, "omdb")
            reply.code == 401 -> Failure(Reason.NO_KEY, "omdb rejected the key")
            !reply.isOk -> failureFor(reply.code, "omdb", keyed = true)
            else -> parsed.imdbRating?.toDoubleOrNull()?.let { Ok(it) } ?: Failure(Reason.NOT_FOUND, "omdb")
        }
    }
}

@Serializable
internal data class OmdbReply(
    @SerialName("Error") val error: String? = null,
    val imdbRating: String? = null,
)
