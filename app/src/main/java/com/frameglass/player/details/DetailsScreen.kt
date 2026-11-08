package com.frameglass.player.details

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Theaters
import com.frameglass.player.quick.Trailer
import com.frameglass.player.quick.openTrailer
import com.frameglass.player.quick.upcomingLine
import com.frameglass.player.ui.rememberUiModel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.frameglass.data.api.CastMember
import com.frameglass.data.api.EpisodeRow
import com.frameglass.data.api.FileFacts
import com.frameglass.data.api.FileVersion
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibrarySort
import com.frameglass.data.api.LibraryRepository
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.TitleDetail
import com.frameglass.data.api.WatchTarget
import com.frameglass.data.api.frameServices
import com.frameglass.data.api.versions
import com.frameglass.glass.CastAvatar
import com.frameglass.glass.CastChip
import com.frameglass.glass.CoverImage
import com.frameglass.glass.FilterChips
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassMenu
import com.frameglass.glass.GlassRow
import com.frameglass.glass.GlassSheetState
import com.frameglass.glass.GlassSource
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.PosterWash
import com.frameglass.glass.RatingBadge
import com.frameglass.glass.SectionHeader
import com.frameglass.glass.StagePoster
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.WhitePill
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.coverGlow
import com.frameglass.glass.glassSheetOrigin
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.glass.rememberPortrait
import com.frameglass.player.R
import com.frameglass.player.library.BackButton
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.Route
import com.frameglass.player.nav.sharedArt
import com.frameglass.player.ui.ArtUnderStatusBar
import com.frameglass.player.ui.TitleMark
import com.frameglass.player.ui.TitlePoster
import com.frameglass.player.ui.clockLabel
import com.frameglass.player.ui.dateLabel
import com.frameglass.player.ui.durationLabel
import com.frameglass.player.ui.fileSizeLabel
import com.frameglass.player.ui.localNumber
import com.frameglass.player.ui.metaLine
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.statusInset
import com.frameglass.player.ui.toCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

// Measured on reference image 03: the art and title block end near 47% of the screen.
private const val ArtShare = 0.47f
private val LogoHeight = 104.dp
private const val LogoWidthShare = 0.78f
private const val PlotLines = 3
private val SideMargin = FrameSpacing.S16
private val BlockGap = FrameSpacing.S16
private val SectionGap = FrameSpacing.S24
private val MetaText = FrameType.Body.copy(fontWeight = FontWeight.W600)
private const val PlayPillShare = 1.25f

// A poster standing in for the art: under half the width, and short enough that Play stays above the fold.
private const val PosterWidthShare = 0.46f
private const val PosterHeightShare = 0.34f
private const val FactLabelShare = 0.36f
private const val FactValueShare = 0.64f
private const val FolderLines = 3

// Above this, the episode list already fills the page, so the file cards wait behind the header.
private const val OpenFileCount = 3

// The dark top belongs to the art under the status bar. The bottom fades into the canvas of the theme, and on
// light it starts higher: the title here is dark, so the art must be washed out before the text begins.
@Composable
private fun rememberArtFade(): Brush {
    val colors = FrameTheme.colors
    return remember(colors) {
        val canvas = colors.canvas
        if (colors.isLight) {
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.24f),
                0.2f to Color.Transparent,
                0.36f to Color.Transparent,
                0.62f to canvas.copy(alpha = 0.72f),
                0.8f to canvas.copy(alpha = 0.94f),
                1f to canvas,
            )
        } else {
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.24f),
                0.2f to Color.Transparent,
                0.5f to Color.Transparent,
                0.8f to canvas.copy(alpha = 0.76f),
                1f to canvas,
            )
        }
    }
}

// The poster sits on its wash with the title below it, so the wash is gone into the canvas before the title.
@Composable
private fun rememberStageFade(): Brush {
    val colors = FrameTheme.colors
    return remember(colors) {
        val canvas = colors.canvas
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.24f),
            0.2f to Color.Transparent,
            0.55f to Color.Transparent,
            0.8f to canvas.copy(alpha = if (colors.isLight) 0.94f else 0.9f),
            1f to canvas,
        )
    }
}

