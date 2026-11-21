package com.frameglass.data.api

import com.frameglass.data.repo.ViewKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The view size each place remembers, and what an install from before the setting shows. */
class ViewSettingsTest {
    private val fresh = AppSettings()

    @Test
    fun theSizesRunFromTheSmallestToTheLargest() {
        assertEquals(
            listOf(
                ViewSize.COMPACT_LIST,
                ViewSize.LIST,
                ViewSize.SMALL_GRID,
                ViewSize.MEDIUM_GRID,
                ViewSize.LARGE_GRID,
            ),
            ViewSize.entries.toList(),
        )
        assertTrue(ViewSize.COMPACT_LIST.isList)
        assertTrue(ViewSize.LIST.isList)
        assertFalse(ViewSize.SMALL_GRID.isList)
        assertFalse(ViewSize.MEDIUM_GRID.isList)
        assertFalse(ViewSize.LARGE_GRID.isList)
    }

    @Test
    fun anInstallFromBeforeViewSizesStoresNothing() {
        for (place in ViewPlace.entries) assertNull(fresh.storedViewSize(place))
    }

    @Test
    fun withNothingStoredLibraryListsAndThePrivateFolderKeepTheGridTheyHad() {
        // The old list toggle of Library lived for one session and was never saved, so there is nothing to carry over.
        assertEquals(ViewSize.MEDIUM_GRID, fresh.viewSize(ViewPlace.LIBRARY))
        assertEquals(ViewSize.MEDIUM_GRID, fresh.viewSize(ViewPlace.LISTS))
        assertEquals(ViewSize.MEDIUM_GRID, fresh.viewSize(ViewPlace.PRIVATE))
    }

    @Test
    fun foldersWereAListAndOpenAsTheCompactOne() {
        assertEquals(ViewSize.COMPACT_LIST, fresh.viewSize(ViewPlace.FOLDERS))
    }

    @Test
    fun eachPlaceRemembersItsOwnSize() {
        val chosen = fresh
            .withViewSize(ViewPlace.LIBRARY, ViewSize.SMALL_GRID)
            .withViewSize(ViewPlace.FOLDERS, ViewSize.LARGE_GRID)
            .withViewSize(ViewPlace.PRIVATE, ViewSize.COMPACT_LIST)
        assertEquals(ViewSize.SMALL_GRID, chosen.viewSize(ViewPlace.LIBRARY))
        assertEquals(ViewSize.LARGE_GRID, chosen.viewSize(ViewPlace.FOLDERS))
        assertEquals(ViewSize.COMPACT_LIST, chosen.viewSize(ViewPlace.PRIVATE))
        // The place nobody touched still shows its default, and stores nothing.
        assertEquals(ViewSize.MEDIUM_GRID, chosen.viewSize(ViewPlace.LISTS))
        assertNull(chosen.storedViewSize(ViewPlace.LISTS))
    }

    @Test
    fun aNewChoiceReplacesTheOldOneAndTouchesNothingElse() {
        val first = fresh.withViewSize(ViewPlace.LIBRARY, ViewSize.LIST).copy(cinemaMode = true)
        val second = first.withViewSize(ViewPlace.LIBRARY, ViewSize.LARGE_GRID)
        assertEquals(ViewSize.LARGE_GRID, second.viewSize(ViewPlace.LIBRARY))
        assertEquals(first.copy(viewLibrary = ViewSize.LARGE_GRID), second)
    }

    @Test
    fun clearingAChoiceBringsTheDefaultBack() {
        val chosen = fresh.withViewSize(ViewPlace.FOLDERS, ViewSize.LIST)
        assertEquals(ViewSize.COMPACT_LIST, chosen.withViewSize(ViewPlace.FOLDERS, null).viewSize(ViewPlace.FOLDERS))
    }

    @Test
    fun aStoredNameReadsBackAndAnUnknownOneIsNoChoice() {
        for (size in ViewSize.entries) assertEquals(size, ViewSize.parse(size.name))
        assertNull(ViewSize.parse(null))
        assertNull(ViewSize.parse(""))
        assertNull(ViewSize.parse("HUGE_GRID"))
        assertNull(ViewSize.parse("list"))
    }

    @Test
    fun everyPlaceHasAPreferenceNameOfItsOwn() {
        val names = ViewPlace.entries.map(ViewKeys::of)
        assertEquals(listOf("viewLibrary", "viewFolders", "viewPrivate", "viewLists"), names)
        assertEquals(names.size, names.toSet().size)
    }
}
