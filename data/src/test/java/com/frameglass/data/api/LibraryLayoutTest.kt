package com.frameglass.data.api

import com.frameglass.data.api.LibraryFilter.ALL
import com.frameglass.data.api.LibraryFilter.ANIME
import com.frameglass.data.api.LibraryFilter.FAVORITES
import com.frameglass.data.api.LibraryFilter.FOLDERS
import com.frameglass.data.api.LibraryFilter.MOVIES
import com.frameglass.data.api.LibraryFilter.SERIES
import com.frameglass.data.api.LibraryFilter.VIDEOS
import com.frameglass.data.api.LibraryFilter.WATCHLIST
import com.frameglass.data.repo.LayoutKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The folders view and the order of the Library chips (owner requests of 2026-11-22). */
class LibraryLayoutTest {
    private val fresh = AppSettings()

    @Test
    fun anInstallFromBeforeTheSettingListsEveryFolderAtOnce() {
        assertNull(fresh.foldersMode)
        assertEquals(FoldersMode.FLAT, fresh.foldersView)
        assertEquals(FoldersMode.FLAT, FoldersMode.DEFAULT)
    }

    @Test
    fun theChosenFoldersViewIsTheOneShown() {
        assertEquals(FoldersMode.TREE, fresh.copy(foldersMode = FoldersMode.TREE).foldersView)
        assertEquals(FoldersMode.FLAT, fresh.copy(foldersMode = FoldersMode.FLAT).foldersView)
    }

    @Test
    fun aStoredFoldersViewReadsBackAndAnUnknownOneIsNoChoice() {
        for (mode in FoldersMode.entries) assertEquals(mode, FoldersMode.parse(mode.name))
        assertNull(FoldersMode.parse(null))
        assertNull(FoldersMode.parse(""))
        assertNull(FoldersMode.parse("GRID"))
        assertNull(FoldersMode.parse("flat"))
    }

    @Test
    fun thePreferenceNamesAreFixed() {
        assertEquals("foldersMode", LayoutKeys.FOLDERS_MODE)
        assertEquals("libraryChips", LayoutKeys.LIBRARY_CHIPS)
    }

    @Test
    fun theDefaultOrderIsTheOneTheAppAlwaysHadAndNamesEveryFilter() {
        assertEquals(listOf(ALL, MOVIES, SERIES, ANIME, VIDEOS, FAVORITES, WATCHLIST, FOLDERS), ChipOrder.DEFAULT)
        assertEquals(LibraryFilter.entries.toSet(), ChipOrder.DEFAULT.toSet())
        assertEquals(ChipOrder.DEFAULT, ChipOrder.full(null))
    }

    @Test
    fun anOrderIsStoredAsNamesAndReadBack() {
        val order = listOf(FOLDERS, ALL, VIDEOS)
        assertEquals("FOLDERS,ALL,VIDEOS", ChipOrder.encode(order))
        assertEquals(order, ChipOrder.parse(ChipOrder.encode(order)))
        assertEquals(order, ChipOrder.fromNames(ChipOrder.names(order)))
    }

    @Test
    fun namesThisBuildDoesNotKnowAndRepeatsAreDropped() {
        assertEquals(listOf(FOLDERS, ALL), ChipOrder.parse("FOLDERS,PODCASTS, ALL ,FOLDERS,"))
        assertNull(ChipOrder.parse(null))
        assertNull(ChipOrder.parse(""))
        assertNull(ChipOrder.parse("PODCASTS,RADIO"))
        assertNull(ChipOrder.fromNames(emptyList()))
    }

    @Test
    fun chipsTheSavedOrderDoesNotNameComeAfterItInTheDefaultOrder() {
        // As after an update that adds a chip, or a backup written by a build that had fewer.
        assertEquals(
            listOf(FOLDERS, VIDEOS, ALL, MOVIES, SERIES, ANIME, FAVORITES, WATCHLIST),
            ChipOrder.full(listOf(FOLDERS, VIDEOS)),
        )
        assertEquals(LibraryFilter.entries.size, ChipOrder.full(listOf(WATCHLIST, WATCHLIST, ALL)).size)
    }

