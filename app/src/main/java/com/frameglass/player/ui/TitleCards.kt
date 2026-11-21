package com.frameglass.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.ViewSize
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassSource
import com.frameglass.glass.LandscapeCard
import com.frameglass.glass.PosterCard
import com.frameglass.player.nav.ArtRef
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.sharedArt
import com.frameglass.player.select.selectItem
import com.frameglass.player.select.selectableItem
import com.frameglass.player.select.selectionMark
import com.frameglass.player.views.LocalFullLinePad
import com.frameglass.player.views.ViewGrid
import com.frameglass.player.views.titleItems

/** A poster that opens Details, with its cover flying there. [source] names the row it sits in. */
@Composable
internal fun TitlePoster(card: TitleCard, source: String, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val key = remember(source, card.id) { "$source:${card.id}" }
    val item = card.selectItem()
    PosterCard(
        title = card.name,
        coverPath = card.coverPath,
        onClick = { navigator.details(card.id, ArtRef(key, card.coverPath)) },
        // The mark sits on the cover alone, so its rim hugs the poster and not the title under it.
        modifier = modifier.selectableItem(item, look = null).cardMenu(item) { CardTarget.Title(card.id, card.name) },
        year = card.year,
        rating = card.rating?.toCard(),
        progress = card.progress,
        watched = card.watched,
        unavailable = card.unavailable,
        coverModifier = Modifier.selectionMark(item).sharedArt(key),
    )
}

/** A title as a 16:9 card, for videos, whose art is a frame. It opens Details like a poster does. */
@Composable
internal fun TitleFrame(card: TitleCard, source: String, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val key = remember(source, card.id) { "$source:${card.id}" }
    val item = card.selectItem()
    LandscapeCard(
        title = card.name,
        imagePath = card.coverPath,
        onClick = { navigator.details(card.id, ArtRef(key, card.coverPath)) },
        modifier = modifier.selectableItem(item).cardMenu(item) { CardTarget.Title(card.id, card.name) },
        subtitle = card.year?.let { localNumber(it) },
        progress = card.progress,
        imageModifier = Modifier.sharedArt(key),
        rating = card.rating?.toCard(),
        watched = card.watched,
        unavailable = card.unavailable,
    )
}

/** A plain grid of titles at [size] that runs under the bars. [frames] says the art is 16:9, as for videos. */
@Composable
internal fun TitleGrid(
    titles: List<TitleCard>,
    source: String,
    state: LazyGridState,
    top: Dp,
    bottom: Dp,
    modifier: Modifier = Modifier,
    frames: Boolean = false,
    size: ViewSize = ViewSize.MEDIUM_GRID,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    GlassSource(modifier.fillMaxSize()) {
        ViewGrid(size, frames, state, top, bottom) {
            header?.invoke(this)
            titleItems(titles, source, size, frames)
        }
    }
}

internal fun gridPadding(top: Dp, bottom: Dp) = PaddingValues(
    start = FrameSpacing.S16,
    end = FrameSpacing.S16,
    top = top,
    bottom = bottom,
)

/**
 * An item that spans the whole grid line: chips, a header, a message. Under it stays the gap a grid has always
 * had, whatever the view size packs the cells to ([LocalFullLinePad]).
 */
internal fun LazyGridScope.fullLine(key: Any, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = "line") {
        Box(Modifier.padding(bottom = LocalFullLinePad.current)) { content() }
    }
}

/** A quiet line of text inside a grid, for an empty state. */
@Composable
internal fun GridMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = FrameType.Body,
        color = FrameColors.SecondaryText,
        modifier = modifier.padding(vertical = FrameSpacing.S24),
    )
}
