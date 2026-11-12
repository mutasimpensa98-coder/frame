package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.AutoBackupMode
import com.frameglass.data.api.CleanupReason
import com.frameglass.data.api.HomeRowKind
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibrarySort
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.TitleRow
import com.frameglass.data.db.Volume
import com.frameglass.data.db.WatchLog
import com.frameglass.data.db.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val FILM_MS = 6_000_000L

private fun title(
    id: Long,
    kind: TitleKind = TitleKind.MOVIE,
    state: MatchState = MatchState.AUTO,
    watchlistAt: Long? = null,
    name: String = "t$id",
) = TitleRow(
    id = id, kind = kind, name = name, year = null, coverPath = "/c$id", framePath = null, backdropPath = null, logoPath = null,
    dominantColor = null, collectionId = null, matchState = state, sortKey = name, createdAt = 0, watchlistAt = watchlistAt,
)

private fun movie(rowId: Long, titleId: Long, size: Long = 100, height: Int = 1080, available: Boolean = true): MediaFile =
    file(
        rowId, "t:$titleId", titleId = titleId, size = size, height = height, available = available,
        nameKind = NameKinds.MOVIE, relPath = "Films/m$rowId.mkv",
    ).copy(durationMs = FILM_MS)

private fun ep(rowId: Long, titleId: Long, number: Int, size: Long = 100, height: Int = 1080): MediaFile =
    file(
        rowId, "t:$titleId:s1:e$number", season = 1, episode = number, titleId = titleId, size = size, height = height,
        relPath = "Show/e$rowId.mkv",
    )

private fun done(key: String) = WatchState(key, 0, FILM_MS, true, 1, null)

private fun snap(
    titles: List<TitleRow>,
    files: List<MediaFile>,
    watch: List<WatchState> = emptyList(),
    hidden: List<TitleRow> = emptyList(),
) = Snapshot(
    titleRows = titles,
    files = files,
    episodes = emptyList(),
    watch = watch.associateBy { it.playableKey },
    ratings = emptyList(),
    genreLinks = emptyList(),
    collections = emptyMap(),
    volumes = listOf(Volume("primary", "Internal", false, 0, "/storage/emulated/0")),
    roots = emptyList(),
    settings = AppSettings(),
    now = TimeUnit.DAYS.toMillis(100),
    mounted = setOf("primary"),
    hiddenRows = hidden,
)

private val labels = object : Labels {
    override fun episode(season: Int, number: Int) = "S$season E$number"
    override fun withTitle(label: String, title: String?) = label
}

class WatchTallyTest {
    @Test
    fun normalPlaybackCountsTheWholeStep() {
        assertEquals(5_000, WatchTally.countedMs(10_000, 0, 15_000, 5_000))
    }

    @Test
    fun theAllowedRateFollowsThePlayingSpeed() {
        // Ten seconds of video in five on the clock is playback at 2x.
        assertEquals(10_000, WatchTally.countedMs(0, 0, 10_000, 5_000, speed = 2f))
        // At 1x the same step held a skip, so only the playback around it counts.
        assertEquals(5_000, WatchTally.countedMs(0, 0, 10_000, 5_000, speed = 1f))
        // At 0.5x a full five seconds of video in five seconds cannot be playback alone.
        assertEquals(2_500, WatchTally.countedMs(0, 0, 9_000, 5_000, speed = 0.5f))
    }

    @Test
    fun aTenSecondSkipDropsOnlyTheSkip() {
        // Five seconds played and a 10 s skip in the same step: 15 s moved, 5 s counted.
        assertEquals(5_000, WatchTally.countedMs(0, 0, 15_000, 5_000))
    }

    @Test
    fun aBigSeekForwardCountsOnlyThePlaybackOfThatStep() {
        assertEquals(5_000, WatchTally.countedMs(0, 0, 600_000, 5_000))
    }

    @Test
    fun aSeekBackOrAPauseCountsNothing() {
        assertEquals(0, WatchTally.countedMs(50_000, 0, 20_000, 5_000))
        assertEquals(0, WatchTally.countedMs(50_000, 0, 50_000, 5_000))
    }

