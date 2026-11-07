package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.HomeRowKind
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibrarySort
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.RatingSource
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.EpisodeLite
import com.frameglass.data.db.GenreLink
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

private val labels = object : Labels {
    override fun episode(season: Int, number: Int) = "S$season E$number"
    override fun withTitle(label: String, title: String?) = if (title == null) label else "$label • $title"
}

private fun row(id: Long, kind: TitleKind, name: String = "t$id", collection: Long? = null) = TitleRow(
    id = id, kind = kind, name = name, year = 2020, coverPath = "/c$id", framePath = null, backdropPath = null, logoPath = null,
    dominantColor = null, collectionId = collection, matchState = MatchState.AUTO, sortKey = name, createdAt = 0,
)

private fun movieFile(id: Long, added: Long = id, available: Boolean = true, volume: String = "primary") = file(
    id, "t:$id", titleId = id, firstSeenAt = added, available = available, nameKind = NameKinds.MOVIE,
    relPath = "Movies/m$id.mkv", volume = volume,
)

private fun episodeFile(rowId: Long, titleId: Long, number: Int, season: Int = 1, added: Long = 0, available: Boolean = true) = file(
    rowId, "t:$titleId:s$season:e$number", season = season, episode = number, titleId = titleId, firstSeenAt = added,
    available = available, relPath = "Show$titleId/e$number.mkv",
)

private fun state(key: String, titleId: Long, position: Long = 0, watched: Boolean = false, at: Long = 0) =
    WatchState(key, position, 1_000_000, watched, at, titleId)

private fun snapshot(
    titles: List<TitleRow>,
    files: List<MediaFile>,
    watch: List<WatchState> = emptyList(),
    ratings: List<RatingRow> = emptyList(),
    genres: List<GenreLink> = emptyList(),
    collections: Map<Long, String> = emptyMap(),
    episodes: List<EpisodeLite> = emptyList(),
    settings: AppSettings = AppSettings(),
    now: Long = TimeUnit.DAYS.toMillis(100),
) = Snapshot(
    titleRows = titles,
    files = files,
    episodes = episodes,
    watch = watch.associateBy { it.playableKey },
    ratings = ratings,
    genreLinks = genres,
    collections = collections,
    volumes = listOf(
        Volume("primary", "Internal", false, 0, "/storage/emulated/0"),
        Volume("usb", "SSD", true, 0, "/storage/AAAA-BBBB"),
    ),
    roots = listOf(RootFolder(1, "primary", "Movies", 0)),
    settings = settings,
    now = now,
    mounted = setOf("primary"),
)

class HomeBuilderTest {
    @Test
    fun heroesAreLastPlayedThenFourRecentlyAdded() {
        val titles = (1L..7L).map { row(it, TitleKind.MOVIE) }
        val files = titles.map { movieFile(it.id) }
        val s = snapshot(titles, files, watch = listOf(state("t:2", 2, position = 5, at = 50)))
        val feed = HomeBuilder.build(s, labels)
        assertEquals(listOf(2L, 7L, 6L, 5L, 4L), feed.heroes.map { it.titleId })
        assertTrue(feed.heroes.first().resumable)
        assertFalse(feed.heroes[1].resumable)
    }

    @Test
    fun unavailableTitlesNeverReachHome() {
        val titles = listOf(row(1, TitleKind.MOVIE), row(2, TitleKind.MOVIE))
        val files = listOf(movieFile(1), movieFile(2, available = false))
        val feed = HomeBuilder.build(snapshot(titles, files), labels)
        assertEquals(listOf(1L), feed.heroes.map { it.titleId })
    }

    @Test
    fun continueWatchingExcludesTheHeroItem() {
        val titles = listOf(row(1, TitleKind.MOVIE), row(2, TitleKind.MOVIE))
        val watch = listOf(state("t:1", 1, position = 5, at = 90), state("t:2", 2, position = 5, at = 10))
        val feed = HomeBuilder.build(snapshot(titles, titles.map { movieFile(it.id) }, watch = watch), labels)
        assertEquals(1L, feed.heroes.first().titleId)
        assertEquals(listOf(2L), feed.continueWatching.map { it.titleId })
    }

