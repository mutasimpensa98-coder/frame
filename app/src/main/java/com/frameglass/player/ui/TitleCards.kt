package com.frameglass.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frameglass.data.api.TitleCard
import com.frameglass.glass.CoverImage
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassSource
import com.frameglass.glass.LandscapeCard
import com.frameglass.glass.PosterCard
import com.frameglass.glass.PressableGlass
import com.frameglass.glass.ProgressLine
import com.frameglass.glass.bidiIsolate
import com.frameglass.player.R
import com.frameglass.player.nav.ArtRef
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.sharedArt

private val GridGap = FrameSpacing.S12
private val GridRowGap = 20.dp
private val ListPosterWidth = 64.dp
private const val UnavailableAlpha = 0.45f

/** A poster that opens Details, with its cover flying there. [source] names the row it sits in. */
@Composable
internal fun TitlePoster(card: TitleCard, source: String, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val key = remember(source, card.id) { "$source:${card.id}" }
    PosterCard(
        title = card.name,
        coverPath = card.coverPath,
        onClick = { navigator.details(card.id, ArtRef(key, card.coverPath)) },
        modifier = modifier.cardMenu { CardTarget.Title(card.id, card.name) },
        year = card.year,
        rating = card.rating?.toCard(),
        progress = card.progress,
        watched = card.watched,
        unavailable = card.unavailable,
        coverModifier = Modifier.sharedArt(key),
    )
}

/** A title as a 16:9 card, for videos, whose art is a frame. It opens Details like a poster does. */
@Composable
internal fun TitleFrame(card: TitleCard, source: String, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val key = remember(source, card.id) { "$source:${card.id}" }
    LandscapeCard(
        title = card.name,
        imagePath = card.coverPath,
        onClick = { navigator.details(card.id, ArtRef(key, card.coverPath)) },
        modifier = modifier.cardMenu { CardTarget.Title(card.id, card.name) },
        subtitle = card.year?.let { localNumber(it) },
        progress = card.progress,
        imageModifier = Modifier.sharedArt(key),
        rating = card.rating?.toCard(),
        watched = card.watched,
        unavailable = card.unavailable,
    )
}

/** The list form of a title, for the Library layout toggle. */
@Composable
internal fun TitleListRow(card: TitleCard, source: String, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val menu = LocalCardMenu.current
    val key = remember(source, card.id) { "$source:${card.id}" }
    PressableGlass(
        onClick = { navigator.details(card.id, ArtRef(key, card.coverPath)) },
        modifier = modifier.fillMaxWidth(),
        shape = FrameShapes.Poster,
        onLongClick = menu?.let { { it.open(CardTarget.Title(card.id, card.name)) } },
        highlight = false,
    ) {
        Row(
            modifier = if (card.unavailable) Modifier.alpha(UnavailableAlpha) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .sharedArt(key)
                    .width(ListPosterWidth)
                    .aspectRatio(FrameSizes.PosterRatio),
            ) {
                CoverImage(card.coverPath, null, Modifier.matchParentSize())
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
            ) {
                Text(
                    text = bidiIsolate(card.name),
                    style = FrameType.Body.copy(fontWeight = FontWeight.W600),
                    color = FrameColors.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val year = card.year?.let { localNumber(it) }
                val rating = card.rating?.let { "${ratingText(it.value)} ${sourceLabel(it.source)}" }
                val state = when {
                    card.unavailable -> stringResource(R.string.app_unavailable)
                    card.watched -> stringResource(R.string.app_watched)
                    else -> null
                }
                val meta = listOfNotNull(year, rating, state)
                if (meta.isNotEmpty()) {
                    Text(
                        text = metaLine(meta),
                        style = FrameType.Caption,
                        color = FrameColors.SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                card.progress?.let {
                    Spacer(Modifier.height(FrameSpacing.S4))
                    ProgressLine(it, Modifier.fillMaxWidth(), FrameColors.Elevated)
                }
            }
        }
    }
}

/** Three posters to a line, two [TitleFrame] cards, or one list row. */
internal fun LazyGridScope.titleItems(
    titles: List<TitleCard>,
    source: String,
    asList: Boolean,
    frames: Boolean = false,
) {
    val type = if (asList) "row" else if (frames) "frame" else "poster"
    items(titles, key = { it.id }, contentType = { type }) { card ->
        when {
            asList -> TitleListRow(card, source)
            frames -> TitleFrame(card, source)
            else -> TitlePoster(card, source)
        }
    }
}

/** Grid columns for the Library layout toggle and for videos. */
internal fun libraryColumns(asList: Boolean, frames: Boolean = false): GridCells =
    GridCells.Fixed(if (asList) 1 else if (frames) 2 else 3)

/** A plain grid of titles that runs under the bars, of posters or of [TitleFrame] cards. */
@Composable
internal fun TitleGrid(
    titles: List<TitleCard>,
    source: String,
    state: LazyGridState,
    top: Dp,
    bottom: Dp,
    modifier: Modifier = Modifier,
    frames: Boolean = false,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    GlassSource(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = libraryColumns(asList = false, frames = frames),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = gridPadding(top, bottom),
            horizontalArrangement = Arrangement.spacedBy(GridGap),
            verticalArrangement = Arrangement.spacedBy(GridRowGap),
        ) {
            header?.invoke(this)
            titleItems(titles, source, asList = false, frames = frames)
        }
    }
}

internal fun gridPadding(top: Dp, bottom: Dp) = PaddingValues(
    start = FrameSpacing.S16,
    end = FrameSpacing.S16,
    top = top,
    bottom = bottom,
)

internal val GridColumnGap = GridGap
internal val GridLineGap = GridRowGap

/** An item that spans the whole grid line. */
internal fun LazyGridScope.fullLine(key: Any, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
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