    @Test
    fun aLongPauseThenAJumpDoesNotPassAsPlayback() {
        val twoHours = TimeUnit.HOURS.toMillis(2)
        // Paused for two hours, then thirty minutes ahead: far more than two minutes of wall time could explain.
        assertEquals(0, WatchTally.countedMs(0, 0, TimeUnit.MINUTES.toMillis(30), twoHours))
        // Paused, then five seconds of real playback: those five seconds count.
        assertEquals(5_000, WatchTally.countedMs(0, 0, 5_000, twoHours))
        // Paused for a minute, then a seek: the step held a pause, so the jump counts nothing.
        assertEquals(0, WatchTally.countedMs(0, 0, 300_000, 60_000))
    }

    @Test
    fun aClockThatWentBackCountsNothing() {
        assertEquals(0, WatchTally.countedMs(0, 10_000, 5_000, 5_000))
    }
}

class WatchlistTest {
    @Test
    fun theChipHoldsWatchlistTitlesOnlyAndIsSeparateFromFavourites() {
        val s = snap(
            listOf(title(1, watchlistAt = 5), title(2).copy(favorite = true), title(3)),
            listOf(movie(1, 1), movie(2, 2), movie(3, 3)),
        )
        assertEquals(listOf(1L), TitleLists.library(s, LibraryFilter.WATCHLIST, LibrarySort.NAME).map { it.id })
        assertEquals(listOf(2L), TitleLists.library(s, LibraryFilter.FAVORITES, LibrarySort.NAME).map { it.id })
    }

    @Test
    fun homeShowsTheRowAfterContinueWatchingNewestFirstAndOnlyWhenItHasAnUnstartedTitle() {
        val empty = HomeBuilder.build(snap(listOf(title(1)), listOf(movie(1, 1))), labels)
        assertTrue(empty.rows.none { it.kind == HomeRowKind.WATCHLIST })

        val s = snap(
            listOf(title(1, watchlistAt = 10), title(2, watchlistAt = 20), title(3, watchlistAt = 30)),
            listOf(movie(1, 1), movie(2, 2), movie(3, 3)),
            watch = listOf(WatchState("t:3", 60_000, FILM_MS, false, 5, 3)),
        )
        val feed = HomeBuilder.build(s, labels)
        val row = feed.rows.first()
        assertEquals(HomeRowKind.WATCHLIST, row.kind)
        // A single title is enough, and the started one is in Continue watching instead.
        assertEquals(listOf(2L, 1L), row.titles.map { it.id })
        assertEquals(listOf(2L, 1L), HomeBuilder.rowTitles(s, HomeRowKind.WATCHLIST, null).map { it.id })
        assertTrue(feed.continueWatching.any { it.titleId == 3L } || feed.heroes.any { it.titleId == 3L })
    }

    @Test
    fun theWatchlistRowIsNotCountedAmongTheAutomaticRows() {
        assertFalse(HomeRowKind.WATCHLIST.automatic)
        assertFalse(HomeRowKind.VIDEOS.automatic)
        assertTrue(HomeRowKind.TOP_RATED.automatic)
    }

    @Test
    fun aTitleIsDoneOnlyWhenEverythingOfItIsWatchedAfterItJoined() {
        assertFalse(WatchlistRules.done(emptyList(), since = 0))
        assertFalse(WatchlistRules.done(listOf(playable(1, watched = true, at = 5), playable(2)), since = 0))
        assertTrue(WatchlistRules.done(listOf(playable(1, watched = true, at = 5), playable(2, watched = true, at = 6)), since = 5))
    }

    @Test
    fun oneRewatchedEpisodeDoesNotEndASeriesSeenBefore() {
        // Seen long ago, put on the list at 100 for a rewatch, then episode 1 finished again at 150.
        val series = listOf(playable(1, watched = true, at = 150), playable(2, watched = true, at = 10), playable(3, watched = true, at = 20))
        assertFalse(WatchlistRules.done(series, since = 100))
        val all = listOf(playable(1, watched = true, at = 150), playable(2, watched = true, at = 160), playable(3, watched = true, at = 170))
        assertTrue(WatchlistRules.done(all, since = 100))
    }

    @Test
    fun markingWatchedByHandCounts() {
        // A hand mark keeps lastPlayedAt as it was, so the keys just marked count as finished.
        val series = listOf(playable(1, watched = true, at = 10), playable(2, watched = true, at = 20))
        assertFalse(WatchlistRules.done(series, since = 100))
        assertTrue(WatchlistRules.done(series, since = 100, justMarked = setOf("t:1:s1:e1", "t:1:s1:e2")))
        assertFalse(WatchlistRules.done(series, since = 100, justMarked = setOf("t:1:s1:e1")))
    }
}

