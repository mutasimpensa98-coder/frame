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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.FolderView
import com.frameglass.data.api.frameServices
import com.frameglass.glass.CoverImage
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
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
import com.frameglass.player.nav.Route
import com.frameglass.player.ui.CardTarget
import com.frameglass.player.ui.FactChip
import com.frameglass.player.ui.GridLineGap
import com.frameglass.player.ui.GridMessage
import com.frameglass.player.ui.LocalCardMenu
import com.frameglass.player.ui.RefreshBox
import com.frameglass.player.ui.fileSizeLabel
import com.frameglass.player.ui.fullLine
import com.frameglass.player.ui.gridPadding
import com.frameglass.player.ui.libraryColumns
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.statusInset

private val ThumbnailWidth = 112.dp
private const val UnavailableAlpha = 0.45f

/** A folder or a video in a folder, with the fact chips of F-L10. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FolderRow(entry: FolderEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val menu = LocalCardMenu.current
    PressableGlass(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = FrameShapes.Poster,
        enabled = entry.isDirectory || entry.play != null,
        onLongClick = menu?.let { { it.open(CardTarget.Place(entry)) } },
        highlight = false,
    ) {
        Row(
            modifier = if (entry.unavailable) Modifier.alpha(UnavailableAlpha) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
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
                        text = pluralStringResource(R.plurals.app_videos, entry.videoCount, entry.videoCount),
                        style = FrameType.Caption,
                        color = FrameColors.SecondaryText,
                        maxLines = 1,
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

/** One folder's content, reached from the Folders chip. */
@Composable
internal fun FolderScreen(path: String) {
    val library = LocalContext.current.frameServices.library
    val navigator = LocalNavigator.current
    val view by remember(path) { library.folder(path) }.collectAsStateWithLifecycle(null as FolderView?)
    val grid = rememberLazyGridState()
    val top = statusInset() + FrameSizes.TopBarHeight
    val bottom = navigationInset() + FrameSpacing.S24

    Box(Modifier.fillMaxSize().background(FrameColors.Canvas)) {
        RefreshBox(indicatorTop = top, Modifier.fillMaxSize()) {
            GlassSource(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = libraryColumns(asList = true),
                    state = grid,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = gridPadding(top, bottom),
                    verticalArrangement = Arrangement.spacedBy(GridLineGap),
                ) {
                    val entries = view?.entries.orEmpty()
                    if (view != null && entries.isEmpty()) {
                        fullLine("empty") { GridMessage(stringResource(R.string.app_folder_empty)) }
                    }
                    folderLines(entries) { navigator.push(Route.Folder(it.path)) }
                }
            }
        }
        TopGlassBar(
            title = view?.name ?: path.substringAfterLast('/'),
            scrolled = { grid.glassScrolled() },
            navigation = { BackButton() },
        )
    }
}

/** The back arrow of pushed screens. It flips in right-to-left. */
@Composable
internal fun BackButton(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    GlassIconButton(
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = stringResource(R.string.app_back),
        onClick = navigator::back,
        modifier = modifier,
    )
}
