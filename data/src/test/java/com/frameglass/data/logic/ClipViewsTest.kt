package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.HomeRowKind
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibrarySort
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.RatingSource
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.RatingRow
import com.frameglass.data.db.RootFolder
import com.frameglass.data.db.TitleRow
import com.frameglass.data.db.Volume
import com.frameglass.data.db.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

private val minute = TimeUnit.MINUTES.toMillis(1)

private val labels = object : Labels {
    override fun episode(season: Int, number: Int) = "S$season E$number"
    override fun withTitle(label: String, title: String?) = label
}

private fun title(id: Long, state: MatchState, kind: TitleKind = TitleKind.MOVIE, name: String = "t$id") = TitleRow(
    id = id, kind = kind, name = name, year = null, coverPath = null, framePath = "/f$id", backdropPath = null, logoPath = null,
    dominantColor = null, collectionId = null, matchState = state, sortKey = name, createdAt = 0,
)

/** A video that has its own card. */
private fun video(
    id: Long,
    minutes: Long,
    added: Long = id,
    nameKind: String = NameKinds.MOVIE,
    available: Boolean = true,
    relPath: String = "Movies/v$id.mp4",
    volume: String = "primary",
): MediaFile = file(
    id, "t:$id", titleId = id, firstSeenAt = added, nameKind = nameKind, available = available, relPath = relPath, volume = volume,
).copy(durationMs = minutes * minute)

private fun snapshot(
    titles: List<TitleRow>,
    files: List<MediaFile>,
    ratings: List<RatingRow> = emptyList(),
    watch: List<WatchState> = emptyList(),
    roots: List<RootFolder> = emptyList(),
    settings: AppSettings = AppSettings(),
    mounted: Set<String> = setOf("primary"),
) = Snapshot(
    titleRows = titles,
    files = files,
    episodes = emptyList(),
    watch = watch.associateBy { it.playableKey },
    ratings = ratings,
    genreLinks = emptyList(),
    collections = emptyMap(),
    volumes = listOf(
        Volume("primary", "Internal", false, 0, "/storage/emulated/0"),
        Volume("usb", "SSD", true, 0, "/storage/AAAA-BBBB"),
    ),
    roots = roots,
    settings = settings,
    now = TimeUnit.DAYS.toMillis(100),
    mounted = mounted,
)

private fun ids(s: Snapshot, filter: LibraryFilter, sort: LibrarySort = LibrarySort.NAME) =
    TitleLists.library(s, filter, sort).map { it.id }

/** One title of each sort the library can hold. */
private val mix = snapshot(
    titles = listOf(
        title(1, MatchState.AUTO, name = "matched film"),
        title(2, MatchState.NONE_FOUND, name = "long unknown"),
        title(3, MatchState.NONE_FOUND, name = "middle length"),
        title(4, MatchState.NONE_FOUND, name = "short unknown"),
        title(5, MatchState.UNMATCHED, name = "camera clip"),
        title(6, MatchState.MANUAL, name = "fixed clip"),
        title(7, MatchState.AUTO, TitleKind.SERIES, name = "show"),
    ),
    files = listOf(
        video(1, 100),
        video(2, 40),
        video(3, 30),
        video(4, 19),
        video(5, 90, nameKind = NameKinds.CLIP),
        video(6, 3, nameKind = NameKinds.CLIP),
        file(70, "t:7:s1:e1", season = 1, episode = 1, titleId = 7, relPath = "Show/e1.mkv").copy(durationMs = 12 * minute),
    ),
)

class ClipRulesTest {
    private fun info(id: Long) = mix.infos.getValue(id)

    @Test
    fun aClipNameIsAClipAtAnyLength() = assertTrue(info(5).isClip)

    @Test
    fun anUnmatchedVideoUnderTwentyMinutesIsAClip() {
        assertTrue(info(4).isClip)
        assertFalse(info(3).isClip)
    }

    @Test
    fun theLimitIsExactlyTwentyMinutes() {
        assertTrue(ClipRules.isClip(TitleKind.MOVIE, false, false, ClipRules.SHORT_MS - 1))
        assertFalse(ClipRules.isClip(TitleKind.MOVIE, false, false, ClipRules.SHORT_MS))
    }