class CleanupRulesTest {
    @Test
    fun watchedFilesComeBiggestFirstAndUnwatchedOrGoneOnesStay() {
        val s = snap(
            listOf(title(1), title(2), title(3), title(4)),
            listOf(movie(1, 1, size = 300), movie(2, 2, size = 900), movie(3, 3, size = 5_000), movie(4, 4, size = 700, available = false)),
            watch = listOf(done("t:1"), done("t:2"), done("t:4")),
        )
        val result = CleanupRules.build(s)
        assertEquals(listOf(2L, 1L), result.watched.map { it.entry.fileRowId })
        assertTrue(result.watched.all { it.reason == CleanupReason.WATCHED })
        assertEquals(1_200, result.totalBytes)
    }

    @Test
    fun anExtraVersionIsSuggestedAndTheBestOneIsKept() {
        val s = snap(
            listOf(title(1, TitleKind.SERIES)),
            listOf(ep(1, 1, 1, size = 400, height = 720), ep(2, 1, 1, size = 900, height = 1080), ep(3, 1, 2)),
        )
        val result = CleanupRules.build(s)
        val item = result.versions.single()
        assertEquals(1L, item.entry.fileRowId)
        assertEquals(CleanupReason.EXTRA_VERSION, item.reason)
        assertEquals("e2.mkv", item.keptName)
        assertTrue(result.watched.isEmpty())
    }

    @Test
    fun aWatchedTitleWithTwoCopiesListsOnlyTheExtraNeverTheKeptOne() {
        val s = snap(
            listOf(title(1, TitleKind.SERIES)),
            listOf(ep(1, 1, 1, size = 400, height = 720), ep(2, 1, 1, size = 900, height = 1080)),
            watch = listOf(done("t:1:s1:e1")),
        )
        val result = CleanupRules.build(s)
        assertEquals(listOf(1L), result.versions.map { it.entry.fileRowId })
        assertTrue(result.watched.isEmpty())
        assertEquals(400, result.totalBytes)
    }

    @Test
    fun selectAllCanNeverTakeEveryCopyOfAPlayable() {
        // Watched films and episodes with one, two and three copies, some of equal height.
        val files = listOf(
            movie(1, 1, size = 100), movie(2, 1, size = 200, height = 720), movie(3, 1, size = 300, height = 2160),
            movie(4, 2, size = 500), movie(5, 2, size = 500),
            movie(6, 3, size = 50),
            ep(7, 4, 1, size = 10), ep(8, 4, 1, size = 20, height = 720), ep(9, 4, 2, size = 30),
        )
        val s = snap(
            listOf(title(1), title(2), title(3), title(4, TitleKind.SERIES)),
            files,
            watch = listOf(done("t:1"), done("t:2"), done("t:3"), done("t:4:s1:e1"), done("t:4:s1:e2")),
        )
        val picked = CleanupRules.build(s).all.mapTo(HashSet()) { it.entry.fileRowId }
        for (group in files.filter { it.available }.groupBy { it.playableKey }.values) {
            if (group.size > 1) assertTrue("every copy of ${group.first().playableKey}", group.any { it.rowId !in picked })
        }
        // A single watched copy is the owner's to delete, and is still suggested.
        assertTrue(6L in picked && 9L in picked)
        assertEquals(setOf(1L, 2L, 5L, 6L, 8L, 9L), picked)
    }

    @Test
    fun clipsAreNeverSuggested() {
        val clipTitle = title(1, state = MatchState.UNMATCHED)
        val clip = file(1, "t:1", titleId = 1, nameKind = NameKinds.CLIP, relPath = "DCIM/c1.mp4")
        val copy = file(2, "t:1", titleId = 1, nameKind = NameKinds.CLIP, relPath = "DCIM/c2.mp4", height = 720)
        val result = CleanupRules.build(snap(listOf(clipTitle), listOf(clip, copy), watch = listOf(done("t:1"))))
        assertTrue(result.all.isEmpty())
    }

