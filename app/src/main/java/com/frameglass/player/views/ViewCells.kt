package com.frameglass.player.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.ViewSize
import com.frameglass.glass.CardDensity
import com.frameglass.glass.CoverImage
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassKind
import com.frameglass.glass.GlassSurface
import com.frameglass.glass.LandscapeCard
import com.frameglass.glass.PosterCard
import com.frameglass.glass.PressableGlass
import com.frameglass.glass.ProgressLine
import com.frameglass.glass.RatingBadge
import com.frameglass.glass.bidiIsolate
import com.frameglass.player.R
import com.frameglass.player.library.FolderRow
import com.frameglass.player.nav.ArtRef
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.sharedArt
import com.frameglass.player.select.SelectItem
import com.frameglass.player.select.SelectionLook
import com.frameglass.player.select.selectItem
import com.frameglass.player.select.selectableItem
import com.frameglass.player.select.selectionMark
import com.frameglass.player.ui.CardTarget
import com.frameglass.player.ui.FactChip
import com.frameglass.player.ui.cardMenuPress
import com.frameglass.player.ui.fileSizeLabel
import com.frameglass.player.ui.localNumber
import com.frameglass.player.ui.longPress
import com.frameglass.player.ui.metaLine
import com.frameglass.player.ui.toCard

// The compact row of the owner's reference: a 46 x 34 dp tile, the name, and one line of facts.
private val CompactTileWidth = 46.dp
private val CompactTileHeight = 34.dp
private val CompactTileShape = RoundedCornerShape(8.dp)
private val CompactIcon = 20.dp
private val ListPosterWidth = 64.dp
private val ListFrameWidth = 112.dp
private val FolderBadgeShape = RoundedCornerShape(8.dp)
private const val UnavailableAlpha = 0.45f

/**
 * What a cell does, apart from how it looks, so the same five looks serve Library and the private folder. [item]
 * is the cell as a pick, [artKey] pairs its picture with Details, and [onLongPress] is null while the screen is
 * selecting or has no menu.
 */
internal class CellHooks(
    val item: SelectItem?,
    val artKey: String?,
    val onClick: () -> Unit,
    val onLongPress: (() -> Unit)?,
)

/** A title of the library: a tap opens Details with the cover flying there, and a long press opens the card menu. */
@Composable
internal fun libraryHooks(card: TitleCard, source: String): CellHooks {
    val navigator = LocalNavigator.current
    val key = remember(source, card.id) { "$source:${card.id}" }
    val item = card.selectItem()
    return CellHooks(
        item = item,
        artKey = key,
        onClick = { navigator.details(card.id, ArtRef(key, card.coverPath)) },
        onLongPress = cardMenuPress(item) { CardTarget.Title(card.id, card.name) },
    )
}

@Composable
private fun titleState(card: TitleCard): String? = when {
    card.unavailable -> stringResource(R.string.app_unavailable)
    card.watched -> stringResource(R.string.app_watched)
    else -> null
}

/** A title at any of the five sizes. [frames] says its art is a 16:9 frame, as for videos. */
@Composable
internal fun TitleCell(card: TitleCard, size: ViewSize, frames: Boolean, hooks: CellHooks, modifier: Modifier = Modifier) {
    val face = ViewRules.face(size, frames)
    when (size) {
        ViewSize.COMPACT_LIST -> TitleCompactRow(card, hooks, face, modifier)
        ViewSize.LIST -> TitleListRow(card, frames, hooks, modifier)
        else -> TitleGridCard(card, ViewRules.density(size), frames, hooks, face, modifier)
    }
}

