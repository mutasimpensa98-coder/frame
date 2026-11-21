package com.frameglass.player.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.MediaEntry
import com.frameglass.data.api.MediaGroup
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.TitleDetail
import com.frameglass.data.api.frameServices
import com.frameglass.data.api.versions
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.GlassRow
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.GlassSheetState
import com.frameglass.glass.Skeleton
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.Route
import com.frameglass.player.select.LocalSelection
import com.frameglass.player.select.SelectItem
import com.frameglass.player.select.selectionStarter
import kotlinx.coroutines.flow.first

private const val SkeletonRows = 4
private val SkeletonRowHeight = 48.dp

/** What a long press landed on. A row in Folders has no title behind it, so it carries the file or folder instead. */
internal sealed interface CardTarget {
    val name: String

    /**
     * [play] is what a tap on the card plays. A poster opens Details and leaves it null, and its pop-up row then
     * plays what Details would. [episode] is the one episode a Continue watching card stands for.
     */
    data class Title(
        val titleId: Long,
        override val name: String,
        val play: PlayRef? = null,
        val episode: CardEpisode? = null,
    ) : CardTarget

    data class Place(val entry: FolderEntry) : CardTarget {
        override val name: String get() = entry.name
    }
}

/** Opens the one card menu of the app. */
@Stable
internal class CardMenu(private val sheet: GlassSheetState) {
    var target by mutableStateOf<CardTarget?>(null)
        private set

    /** Starts selection mode on the screen of the card with that card picked, or null where nothing can be picked. */
    var onSelect by mutableStateOf<(() -> Unit)?>(null)
        private set

    /** [onSelect] is what the "Select" row runs. */
    fun open(target: CardTarget, onSelect: (() -> Unit)? = null) {
        this.target = target
        this.onSelect = onSelect
        sheet.open()
    }
}

internal val LocalCardMenu = staticCompositionLocalOf<CardMenu?> { null }

/** One sheet for every card, so a grid of thousands holds no menu state. */
@Composable
internal fun CardMenuHost(content: @Composable () -> Unit) {
    val sheet = rememberGlassSheetState()
    val menu = remember(sheet) { CardMenu(sheet) }
    CompositionLocalProvider(LocalCardMenu provides menu, content = content)
    GlassSheet(sheet, title = menu.target?.name) {
        when (val target = menu.target) {
            is CardTarget.Title -> TitleActions(target, sheet, menu.onSelect)
            is CardTarget.Place -> PlaceActions(target.entry, sheet, menu.onSelect)
            null -> Unit
        }
    }
}

/**
 * PosterCard and LandscapeCard take no long press, so this watches from outside them. The inner click stays
 * untouched until the long press fires, then the rest of that touch is consumed so the release opens nothing.
 */
@Composable
internal fun Modifier.longPress(label: String, onLongPress: () -> Unit): Modifier {
    val action by rememberUpdatedState(onLongPress)
    val haptics = LocalHapticFeedback.current
    return semantics {
        onLongClick(label) {
            action()
            true
        }
    }.pointerInput(Unit) {
        val timeout = viewConfiguration.longPressTimeoutMillis
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val ended = withTimeoutOrNull(timeout) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val moved = (change.position - down.position).getDistance() > slop
                    if (!change.pressed || change.isConsumed || moved || event.changes.size > 1) break
                }
                true
            }
            if (ended == null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                action()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}

/**
 * Gives a card the long press menu. [target] is read when the press fires. With [select], and on a screen that can
 * select, the menu offers "Select" for that item. While the screen is selecting there is no menu at all.
 */
@Composable
internal fun Modifier.cardMenu(select: SelectItem? = null, target: () -> CardTarget): Modifier {
    val menu = LocalCardMenu.current ?: return this
    if (LocalSelection.current?.active == true) return this
    val start = selectionStarter(select)
    return longPress(stringResource(R.string.app_more)) { menu.open(target(), start) }
}

/** The same menu for a row that takes its long press itself. Null where there is no menu, and while selecting. */
@Composable
internal fun cardMenuPress(select: SelectItem? = null, target: () -> CardTarget): (() -> Unit)? {
    val menu = LocalCardMenu.current ?: return null
    if (LocalSelection.current?.active == true) return null
    val start = selectionStarter(select)
    return { menu.open(target(), start) }
}

/**
 * The row right under "Play in pop-up" (owner request of 2026-11-21): it closes the menu and starts selection mode
 * on the screen behind it, with this card already picked. Absent where nothing can be picked, as on Home.
 */
@Composable
internal fun SelectRow(onSelect: (() -> Unit)?, onPicked: () -> Unit) {
    if (onSelect == null) return
    GlassRow(
        text = stringResource(R.string.app_media_select),
        icon = Icons.Rounded.CheckCircleOutline,
        onClick = {
            onPicked()
            onSelect()
        },
    )
}

/**
 * The first row of every menu whose card can play: the same video a tap would play, started in a pop-up
 * (picture-in-picture) so the library stays on screen behind it.
 */
@Composable
internal fun PopUpRow(play: PlayRef, onPicked: () -> Unit) {
    val navigator = LocalNavigator.current
    GlassRow(
        text = stringResource(R.string.app_menu_popup),
        icon = Icons.Rounded.PictureInPictureAlt,
        onClick = {
            onPicked()
            navigator.playInPopUp(play)
        },
    )
}