    @Test
    fun anUnknownLengthIsNotShort() {
        assertFalse(ClipRules.isClip(TitleKind.MOVIE, false, false, 0))
        assertFalse(ClipRules.skipsLookup(TitleKind.MOVIE, null, false, 0))
    }

    @Test
    fun aMatchAlwaysWins() {
        assertFalse(info(6).isClip)
        assertFalse(ClipRules.isClip(TitleKind.MOVIE, true, false, minute))
    }

    @Test
    fun shortEpisodesStaySeries() {
        assertFalse(ClipRules.isClip(TitleKind.SERIES, false, false, 12 * minute))
        assertFalse(ClipRules.isClip(TitleKind.ANIME, false, false, 12 * minute))
    }

    @Test
    fun aClipNameNeverReachesTmdb() {
        assertTrue(ClipRules.skipsLookup(TitleKind.MOVIE, null, true, 0))
        assertTrue(ClipRules.skipsLookup(TitleKind.MOVIE, null, true, 100 * minute))
    }

    @Test
    fun aShortVideoWithAPlainNameNeverReachesTmdb() {
        assertTrue(ClipRules.skipsLookup(TitleKind.MOVIE, null, false, 5 * minute))
    }

    @Test
    fun aYearOrAnEpisodeEarnsAShortVideoALookup() {
        assertFalse(ClipRules.skipsLookup(TitleKind.MOVIE, 2019, false, 5 * minute))
        assertFalse(ClipRules.skipsLookup(TitleKind.SERIES, null, false, 12 * minute))
        assertFalse(ClipRules.skipsLookup(TitleKind.ANIME, null, false, 12 * minute))
    }

    @Test
    fun aLongVideoIsMatchedAsBefore() {
        assertFalse(ClipRules.skipsLookup(TitleKind.MOVIE, null, false, 25 * minute))
    }

    @Test
    fun anUnmatchedShortVideoWithAYearIsAClipAfterItsLookup() {
        assertTrue(ClipRules.isClip(TitleKind.MOVIE, false, false, 5 * minute))
    }
}

class LibraryFilterTest {
    @Test
    fun videosHoldsOnlyClips() = assertEquals(listOf(5L, 4L), ids(mix, LibraryFilter.VIDEOS))

    @Test
    fun moviesHoldMatchedFilmsAndLongUnmatchedVideos() = assertEquals(listOf(6L, 2L, 1L), ids(mix, LibraryFilter.MOVIES))

    @Test
    fun aVideoOfFortyMinutesIsAFilmAndOneMinuteLessIsNot() {
        val titles = listOf(title(1, MatchState.NONE_FOUND), title(2, MatchState.NONE_FOUND))
        val files = listOf(video(1, 40), video(2, 40).copy(durationMs = ClipRules.FILM_MS - 1))
        assertEquals(listOf(1L), ids(snapshot(titles, files), LibraryFilter.MOVIES))
    }

    @Test
    fun aMatchedFilmCountsAtAnyLength() {
        val s = snapshot(listOf(title(1, MatchState.AUTO)), listOf(video(1, 0)))
        assertEquals(listOf(1L), ids(s, LibraryFilter.MOVIES))
    }

    @Test
    fun aVideoStillBeingProbedIsInNeitherList() {
        val s = snapshot(listOf(title(1, MatchState.QUEUED)), listOf(video(1, 0)))
        assertTrue(ids(s, LibraryFilter.MOVIES).isEmpty())
        assertTrue(ids(s, LibraryFilter.VIDEOS).isEmpty())
        assertEquals(listOf(1L), ids(s, LibraryFilter.ALL))
    }

    @Test
    fun allHoldsEverything() = assertEquals(7, ids(mix, LibraryFilter.ALL).size)

    @Test
    fun seriesHoldOnlySeries() = assertEquals(listOf(7L), ids(mix, LibraryFilter.SERIES))

