package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.TitleKind
import com.frameglass.data.api.UpcomingEpisode
import com.frameglass.data.db.GenreLink
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.TitleRow
import com.frameglass.data.db.Volume
import com.frameglass.data.db.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

private val quickLabels = object : Labels {
    override fun episode(season: Int, number: Int) = "S$season E$number"
    override fun withTitle(label: String, title: String?) = label
}

private fun titleRow(
    id: Long,
    kind: TitleKind,
    matched: Boolean = true,
    status: String? = null,
    next: Triple<String, Int, Int>? = null,
) = TitleRow(
    id = id, kind = kind, name = "Title $id", year = 2020, coverPath = "/c$id", framePath = null, backdropPath = null,
    logoPath = null, dominantColor = null, collectionId = null,
    matchState = if (matched) MatchState.AUTO else MatchState.UNMATCHED, sortKey = "title $id", createdAt = 0,
    seriesStatus = status, nextAirDate = next?.first, nextSeason = next?.second, nextEpisode = next?.third,
)

private fun film(id: Long, available: Boolean = true, nameKind: String = NameKinds.MOVIE) =
    file(id, "t:$id", titleId = id, available = available, nameKind = nameKind, relPath = "Films/f$id.mkv")

private fun showFile(rowId: Long, titleId: Long, number: Int, available: Boolean = true) = file(
    rowId, "t:$titleId:s1:e$number", season = 1, episode = number, titleId = titleId, available = available,
    relPath = "Shows/s$titleId/e$number.mkv",
)

private fun seen(key: String, titleId: Long, position: Long = 5, watched: Boolean = false, at: Long) =
    WatchState(key, position, 1_000_000, watched, at, titleId)

private fun library(
    titles: List<TitleRow>,
    files: List<MediaFile>,
    watch: List<WatchState> = emptyList(),
    genres: List<GenreLink> = emptyList(),
) = Snapshot(
    titleRows = titles, files = files, episodes = emptyList(), watch = watch.associateBy { it.playableKey },
    ratings = emptyList(), genreLinks = genres, collections = emptyMap(),
    volumes = listOf(Volume("primary", "Internal", false, 0, "/storage/emulated/0")), roots = emptyList(),
    settings = AppSettings(), now = TimeUnit.DAYS.toMillis(100), mounted = setOf("primary"),
)

class QuickResumeTest {
    @Test
    fun theLatestPlayedComesFirstThenOtherTitlesInProgressAtMostThree() {
        val titles = (1L..5L).map { titleRow(it, TitleKind.MOVIE) }
        val watch = (1L..5L).map { seen("t:$it", it, at = it * 10) }
        val entries = QuickResume.build(library(titles, titles.map { film(it.id) }, watch), quickLabels)
        assertEquals(listOf(5L, 4L, 3L), entries.map { it.titleId })
        assertEquals(PlayRef(playableKey = "t:5"), entries.first().play)
        assertNull(entries.first().episodeLabel)
        assertEquals("/c5", entries.first().coverPath)
    }

    @Test
    fun aSeriesResumesOnItsNextEpisodeWithItsLabel() {
        val titles = listOf(titleRow(1, TitleKind.SERIES))
        val files = listOf(showFile(1, 1, 1), showFile(2, 1, 2))
        val watch = listOf(seen("t:1:s1:e1", 1, position = 0, watched = true, at = 10))
        val entry = QuickResume.build(library(titles, files, watch), quickLabels).single()
        assertEquals("t:1:s1:e2", entry.play.playableKey)
        assertEquals("S1 E2", entry.episodeLabel)
        assertEquals(0f, entry.progress)
    }

