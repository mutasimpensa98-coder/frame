package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** These match the names of SegmentKind in :data. */
private const val KIND_OP = "OP"
private const val KIND_ED = "ED"
private const val KIND_RECAP = "RECAP"

internal const val SOURCE_ANISKIP = "aniskip"
internal const val SOURCE_THEINTRODB = "theintrodb"

/** TheIntroDB clusters release versions at 5 minutes, so further apart means a different cut. */
private const val MAX_CUT_DIFFERENCE_MS = 5L * 60 * 1000

/**
 * F-S4 online sources. Neither needs a key. A provider with nothing for this episode answers
 * 404, which is a normal empty result here and not a failure.
 */
internal class SkipApi(private val http: Http) {
    private val aniSkipRoot: HttpUrl = "https://api.aniskip.com/v2/skip-times".toHttpUrl()
    private val introDbRoot: HttpUrl = "https://api.theintrodb.org/v4/media".toHttpUrl()

    suspend fun aniSkip(malId: Long, episode: Int, lengthMs: Long): NetResult<List<NetSegment>> {
        val url = aniSkipRoot.newBuilder()
            .addPathSegment(malId.toString())
            .addPathSegment(episode.toString())
            .addEncodedQueryParameter("types[]", "op")
            .addEncodedQueryParameter("types[]", "ed")
            .addEncodedQueryParameter("types[]", "recap")
            // Required by the API, in seconds. 0 turns its length filter off.
            .addQueryParameter("episodeLength", (lengthMs / 1000L).coerceAtLeast(0L).toString())
            .build()
        return emptyOn404(http.get(url, "aniskip").decoded<AniSkipReply>("aniskip").map { reply ->
            reply.results.mapNotNull(AniSkipResult::segment)
        })
    }

    /**
     * Addressed by TMDB ID. `duration_ms` would pick the right cut, but R2 allows only IDs and
     * numbers to leave the phone, so `details=true` returns the cut's length and it is compared here.
     */
    suspend fun theIntroDb(tmdbId: Long, season: Int?, episode: Int?, lengthMs: Long): NetResult<List<NetSegment>> {
        val url = introDbRoot.newBuilder()
            .addQueryParameter("tmdb_id", tmdbId.toString())
            .apply {
                if (season != null && episode != null) {
                    addQueryParameter("season", season.toString())
                    addQueryParameter("episode", episode.toString())
                }
            }
            .addQueryParameter("details", "true")
            .build()
        return emptyOn404(http.get(url, "theintrodb").decoded<IntroDbReply>("theintrodb").map { it.segments(lengthMs) })
    }

    private fun emptyOn404(result: NetResult<List<NetSegment>>): NetResult<List<NetSegment>> =
        if (result is Failure && result.reason == Reason.NOT_FOUND) Ok(emptyList()) else result
}

@Serializable
internal data class AniSkipReply(val results: List<AniSkipResult> = emptyList())

@Serializable
internal data class AniSkipResult(val interval: AniSkipInterval, val skipType: String? = null) {
    fun segment(): NetSegment? {
        val kind = when (skipType) {
            "op" -> KIND_OP
            "ed" -> KIND_ED
            "recap" -> KIND_RECAP
            else -> return null
        }
        val start = (interval.startTime * 1000.0).roundToLong()
        val end = (interval.endTime * 1000.0).roundToLong()
        return if (end > start) NetSegment(kind, start, end, SOURCE_ANISKIP) else null
    }
}

@Serializable
internal data class AniSkipInterval(val startTime: Double, val endTime: Double)

@Serializable
internal data class IntroDbReply(
    @SerialName("group_duration_ms") val groupDurationMs: Long? = null,
    val intro: List<IntroDbSpan> = emptyList(),
    val recap: List<IntroDbSpan> = emptyList(),
    val credits: List<IntroDbSpan> = emptyList(),
) {
    fun segments(lengthMs: Long): List<NetSegment> {
        val group = groupDurationMs
        if (group != null && lengthMs > 0L && abs(group - lengthMs) > MAX_CUT_DIFFERENCE_MS) return emptyList()
        return (
            intro.mapNotNull { it.opening(KIND_OP, lengthMs) } +
                recap.mapNotNull { it.opening(KIND_RECAP, lengthMs) } +
                credits.mapNotNull { it.closing(lengthMs) }
            ).sortedBy(NetSegment::startMs)
    }
}

/** Times are in ms. The spec gives null and 0 different meanings per segment type. */
@Serializable
internal data class IntroDbSpan(
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
) {
    /** Intro and recap: null start is the first frame, end 0 means no segment. */
    fun opening(kind: String, lengthMs: Long): NetSegment? {
        val end = fit(endMs ?: return null, lengthMs)
        val start = startMs ?: 0L
        return if (end > start) NetSegment(kind, start, end, SOURCE_THEINTRODB) else null
    }

    /** Credits: start 0 or end 0 mean no segment, null end is the end of the media. */
    fun closing(lengthMs: Long): NetSegment? {
        val start = startMs?.takeIf { it > 0L } ?: return null
        val end = if (endMs == null) lengthMs else fit(endMs, lengthMs)
        return if (end > start) NetSegment(KIND_ED, start, end, SOURCE_THEINTRODB) else null
    }

    private fun fit(value: Long, lengthMs: Long): Long = if (lengthMs > 0L) minOf(value, lengthMs) else value
}