    @Test
    fun aClipHasACardWithItsProgress() {
        val titles = listOf(title(1, MatchState.UNMATCHED))
        val files = listOf(video(1, 2, nameKind = NameKinds.CLIP))
        val watch = listOf(WatchState("t:1", 30_000, 120_000, false, 5, 1))
        val card = TitleLists.library(snapshot(titles, files, watch = watch), LibraryFilter.VIDEOS, LibrarySort.RECENT).single()
        assertEquals("/f1", card.coverPath)
        assertEquals(0.25f, card.progress ?: 0f, 0.001f)
        assertNull(card.rating)
    }
}

class LibraryOrderTest {
    private val titles = listOf(
        title(1, MatchState.AUTO, name = "old film"),
        title(2, MatchState.UNMATCHED, name = "new clip"),
        title(3, MatchState.NONE_FOUND, name = "unknown"),
        title(4, MatchState.UNMATCHED, name = "older clip"),
    )
    private val files = listOf(
        video(1, 100, added = 10),
        video(2, 1, added = 90, nameKind = NameKinds.CLIP),
        video(3, 100, added = 50),
        video(4, 1, added = 80, nameKind = NameKinds.CLIP),
    )
    private val s = snapshot(titles, files)

    @Test
    fun recentlyAddedPutsRealTitlesFirstSoLibraryOpensOnPosters() =
        assertEquals(listOf(3L, 1L, 2L, 4L), ids(s, LibraryFilter.ALL, LibrarySort.RECENT))

    @Test
    fun clipsThemselvesSortByRecency() = assertEquals(listOf(2L, 4L), ids(s, LibraryFilter.VIDEOS, LibrarySort.RECENT))

    @Test
    fun nameSortIgnoresWhatKindItIs() =
        assertEquals(listOf(2L, 1L, 4L, 3L), ids(s, LibraryFilter.ALL, LibrarySort.NAME))

    @Test
    fun ratingSortPlacesUnratedLastAcrossClipsAndTitles() {
        val rated = snapshot(titles, files, ratings = listOf(RatingRow(3, RatingSource.TMDB, 6.0), RatingRow(1, RatingSource.TMDB, 8.0)))
        val cards = ids(rated, LibraryFilter.ALL, LibrarySort.RATING)
        assertEquals(listOf(1L, 3L), cards.take(2))
        assertEquals(setOf(2L, 4L), cards.drop(2).toSet())
    }
}

class HomeClipsTest {
    private fun films(count: Long) = (1L..count).map { title(it, MatchState.AUTO) }
    private fun filmFiles(count: Long) = (1L..count).map { video(it, 100, added = it) }

    private fun clips(from: Long, count: Int) = (0 until count).map { title(from + it, MatchState.UNMATCHED) }
    private fun clipFiles(from: Long, count: Int) =
        (0 until count).map { video(from + it, 1, added = 1_000L + it, nameKind = NameKinds.CLIP) }

    @Test
    fun heroesComeFromRealTitlesWhenAnyExist() {
        val feed = HomeBuilder.build(snapshot(films(2) + clips(10, 5), filmFiles(2) + clipFiles(10, 5)), labels)
        assertEquals(listOf(2L, 1L), feed.heroes.map { it.titleId })
    }

    @Test
    fun heroesFallBackToTheMostRecentVideos() {
        val feed = HomeBuilder.build(snapshot(clips(10, 6), clipFiles(10, 6)), labels)
        assertEquals(listOf(15L, 14L, 13L, 12L), feed.heroes.map { it.titleId })
    }

    @Test
    fun aClipHeroIsMarkedSoItIsNotCalledAMovie() {
        val feed = HomeBuilder.build(snapshot(clips(10, 1), clipFiles(10, 1)), labels)
        assertTrue(feed.heroes.single().clip)
        assertFalse(HomeBuilder.build(snapshot(films(1), filmFiles(1)), labels).heroes.single().clip)
    }

    @Test
    fun aClipPlayedLastStaysOutOfTheHeroWhileATitleExists() {
        val watch = listOf(WatchState("t:10", 5, 60_000, false, 500, 10))
        val feed = HomeBuilder.build(snapshot(films(1) + clips(10, 1), filmFiles(1) + clipFiles(10, 1), watch = watch), labels)
        assertEquals(listOf(1L), feed.heroes.map { it.titleId })
    }