private val CreditJobs = mapOf(
    "Director" to R.string.app_job_director,
    "Writer" to R.string.app_job_writer,
    "Screenplay" to R.string.app_job_screenplay,
    "Story" to R.string.app_job_story,
    "Creator" to R.string.app_job_creator,
)

/**
 * Pixels scrolled into the last [collapsePx] of the art above the bar, so the bar is whole glass by the time the
 * art has left it. Anything below the art, such as a long episode list, then scrolls under glass.
 */
internal fun scrolledPastArt(firstIndex: Int, firstOffset: Int, artPx: Float, barPx: Float, collapsePx: Float): Float =
    if (firstIndex > 0) Float.MAX_VALUE else (firstOffset - (artPx - barPx - collapsePx)).coerceAtLeast(0f)

/** A cast with no photo at all, as OMDb gives, reads better as names than as a row of empty circles. */
internal fun castAsNames(cast: List<CastMember>): Boolean = cast.isNotEmpty() && cast.none { it.photoPath != null }

/** F-U7 and F-U9: the movie and series page of images 03 and 10. */
@Composable
internal fun DetailsScreen(route: Route.Details) {
    val library = LocalContext.current.frameServices.library
    val navigator = LocalNavigator.current
    val detail by remember(route.titleId) { library.title(route.titleId) }
        .collectAsStateWithLifecycle(null as TitleDetail?)
    val list = rememberLazyListState()
    val fixMatch = rememberGlassSheetState()
    val remove = rememberGlassSheetState()
    val versions = rememberGlassSheetState()
    val episodeSheet = rememberGlassSheetState()
    var pickedEpisode by remember { mutableStateOf<EpisodeRow?>(null) }
    val current = detail

    val seasons = current?.seasons.orEmpty()
    val resume = current?.play
    var picked by rememberSaveable(route.titleId) { mutableIntStateOf(-1) }
    var fileOpen by rememberSaveable(route.titleId) { mutableStateOf<Boolean?>(null) }
    // The season holding the next episode opens first.
    val opening by produceState<Int?>(null, route.titleId, seasons, resume) {
        value = seasons.firstOrNull { library.episodes(route.titleId, it).first().any { row -> row.play == resume } }
            ?: seasons.firstOrNull()
    }
    val season = seasons.getOrNull(picked) ?: opening
    val rows by produceState(emptyList<EpisodeRow>(), route.titleId, season) {
        if (season != null) library.episodes(route.titleId, season).collect { value = it }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(FrameColors.Canvas)) {
        val artHeight = maxHeight * ArtShare
        val posterHeight = min(maxWidth * PosterWidthShare / FrameSizes.PosterRatio, maxHeight * PosterHeightShare)
        val density = LocalDensity.current
        val artPx = with(density) { artHeight.toPx() }
        val barPx = with(density) { (statusInset() + FrameSizes.TopBarHeight).toPx() }
        val collapsePx = with(density) { FrameSizes.TopBarCollapse.toPx() }
        // The poster header grows with its content, so the bar follows the header as measured.
        var headerPx by remember { mutableFloatStateOf(0f) }
        val scrolled = remember(list, artPx, barPx, collapsePx) {
            {
                val header = headerPx.takeIf { it > 0f } ?: artPx
                scrolledPastArt(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, header, barPx, collapsePx)
            }
        }
        // The icons turn with the glass: light ones over the art, the theme's once the bar has come in.
        val overArt by remember(scrolled, collapsePx) { derivedStateOf { scrolled() < collapsePx / 2f } }
        ArtUnderStatusBar(overArt)
        GlassSource(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = navigationInset() + FrameSpacing.S32),
            ) {
                item(key = "art") {
                    ArtHeader(
                        route = route,
                        detail = current,
                        height = artHeight,
                        posterHeight = posterHeight,
                        modifier = Modifier.onSizeChanged { headerPx = it.height.toFloat() },
                    )
                }
                if (current != null) {
                    item(key = "actions") {
                        ActionRow(current, library, fixMatch, remove, versions)
                    }
                    item(key = "facts") { Facts(current) }
                    if (current.matchState == MatchState.NEEDS_CHOICE) {
                        item(key = "needsChoice") {
                            OutlinePill(
                                text = stringResource(R.string.app_pick_match),
                                onClick = fixMatch::open,
                                modifier = Modifier
                                    .padding(horizontal = SideMargin)
                                    .padding(top = BlockGap)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                    if (current.cast.isNotEmpty()) {
                        item(key = "cast") {
                            if (castAsNames(current.cast)) CastNames(current) else CastRow(current)
                        }
                    }
                    if (current.files.isNotEmpty()) {
                        val open = fileOpen ?: (current.littleMetadata() && current.files.size <= OpenFileCount)
                        item(key = "fileHeader") {
                            FileHeader(current.files, open, onToggle = { fileOpen = !open })
                        }
                        if (open) {
                            items(current.files, key = { "file${it.fileRowId}" }, contentType = { "file" }) { file ->
                                FileCard(file, showName = current.files.size > 1)
                            }
                        }
                    }
                    if (season != null) {
                        item(key = "seasons") {
                            SeasonHeader(current, season, rows, onPick = { picked = it })
                        }
                        // Two unavailable files can share a number and title, so the position keeps keys unique.
                        itemsIndexed(
                            items = rows,
                            key = { index, row -> "$index:${row.play?.playableKey}:${row.number}" },
                            contentType = { _, _ -> "episode" },
                        ) { _, row ->
                            EpisodeListRow(
                                row = row,
                                poster = current.coverPath,
                                onLongPress = {
                                    pickedEpisode = row
                                    episodeSheet.open()
                                },
                            )
                        }
                    }
                    if (current.collection.isNotEmpty()) {
                        item(key = "collection") { CollectionRow(current) }
                    }
                }
            }
        }
        TopGlassBar(
            title = current?.name.orEmpty(),
            scrolled = scrolled,
            navigation = { BackButton() },
            titleOnScroll = true,
        )
    }
    if (current != null) {
        FixMatchSheet(fixMatch, current.id, current.name)
        RemoveSheet(remove, current.id, onRemoved = navigator::back)
        current.play?.playableKey?.let { VersionSheet(versions, it) }
        EpisodeSheet(episodeSheet, pickedEpisode)
    }
}

@Composable
private fun ArtHeader(
    route: Route.Details,
    detail: TitleDetail?,
    height: Dp,
    posterHeight: Dp,
    modifier: Modifier = Modifier,
) {
    // The route's path keeps the first frame steady. With no backdrop, the live cover shows a change at once.
    val cover = if (detail?.backdropPath == null) detail?.coverPath ?: route.art.path else route.art.path ?: detail.coverPath
    val backdrop = detail?.backdropPath?.takeIf { it != cover }
    val portrait = rememberPortrait(cover)
    if (backdrop == null && cover != null && portrait == true) {
        PosterHeader(route, detail, cover, height, posterHeight, modifier)
        return
    }
    Box(modifier.fillMaxWidth().height(height)) {
        Box(Modifier.matchParentSize().sharedArt(route.art.key)) {
            CoverImage(cover, null, Modifier.matchParentSize(), RectangleShape)
            // The cover flies in first, then the textless backdrop fades over it.
            if (backdrop != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(backdrop, context) {
                        ImageRequest.Builder(context).data(File(backdrop)).crossfade(true).build()
                    },
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(Modifier.matchParentSize().background(rememberArtFade()))
            Box(Modifier.matchParentSize().coverGlow(detail?.dominantColor))
        }
        if (detail != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = FrameSpacing.S24),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
            ) {
                TitleMark(
                    logoPath = detail.logoPath,
                    name = detail.name,
                    logoModifier = Modifier.fillMaxWidth(LogoWidthShare).height(LogoHeight),
                )
                GenreLine(detail)
            }
        }
    }
}

