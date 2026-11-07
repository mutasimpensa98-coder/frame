package com.frameglass.player.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.frameglass.data.api.SearchResults
import com.frameglass.data.api.frameServices
import com.frameglass.glass.CastAvatar
import com.frameglass.glass.FilterChips
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
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
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.statusInset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest

private val FieldHeight = 52.dp
private val FieldSpace = FieldHeight + FrameSpacing.S24

/** F-U10: titles, people and genres from the search index, updating with every letter. */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun SearchScreen() {
    val library = LocalContext.current.frameServices.library
    val navigator = LocalNavigator.current
    var query by rememberSaveable { mutableStateOf("") }
    val grid = rememberLazyGridState()
    val results by produceState<SearchResults?>(null, library) {
        snapshotFlow { query }
            .flatMapLatest { library.search(it) }
            .collectLatest { value = it }
    }
    val found = results
    val nothing = found != null && found.titles.isEmpty() && found.people.isEmpty() && found.genres.isEmpty()

    Box(Modifier.fillMaxSize()) {
        when {
            query.isBlank() -> CenteredMessage(
                title = stringResource(R.string.app_search_title),
                body = stringResource(R.string.app_search_hint),
            )
            nothing -> CenteredMessage(stringResource(R.string.app_search_nothing))
            found != null -> TitleGrid(
                titles = found.titles,
                source = "search",
                state = grid,
                top = statusInset() + FieldSpace,
                bottom = navigationInset() + FrameSizes.TabBarClearance,
                header = {
                    if (found.genres.isNotEmpty()) {
                        fullLine("genres") {
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
                            SectionHeader(
                                stringResource(R.string.app_search_titles),
                                Modifier.bleed(FrameSpacing.S16),
                            )
                        }
                    }
                },
            )
        }
        GlassSurface(
            kind = GlassKind.Control,
            shape = FrameShapes.Chip,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = FrameSpacing.S16, end = FrameSpacing.S16, top = FrameSpacing.S8)
                .fillMaxWidth()
                .height(FieldHeight),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = FrameSpacing.S16, end = FrameSpacing.S4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(24.dp), tint = FrameColors.White)
                FrameTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.app_search_placeholder),
                    modifier = Modifier.weight(1f),
                    filled = false,
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Search,
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Rounded.Close,
                            stringResource(R.string.app_search_clear),
                            tint = FrameColors.White,
                        )
                    }
                }
            }
        }
    }
}