@Composable
private fun TitleActions(target: CardTarget.Title, sheet: GlassSheetState, onSelect: (() -> Unit)?) {
    val model = rememberUiModel()
    val library = LocalContext.current.frameServices.library
    val navigator = LocalNavigator.current
    val loaded by remember(target.titleId) { library.title(target.titleId) }
        .collectAsStateWithLifecycle(null as TitleDetail?)
    val detail = loaded
    if (detail == null) {
        repeat(SkeletonRows) { Skeleton(Modifier.fillMaxWidth().height(SkeletonRowHeight), FrameShapes.Poster) }
        return
    }
    val files = detail.files.filter { it.available }
    val rows = CardMenuRules.titleRows(detail.kind, files.size, detail.play, target.play, target.episode)
    rows.popUp?.let { PopUpRow(it, sheet::close) }
    SelectRow(onSelect, sheet::close)
    GlassRow(
        text = stringResource(if (detail.watchlist) R.string.app_menu_watchlist_remove else R.string.app_menu_watchlist_add),
        icon = if (detail.watchlist) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd,
        onClick = {
            sheet.close()
            model.launch { library.setWatchlist(detail.id, !detail.watchlist) }
        },
    )
    GlassRow(
        text = stringResource(if (detail.favorite) R.string.app_menu_favorite_remove else R.string.app_menu_favorite_add),
        icon = if (detail.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        onClick = {
            sheet.close()
            model.launch { library.setFavorite(detail.id, !detail.favorite) }
        },
    )
    GlassRow(
        text = stringResource(R.string.app_menu_hide),
        supporting = stringResource(R.string.app_menu_hide_hint),
        icon = Icons.Rounded.VisibilityOff,
        onClick = {
            sheet.close()
            model.launch { library.removeFromLibrary(detail.id) }
        },
    )
    files.map { it.folder }.distinct().forEach { folder ->
        ExcludeRow(folder, sheet)
    }
    val delete = rows.delete ?: return
    GlassRow(
        text = stringResource(R.string.app_menu_private),
        icon = Icons.Rounded.Lock,
        onClick = {
            sheet.close()
            model.vault.moveIn(listOf(detail.id), emptyList()) { navigator.push(Route.PrivateFolder) }
        },
    )
    // Both deletes only open the confirmation sheet, which names the files and asks before anything goes.
    rows.episode?.let { episode ->
        GlassRow(
            text = stringResource(R.string.app_menu_delete_episode),
            supporting = episode.label,
            icon = Icons.Rounded.DeleteOutline,
            onClick = {
                sheet.close()
                model.launch {
                    val versions = library.versions(episode.playableKey)
                    model.deletes.ask(CardMenuRules.episodeEntries(versions, detail.name, episode.label))
                }
            },
        )
    }
    val wholeSeries = delete == DeleteWording.WHOLE_SERIES
    GlassRow(
        text = stringResource(if (wholeSeries) R.string.app_menu_delete_series else R.string.app_menu_delete),
        icon = if (wholeSeries) Icons.Rounded.DeleteSweep else Icons.Rounded.DeleteOutline,
        onClick = {
            sheet.close()
            model.deletes.ask(listOf(CardMenuRules.titleEntry(detail.id, detail.name)))
        },
    )
}

@Composable
private fun ExcludeRow(folder: String, sheet: GlassSheetState) {
    val model = rememberUiModel()
    val library = LocalContext.current.frameServices.library
    GlassRow(
        text = stringResource(R.string.app_menu_exclude),
        supporting = shortPath(folder),
        icon = Icons.Rounded.FolderOff,
        onClick = {
            sheet.close()
            model.launch { library.excludeFolder(folder) }
        },
    )
}

/** A row in Folders: what can be done to a place on disk, with no title behind it. */
@Composable
private fun PlaceActions(entry: FolderEntry, sheet: GlassSheetState, onSelect: (() -> Unit)?) {
    val model = rememberUiModel()
    val media = LocalContext.current.frameServices.media
    val navigator = LocalNavigator.current
    val fileRow = entry.play?.fileRowId
    // A folder of the tree stands for every video under it, in folders of its own too, and a row of the flat list
    // for the videos directly in it. Empty when nothing there can go.
    val content by produceState(emptyList<MediaEntry>(), entry.path, entry.direct) {
        value = when {
            fileRow != null -> listOf(CardMenuRules.fileEntry(fileRow, entry.name))
            entry.isDirectory ->
                CardMenuRules.folderContent(entry.path, entry.direct, media.entries(MediaGroup.FOLDERS).first())
            else -> emptyList()
        }
    }
    entry.play?.let { PopUpRow(it, sheet::close) }
    SelectRow(onSelect, sheet::close)
    ExcludeRow(if (entry.isDirectory) entry.path else entry.path.substringBeforeLast('/'), sheet)
    val found = content
    if (found.isNotEmpty()) {
        GlassRow(
            text = stringResource(R.string.app_menu_private),
            icon = Icons.Rounded.Lock,
            onClick = {
                sheet.close()
                model.launch {
                    val rows = if (fileRow != null) {
                        listOf(fileRow)
                    } else {
                        found.flatMap { folder -> media.children(folder).first().mapNotNull { it.fileRowId } }.distinct()
                    }
                    model.vault.moveIn(emptyList(), rows) { navigator.push(Route.PrivateFolder) }
                }
            },
        )
        GlassRow(
            text = stringResource(R.string.app_menu_delete),
            icon = Icons.Rounded.DeleteOutline,
            onClick = {
                sheet.close()
                model.deletes.ask(found)
            },
        )
    }
}
