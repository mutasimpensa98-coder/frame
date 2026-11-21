package com.frameglass.player.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frameglass.data.api.SearchResults
import com.frameglass.data.api.ViewSize
import com.frameglass.glass.CastAvatar
import com.frameglass.glass.FilterChips
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassKind
import com.frameglass.glass.GlassSurface
import com.frameglass.glass.SectionHeader
import com.frameglass.player.R
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.Route
import com.frameglass.player.ui.CenteredMessage
import com.frameglass.player.ui.FrameTextField
import com.frameglass.player.ui.TitleGrid
import com.frameglass.player.ui.bleed
import com.frameglass.player.ui.fullLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

private val FieldHeight = 52.dp

/** What [SearchBar] takes under the status bar: its field and the room around it. Lists under it start below this. */
internal val SearchBarSpace = FieldHeight + FrameSpacing.S24

/**
 * The search a screen can turn its top bar into (owner request of 2026-11-22): whether it is open, and the words
 * typed. Closing it forgets the words, and the screen under it is as it was.
 */
@Stable
internal class SearchMode(open: Boolean = false, words: String = "") {
    var open by mutableStateOf(open)
        private set
    var words by mutableStateOf(words)

    /** The words to search for now: nothing while the search is closed, so a closed search asks nothing. */
    val asked: String get() = if (open) words else ""

    fun start() {
        open = true
    }

    fun close() {
        open = false
        words = ""
    }

    companion object {
        val Saver = listSaver<SearchMode, Any>(
            save = { listOf(it.open, it.words) },
            restore = { SearchMode(it[0] as Boolean, it[1] as String) },
        )
    }
}

@Composable
internal fun rememberSearchMode(): SearchMode = rememberSaveable(saver = SearchMode.Saver) { SearchMode() }

/**
 * Back closes an open search before anything else of the screen. Composed only while the search is open, so it is
 * a newer handler than the one of the tabs, and an older one than selection mode, which closes first.
 */
@Composable
internal fun SearchBack(mode: SearchMode) {
    if (mode.open) BackHandler(onBack = mode::close)
}

/** The button of a top bar that opens [mode]. */
@Composable
internal fun SearchAction(mode: SearchMode, description: String, modifier: Modifier = Modifier) {
    GlassIconButton(
        icon = Icons.Rounded.Search,
        contentDescription = description,
        onClick = mode::start,
        modifier = modifier,
    )
}

/**
 * The answer to the words being typed, live (F-U10). A new letter cancels the search before it, so only the last
 * one is ever worked out, and each answer carries the words it belongs to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun <T> rememberSearch(query: () -> String, search: (String) -> Flow<T>): Searched<T> {
    val words by rememberUpdatedState(query)
    val ask by rememberUpdatedState(search)
    val searched by produceState(Searched<T>("", null)) {
        snapshotFlow { words() }
            .flatMapLatest { asked -> ask(asked).map { Searched(asked, it) } }
            .collectLatest { value = it }
    }
    return searched
}

/**
 * The search field as a top bar. With [onClose] it stands in for the top bar of another screen, so a back arrow
 * closes it, and [autoFocus] puts the cursor in it and raises the keyboard. [trailing] is what stays beside the
 * field, such as the view button.
 */
@Composable
internal fun SearchBar(
    query: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.app_search_placeholder),
    onClose: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = FrameTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Only an empty field asks for the keyboard. A field that comes back with its words, as after selection mode
    // or after a page opened from a result, leaves the results in full view.
    if (autoFocus) {
        LaunchedEffect(focus) {
            if (query.isEmpty()) {
                focus.requestFocus()
                keyboard?.show()
            }
        }
    }
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(start = FrameSpacing.S16, end = FrameSpacing.S16, top = FrameSpacing.S8)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        if (onClose != null) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.app_search_close),
                onClick = onClose,
            )
        }
        GlassSurface(
            kind = GlassKind.Control,
            shape = FrameShapes.Chip,
            modifier = Modifier.weight(1f).height(FieldHeight),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = FrameSpacing.S16, end = FrameSpacing.S4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(24.dp), tint = colors.content)
                FrameTextField(
                    value = query,
                    onValueChange = onChange,
                    placeholder = placeholder,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    filled = false,
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Search,
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onChange("") }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.app_search_clear), tint = colors.content)
                    }
                }
            }
        }
        trailing()
    }
}

/** What stands over the titles of an answer: its genres as chips, its people as a row, and a header for the titles. */
internal fun LazyGridScope.searchHeader(found: SearchResults) {
    if (found.genres.isNotEmpty()) {
        fullLine("genres") {
            val navigator = LocalNavigator.current
            FilterChips(
                labels = found.genres,
                selectedIndex = -1,
                onSelect = { navigator.push(Route.Genre(found.genres[it])) },
                modifier = Modifier.bleed(FrameSpacing.S16),
            )
        }
    }
    if (found.people.isNotEmpty()) {
        fullLine("people") {
            val navigator = LocalNavigator.current
            Column(
                modifier = Modifier.bleed(FrameSpacing.S16),
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
            ) {
                SectionHeader(stringResource(R.string.app_search_people))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = FrameSpacing.S16),
                    horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
                ) {
                    items(found.people, key = { it.personId }) { person ->
                        CastAvatar(
                            name = person.name,
                            photoPath = person.photoPath,
                            onClick = { navigator.push(Route.Person(person.personId)) },
                        )
                    }
                }
            }
        }
    }
    if (found.titles.isNotEmpty()) {
        fullLine("titles") {
            SectionHeader(stringResource(R.string.app_search_titles), Modifier.bleed(FrameSpacing.S16))
        }
    }
}

/**
 * What a title search shows under its field: titles, people and genres from the search index (F-U10), at [size],
 * or a message. The Search tab and the search of Library are this one body. [source] names the list for the cover
 * that flies to Details, and differs between the two, because both can be on screen while a tab slides.
 */
@Composable
internal fun TitleSearchBody(
    query: String,
    searched: Searched<SearchResults>,
    source: String,
    grid: LazyGridState,
    top: Dp,
    bottom: Dp,
    size: ViewSize,
) {
    val found = searched.found
    when (SearchRules.titles(query, searched)) {
        SearchLook.HINT -> CenteredMessage(
            title = stringResource(R.string.app_search_title),
            body = stringResource(R.string.app_search_hint),
        )
        SearchLook.NOTHING -> CenteredMessage(stringResource(R.string.app_search_nothing))
        SearchLook.WAITING -> Unit
        SearchLook.RESULTS -> if (found != null) {
            TitleGrid(
                titles = found.titles,
                source = source,
                state = grid,
                top = top,
                bottom = bottom,
                size = size,
                header = { searchHeader(found) },
            )
        }
    }
}