    @Test
    fun suggestionsAreFileEntriesThatTheConfirmedDeleteAccepts() {
        val s = snap(listOf(title(1)), listOf(movie(7, 1, size = 50)), watch = listOf(done("t:1")))
        val entry = CleanupRules.build(s).watched.single().entry
        assertEquals("file|7", entry.id)
        assertEquals(listOf(7L), MediaViews.expand(s, listOf(entry)).map { it.rowId })
    }

    @Test
    fun aHiddenTitleStillGetsSuggestedBecauseItStillHoldsSpace() {
        val s = snap(emptyList(), listOf(movie(1, 1, size = 10)), watch = listOf(done("t:1")), hidden = listOf(title(1)))
        assertEquals(listOf(1L), CleanupRules.build(s).watched.map { it.entry.fileRowId })
    }

    @Test
    fun theWatchedListIsCapped() {
        val titles = (1L..40L).map { title(it) }
        val files = (1L..40L).map { movie(it, it, size = it) }
        val result = CleanupRules.build(snap(titles, files, watch = (1L..40L).map { done("t:$it") }))
        assertEquals(CleanupRules.MAX_WATCHED, result.watched.size)
        assertEquals(40L, result.watched.first().entry.sizeBytes)
    }
}

class WatchStatsBuilderTest {
    private val today = LocalDate.of(2026, 11, 12) // A Thursday.

    private fun log(day: String, titleId: Long, minutes: Long) = WatchLog(day, titleId, TimeUnit.MINUTES.toMillis(minutes))

    @Test
    fun weekMonthAndAllTimeFollowTheCalendar() {
        val logs = listOf(
            log("2026-11-12", 0, 30), // today
            log("2026-11-07", 0, 20), // Saturday, the first day of a Libyan week
            log("2026-11-06", 0, 10), // Friday: last week, this month
            log("2026-10-31", 0, 40), // last month
        )
        val stats = WatchStatsBuilder.build(logs, snap(emptyList(), emptyList()), today, DayOfWeek.SATURDAY)
        assertEquals(TimeUnit.MINUTES.toMillis(50), stats.weekMs)
        assertEquals(TimeUnit.MINUTES.toMillis(60), stats.monthMs)
        assertEquals(TimeUnit.MINUTES.toMillis(100), stats.allTimeMs)
        val monday = WatchStatsBuilder.build(logs, snap(emptyList(), emptyList()), today, DayOfWeek.MONDAY)
        assertEquals(TimeUnit.MINUTES.toMillis(30), monday.weekMs)
    }

    @Test
    fun theChartHasSevenDaysOldestFirstEndingToday() {
        val logs = listOf(log("2026-11-12", 0, 5), log("2026-11-06", 0, 7), log("2026-11-05", 0, 9))
        val days = WatchStatsBuilder.build(logs, snap(emptyList(), emptyList()), today, DayOfWeek.SATURDAY).lastSevenDays
        assertEquals(7, days.size)
        assertEquals("2026-11-06", days.first().date)
        assertEquals("2026-11-12", days.last().date)
        assertEquals(TimeUnit.MINUTES.toMillis(7), days.first().watchedMs)
        assertEquals(TimeUnit.MINUTES.toMillis(5), days.last().watchedMs)
        assertEquals(0L, days[3].watchedMs)
    }

    @Test
    fun topSeriesLeavesOutFilmsAndTimeWithNoTitle() {
        val s = snap(
            listOf(title(1, TitleKind.SERIES, name = "Long Show"), title(2, TitleKind.ANIME, name = "Short Anime"), title(3)),
            listOf(ep(1, 1, 1), ep(2, 2, 1), movie(3, 3)),
        )
        val logs = listOf(log("2026-11-12", 1, 50), log("2026-11-11", 1, 10), log("2026-11-12", 2, 20), log("2026-11-12", 3, 99), log("2026-11-12", 0, 500))
        val top = WatchStatsBuilder.build(logs, s, today, DayOfWeek.SATURDAY).topSeries
        assertEquals(listOf("Long Show", "Short Anime"), top.map { it.name })
        assertEquals(TimeUnit.MINUTES.toMillis(60), top.first().watchedMs)
    }

    @Test
    fun aTitleWithNoPublicFileHasNoNameInTheStats() {
        // A title moved whole into the private folder has no file rows, so the snapshot does not hold it.
        val logs = listOf(log("2026-11-12", 9, 40))
        val stats = WatchStatsBuilder.build(logs, snap(listOf(title(9, TitleKind.SERIES)), emptyList()), today, DayOfWeek.SATURDAY)
        assertTrue(stats.topSeries.isEmpty())
        assertEquals(TimeUnit.MINUTES.toMillis(40), stats.allTimeMs)
    }