    @Test
    fun continueWatchingIncludesClips() {
        val watch = listOf(WatchState("t:10", 5, 60_000, false, 500, 10))
        val feed = HomeBuilder.build(snapshot(films(1) + clips(10, 1), filmFiles(1) + clipFiles(10, 1), watch = watch), labels)
        val item = feed.continueWatching.single()
        assertEquals(10L, item.titleId)
        assertNull(item.episodeLabel)
    }

    @Test
    fun aRowOfClipsComesLast() {
        val titles = films(12) + clips(20, 12)
        val files = filmFiles(12) + clipFiles(20, 12)
        val ratings = (1L..12L).map { RatingRow(it, RatingSource.TMDB, 7.0) }
        val rows = HomeBuilder.build(snapshot(titles, files, ratings = ratings), labels).rows
        assertEquals(listOf(HomeRowKind.TOP_RATED, HomeRowKind.VIDEOS), rows.map { it.kind })
        val videos = rows.last()
        assertEquals(10, videos.titles.size)
        assertEquals(2, videos.moreCount)
        assertEquals(31L, videos.titles.first().id)
    }

    @Test
    fun theRowOfClipsIsHiddenUnderFourItems() {
        val feed = HomeBuilder.build(snapshot(films(1) + clips(10, 3), filmFiles(1) + clipFiles(10, 3)), labels)
        assertTrue(feed.rows.none { it.kind == HomeRowKind.VIDEOS })
    }

    @Test
    fun theRowOfClipsDoesNotCountTowardTheThree() {
        val titles = films(8) + clips(20, 4)
        val files = filmFiles(8) + clipFiles(20, 4)
        val s = Snapshot(
            titleRows = titles,
            files = files,
            episodes = emptyList(),
            watch = emptyMap(),
            ratings = (1L..8L).map { RatingRow(it, RatingSource.TMDB, 7.0) },
            genreLinks = (1L..8L).map { com.frameglass.data.db.GenreLink(it, "Drama") },
            collections = emptyMap(),
            volumes = emptyList(),
            roots = emptyList(),
            settings = AppSettings(),
            now = 0,
        )
        val rows = HomeBuilder.build(s, labels).rows
        assertEquals(listOf(HomeRowKind.TOP_RATED, HomeRowKind.GENRE, HomeRowKind.VIDEOS), rows.map { it.kind })
    }

    @Test
    fun theRowViewAllListsEveryClipNewestFirst() {
        val s = snapshot(films(1) + clips(10, 12), filmFiles(1) + clipFiles(10, 12))
        val cards = HomeBuilder.rowTitles(s, HomeRowKind.VIDEOS, null)
        assertEquals(12, cards.size)
        assertEquals(21L, cards.first().id)
    }

    @Test
    fun clipsStayOutOfTopRated() {
        val cards = HomeBuilder.rowTitles(snapshot(clips(10, 5), clipFiles(10, 5)), HomeRowKind.TOP_RATED, null)
        assertTrue(cards.isEmpty())
    }

    @Test
    fun filmsAndSeriesKeepARowWhenNothingIsRated() {
        val show = title(9, MatchState.NONE_FOUND, TitleKind.SERIES)
        val showFile = file(90, "t:9:s1:e1", season = 1, episode = 1, titleId = 9, firstSeenAt = 500, relPath = "Show/e1.mkv")
        val feed = HomeBuilder.build(snapshot(films(4) + show + clips(20, 6), filmFiles(4) + showFile + clipFiles(20, 6)), labels)
        assertEquals(listOf(HomeRowKind.RECENT, HomeRowKind.VIDEOS), feed.rows.map { it.kind })
        assertEquals(listOf(9L, 4L, 3L, 2L, 1L), feed.rows.first().titles.map { it.id })
    }