/**
 * A portrait cover with no backdrop, as for a title matched through OMDb before it has a frame: the poster stands
 * whole on a wash of its own colours with the title under it, so nothing is cropped into a wide strip.
 */
@Composable
private fun PosterHeader(
    route: Route.Details,
    detail: TitleDetail?,
    cover: String,
    minHeight: Dp,
    posterHeight: Dp,
    modifier: Modifier,
) {
    Box(modifier.fillMaxWidth().heightIn(min = minHeight)) {
        PosterWash(cover, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(rememberStageFade()))
        Box(Modifier.matchParentSize().coverGlow(detail?.dominantColor))
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = FrameSpacing.S24,
                    end = FrameSpacing.S24,
                    top = statusInset() + FrameSizes.TopBarHeight,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
        ) {
            Box(
                Modifier
                    .padding(bottom = FrameSpacing.S8)
                    .height(posterHeight)
                    .aspectRatio(FrameSizes.PosterRatio),
            ) {
                Box(Modifier.matchParentSize().sharedArt(route.art.key)) {
                    StagePoster(cover, Modifier.matchParentSize())
                }
            }
            if (detail != null) {
                // The poster carries its own title art, so the typeset title steps down a size under it.
                TitleMark(
                    logoPath = detail.logoPath,
                    name = detail.name,
                    logoModifier = Modifier.fillMaxWidth(LogoWidthShare).height(LogoHeight),
                    style = FrameType.TitleLarge,
                )
                GenreLine(detail)
            }
        }
    }
}