    @Test
    fun theHeroCarriesTheCardItTookFromTheRow() {
        val titles = listOf(row(1, TitleKind.MOVIE), row(2, TitleKind.MOVIE))
        val watch = listOf(state("t:1", 1, position = 5, at = 90), state("t:2", 2, position = 5, at = 10))
        val feed = HomeBuilder.build(snapshot(titles, titles.map { movieFile(it.id) }, watch = watch), labels)
        assertEquals("t:1", feed.heroes.first().continueItem?.play?.playableKey)
        assertTrue(feed.heroes.drop(1).all { it.continueItem == null })
    }

    @Test
    fun continueWatchingHoldsOneItemPerSeries() {
        val titles = listOf(row(1, TitleKind.MOVIE), row(9, TitleKind.SERIES))
        val files = listOf(movieFile(1)) + (1..3).map { episodeFile(10L + it, 9, it) }
        val watch = listOf(
            state("t:1", 1, position = 5, at = 90),
            state("t:9:s1:e1", 9, watched = true, at = 10),
            state("t:9:s1:e2", 9, position = 7, at = 20),
        )
        val feed = HomeBuilder.build(snapshot(titles, files, watch = watch), labels)
        val item = feed.continueWatching.single()
        assertEquals(9L, item.titleId)
        assertEquals("t:9:s1:e2", item.play.playableKey)
        assertEquals("S1 E2", item.episodeLabel)
    }

    @Test
    fun nextUpAppearsAfterAFinishedEpisodeWithTheNewBadge() {
        val titles = listOf(row(1, TitleKind.MOVIE), row(9, TitleKind.SERIES))
        val files = listOf(movieFile(1), episodeFile(11, 9, 1, added = 5), episodeFile(12, 9, 2, added = 60))
        val watch = listOf(state("t:1", 1, position = 5, at = 90), state("t:9:s1:e1", 9, watched = true, at = 50))
        val item = HomeBuilder.build(snapshot(titles, files, watch = watch), labels).continueWatching.single()
        assertEquals("t:9:s1:e2", item.play.playableKey)
        assertEquals(0f, item.progress, 0f)
        assertTrue(item.newEpisode)
        assertEquals(1_000_000, item.timeLeftMs)
    }

    @Test
    fun rowsNeedFourTitlesAndCapAtTen() {
        val titles = (1L..12L).map { row(it, TitleKind.MOVIE) }
        val ratings = titles.map { RatingRow(it.id, RatingSource.TMDB, it.id.toDouble() / 2) }
        val feed = HomeBuilder.build(snapshot(titles, titles.map { movieFile(it.id) }, ratings = ratings), labels)
        val top = feed.rows.single()
        assertEquals(HomeRowKind.TOP_RATED, top.kind)
        assertEquals(10, top.titles.size)
        assertEquals(2, top.moreCount)
        assertEquals(12L, top.titles.first().id)
    }

    @Test
    fun aRowUnderFourTitlesIsHidden() {
        val titles = (1L..3L).map { row(it, TitleKind.MOVIE) }
        val ratings = titles.map { RatingRow(it.id, RatingSource.TMDB, 8.0) }
        assertTrue(HomeBuilder.build(snapshot(titles, titles.map { movieFile(it.id) }, ratings = ratings), labels).rows.isEmpty())
    }

    @Test
    fun continueWatchingCountsTowardThreeRows() {
        val titles = (1L..8L).map { row(it, TitleKind.MOVIE, collection = 77) }
        val watch = listOf(state("t:1", 1, position = 5, at = 9), state("t:2", 2, position = 5, at = 5))
        val s = snapshot(
            titles,
            titles.map { movieFile(it.id) },
            watch = watch,
            ratings = titles.map { RatingRow(it.id, RatingSource.TMDB, 7.0) },
            genres = titles.map { GenreLink(it.id, "Drama") },
            collections = mapOf(77L to "Saga"),
        )
        val feed = HomeBuilder.build(s, labels)
        assertEquals(1, feed.continueWatching.size)
        assertEquals(listOf(HomeRowKind.TOP_RATED, HomeRowKind.GENRE), feed.rows.map { it.kind })
    }