@Composable
private fun TitleGridCard(
    card: TitleCard,
    density: CardDensity,
    frames: Boolean,
    hooks: CellHooks,
    face: CellFace,
    modifier: Modifier,
) {
    val press = hooks.onLongPress
    val year = card.year?.let { localNumber(it) }
    val facts = ViewRules.largeFacts(year, card.genre).takeIf { face.moreFacts }?.let { metaLine(it) }
    val rating = card.rating?.takeIf { face.rating }?.toCard()
    // A frame is all picture, so the mark covers the card. A poster has its title under it, so the mark hugs the cover.
    val pickable = modifier.selectableItem(hooks.item, look = if (frames) SelectionLook.Art else null)
    // These cards take no long press themselves, so it is watched from outside, as the card menu always has.
    val pressable = if (press != null) pickable.longPress(stringResource(R.string.app_more), press) else pickable
    if (frames) {
        LandscapeCard(
            title = card.name,
            imagePath = card.coverPath,
            onClick = hooks.onClick,
            modifier = pressable,
            subtitle = facts ?: year,
            progress = card.progress,
            imageModifier = Modifier.sharedArt(hooks.artKey),
            rating = rating,
            watched = card.watched,
            unavailable = card.unavailable,
            density = density,
        )
    } else {
        PosterCard(
            title = card.name,
            coverPath = card.coverPath,
            onClick = hooks.onClick,
            modifier = pressable,
            year = card.year,
            rating = rating,
            progress = card.progress,
            watched = card.watched,
            unavailable = card.unavailable,
            coverModifier = Modifier.selectionMark(hooks.item).sharedArt(hooks.artKey),
            density = density,
            meta = facts,
        )
    }
}

/** The list row the app has always had. A video's thumbnail is its 16:9 frame; a film's is its poster. */
@Composable
private fun TitleListRow(card: TitleCard, frames: Boolean, hooks: CellHooks, modifier: Modifier) {
    val colors = FrameTheme.colors
    PressableGlass(
        onClick = hooks.onClick,
        modifier = modifier.fillMaxWidth().selectableItem(hooks.item, SelectionLook.Row),
        shape = FrameShapes.Poster,
        onLongClick = hooks.onLongPress,
        highlight = false,
    ) {
        Row(
            modifier = if (card.unavailable) Modifier.alpha(UnavailableAlpha) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .selectionMark(hooks.item)
                    .sharedArt(hooks.artKey)
                    .width(if (frames) ListFrameWidth else ListPosterWidth)
                    .aspectRatio(if (frames) FrameSizes.LandscapeRatio else FrameSizes.PosterRatio),
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
                    color = colors.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(card.year?.let { localNumber(it) }, card.genre, titleState(card))
                val rating = card.rating
                if (meta.isNotEmpty() || rating != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (meta.isNotEmpty()) {
                            Text(
                                text = metaLine(meta),
                                style = FrameType.Caption,
                                color = colors.secondaryContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        rating?.let { RatingBadge(it.toCard()) }
                    }
                }
                card.progress?.let {
                    Spacer(Modifier.height(FrameSpacing.S4))
                    ProgressLine(it, Modifier.fillMaxWidth(), colors.elevated)
                }
            }
        }
    }
}