@Composable
private fun GenreLine(detail: TitleDetail) {
    if (detail.genres.isEmpty()) return
    Text(
        text = metaLine(detail.genres.take(3)),
        style = FrameType.Body,
        color = FrameColors.SecondaryText,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ActionRow(
    detail: TitleDetail,
    library: LibraryRepository,
    fixMatch: GlassSheetState,
    remove: GlassSheetState,
    versions: GlassSheetState,
) {
    val navigator = LocalNavigator.current
    val play = detail.play
    val label = when {
        play == null -> stringResource(R.string.app_unavailable)
        detail.resumable -> stringResource(R.string.app_resume)
        detail.playLabel != null -> stringResource(R.string.app_play_episode, bidiIsolate(detail.playLabel!!))
        else -> stringResource(R.string.app_play)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SideMargin)
            .padding(top = BlockGap),
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WhitePill(
            text = label,
            onClick = { play?.let(navigator::play) },
            modifier = Modifier.weight(PlayPillShare),
            icon = Icons.Rounded.PlayArrow,
            enabled = play != null,
        )
        SaveButton(detail, library, Modifier.weight(1f))
        Box {
            var open by remember { mutableStateOf(false) }
            GlassIconButton(
                icon = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.app_more),
                onClick = { open = true },
                modifier = Modifier
                    .glassSheetOrigin(fixMatch)
                    .glassSheetOrigin(remove)
                    .glassSheetOrigin(versions),
                size = FrameSpacing.S48,
            )
            OverflowMenu(detail, library, open, { open = false }, fixMatch, remove, versions)
        }
    }
}

/** Image 03: the outlined pill beside Play. It answers at once, then the library catches up. */
@Composable
private fun SaveButton(detail: TitleDetail, library: LibraryRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var saved by remember(detail.id, detail.favorite) { mutableStateOf(detail.favorite) }
    OutlinePill(
        text = stringResource(if (saved) R.string.app_saved else R.string.app_save),
        onClick = {
            saved = !saved
            val wanted = saved
            scope.launch { library.setFavorite(detail.id, wanted) }
        },
        modifier = modifier,
        icon = if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
    )
}

