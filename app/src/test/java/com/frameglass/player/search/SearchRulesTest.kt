package com.frameglass.player.search

import com.frameglass.data.api.CastMember
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.FolderView
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.SearchResults
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.TitleKind
import com.frameglass.player.select.SelectItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How typed words and their answer turn into a page, with no screen. The Search tab, the search of Library and
 * the search of Folders all read these rules (owner request of 2026-11-22).
 */
class SearchRulesTest {
    private fun card(id: Long, unavailable: Boolean = false) = TitleCard(
        id = id, kind = TitleKind.MOVIE, name = "Title $id", year = 2020, coverPath = null, rating = null,
        progress = null, unavailable = unavailable, watched = false,
    )

    private val nobody = SearchResults(emptyList(), emptyList(), emptyList())
    private val titles = SearchResults(listOf(card(1), card(2, unavailable = true)), emptyList(), emptyList())
    private val people = SearchResults(emptyList(), listOf(CastMember(9, "An Actor", null, null)), emptyList())
    private val genres = SearchResults(emptyList(), emptyList(), listOf("Drama"))

    private fun entry(path: String, directory: Boolean, row: Long? = null) = FolderEntry(
        path = path, name = path.substringAfterLast('/'), isDirectory = directory, videoCount = 1, sizeBytes = 10,
        isNew = false, hasSubtitle = false, coverPath = null, progress = null, unavailable = false,
        play = row?.let { PlayRef(fileRowId = it) }, direct = directory,
    )

    @Test
    fun withNothingTypedThePageSaysWhatCanBeSearched() {
        assertEquals(SearchLook.HINT, SearchRules.titles("", Searched("", null)))
        assertEquals(SearchLook.HINT, SearchRules.titles("   ", Searched("   ", nobody)))
        // What an earlier search found does not show under an empty field.
        assertEquals(SearchLook.HINT, SearchRules.titles("", Searched("harbour", titles)))
        assertEquals(SearchLook.HINT, SearchRules.folders("", Searched("", null)))
    }

    @Test
    fun beforeTheFirstAnswerThePageIsQuiet() {
        assertEquals(SearchLook.WAITING, SearchRules.titles("h", Searched("", null)))
        assertEquals(SearchLook.WAITING, SearchRules.folders("h", Searched("", null)))
    }

    @Test
    fun anyKindOfResultIsAResult() {
        for (found in listOf(titles, people, genres)) {
            assertEquals(SearchLook.RESULTS, SearchRules.titles("dra", Searched("dra", found)))
        }
        val folders = FolderView(null, null, listOf(entry("/storage/Clips", directory = true)))
        assertEquals(SearchLook.RESULTS, SearchRules.folders("cl", Searched("cl", folders)))
    }

    @Test
    fun nothingFoundIsSaidOnlyOfTheWordsOnScreen() {
        assertEquals(SearchLook.NOTHING, SearchRules.titles("zzz", Searched("zzz", nobody)))
        assertEquals(SearchLook.NOTHING, SearchRules.folders("zzz", Searched("zzz", FolderView(null, null, emptyList()))))
        // The empty answer a cleared field left behind is not an answer to the first letter of the next search.
        assertEquals(SearchLook.WAITING, SearchRules.titles("h", Searched("", nobody)))
        assertEquals(SearchLook.WAITING, SearchRules.titles("zzzz", Searched("zzz", nobody)))
        assertEquals(SearchLook.WAITING, SearchRules.folders("h", Searched("", FolderView(null, null, emptyList()))))
    }

    @Test
    fun theLastResultsStayWhileTheNextAnswerIsOnItsWay() {
        // No flash of an empty page between two letters.
        assertEquals(SearchLook.RESULTS, SearchRules.titles("harb", Searched("har", titles)))
    }

    @Test
    fun theViewButtonShowsOnlyWhileTitlesAreListed() {
        assertTrue(SearchRules.showsView("har", titles))
        assertFalse(SearchRules.showsView("", titles))
        assertFalse(SearchRules.showsView("har", people))
        assertFalse(SearchRules.showsView("har", genres))
        assertFalse(SearchRules.showsView("har", null))
    }

    @Test
    fun selectAllPicksTheTitlesFoundAndNeverAPersonOrAGenre() {
        val found = SearchResults(titles.titles, people.people, genres.genres)
        val listed = SearchRules.listed(found).orEmpty()
        assertEquals(listOf("title|1", "title|2"), listed.map { it.key })
        assertEquals(listOf(true, false), listed.map { it.onStorage })
        assertTrue(SearchRules.listed(people).orEmpty().isEmpty())
        assertNull(SearchRules.listed(null as SearchResults?))
    }

    @Test
    fun selectAllPicksTheFoldersAndVideosFoundAndSkipsARowTheLibraryLost() {
        val found = FolderView(
            null,
            null,
            listOf(
                entry("/storage/Clips", directory = true),
                entry("/storage/Clips/a.mp4", directory = false, row = 4),
                entry("/storage/Clips/lost.mp4", directory = false, row = null),
            ),
        )
        val listed = SearchRules.listed(found).orEmpty()
        assertEquals(listOf("folder|/storage/Clips", "file|4"), listed.map { it.key })
        // A folder that was found is a row of its own videos, so a delete takes those alone.
        assertTrue((listed.first() as SelectItem.Folder).direct)
        assertNull(SearchRules.listed(null as FolderView?))
    }
}
