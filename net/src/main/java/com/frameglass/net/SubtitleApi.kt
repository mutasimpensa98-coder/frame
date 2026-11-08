package com.frameglass.net

import java.io.File

/*
 * F-S9, beside MetadataClient in NetApi.kt. Every call fails softly with NetResult.Failure.
 * Nothing here may throw on a network error.
 */

/**
 * One search, made on a tap. [languages] takes tags such as `ar` or `en`, and empty means both.
 * Arabic always ranks first and English second, whatever the order here.
 *
 * [tmdbId] and [imdbId] name the show for an episode and the film for a movie.
 *
 * [osHash], [fileSize] and [releaseName] are the D9 exception, so leave them null unless the owner
 * allowed them. The hash goes to OpenSubtitles and nowhere else. [releaseName] leaves the phone only
 * as the OpenSubtitles text search for a file with no IDs. With IDs it only orders the results here.
 * The OpenSubtitles REST search has no size parameter, so [fileSize] is not sent to anyone.
 *
 * [automatic] marks a search the owner did not tap (the waiver of 2026-11-08). It skips every site whose quota is
 * spent only on a tap, which is OpenSubtitles, and the three D9 fields must then be null.
 */
data class SubtitleQuery(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val tmdbId: Long?,
    val imdbId: String?,
    val languages: List<String>,
    val osHash: String?,
    val fileSize: Long?,
    val releaseName: String?,
    val automatic: Boolean = false,
)

/**
 * [provider] is the name to show. [id] only means something to [SubtitleClient.download].
 * [language] is one of the tags that were searched. [format] is a file extension when the provider
 * states one. [seasonPack] marks an archive that holds more than the asked episode.
 */
data class SubtitleHit(
    val provider: String,
    val id: String,
    val language: String,
    val release: String,
    val hashMatch: Boolean,
    val downloads: Int?,
    val format: String?,
    val seasonPack: Boolean,
)

/**
 * One subtitle site as the sheet names it. [key] is null for a site that needs no key, which is searched whenever
 * Online features are on. [episodesOnly] marks a site that holds TV episodes and no films. [tapOnly] marks a site
 * whose small download quota is spent only on the owner's tap, so an automatic search never asks it.
 */
data class SubtitleSite(val name: String, val key: ApiKey?, val episodesOnly: Boolean, val tapOnly: Boolean = false)

interface SubtitleClient {
    /** The providers with a key, in the default order. Gate 6 may change that order. */
    val configuredProviders: List<ApiKey>

    /** Every site, keyed or not, in the default order. Reading it costs nothing and sends nothing. */
    val sites: List<SubtitleSite>

    /**
     * The sites that need no key, and those in [configuredProviders], at once. A site that cannot hold the query,
     * such as a film on an episodes-only site, is not asked. A provider that fails is left out, and a failure comes
     * back only when every provider failed. [NetResult.Reason.NO_KEY] means no site could be asked at all. Search
     * spends no OpenSubtitles quota. An [SubtitleQuery.automatic] query leaves out every [SubtitleSite.tapOnly] site.
     */
    suspend fun search(query: SubtitleQuery): NetResult<List<SubtitleHit>>

    /**
     * Saves one subtitle under [targetDir] and returns it, as .srt, .ass, .ssa or .vtt. Only this call
     * spends OpenSubtitles quota, and a hit already saved there is returned without spending any.
     * [season] and [episode] pick the right file inside a pack, and a pack that does not hold that
     * episode fails with [NetResult.Reason.NOT_FOUND] rather than guessing.
     */
    suspend fun download(hit: SubtitleHit, season: Int?, episode: Int?, targetDir: File): NetResult<File>
}
