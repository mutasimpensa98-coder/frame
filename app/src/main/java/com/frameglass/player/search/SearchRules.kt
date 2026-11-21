package com.frameglass.player.search

import com.frameglass.data.api.FolderView
import com.frameglass.data.api.SearchResults
import com.frameglass.player.select.SelectItem
import com.frameglass.player.select.selectItem

/** What came back, and the words it answers. [found] is null until the first answer. */
internal data class Searched<T>(val query: String, val found: T?)

/** What a search page shows under its field. */
internal enum class SearchLook {
    /** Nothing is typed: the page says what can be searched. */
    HINT,

    /** Words are typed and no answer to show has come yet. The page stays quiet, with no message. */
    WAITING,

    /** The answer to these very words is empty. */
    NOTHING,

    /** Something to list. While the next answer is on its way, the last one stays. */
    RESULTS,
}

/**
 * How typed words and their answer turn into a page, shared by the Search tab, the search of Library and the
 * search of Folders (owner request of 2026-11-22). No screen is needed to test it.
 */
internal object SearchRules {
    /**
     * An empty answer says "nothing found" only when it answers the words on screen. An empty answer to earlier
     * words, such as the one a cleared field leaves behind, is not an answer to the next letter.
     */
    fun <T> look(query: String, searched: Searched<T>, isEmpty: (T) -> Boolean): SearchLook {
        if (query.isBlank()) return SearchLook.HINT
        val found = searched.found ?: return SearchLook.WAITING
        if (!isEmpty(found)) return SearchLook.RESULTS
        return if (searched.query == query) SearchLook.NOTHING else SearchLook.WAITING
    }

    fun titles(query: String, searched: Searched<SearchResults>): SearchLook =
        look(query, searched) { it.titles.isEmpty() && it.people.isEmpty() && it.genres.isEmpty() }

    fun folders(query: String, searched: Searched<FolderView>): SearchLook = look(query, searched) { it.entries.isEmpty() }

    /** The view button stands beside the field only while titles are listed under it. */
    fun showsView(query: String, found: SearchResults?): Boolean = query.isNotBlank() && found?.titles?.isNotEmpty() == true

    /** What "Select all" picks among title results: the titles, never a person or a genre. Null before any answer. */
    fun listed(found: SearchResults?): List<SelectItem>? = found?.titles?.map { it.selectItem() }

    /** What "Select all" picks among folder results. A video row the library lost cannot be picked. */
    fun listed(found: FolderView?): List<SelectItem>? = found?.entries?.mapNotNull { it.selectItem() }
}