    @Test
    fun animeAndWatchlistShowOnlyWhileTheyHoldSomething() {
        assertEquals(
            listOf(ALL, MOVIES, SERIES, VIDEOS, FAVORITES, FOLDERS),
            ChipOrder.shown(null, hasAnime = false, hasWatchlist = false),
        )
        assertEquals(ChipOrder.DEFAULT, ChipOrder.shown(null, hasAnime = true, hasWatchlist = true))
    }

    @Test
    fun aConditionalChipTakesItsSavedPlaceWhenItAppears() {
        val saved = listOf(WATCHLIST, FOLDERS, ANIME, ALL, MOVIES, SERIES, VIDEOS, FAVORITES)
        assertEquals(
            listOf(FOLDERS, ALL, MOVIES, SERIES, VIDEOS, FAVORITES),
            ChipOrder.shown(saved, hasAnime = false, hasWatchlist = false),
        )
        assertEquals(
            listOf(WATCHLIST, FOLDERS, ALL, MOVIES, SERIES, VIDEOS, FAVORITES),
            ChipOrder.shown(saved, hasAnime = false, hasWatchlist = true),
        )
        assertEquals(saved, ChipOrder.shown(saved, hasAnime = true, hasWatchlist = true))
    }

    @Test
    fun theFilterInViewAlwaysHasItsChip() {
        val shown = ChipOrder.shown(null, hasAnime = false, hasWatchlist = false, current = WATCHLIST)
        assertEquals(listOf(ALL, MOVIES, SERIES, VIDEOS, FAVORITES, WATCHLIST, FOLDERS), shown)
    }

    @Test
    fun libraryOpensOnTheFirstChipOfTheOrder() {
        assertEquals(ALL, ChipOrder.opening(null, hasAnime = null, hasWatchlist = null))
        assertEquals(FOLDERS, ChipOrder.opening(listOf(FOLDERS, ALL), hasAnime = null, hasWatchlist = null))
        assertEquals(VIDEOS, ChipOrder.opening(listOf(VIDEOS), hasAnime = true, hasWatchlist = true))
    }

    @Test
    fun aFirstChipThatIsNotOnScreenGivesWayToTheNext() {
        val saved = listOf(ANIME, WATCHLIST, SERIES)
        assertEquals(ANIME, ChipOrder.opening(saved, hasAnime = true, hasWatchlist = false))
        assertEquals(WATCHLIST, ChipOrder.opening(saved, hasAnime = false, hasWatchlist = true))
        assertEquals(SERIES, ChipOrder.opening(saved, hasAnime = false, hasWatchlist = false))
    }

    @Test
    fun theOpeningFilterWaitsWhileAFirstConditionalChipIsNotKnownYet() {
        val saved = listOf(ANIME, WATCHLIST, SERIES)
        assertNull(ChipOrder.opening(saved, hasAnime = null, hasWatchlist = null))
        assertNull(ChipOrder.opening(saved, hasAnime = false, hasWatchlist = null))
        // Nothing is waited for once a chip before the unknown one is on screen.
        assertEquals(ANIME, ChipOrder.opening(saved, hasAnime = true, hasWatchlist = null))
    }

    @Test
    fun aChipMovesTowardTheStartAndTowardTheEnd() {
        val shown = ChipOrder.DEFAULT
        assertEquals(
            listOf(FOLDERS, ALL, MOVIES, SERIES, ANIME, VIDEOS, FAVORITES, WATCHLIST),
            ChipOrder.move(null, shown, from = 7, to = 0),
        )
        assertEquals(
            listOf(MOVIES, SERIES, ALL, ANIME, VIDEOS, FAVORITES, WATCHLIST, FOLDERS),
            ChipOrder.move(null, shown, from = 0, to = 2),
        )
        assertEquals(
            listOf(MOVIES, SERIES, ANIME, VIDEOS, FAVORITES, WATCHLIST, FOLDERS, ALL),
            ChipOrder.move(null, shown, from = 0, to = 7),
        )
    }