    @Test
    fun filmsFinishedCountsWatchedFilms() {
        val s = snap(
            listOf(title(1), title(2), title(3, TitleKind.SERIES)),
            listOf(movie(1, 1), movie(2, 2), ep(3, 3, 1)),
            watch = listOf(done("t:1"), done("t:3:s1:e1")),
        )
        val stats = WatchStatsBuilder.build(emptyList(), s, today, DayOfWeek.SATURDAY)
        assertEquals(1, stats.filmsFinished)
        assertFalse(stats.empty)
    }
}

class AutoBackupRulesTest {
    private val at = LocalDate.of(2026, 11, 12).atTime(23, 5, 9).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun theNameIsDatedWithLatinDigitsWhateverTheLanguage() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-LY"))
            assertEquals("frame-auto-2026-11-12-230509.zip", AutoBackupRules.fileName(at, ZoneId.of("UTC")))
        } finally {
            Locale.setDefault(saved)
        }
        assertTrue(AutoBackupRules.isOurs(AutoBackupRules.fileName(at, ZoneId.of("Africa/Tripoli"))))
    }

    @Test
    fun onlyOurOwnPatternIsEverDeleted() {
        assertFalse(AutoBackupRules.isOurs("frame-glass-backup-2026-11-12.zip"))
        assertFalse(AutoBackupRules.isOurs("frame-glass-auto-2026-11-12-230509 (1).zip"))
        assertFalse(AutoBackupRules.isOurs("frame-glass-auto-2026-11-12-230509.zip.txt"))
        assertFalse(AutoBackupRules.isOurs("my-frame-glass-auto-2026-11-12-230509.zip"))
        // ICU on the phone matches Arabic-Indic digits with \d, so the pattern spells out [0-9].
        assertFalse(AutoBackupRules.isOurs("frame-glass-auto-٢٠٢٦-١١-١٢-٢٣٠٥٠٩.zip"))
        assertFalse(AutoBackupRules.isOurs("frame-glass-auto-2026-11-12-23050٩.zip"))
    }

    @Test
    fun filesNamedBeforeTheRenameStillCountAndSortByTime() {
        assertTrue(AutoBackupRules.isOurs("frame-glass-auto-2026-11-01-120000.zip"))
        assertTrue(AutoBackupRules.isOurs("frame-auto-2026-11-13-120000.zip"))
        val old = (1..4).map { "frame-glass-auto-2026-11-0$it-120000.zip" }
        val new = (5..8).map { "frame-auto-2026-11-0$it-120000.zip" }
        assertEquals(old.take(3).reversed(), AutoBackupRules.toDelete((new + old).shuffled(java.util.Random(7)), keep = 5))
    }

    @Test
    fun theNewestFiveStay() {
        val ours = (1..8).map { "frame-glass-auto-2026-11-0$it-120000.zip" }
        val others = listOf("frame-glass-backup-2026-01-01.zip", "notes.txt")
        val gone = AutoBackupRules.toDelete((ours + others).shuffled(java.util.Random(4)), keep = 5)
        assertEquals(ours.take(3).reversed(), gone)
        assertTrue(AutoBackupRules.toDelete(ours.take(5) + others, keep = 5).isEmpty())
    }

    @Test
    fun dueFollowsTheChosenPeriodWithSomeGrace() {
        val day = TimeUnit.DAYS.toMillis(1)
        assertFalse(AutoBackupRules.due(AutoBackupMode.OFF, null, 0))
        assertTrue(AutoBackupRules.due(AutoBackupMode.DAILY, null, 0))
        assertFalse(AutoBackupRules.due(AutoBackupMode.DAILY, 0, day / 2))
        assertTrue(AutoBackupRules.due(AutoBackupMode.DAILY, 0, day - AutoBackupRules.GRACE_MS))
        assertFalse(AutoBackupRules.due(AutoBackupMode.WEEKLY, 0, 3 * day))
        assertTrue(AutoBackupRules.due(AutoBackupMode.WEEKLY, 0, 7 * day))
        assertNull(AutoBackupRules.periodMs(AutoBackupMode.OFF))
    }
}