    @Test
    fun viewAllListsTheWholeRow() {
        val titles = (1L..12L).map { row(it, TitleKind.MOVIE) }
        val s = snapshot(titles, titles.map { movieFile(it.id) }, genres = titles.map { GenreLink(it.id, "Drama") })
        assertEquals(12, HomeBuilder.rowTitles(s, HomeRowKind.GENRE, "Drama").size)
    }

    @Test
    fun emptyMeansNoVideoYet() {
        assertTrue(HomeBuilder.build(snapshot(emptyList(), emptyList()), labels).empty)
    }
}

class TitleListsTest {
    private val usbFile = movieFile(3, available = false, volume = "usb")

    private val base = listOf(row(1, TitleKind.MOVIE, "b"), row(2, TitleKind.MOVIE, "a"), row(3, TitleKind.MOVIE, "c"))
    private val files = listOf(movieFile(1), movieFile(2, available = false), usbFile)

    @Test
    fun goneInternalTitlesWaitBehindTheToggle() {
        val hidden = TitleLists.library(snapshot(base, files), LibraryFilter.ALL, LibrarySort.NAME)
        assertEquals(listOf(1L, 3L), hidden.map { it.id })
        val shown = TitleLists.library(snapshot(base, files, settings = AppSettings(showUnavailable = true)), LibraryFilter.ALL, LibrarySort.NAME)
        assertEquals(listOf(2L, 1L, 3L), shown.map { it.id })
        assertTrue(shown.first().unavailable)
    }

    @Test
    fun aGoneDriveStaysVisibleAndDimmed() {
        val cards = TitleLists.library(snapshot(base, files), LibraryFilter.ALL, LibrarySort.NAME)
        assertTrue(cards.last().unavailable)
    }

    @Test
    fun filterSeparatesKinds() {
        val titles = listOf(row(1, TitleKind.MOVIE), row(2, TitleKind.SERIES), row(3, TitleKind.ANIME))
        val all = listOf(movieFile(1), episodeFile(20, 2, 1), episodeFile(30, 3, 1))
        val s = snapshot(titles, all)
        assertEquals(listOf(1L), TitleLists.library(s, LibraryFilter.MOVIES, LibrarySort.NAME).map { it.id })
        assertEquals(listOf(2L), TitleLists.library(s, LibraryFilter.SERIES, LibrarySort.NAME).map { it.id })
        assertEquals(listOf(3L), TitleLists.library(s, LibraryFilter.ANIME, LibrarySort.NAME).map { it.id })
        assertTrue(TitleLists.library(s, LibraryFilter.FOLDERS, LibrarySort.NAME).isEmpty())
    }

    @Test
    fun sortsByNameNaturally() {
        val titles = listOf(row(1, TitleKind.MOVIE, "part 10"), row(2, TitleKind.MOVIE, "part 2"))
        val cards = TitleLists.library(snapshot(titles, titles.map { movieFile(it.id) }), LibraryFilter.ALL, LibrarySort.NAME)
        assertEquals(listOf(2L, 1L), cards.map { it.id })
    }

    @Test
    fun ratingSortPutsUnratedLast() {
        val titles = listOf(row(1, TitleKind.MOVIE), row(2, TitleKind.MOVIE))
        val cards = TitleLists.library(
            snapshot(titles, titles.map { movieFile(it.id) }, ratings = listOf(RatingRow(2, RatingSource.TMDB, 7.0))),
            LibraryFilter.ALL,
            LibrarySort.RATING,
        )
        assertEquals(listOf(2L, 1L), cards.map { it.id })
        assertNull(cards.last().rating)
    }