@Composable
private fun OverflowMenu(
    detail: TitleDetail,
    library: LibraryRepository,
    expanded: Boolean,
    onDismiss: () -> Unit,
    fixMatch: GlassSheetState,
    remove: GlassSheetState,
    versions: GlassSheetState,
) {
    val scope = rememberCoroutineScope()
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch { library.setManualCover(detail.id, uri) }
    }
    val watched by remember(detail.id) {
        library.titles(LibraryFilter.ALL, LibrarySort.RECENT).map { titles ->
            titles.firstOrNull { it.id == detail.id }?.watched == true
        }
    }.collectAsStateWithLifecycle(false)
    val choices by produceState(emptyList<FileVersion>(), detail.play?.playableKey) {
        value = detail.play?.playableKey?.let { library.versions(it) }.orEmpty()
    }

    GlassMenu(expanded = expanded, onDismiss = onDismiss) {
        GlassRow(
            text = stringResource(if (watched) R.string.app_mark_unwatched else R.string.app_mark_watched),
            icon = if (watched) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle,
            onClick = {
                onDismiss()
                scope.launch { library.setWatched(WatchTarget.Title(detail.id), !watched) }
            },
        )
        if (choices.size > 1) {
            GlassRow(
                text = stringResource(R.string.app_choose_version),
                icon = Icons.Rounded.Layers,
                onClick = {
                    onDismiss()
                    versions.open()
                },
            )
        }
        GlassRow(
            text = stringResource(R.string.app_fix_match),
            icon = Icons.AutoMirrored.Rounded.ManageSearch,
            onClick = {
                onDismiss()
                fixMatch.open()
            },
        )
        GlassRow(
            text = stringResource(R.string.app_change_cover),
            icon = Icons.Rounded.Image,
            onClick = {
                onDismiss()
                pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
        GlassRow(
            text = stringResource(R.string.app_remove_from_library),
            icon = Icons.Rounded.DeleteOutline,
            onClick = {
                onDismiss()
                remove.open()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(detail: TitleDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SideMargin)
            .padding(top = BlockGap),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        val facts = listOfNotNull(
            detail.year?.let { localNumber(it) },
            detail.runtimeMinutes?.takeIf { it > 0 }?.let { durationLabel(it) },
        )
        val ratings = listOfNotNull(detail.rating, detail.imdbRating)
        if (facts.isNotEmpty() || ratings.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                facts.forEach { fact ->
                    Text(bidiIsolate(fact), style = MetaText, color = FrameColors.White, maxLines = 1)
                }
                ratings.forEach { RatingBadge(it.toCard()) }
            }
        }
        detail.upcoming?.let { upcomingLine(it) }?.let { line ->
            Text(text = line, style = FrameType.Body, color = FrameColors.White)
        }
        TrailerButton(detail)
        val separator = stringResource(R.string.app_list_separator)
        detail.credits.groupBy({ it.first }, { it.second }).forEach { (job, names) ->
            Row(horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8)) {
                val label = CreditJobs[job]?.let { stringResource(it) } ?: job
                Text(
                    text = stringResource(R.string.app_credit_label, label),
                    style = FrameType.Body,
                    color = FrameColors.SecondaryText,
                    maxLines = 1,
                )
                Text(
                    text = bidiIsolate(names.joinToString(separator)),
                    style = FrameType.Body,
                    color = FrameColors.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        detail.overview?.takeIf { it.isNotBlank() }?.let { Plot(it) }
    }
}

/** Opens the TMDB trailer outside the app. Hidden with no trailer, and while Online features is off. */
@Composable
private fun TrailerButton(detail: TitleDetail) {
    val settings by rememberUiModel().settings.collectAsStateWithLifecycle()
    val url = Trailer.url(detail.trailerKey) ?: return
    if (settings?.onlineFeatures != true) return
    val context = LocalContext.current
    OutlinePill(
        text = stringResource(R.string.app_trailer),
        onClick = { context.openTrailer(url) },
        icon = Icons.Rounded.Theaters,
    )
}

@Composable
private fun Plot(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var clipped by remember { mutableStateOf(false) }
    Text(
        text = bidiIsolate(text),
        style = FrameType.Body,
        color = FrameColors.White,
        maxLines = if (expanded) Int.MAX_VALUE else PlotLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) clipped = it.hasVisualOverflow },
    )
    if (clipped || expanded) {
        val turn by animateFloatAsState(
            if (expanded) 180f else 0f,
            FrameMotion.effects(),
            label = "plotChevron",
        )
        Row(
            modifier = Modifier.clickable(
                interactionSource = null,
                indication = null,
                onClick = { expanded = !expanded },
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
        ) {
            Text(
                text = stringResource(if (expanded) R.string.app_show_less else R.string.app_show_more),
                style = FrameType.Body,
                color = FrameColors.SecondaryText,
            )
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                null,
                Modifier.size(18.dp).rotate(turn),
                tint = FrameColors.SecondaryText,
            )
        }
    }
}

/** Nothing but the file to show: an unmatched video, or a personal clip. */
private fun TitleDetail.littleMetadata(): Boolean = overview.isNullOrBlank() && cast.isEmpty()

@Composable
private fun FileHeader(files: List<FileFacts>, open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(
        if (open) 180f else 0f,
        FrameMotion.effects(),
        label = "fileChevron",
    )
    Row(
        modifier = Modifier
            .padding(top = SectionGap)
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = null, onClick = onToggle)
            .padding(horizontal = SideMargin, vertical = FrameSpacing.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(if (files.size == 1) R.string.app_file_section else R.string.app_files_section),
                style = FrameType.Title,
                color = FrameColors.White,
            )
            if (files.size > 1) {
                Text(
                    text = metaLine(
                        listOf(
                            pluralStringResource(R.plurals.app_videos, files.size, files.size),
                            fileSizeLabel(files.sumOf { it.sizeBytes }),
                        ),
                    ),
                    style = FrameType.Caption,
                    color = FrameColors.SecondaryText,
                    maxLines = 1,
                )
            }
        }
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            null,
            Modifier.size(24.dp).rotate(turn),
            tint = FrameColors.SecondaryText,
        )
    }
}