    @Test
    fun recentlyAddedStepsAsideForARealRow() {
        val ratings = (1L..4L).map { RatingRow(it, RatingSource.TMDB, 7.0) }
        val rows = HomeBuilder.build(snapshot(films(6), filmFiles(6), ratings = ratings), labels).rows
        assertEquals(listOf(HomeRowKind.TOP_RATED), rows.map { it.kind })
    }

    @Test
    fun recentlyAddedNeedsFourTitles() {
        assertTrue(HomeBuilder.build(snapshot(films(3), filmFiles(3)), labels).rows.isEmpty())
    }

    @Test
    fun theRecentViewAllListsEveryTitleAndNoClip() {
        val s = snapshot(films(12) + clips(20, 3), filmFiles(12) + clipFiles(20, 3))
        val cards = HomeBuilder.rowTitles(s, HomeRowKind.RECENT, null)
        assertEquals((12L downTo 1L).toList(), cards.map { it.id })
    }

    @Test
    fun withNothingAvailableHomeIsEmptyAndNotABlankPage() {
        val s = snapshot(films(1), listOf(video(1, 100, available = false)))
        assertTrue(HomeBuilder.build(s, labels).empty)
    }

    @Test
    fun wholePhoneSearchNeedsNoAddedFolder() {
        assertFalse(HomeBuilder.build(snapshot(clips(10, 1), clipFiles(10, 1)), labels).empty)
    }

    @Test
    fun withWholePhoneSearchOffNoFolderMeansEmpty() {
        val s = snapshot(clips(10, 1), clipFiles(10, 1), settings = AppSettings(scanWholePhone = false))
        assertTrue(HomeBuilder.build(s, labels).empty)
    }
}

class FolderStartTest {
    private val titles = listOf(title(1, MatchState.AUTO), title(2, MatchState.AUTO), title(3, MatchState.AUTO))

    @Test
    fun oneVolumeOpensOnItsOwnFolders() {
        val files = listOf(video(1, 90, relPath = "Movies/a.mkv"), video(2, 90, relPath = "DCIM/Camera/b.mp4"), video(3, 90, relPath = "c.mp4"))
        val view = FolderBuilder.build(null, snapshot(titles, files))
        assertNull(view.path)
        assertEquals(listOf("DCIM", "Movies", "c.mp4"), view.entries.map { it.name })
        assertEquals("/storage/emulated/0/DCIM", view.entries.first().path)
        assertTrue(view.entries.first().isDirectory)
    }

    @Test
    fun aFolderFromTheStartOpensByItsPath() {
        val files = listOf(video(1, 90, relPath = "DCIM/Camera/b.mp4"))
        val s = snapshot(titles, files)
        val start = FolderBuilder.build(null, s).entries.single()
        assertEquals("Camera", FolderBuilder.build(start.path, s).entries.single().name)
    }

    @Test
    fun severalVolumesListEachRoot() {
        val files = listOf(video(1, 90, relPath = "Movies/a.mkv"), video(2, 90, relPath = "Films/b.mkv", volume = "usb", available = false))
        val entries = FolderBuilder.build(null, snapshot(titles, files)).entries
        assertEquals(listOf("Internal", "SSD"), entries.map { it.name })
        assertEquals("/storage/AAAA-BBBB", entries.last().path)
        assertTrue(entries.last().unavailable)
    }

    @Test
    fun noVideoMeansAnEmptyStart() {
        assertTrue(FolderBuilder.build(null, snapshot(emptyList(), emptyList())).entries.isEmpty())
    }

    @Test
    fun withWholePhoneSearchOffTheStartListsAddedFolders() {
        val files = listOf(video(1, 90, relPath = "Movies/a.mkv"))
        val roots = listOf(RootFolder(1, "primary", "Movies", 0))
        val s = snapshot(titles, files, roots = roots, settings = AppSettings(scanWholePhone = false))
        assertEquals(listOf("Movies"), FolderBuilder.build(null, s).entries.map { it.name })
    }

    @Test
    fun automaticRootsAreNeverListedAsAddedFolders() {
        val s = snapshot(titles, listOf(video(1, 90, relPath = "Movies/a.mkv")))
        assertTrue(FolderBuilder.roots(s).isEmpty())
    }
}