    @Test
    fun aTmdbRatingIsNeverLabelledImdb() {
        val titles = listOf(row(1, TitleKind.MOVIE))
        val both = listOf(RatingRow(1, RatingSource.TMDB, 7.0), RatingRow(1, RatingSource.IMDB, 8.0))
        val card = TitleLists.library(snapshot(titles, titles.map { movieFile(it.id) }, ratings = both), LibraryFilter.ALL, LibrarySort.NAME).single()
        assertEquals(RatingSource.TMDB, card.rating?.source)
        assertEquals(7.0, card.rating?.value ?: 0.0, 0.0)
    }

    @Test
    fun extrasDoNotMakeATitle() {
        val titles = listOf(row(1, TitleKind.ANIME))
        val extra = file(1, "f:x", titleId = 1, nameKind = NameKinds.EXTRA, relPath = "NCOP1.mkv")
        assertTrue(TitleLists.library(snapshot(titles, listOf(extra)), LibraryFilter.ALL, LibrarySort.NAME).isEmpty())
    }

    @Test
    fun seriesProgressCountsEpisodes() {
        val titles = listOf(row(9, TitleKind.SERIES))
        val files = (1..4).map { episodeFile(it.toLong(), 9, it) }
        val watch = listOf(state("t:9:s1:e1", 9, watched = true, at = 1), state("t:9:s1:e2", 9, position = 500_000, at = 2))
        val card = TitleLists.library(snapshot(titles, files, watch = watch), LibraryFilter.ALL, LibrarySort.NAME).single()
        assertEquals(0.375f, card.progress ?: 0f, 0.001f)
        assertFalse(card.watched)
    }
}

class EpisodeRowsTest {
    private val title = row(1, TitleKind.SERIES)

    private fun info(files: List<MediaFile>, episodes: List<EpisodeLite>, watch: List<WatchState> = emptyList()) =
        snapshot(listOf(title), files, episodes = episodes, watch = watch).infos.getValue(1)

    private fun f(rowId: Long, number: Int, height: Int = 1080, episodeId: Long? = number.toLong(), end: Int? = null) =
        file(rowId, "t:1:s1:e$number", season = 1, episode = number, height = height, episodeId = episodeId, end = end)

    @Test
    fun twoFilesOfOneEpisodeAreOneRow() {
        val rows = EpisodeRows.build(info(listOf(f(1, 1, 720), f(2, 1, 1080)), listOf(episode(1, 1))), 1, listOf(episode(1, 1)), "2030-01-01", labels)
        assertEquals(1, rows.size)
    }

    @Test
    fun missingEpisodesAreDimmedAndUnreleasedOnesIgnored() {
        val list = listOf(episode(1, 1), episode(2, 2), episode(3, 3, air = "2031-01-01"), episode(4, 4, air = null))
        val rows = EpisodeRows.build(info(listOf(f(1, 1)), list), 1, list, "2030-01-01", labels)
        assertEquals(listOf(1, 2), rows.map { it.number })
        assertTrue(rows[1].missing)
        assertFalse(rows[1].available)
        assertNull(rows[1].play)
    }

    @Test
    fun missingRowsAreLimitedToOwnedSeasons() {
        val list = listOf(episode(1, 1), episode(5, 1, season = 2))
        assertTrue(EpisodeRows.build(info(listOf(f(1, 1)), list), 2, list, "2030-01-01", labels).isEmpty())
    }

    @Test
    fun aLocalPlaceholderIsNeverMissing() {
        val list = listOf(episode(1, 1), episode(2, 2, fetched = null))
        val rows = EpisodeRows.build(info(listOf(f(1, 1)), list), 1, list, "2030-01-01", labels)
        assertEquals(1, rows.size)
    }

    @Test
    fun aDoubleEpisodeFileFillsBothRows() {
        val list = listOf(episode(7, 7), episode(8, 8))
        val rows = EpisodeRows.build(info(listOf(f(1, 7, episodeId = 7, end = 8)), list), 1, list, "2030-01-01", labels)
        assertEquals(listOf(7, 8), rows.map { it.number })
        assertEquals(rows[0].play, rows[1].play)
        assertFalse(rows[1].missing)
    }