/** The small tile of a compact row, with the mark of a picked item on it. */
@Composable
private fun CompactTile(item: SelectItem?, artKey: String?, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .selectionMark(item, shape = CompactTileShape)
            .sharedArt(artKey)
            .size(CompactTileWidth, CompactTileHeight)
            .clip(CompactTileShape)
            .background(FrameTheme.colors.elevated),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun CompactName(name: String) {
    Text(
        text = bidiIsolate(name),
        style = FrameType.Body.copy(fontWeight = FontWeight.W500),
        color = FrameTheme.colors.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CompactFacts(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = FrameType.Caption,
        color = FrameTheme.colors.secondaryContent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The smallest look: a tiny picture, the name, and one line with the year, the state and the rating. */
@Composable
private fun TitleCompactRow(card: TitleCard, hooks: CellHooks, face: CellFace, modifier: Modifier) {
    PressableGlass(
        onClick = hooks.onClick,
        modifier = modifier.fillMaxWidth().selectableItem(hooks.item, SelectionLook.Row),
        shape = FrameShapes.Poster,
        onLongClick = hooks.onLongPress,
        highlight = false,
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = FrameSpacing.S4)
                .then(if (card.unavailable) Modifier.alpha(UnavailableAlpha) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactTile(hooks.item, hooks.artKey) {
                CoverImage(card.coverPath, null, Modifier.matchParentSize(), CompactTileShape)
                card.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
            }
            Column(Modifier.weight(1f)) {
                CompactName(card.name)
                val meta = listOfNotNull(card.year?.let { localNumber(it) }, card.genre, titleState(card))
                val rating = card.rating?.takeIf { face.rating }
                if (meta.isNotEmpty() || rating != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (meta.isNotEmpty()) CompactFacts(metaLine(meta), Modifier.weight(1f, fill = false))
                        rating?.let { RatingBadge(it.toCard()) }
                    }
                }
            }
        }
    }
}

/** The titles of a list at [size]. [hooks] says what each one does; the looks are the same everywhere. */
internal fun LazyGridScope.titleCells(
    titles: List<TitleCard>,
    size: ViewSize,
    frames: Boolean,
    hooks: @Composable (TitleCard) -> CellHooks,
) {
    val type = ViewRules.contentType("title", size, frames)
    items(titles, key = { it.id }, contentType = { type }) { card ->
        TitleCell(card, size, frames, hooks(card))
    }
}

/** The titles of the library at [size]. [source] names the list, for the cover that flies to Details. */
internal fun LazyGridScope.titleItems(titles: List<TitleCard>, source: String, size: ViewSize, frames: Boolean = false) {
    titleCells(titles, size, frames) { card -> libraryHooks(card, source) }
}

@Composable
private fun folderCount(entry: FolderEntry): String =
    pluralStringResource(R.plurals.app_videos, entry.videoCount, entry.videoCount)

/**
 * The dimmed line of a folder: how many videos it holds, after the parent path of a folder whose name another
 * folder of the flat list shares, so the two can be told apart (owner request of 2026-11-22).
 */
@Composable
internal fun folderFacts(entry: FolderEntry): String = metaLine(listOfNotNull(entry.hint, folderCount(entry)))

/** A folder or a video of a folder as the compact row of the owner's reference: tile, name, count and a size chip. */
@Composable
private fun FolderCompactRow(entry: FolderEntry, onClick: () -> Unit, modifier: Modifier) {
    val item = entry.selectItem()
    val colors = FrameTheme.colors
    PressableGlass(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().selectableItem(item, SelectionLook.Row),
        shape = FrameShapes.Poster,
        enabled = entry.isDirectory || entry.play != null,
        onLongClick = cardMenuPress(item) { CardTarget.Place(entry) },
        highlight = false,
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = FrameSpacing.S4)
                .then(if (entry.unavailable) Modifier.alpha(UnavailableAlpha) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactTile(item, artKey = null) {
                when {
                    entry.isDirectory -> Icon(Icons.Rounded.Folder, null, Modifier.size(CompactIcon), colors.secondaryContent)
                    entry.coverPath != null ->
                        CoverImage(entry.coverPath, null, Modifier.matchParentSize(), CompactTileShape)
                    else -> Icon(Icons.Rounded.Movie, null, Modifier.size(CompactIcon), colors.secondaryContent)
                }
                entry.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
            }
            Column(Modifier.weight(1f)) {
                CompactName(entry.name)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.isDirectory) CompactFacts(folderFacts(entry), Modifier.weight(1f, fill = false))
                    FactChip(fileSizeLabel(entry.sizeBytes))
                    if (entry.isNew) FactChip(stringResource(R.string.app_chip_new))
                }
            }
        }
    }
}

