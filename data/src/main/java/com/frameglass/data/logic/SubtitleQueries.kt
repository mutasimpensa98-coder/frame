package com.frameglass.data.logic

import com.frameglass.data.api.ApiKeyKind
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.SubtitleSiteInfo
import com.frameglass.data.api.SubtitleSites
import com.frameglass.net.SubtitleQuery
import com.frameglass.net.SubtitleSite

/** What is known about the file on screen. Nothing here leaves the phone until [SubtitleQueries.build] lets it. */
internal data class SubtitleFacts(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val tmdbId: Long?,
    val imdbId: String?,
    val osHash: String?,
    val fileSize: Long?,
    val releaseName: String?,
)

/** Decision D9. Only a search the owner taps comes through here, and only his two switches open the extra fields. */
internal object SubtitleQueries {
    private val LANGUAGES = listOf("ar", "en")

    fun build(facts: SubtitleFacts, settings: AppSettings): SubtitleQuery {
        val hash = facts.osHash.takeIf { settings.sendFileHash }
        return SubtitleQuery(
            title = facts.title,
            year = facts.year,
            season = facts.season,
            episode = facts.episode,
            tmdbId = facts.tmdbId,
            imdbId = facts.imdbId,
            languages = LANGUAGES,
            osHash = hash,
            fileSize = facts.fileSize.takeIf { hash != null },
            releaseName = facts.releaseName.takeIf { settings.sendReleaseName },
        )
    }

    /**
     * The search the owner did not tap (waiver of 2026-11-08). Decision D9 holds whatever the switches say: only the
     * title, year, season, episode and IDs, never the release name, the hash or the size.
     */
    fun automatic(facts: SubtitleFacts, languages: List<String>): SubtitleQuery = SubtitleQuery(
        title = facts.title,
        year = facts.year,
        season = facts.season,
        episode = facts.episode,
        tmdbId = facts.tmdbId,
        imdbId = facts.imdbId,
        languages = languages,
        osHash = null,
        fileSize = null,
        releaseName = null,
        automatic = true,
    )
}

internal object SubtitleSiteList {
    /** [sites] in their default order. A site that needs no key is always active. */
    fun of(sites: List<SubtitleSite>, savedKeys: Set<ApiKeyKind>): SubtitleSites {
        val infos = sites.map {
            SubtitleSiteInfo(it.name, it.key?.let { key -> ApiKeyKind.valueOf(key.name) }, it.episodesOnly, it.tapOnly)
        }
        val (active, locked) = infos.partition { it.key == null || it.key in savedKeys }
        return SubtitleSites(active = active, withFreeKey = locked)
    }
}