/** What the file itself says, in tidy rows. A fact the probe has not filled yet leaves its row out. */
@Composable
private fun FileCard(file: FileFacts, showName: Boolean) {
    Column(
        modifier = Modifier
            .padding(horizontal = SideMargin, vertical = FrameSpacing.S4)
            .fillMaxWidth()
            .clip(FrameShapes.Card)
            .background(FrameColors.Card)
            .padding(horizontal = FrameSpacing.S16, vertical = FrameSpacing.S12),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        if (showName) {
            Text(
                text = bidiIsolate(file.name),
                style = FrameType.Label,
                color = FrameColors.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!file.available) {
            Text(
                text = stringResource(R.string.app_unavailable),
                style = FrameType.Label,
                color = FrameColors.Warning,
            )
        }
        FactRow(R.string.app_fact_size, fileSizeLabel(file.sizeBytes))
        file.durationMs.takeIf { it > 0 }?.let { FactRow(R.string.app_fact_duration, clockLabel(it)) }
        if (file.width > 0 && file.height > 0) {
            FactRow(
                R.string.app_fact_resolution,
                stringResource(R.string.app_fact_resolution_value, localNumber(file.width), localNumber(file.height)),
            )
        }
        file.videoCodec?.let { FactRow(R.string.app_fact_video, it) }
        file.audioSummary?.let { FactRow(R.string.app_fact_audio, it) }
        if (file.videoCodec != null) {
            FactRow(R.string.app_fact_hdr, file.hdrType ?: stringResource(R.string.app_fact_sdr))
        }
        if (file.folder.isNotEmpty()) FactRow(R.string.app_fact_folder, file.folder, FolderLines)
        file.modifiedAtMs.takeIf { it > 0 }?.let { FactRow(R.string.app_fact_modified, dateLabel(it)) }
    }
}

@Composable
private fun FactRow(label: Int, value: String, lines: Int = 1) {
    Row(horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12)) {
        Text(
            text = stringResource(label),
            style = FrameType.Label,
            color = FrameColors.SecondaryText,
            modifier = Modifier.weight(FactLabelShare),
        )
        Text(
            text = bidiIsolate(value),
            style = FrameType.Label,
            color = FrameColors.White,
            textAlign = TextAlign.End,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(FactValueShare),
        )
    }
}