/** A folder or a video of a folder as a grid card: its 16:9 picture, or an icon, with the name under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderCard(entry: FolderEntry, density: CardDensity, face: CellFace, onClick: () -> Unit, modifier: Modifier) {
    val item = entry.selectItem()
    val colors = FrameTheme.colors
    val small = density == CardDensity.Small
    PressableGlass(
        onClick = onClick,
        modifier = modifier.selectableItem(item, look = null),
        shape = FrameShapes.Poster,
        enabled = entry.isDirectory || entry.play != null,
        onLongClick = cardMenuPress(item) { CardTarget.Place(entry) },
        highlight = false,
    ) {
        Column(if (entry.unavailable) Modifier.alpha(UnavailableAlpha) else Modifier) {
            Box(
                modifier = Modifier
                    .selectionMark(item)
                    .fillMaxWidth()
                    .aspectRatio(FrameSizes.LandscapeRatio)
                    .clip(FrameShapes.Poster)
                    .background(colors.elevated),
                contentAlignment = Alignment.Center,
            ) {
                val icon = if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Movie
                if (entry.coverPath != null) {
                    CoverImage(entry.coverPath, null, Modifier.matchParentSize())
                    // A folder with a picture still has to read as a folder beside the videos around it.
                    if (entry.isDirectory) {
                        GlassSurface(
                            kind = GlassKind.Badge,
                            shape = FolderBadgeShape,
                            modifier = Modifier.align(Alignment.TopStart).padding(FrameSpacing.S4),
                            blur = false,
                        ) {
                            Icon(icon, null, Modifier.padding(FrameSpacing.S4).size(16.dp), colors.content)
                        }
                    }
                } else {
                    Icon(icon, null, Modifier.size(if (small) 24.dp else 32.dp), colors.secondaryContent)
                }
                entry.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
            }
            if (face.name) {
                Spacer(Modifier.height(if (small) FrameSpacing.S4 else FrameSpacing.S8))
                Text(
                    text = bidiIsolate(entry.name),
                    style = when (density) {
                        CardDensity.Small -> FrameType.Caption
                        CardDensity.Medium -> FrameType.Body
                        CardDensity.Large -> FrameType.Title
                    },
                    color = colors.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // A small card says the name alone, so two folders of one name still need the line that tells them apart.
            val hint = entry.hint
            if (!face.facts && face.name && hint != null) {
                Text(
                    text = bidiIsolate(hint),
                    style = FrameType.Caption,
                    color = colors.secondaryContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (face.facts) {
                val facts = listOfNotNull(
                    entry.hint.takeIf { entry.isDirectory },
                    folderCount(entry).takeIf { entry.isDirectory },
                    fileSizeLabel(entry.sizeBytes),
                )
                Text(
                    text = metaLine(facts),
                    style = if (density == CardDensity.Large) FrameType.Label else FrameType.Caption,
                    color = colors.secondaryContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (face.moreFacts && (entry.isNew || entry.hasSubtitle)) {
                FlowRow(
                    modifier = Modifier.padding(top = FrameSpacing.S4),
                    horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
                ) {
                    if (entry.isNew) FactChip(stringResource(R.string.app_chip_new))
                    if (entry.hasSubtitle) FactChip(stringResource(R.string.app_chip_sub))
                }
            }
        }
    }
}

/** A folder or a video of a folder at any of the five sizes. The list is the row Folders has always had. */
@Composable
internal fun FolderCell(entry: FolderEntry, size: ViewSize, onClick: () -> Unit, modifier: Modifier = Modifier) {
    when (size) {
        ViewSize.COMPACT_LIST -> FolderCompactRow(entry, onClick, modifier)
        ViewSize.LIST -> FolderRow(entry, onClick, modifier)
        else -> FolderCard(entry, ViewRules.density(size), ViewRules.face(size, frames = true), onClick, modifier)
    }
}

/**
 * The rows of a folder listing at [size], shared by the Folders chip and the folder screen. A folder's picture is
 * a frame of one of its videos, so its grids are grids of frames.
 */
internal fun LazyGridScope.folderItems(entries: List<FolderEntry>, size: ViewSize, openFolder: (FolderEntry) -> Unit) {
    val type = ViewRules.contentType("folder", size, frames = true)
    items(entries, key = { it.path }, contentType = { type }) { entry ->
        val navigator = LocalNavigator.current
        FolderCell(
            entry = entry,
            size = size,
            onClick = { if (entry.isDirectory) openFolder(entry) else entry.play?.let(navigator::play) },
        )
    }
}
