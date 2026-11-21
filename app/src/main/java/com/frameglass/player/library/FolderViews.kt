package com.frameglass.player.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.FolderView
import com.frameglass.data.api.ViewPlace
import com.frameglass.data.api.frameServices
import com.frameglass.glass.CoverImage
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassSource
import com.frameglass.glass.PressableGlass
import com.frameglass.glass.ProgressLine
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.glassScrolled
import com.frameglass.player.R
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.search.SearchAction
import com.frameglass.player.search.SearchBack
import com.frameglass.player.search.SearchBarSpace
import com.frameglass.player.search.SearchRules
import com.frameglass.player.search.rememberSearch
import com.frameglass.player.search.rememberSearchMode
import com.frameglass.player.select.SelectItem
import com.frameglass.player.select.SelectionLook
import com.frameglass.player.select.SelectionPlace
import com.frameglass.player.select.SelectionScaffold
import com.frameglass.player.select.SelectionState
import com.frameglass.player.select.clearance
import com.frameglass.player.select.selectItem
import com.frameglass.player.select.selectableItem
import com.frameglass.player.select.selectionMark
import com.frameglass.player.ui.CardTarget
import com.frameglass.player.ui.FactChip
import com.frameglass.player.ui.GridMessage
import com.frameglass.player.ui.cardMenuPress
import com.frameglass.player.ui.RefreshBox
import com.frameglass.player.ui.fileSizeLabel
import com.frameglass.player.ui.fullLine
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.statusInset
import com.frameglass.player.views.ViewButton
import com.frameglass.player.views.rememberViewSize
import com.frameglass.player.views.ViewGrid
import com.frameglass.player.views.folderFacts
import com.frameglass.player.views.folderItems

private val ThumbnailWidth = 112.dp
private const val UnavailableAlpha = 0.45f

/** A folder or a video in a folder, with the fact chips of F-L10. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FolderRow(entry: FolderEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val item = entry.selectItem()
    PressableGlass(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().selectableItem(item, SelectionLook.Row),
        shape = FrameShapes.Poster,
        enabled = entry.isDirectory || entry.play != null,
        onLongClick = cardMenuPress(item) { CardTarget.Place(entry) },
        highlight = false,
    ) {
        Row(
            modifier = if (entry.unavailable) Modifier.alpha(UnavailableAlpha) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .selectionMark(item)
                    .width(ThumbnailWidth)
                    .aspectRatio(FrameSizes.LandscapeRatio)
                    .clip(FrameShapes.Poster)
                    .background(FrameColors.Elevated),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.coverPath != null) {
                    CoverImage(entry.coverPath, null, Modifier.matchParentSize())
                } else if (entry.isDirectory) {
                    Icon(Icons.Rounded.Folder, null, Modifier.size(28.dp), tint = FrameColors.SecondaryText)
                }
                entry.progress?.let {
                    ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
            ) {
                Text(
                    text = bidiIsolate(entry.name),
                    style = FrameType.Body.copy(fontWeight = FontWeight.W500),
                    color = FrameColors.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.isDirectory) {
                    Text(
                        text = folderFacts(entry),
                        style = FrameType.Caption,
                        color = FrameColors.SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S4)) {
                    if (entry.isNew) FactChip(stringResource(R.string.app_chip_new))
                    if (entry.hasSubtitle) FactChip(stringResource(R.string.app_chip_sub))
                    FactChip(fileSizeLabel(entry.sizeBytes))
                }
            }
        }
    }
}

/**
 * One folder's content as a page of its own, pushed from the Folders chip of Library or from Media Manager. With
 * [direct] it is a row of the flat list, and shows the videos directly in the folder.
 */
@Composable
internal fun FolderScreen(path: String, direct: Boolean = false) {
    val navigator = LocalNavigator.current
    FolderPage(
        path = path,
        direct = direct,
        onBack = navigator::back,
        open = navigator::openFolder,
        restBottom = FrameSpacing.S24,
        modifier = Modifier.background(FrameTheme.colors.canvas),
    )
}

/**
 * One folder's content, wherever it is shown: pushed over the tabs, or inside the Folders screen, where [onBack]
 * steps back within the tab and [restBottom] clears the tab bar. [open] opens a folder of the list. Its search
 * (owner request of 2026-11-22) looks at folder and video names under this folder only.
 */
@Composable
internal fun FolderPage(
    path: String,
    direct: Boolean,
    onBack: () -> Unit,
    open: (FolderEntry) -> Unit,
    restBottom: Dp,
    modifier: Modifier = Modifier,
) {
    val library = LocalContext.current.frameServices.library
    val view by remember(path, direct) { if (direct) library.folderVideos(path) else library.folder(path) }
        .collectAsStateWithLifecycle(null as FolderView?)
    val grid = rememberLazyGridState()
    val search = rememberSearchMode()
    val searched = rememberSearch({ search.asked }) { library.searchFolders(it, under = path, deep = !direct) }
    val selection = remember { SelectionState<SelectItem> { it.key } }
    val listed = remember(search.open, searched, view) {
        if (search.open) SearchRules.listed(searched.found) else view?.entries?.mapNotNull { it.selectItem() }
    }
    // Picks belong to the list they were made in.
    LaunchedEffect(search.open) { selection.clear() }
    SearchBack(search)
    val size = rememberViewSize(ViewPlace.FOLDERS)
    val top = statusInset() + FrameSizes.TopBarHeight
    val bottom = navigationInset() + selection.clearance(restBottom)

    SelectionScaffold(
        selection = selection,
        place = SelectionPlace.FOLDERS,
        listed = listed,
        modifier = modifier,
        selectingActions = { ViewButton(ViewPlace.FOLDERS) },
        topBar = {
            if (search.open) {
                FolderSearchBar(search)
            } else {
                TopGlassBar(
                    title = view?.name ?: path.substringAfterLast('/'),
                    scrolled = { grid.glassScrolled() },
                    navigation = { BackArrow(onBack) },
                    actions = {
                        SearchAction(search, stringResource(R.string.app_folders_search))
                        ViewButton(ViewPlace.FOLDERS)
                    },
                )
            }
        },
    ) {
        if (search.open) {
            FolderSearchBody(search.words, searched, statusInset() + SearchBarSpace, bottom, size, open)
        } else {
            RefreshBox(indicatorTop = top, Modifier.fillMaxSize()) {
                GlassSource(Modifier.fillMaxSize()) {
                    ViewGrid(size, frames = true, grid, top, bottom) {
                        val entries = view?.entries.orEmpty()
                        if (view != null && entries.isEmpty()) {
                            fullLine("empty") { GridMessage(stringResource(R.string.app_folder_empty)) }
                        }
                        folderItems(entries, size, open)
                    }
                }
            }
        }
    }
}

/** A back arrow that runs [onBack]. It flips in right-to-left. */
@Composable
internal fun BackArrow(onBack: () -> Unit, modifier: Modifier = Modifier) {
    GlassIconButton(
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = stringResource(R.string.app_back),
        onClick = onBack,
        modifier = modifier,
    )
}

/** The back arrow of pushed screens. */
@Composable
internal fun BackButton(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    BackArrow(navigator::back, modifier)
}
