package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val SEARCH_PAGE_SIZE = 8
private const val MAX_SEARCHES = 2

private val SEARCH_QUERY = """
    query (${'$'}search: String) {
      Page(perPage: $SEARCH_PAGE_SIZE) {
        media(search: ${'$'}search, type: ANIME, isAdult: false, sort: SEARCH_MATCH) {
          id idMal format startDate { year } title { romaji english native } synonyms
        }
      }
    }
""".trimIndent()

internal class AniHit(val media: AniMedia, val score: Double)

/** F-M9 step 1. The title is the only thing sent, and the query is fixed. */
internal class AniListApi(private val http: Http) {
    private val endpoint: HttpUrl = "https://graphql.anilist.co".toHttpUrl()

    /**
     * Compares against [names] with the F-M2 rules. Null means no confident entry. The search
     * spends a request per name, and the rate limit is degraded to 30 a minute, so at most two.
     */
    suspend fun identify(names: List<String>, year: Int?): NetResult<AniHit?> {
        for (text in names.filter(String::isNotBlank).distinct().take(MAX_SEARCHES)) {
            when (val found = search(text)) {
                is Failure -> return found
                is Ok -> {
                    val hits = found.value.map { AniHit(it, Scoring.score(names, it.names, year, it.startYear)) }
                    Scoring.pick(hits, AniHit::score)?.let { return Ok(it) }
                }
            }
        }
        return Ok(null)
    }

    private suspend fun search(text: String): NetResult<List<AniMedia>> {
        val body = buildJsonObject {
            put("query", SEARCH_QUERY)
            put("variables", buildJsonObject { put("search", text) })
        }
        return when (val reply = http.postJson(endpoint, body.toString(), "anilist").decoded<AniReply>("anilist")) {
            is Failure -> reply
            is Ok -> reply.value.data?.page?.media?.let { Ok(it) } ?: Failure(Reason.BAD_RESPONSE, "anilist")
        }
    }
}

@Serializable
internal data class AniReply(val data: AniData? = null)

@Serializable
internal data class AniData(@SerialName("Page") val page: AniPage? = null)

@Serializable
internal data class AniPage(val media: List<AniMedia> = emptyList())

@Serializable
internal data class AniMedia(
    val id: Long,
    val idMal: Long? = null,
    val format: String? = null,
    val startDate: AniDate? = null,
    val title: AniTitle = AniTitle(),
    val synonyms: List<String?> = emptyList(),
) {
    val isMovie: Boolean get() = format == "MOVIE"
    val startYear: Int? get() = startDate?.year
    val names: List<String> get() = listOfNotNull(title.romaji, title.english, title.native) + synonyms.filterNotNull()

    /** What TMDB is most likely to carry in en-US, best first. */
    val searchNames: List<String> get() = listOfNotNull(title.english, title.romaji)
}

@Serializable
internal data class AniDate(val year: Int? = null)

@Serializable
internal data class AniTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)
