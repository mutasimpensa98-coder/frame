package com.frameglass.player.search

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.frameglass.data.api.ViewPlace
import com.frameglass.data.api.frameServices
import com.frameglass.glass.FrameSizes
import com.frameglass.player.select.SelectItem
import com.frameglass.player.select.SelectionPlace
import com.frameglass.player.select.SelectionScaffold
import com.frameglass.player.select.SelectionState
import com.frameglass.player.select.clearance
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.statusInset
import com.frameglass.player.views.ViewButton
import com.frameglass.player.views.rememberViewSize

/**
 * F-U10: titles, people and genres from the search index, updating with every letter. The field, the live answer
 * and the list are shared with the search of Library (SearchParts.kt), so the two cannot drift apart.
 */
@Composable
internal fun SearchScreen() {
    val library = LocalContext.current.frameServices.library
    var query by rememberSaveable { mutableStateOf("") }
    val grid = rememberLazyGridState()
    val searched = rememberSearch({ query }, library::search)
    val selection = remember { SelectionState<SelectItem> { it.key } }
    val listed = remember(searched) { SearchRules.listed(searched.found) }
    // Results are a list of titles like the row, genre and person pages, and share their size.
    val size = rememberViewSize(ViewPlace.LISTS)

    // While selecting, the selection bar stands where the search field was, and the words typed are kept.
    SelectionScaffold(
        selection = selection,
        place = SelectionPlace.TITLES,
        listed = listed,
        selectingActions = { ViewButton(ViewPlace.LISTS) },
        topBar = {
            SearchBar(query, onChange = { query = it }) {
                if (SearchRules.showsView(query, searched.found)) ViewButton(ViewPlace.LISTS)
            }
        },
    ) {
        TitleSearchBody(
            query = query,
            searched = searched,
            source = "search",
            grid = grid,
            top = statusInset() + SearchBarSpace,
            bottom = navigationInset() + selection.clearance(FrameSizes.TabBarClearance),
            size = size,
        )
    }
}