    @Test
    fun aMoveThatGoesNowhereOrNamesNoChipChangesNothing() {
        val shown = ChipOrder.shown(null, hasAnime = false, hasWatchlist = false)
        assertEquals(ChipOrder.DEFAULT, ChipOrder.move(null, shown, from = 2, to = 2))
        assertEquals(ChipOrder.DEFAULT, ChipOrder.move(null, shown, from = -1, to = 2))
        assertEquals(ChipOrder.DEFAULT, ChipOrder.move(null, shown, from = 1, to = shown.size))
    }

    @Test
    fun chipsThatAreNotOnScreenKeepTheirPlacesThroughAMove() {
        // Anime and Watchlist are hidden, so the row is All, Movies, Series, Videos, Favourites, Folders.
        val shown = ChipOrder.shown(null, hasAnime = false, hasWatchlist = false)
        // Folders dropped between Series and Videos: it lands right before Videos, after the hidden Anime.
        val moved = ChipOrder.move(null, shown, from = 5, to = 3)
        assertEquals(listOf(ALL, MOVIES, SERIES, ANIME, FOLDERS, VIDEOS, FAVORITES, WATCHLIST), moved)
        assertEquals(listOf(ALL, MOVIES, SERIES, FOLDERS, VIDEOS, FAVORITES), ChipOrder.shown(moved, false, false))
        // All dropped at the end of the row: it lands right after Folders, the chip now before it.
        val toEnd = ChipOrder.move(null, shown, from = 0, to = 5)
        assertEquals(listOf(MOVIES, SERIES, ANIME, VIDEOS, FAVORITES, WATCHLIST, FOLDERS, ALL), toEnd)
        // When the hidden chips appear they are where they were, around the chips that moved.
        assertEquals(listOf(ALL, MOVIES, SERIES, ANIME, FOLDERS, VIDEOS, FAVORITES, WATCHLIST), ChipOrder.shown(moved, true, true))
    }

    @Test
    fun aMoveOnAShortSavedOrderCompletesItFirst() {
        val shown = ChipOrder.shown(listOf(FOLDERS), hasAnime = true, hasWatchlist = true)
        assertEquals(FOLDERS, shown.first())
        val moved = ChipOrder.move(listOf(FOLDERS), shown, from = 0, to = 1)
        assertEquals(listOf(ALL, FOLDERS, MOVIES, SERIES, ANIME, VIDEOS, FAVORITES, WATCHLIST), moved)
        assertEquals(LibraryFilter.entries.toSet(), moved.toSet())
    }

    @Test
    fun everyMoveKeepsEveryChipOnce() {
        val shown = ChipOrder.shown(null, hasAnime = true, hasWatchlist = false)
        for (from in shown.indices) {
            for (to in shown.indices) {
                val moved = ChipOrder.move(null, shown, from, to)
                assertEquals(LibraryFilter.entries.size, moved.size)
                assertEquals(LibraryFilter.entries.toSet(), moved.toSet())
                // The row reads as the drag left it.
                val expected = shown.toMutableList().apply { add(to, removeAt(from)) }
                assertEquals(expected, ChipOrder.shown(moved, hasAnime = true, hasWatchlist = false))
            }
        }
    }

    @Test
    fun resettingIsGoingBackToNoSavedOrder() {
        assertTrue(ChipOrder.isDefault(null))
        assertTrue(ChipOrder.isDefault(ChipOrder.DEFAULT))
        assertTrue(ChipOrder.isDefault(listOf(ALL, MOVIES)))
        assertFalse(ChipOrder.isDefault(listOf(FOLDERS)))
        val moved = fresh.copy(libraryChips = ChipOrder.move(null, ChipOrder.DEFAULT, 7, 0), cinemaMode = true)
        val reset = moved.copy(libraryChips = null)
        assertEquals(ChipOrder.DEFAULT, ChipOrder.full(reset.libraryChips))
        assertEquals(fresh.copy(cinemaMode = true), reset)
    }
}