@Composable
private fun CastRow(detail: TitleDetail) {
    val navigator = LocalNavigator.current
    Column(
        modifier = Modifier.padding(top = SectionGap),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
    ) {
        SectionHeader(stringResource(R.string.app_cast))
        LazyRow(
            contentPadding = PaddingValues(horizontal = SideMargin),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
        ) {
            items(detail.cast, key = { it.personId }) { person ->
                CastAvatar(
                    name = person.name,
                    photoPath = person.photoPath,
                    onClick = { navigator.push(Route.Person(person.personId)) },
                    character = person.character?.takeIf { it.isNotBlank() },
                )
            }
        }
    }
}

/** The cast as tappable names, for a cast with no photos. Each still opens that person's titles (F-U8). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CastNames(detail: TitleDetail) {
    val navigator = LocalNavigator.current
    Column(
        modifier = Modifier.padding(top = SectionGap),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        SectionHeader(stringResource(R.string.app_cast))
        FlowRow(
            modifier = Modifier.padding(horizontal = SideMargin),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
        ) {
            detail.cast.forEach { person ->
                CastChip(
                    name = person.name,
                    onClick = { navigator.push(Route.Person(person.personId)) },
                    character = person.character?.takeIf { it.isNotBlank() },
                )
            }
        }
    }
}

/** Season chips and the mark that covers the whole season. */
@Composable
private fun SeasonHeader(detail: TitleDetail, season: Int, rows: List<EpisodeRow>, onPick: (Int) -> Unit) {
    val library = LocalContext.current.frameServices.library
    val scope = rememberCoroutineScope()
    val present = rows.filter { !it.missing }
    val allWatched = present.isNotEmpty() && present.all { it.watched }
    Column(
        modifier = Modifier.padding(top = SectionGap, bottom = FrameSpacing.S8),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SideMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_seasons),
                    style = FrameType.Title,
                    color = FrameColors.White,
                )
                if (present.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(R.plurals.app_episodes, present.size, present.size),
                        style = FrameType.Caption,
                        color = FrameColors.SecondaryText,
                    )
                }
            }
            GlassIconButton(
                icon = if (allWatched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = stringResource(
                    if (allWatched) R.string.app_mark_season_unwatched else R.string.app_mark_season_watched,
                ),
                onClick = { scope.launch { library.setWatched(WatchTarget.Season(detail.id, season), !allWatched) } },
            )
        }
        FilterChips(
            labels = detail.seasons.map { number ->
                if (number == 0) {
                    stringResource(R.string.app_specials)
                } else {
                    stringResource(R.string.app_season_number, localNumber(number))
                }
            },
            selectedIndex = detail.seasons.indexOf(season),
            onSelect = onPick,
        )
    }
}

@Composable
private fun CollectionRow(detail: TitleDetail) {
    Column(
        modifier = Modifier.padding(top = SectionGap),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
    ) {
        SectionHeader(detail.collectionName ?: stringResource(R.string.app_collection))
        LazyRow(
            contentPadding = PaddingValues(horizontal = SideMargin),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
        ) {
            items(detail.collection, key = { it.id }) { card ->
                TitlePoster(card, "collection:${detail.id}", Modifier.width(FrameSizes.PosterWidth))
            }
        }
    }
}
