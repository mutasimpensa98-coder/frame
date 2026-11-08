package com.frameglass.data.logic

import com.frameglass.data.api.PickOption
import com.frameglass.data.api.ResumeEntry
import com.frameglass.data.api.UpcomingEpisode
import java.time.LocalDate

/** The next episode a series announced on TMDB, while it is still to come. */
internal object Upcoming {
    private val FINISHED = setOf("ended", "canceled", "cancelled")

    /**
     * [today] and [airDate] are yyyy-MM-dd. TMDB's air date is the network's local date, which in Libya is often the
     * night of that date or the early hours of the next, so the episode stays announced until [airDate] + 1 day.
     * Later than that it waits for a refresh.
     */
    fun of(status: String?, airDate: String?, season: Int?, number: Int?, today: String): UpcomingEpisode? {
        if (status?.lowercase() in FINISHED) return null
        if (airDate == null || season == null || number == null) return null
        val date = runCatching { LocalDate.parse(airDate) }.getOrNull() ?: return null
        val now = runCatching { LocalDate.parse(today) }.getOrNull() ?: return null
        if (date.plusDays(GRACE_DAYS).isBefore(now)) return null
        return UpcomingEpisode(season, number, airDate)
    }

    /** How long after its air date an episode still shows as coming out tonight. */
    const val GRACE_DAYS = 1L

    fun of(info: TitleInfo, today: String): UpcomingEpisode? {
        if (info.isMovie) return null
        val row = info.title
        return of(row.seriesStatus, row.nextAirDate, row.nextSeason, row.nextEpisode, today)
    }
}

/**
 * Launcher shortcuts and the Continue watching widget. The first entry is the F-S10 resume item of the title played
 * last, then other titles in progress, newest first. Clips never appear, and private-folder videos are not in the
 * snapshot at all, so neither can reach the launcher.
 */
internal object QuickResume {
    const val MAX_ENTRIES = 3

    fun build(s: Snapshot, labels: Labels, max: Int = MAX_ENTRIES): List<ResumeEntry> =
        s.infos.values
            .filter { it.available && !it.isClip && it.lastPlayedAt > 0 }
            .mapNotNull { info -> resumeOf(info)?.let { info to it } }
            .sortedWith(compareByDescending<Pair<TitleInfo, Playable>> { it.first.lastPlayedAt }.thenBy { it.first.id })
            .take(max)
            .map { (info, p) ->
                ResumeEntry(
                    titleId = info.id,
                    play = p.ref(),
                    name = info.title.name,
                    episodeLabel = if (info.isMovie || !p.numbered) null else labels.episode(p.season, p.number),
                    coverPath = info.cover,
                    imagePath = p.episode?.stillPath ?: info.title.backdropPath ?: p.best.framePath ?: info.cover,
                    progress = p.partial ?: 0f,
                )
            }

    /** A film resumes only while in progress. A series resumes where F-S10 says, on an available episode. */
    private fun resumeOf(info: TitleInfo): Playable? {
        val playable = if (info.isMovie) info.playables.firstOrNull { it.inProgress } else ResumeLogic.pick(info.playables)
        return playable?.takeIf { it.available && !it.broken }
    }
}

/**
 * What "What should I watch tonight?" picks from: available films nobody has started, and series with no episode
 * started. Clips stay out, and so do private-folder videos, which the snapshot does not hold.
 */
internal object TonightPool {
    fun build(s: Snapshot): List<PickOption> =
        s.infos.values
            .filter { it.available && !it.isClip && !it.started && it.playables.any { p -> p.available } }
            .filter { it.isFilm || !it.isMovie }
            .sortedBy { it.id }
            .map { PickOption(it.id, it.isMovie, it.genres) }
}