    @Test
    fun unavailableRowsCannotPlay() {
        val gone = file(1, "t:1:s1:e1", season = 1, episode = 1, available = false, episodeId = 1)
        val list = listOf(episode(1, 1))
        val row = EpisodeRows.build(info(listOf(gone), list), 1, list, "2030-01-01", labels).single()
        assertFalse(row.available)
        assertNull(row.play)
    }

    @Test
    fun aMissingNameFallsBackToACleanedFileName() {
        val named = file(1, "t:1:s1:e1", season = 1, episode = 1, episodeId = null, relPath = "Show.S01E01.mkv")
        val row = EpisodeRows.build(info(listOf(named), emptyList()), 1, emptyList(), "2030-01-01", labels).single()
        assertEquals("Show S01E01", row.title)
    }

    @Test
    fun metadataSeasonsOverrideParserNumbersForDisplayOnly() {
        val remapped = EpisodeLite(50, 1, 3, 12, "Name", null, 24, "2020-01-01", 1)
        val absolute = file(1, "t:1:s1:e60", season = null, episode = 60, episodeId = 50)
        val item = info(listOf(absolute), listOf(remapped))
        assertEquals(3, item.playables.single().season)
        assertEquals(12, item.playables.single().number)
        assertEquals("t:1:s1:e60", item.playables.single().key)
    }
}

class FolderBuilderTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val titles = listOf(row(1, TitleKind.MOVIE))

    private fun inside(rowId: Long, rel: String, added: Long = 0, size: Long = 10, key: String = "f:$rowId") =
        file(rowId, key, relPath = rel, firstSeenAt = added, size = size, nameKind = NameKinds.MOVIE)

    @Test
    fun rootsListTheAddedFolders() {
        val s = snapshot(titles, listOf(inside(1, "Movies/a.mkv")))
        val root = FolderBuilder.roots(s).single()
        assertEquals("/storage/emulated/0/Movies", root.path)
        assertEquals("Movies", root.label)
        assertEquals(1, root.videoCount)
        assertTrue(root.available)
    }

    @Test
    fun listsFoldersThenFiles() {
        val s = snapshot(titles, listOf(inside(1, "Movies/z.mkv", size = 5), inside(2, "Movies/Sub/a.mkv", size = 7), inside(3, "Movies/Sub/b.mkv", size = 8)))
        val view = FolderBuilder.build("/storage/emulated/0/Movies", s)
        assertEquals(listOf("Sub", "z.mkv"), view.entries.map { it.name })
        val folder = view.entries.first()
        assertTrue(folder.isDirectory)
        assertEquals(2, folder.videoCount)
        assertEquals(15, folder.sizeBytes)
        assertEquals(1L, view.entries.last().play?.fileRowId)
    }

    @Test
    fun newMeansUnplayedAndAddedThisWeek() {
        val now = 100 * day
        val fresh = inside(1, "Movies/new.mkv", added = now - day)
        val old = inside(2, "Movies/old.mkv", added = now - 8 * day)
        val played = inside(3, "Movies/played.mkv", added = now - day, key = "t:1")
        val s = snapshot(titles, listOf(fresh, old, played), watch = listOf(state("t:1", 1, position = 5, at = 1)), now = now)
        val byName = FolderBuilder.build("/storage/emulated/0/Movies", s).entries.associateBy { it.name }
        assertTrue(byName.getValue("new.mkv").isNew)
        assertFalse(byName.getValue("old.mkv").isNew)
        assertFalse(byName.getValue("played.mkv").isNew)
    }

    @Test
    fun aGoneFileCannotPlay() {
        val gone = inside(1, "Movies/a.mkv").copy(available = false)
        val entry = FolderBuilder.build("/storage/emulated/0/Movies", snapshot(titles, listOf(gone))).entries.single()
        assertTrue(entry.unavailable)
        assertNull(entry.play)
    }
}
