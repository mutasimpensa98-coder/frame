package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.net.SubtitleQuery

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
}