    @Test
    fun clipsFinishedFilmsUnplayedAndUnavailableTitlesStayOut() {
        val titles = listOf(
            titleRow(1, TitleKind.MOVIE, matched = false),
            titleRow(2, TitleKind.MOVIE),
            titleRow(3, TitleKind.MOVIE),
            titleRow(4, TitleKind.MOVIE),
            titleRow(5, TitleKind.MOVIE),
        )
        val files = listOf(
            film(1, nameKind = NameKinds.CLIP), film(2), film(3), film(4, available = false), film(5),
        )
        val watch = listOf(
            seen("t:1", 1, at = 50),
            seen("t:2", 2, position = 0, watched = true, at = 40),
            seen("t:4", 4, at = 30),
            seen("t:5", 5, at = 20),
        )
        assertEquals(listOf(5L), QuickResume.build(library(titles, files, watch), quickLabels).map { it.titleId })
    }
}

class TonightPoolTest {
    @Test
    fun onlyUnstartedAvailableFilmsAndSeriesWithTheirGenres() {
        val titles = listOf(
            titleRow(1, TitleKind.MOVIE),
            titleRow(2, TitleKind.MOVIE),
            titleRow(3, TitleKind.SERIES),
            titleRow(4, TitleKind.SERIES),
            titleRow(5, TitleKind.MOVIE, matched = false),
            titleRow(6, TitleKind.MOVIE),
        )
        val files = listOf(
            film(1), film(2), showFile(30, 3, 1), showFile(40, 4, 1), showFile(41, 4, 2),
            film(5, nameKind = NameKinds.CLIP), film(6, available = false),
        )
        val watch = listOf(seen("t:2", 2, at = 10), seen("t:4:s1:e1", 4, position = 0, watched = true, at = 10))
        val genres = listOf(GenreLink(1, "Drama"), GenreLink(3, "Comedy"))
        val pool = TonightPool.build(library(titles, files, watch, genres))
        assertEquals(listOf(1L, 3L), pool.map { it.titleId })
        assertEquals(listOf(true, false), pool.map { it.isMovie })
        assertEquals(listOf("Drama"), pool.first().genres)
    }
}

class UpcomingTest {
    private val today = "2026-11-08"

    @Test
    fun aReturningSeriesShowsItsNextEpisodeUntilItAirs() {
        assertEquals(UpcomingEpisode(2, 6, "2026-11-12"), Upcoming.of("Returning Series", "2026-11-12", 2, 6, today))
        assertEquals(UpcomingEpisode(2, 6, today), Upcoming.of("Returning Series", today, 2, 6, today))
        assertNull(Upcoming.of("Returning Series", "2026-11-07", 2, 6, today))
    }

    @Test
    fun endedUnknownAndIncompleteGiveNothing() {
        assertNull(Upcoming.of("Ended", "2026-11-12", 2, 6, today))
        assertNull(Upcoming.of("Canceled", "2026-11-12", 2, 6, today))
        assertNull(Upcoming.of("Returning Series", null, null, null, today))
        assertNull(Upcoming.of(null, "2026-11-12", 2, null, today))
        assertEquals(UpcomingEpisode(1, 1, "2026-12-01"), Upcoming.of(null, "2026-12-01", 1, 1, today))
    }

    @Test
    fun onlyTheNewestOwnedEpisodeCardCarriesTheHint() {
        val next = Triple("2099-01-01", 1, 3)
        val titles = listOf(titleRow(1, TitleKind.SERIES, status = "Returning Series", next = next), titleRow(2, TitleKind.SERIES, next = next))
        val files = listOf(showFile(1, 1, 1), showFile(2, 1, 2), showFile(3, 2, 1), showFile(4, 2, 2))
        val watch = listOf(
            seen("t:1:s1:e1", 1, position = 0, watched = true, at = 20),
            seen("t:2:s1:e1", 2, at = 10),
        )
        val feed = HomeBuilder.build(library(titles, files, watch), quickLabels)
        val cards = (listOfNotNull(feed.heroes.firstOrNull()?.continueItem) + feed.continueWatching).associateBy { it.titleId }
        assertEquals(UpcomingEpisode(1, 3, "2099-01-01"), cards.getValue(1).upcoming)
        assertNull(cards.getValue(2).upcoming)
        assertTrue(cards.size == 2)
    }
}
