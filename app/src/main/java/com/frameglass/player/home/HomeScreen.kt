package com.frameglass.player.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.ContinueItem
import com.frameglass.data.api.HomeFeed
import com.frameglass.data.api.HomeRow
import com.frameglass.data.api.HomeRowKind
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.ScanState
import com.frameglass.glass.ActivityLine
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassKind
import com.frameglass.glass.GlassSource
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassSurface
import com.frameglass.glass.LandscapeCard
import com.frameglass.glass.SectionHeader
import com.frameglass.glass.Skeleton
import com.frameglass.glass.WhitePill
import com.frameglass.glass.glassSheetOrigin
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.folders.AddFolderButton
import com.frameglass.player.folders.AddFolderSheet
import com.frameglass.player.folders.openAllFilesAccess
import com.frameglass.player.folders.rememberAllFilesAccess
import com.frameglass.player.nav.BarExtrasSlot
import com.frameglass.player.nav.LocalBarExtras
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.Route
import com.frameglass.player.quick.upcomingHint
import com.frameglass.player.ui.ArtUnderStatusBar
import com.frameglass.player.ui.CenteredMessage
import com.frameglass.player.ui.RefreshBox
import com.frameglass.player.ui.TitleFrame
import com.frameglass.player.ui.TitlePoster
import com.frameglass.player.ui.cardMenu
import com.frameglass.player.ui.cardTarget
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.rememberUiModel
import com.frameglass.player.ui.statusInset
import kotlinx.coroutines.delay

// Measured on reference image 01: the hero fills 53.5% of the screen and a Continue card 62.5% of its width.
private const val HeroShare = 0.535f
private const val ContinueCardShare = 0.625f
private val RowGap = FrameSpacing.S12
private val HeaderGap = FrameSpacing.S12
private val HeaderLineWidth = 56.dp
private val HeaderLineHeight = 3.dp
private val ScanLineWidth = 160.dp
private val ScanChipMaxWidth = 240.dp

// A glass button and the margins on both of its sides, at the top end.
private val EndButtonSpace = FrameSpacing.S12 + FrameSpacing.S48 + FrameSpacing.S12

private val ExtraButtonGap = FrameSpacing.S8

// A routine scan runs on every app open and ends within DoD-10's 3 s, so a full Home stays silent for it.
private const val QuietScanMillis = 4_000L

@Composable
internal fun HomeScreen(onOpenLibrary: () -> Unit) {
    val model = rememberUiModel()
    val feed by model.home.collectAsStateWithLifecycle()
    val scan by model.scan.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val current = feed
    var longScan by remember { mutableStateOf(false) }
    LaunchedEffect(scan.running) {
        longScan = false
        if (scan.running) {
            delay(QuietScanMillis)
            longScan = true
        }
    }

    val view = homeView(current)
    val endButtons = if (LocalBarExtras.current != null) EndButtonSpace + FrameSpacing.S48 + ExtraButtonGap else EndButtonSpace

    BoxWithConstraints(Modifier.fillMaxSize()) {
        when (view) {
            HomeView.Loading -> HomeSkeleton(maxHeight * HeroShare)
            HomeView.NoVideos -> HomeEmpty(scan, wholePhone = settings?.scanWholePhone != false)
            HomeView.NoCinemaTitles -> CinemaEmpty()
            HomeView.LibraryOnly -> HomeInLibrary(scan, onOpenLibrary)
            HomeView.Feed -> current?.let { HomeList(it, maxWidth * ContinueCardShare, maxHeight * HeroShare) }
        }
        // The private folder at the top start and "Add a place" at the top end keep opposite corners in both
        // directions. The count of a long scan sits beside the lock, where it used to sit alone.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                // The end side stays clear of "Add a place", so a long count is cut short before it reaches it.
                .padding(start = FrameSpacing.S12, top = FrameSpacing.S12, bottom = FrameSpacing.S12, end = endButtons),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrivateFolderButton()
            AnimatedVisibility(
                // The empty and Library-only views show the search in their own message.
                visible = (view == HomeView.Feed || view == HomeView.NoCinemaTitles) && scan.running && longScan,
                modifier = Modifier.weight(1f, fill = false),
                enter = fadeIn(FrameMotion.effects()),
                exit = fadeOut(FrameMotion.effects()),
            ) {
                ScanChip(scan.found)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(FrameSpacing.S12),
            horizontalArrangement = Arrangement.spacedBy(ExtraButtonGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The gear, only while Settings has no tab and Home is the first one (owner request of 2026-11-22).
            BarExtrasSlot()
            AddFolderButton()
        }
    }
}

/**
 * The shortcut to the private folder (owner request of 2026-11-21): the same glass button as "Add a place", at the
 * other top corner. It only opens the page, which asks for the fingerprint or the password, or starts the setup
 * when no password exists. It carries no badge and no count: a locked folder says nothing about what it holds.
 */
@Composable
private fun PrivateFolderButton(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    GlassIconButton(
        icon = Icons.Rounded.Lock,
        contentDescription = stringResource(R.string.app_settings_private),
        onClick = { navigator.push(Route.PrivateFolder) },
        modifier = modifier,
    )
}

@Composable
private fun scanLabel(found: Int): String = if (found > 0) {
    pluralStringResource(R.plurals.app_scan_found, found, found)
} else {
    stringResource(R.string.app_home_scanning)
}

/** The count of videos found so far, while the library fills in behind it. */
@Composable
private fun ScanChip(found: Int) {
    GlassSurface(GlassKind.Badge, FrameShapes.Poster, Modifier.widthIn(max = ScanChipMaxWidth), blur = false) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(horizontal = FrameSpacing.S12, vertical = FrameSpacing.S8),
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
        ) {
            Text(
                text = scanLabel(found),
                style = FrameType.Caption,
                color = FrameColors.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ActivityLine(Modifier.fillMaxWidth())
        }
    }
}

/** F-U15: one message and one white pill, or the calm first search with its count and a thin line. */
@Composable
private fun HomeEmpty(scan: ScanState, wholePhone: Boolean) {
    val model = rememberUiModel()
    val context = LocalContext.current
    val access = rememberAllFilesAccess()
    val sheet = rememberGlassSheetState()
    // A source of its own, so a sheet opened from here has something to blur.
    GlassSource(Modifier.fillMaxSize()) {
        when {
            !access -> CenteredMessage(
                title = stringResource(R.string.app_home_no_access),
                action = {
                    WhitePill(
                        text = stringResource(R.string.app_files_allow),
                        onClick = { context.openAllFilesAccess() },
                    )
                },
            )
            scan.running -> CenteredMessage(
                title = scanLabel(scan.found),
                action = { ActivityLine(Modifier.width(ScanLineWidth)) },
            )
            wholePhone -> CenteredMessage(
                title = stringResource(R.string.app_home_empty),
                action = {
                    WhitePill(
                        text = stringResource(R.string.app_home_search_again),
                        onClick = model::scanNow,
                    )
                },
            )
            else -> CenteredMessage(
                title = stringResource(R.string.app_home_empty_places),
                action = {
                    WhitePill(
                        text = stringResource(R.string.app_add_place),
                        onClick = sheet::open,
                        modifier = Modifier.glassSheetOrigin(sheet),
                    )
                },
            )
        }
    }
    AddFolderSheet(sheet)
}

/** Cinema mode with no film, series or anime: said plainly, with the way back to every video. */
@Composable
private fun CinemaEmpty() {
    val model = rememberUiModel()
    GlassSource(Modifier.fillMaxSize()) {
        CenteredMessage(
            title = stringResource(R.string.app_home_cinema_empty),
            body = stringResource(R.string.app_home_cinema_empty_body),
            action = {
                WhitePill(
                    text = stringResource(R.string.app_home_cinema_off),
                    onClick = { model.updateSettings { it.copy(cinemaMode = false) } },
                )
            },
        )
    }
}

/** Videos that Home has nothing to show for yet, such as a few clips whose frames are still being made. */
@Composable
private fun HomeInLibrary(scan: ScanState, onOpenLibrary: () -> Unit) {
    val model = rememberUiModel()
    GlassSource(Modifier.fillMaxSize()) {
        if (scan.running) {
            CenteredMessage(
                title = scanLabel(scan.found),
                action = { ActivityLine(Modifier.width(ScanLineWidth)) },
            )
        } else {
            CenteredMessage(
                title = stringResource(R.string.app_home_in_library),
                action = {
                    WhitePill(
                        text = stringResource(R.string.app_home_open_library),
                        onClick = {
                            model.changeQuery { it.copy(filter = LibraryFilter.ALL) }
                            onOpenLibrary()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun HomeSkeleton(heroHeight: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(FrameSpacing.S24)) {
        Skeleton(Modifier.fillMaxWidth().height(heroHeight), RectangleShape)
        Row(
            modifier = Modifier.padding(horizontal = FrameSpacing.S16),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
        ) {
            repeat(2) {
                Skeleton(Modifier.weight(1f).aspectRatio(FrameSizes.LandscapeRatio), FrameShapes.Poster)
            }
        }
    }
}

@Composable
private fun HomeList(feed: HomeFeed, cardWidth: Dp, heroHeight: Dp) {
    val top = if (feed.heroes.isEmpty()) statusInset() + FrameSizes.TopBarHeight else 0.dp
    val bottom = navigationInset() + FrameSizes.TabBarClearance
    val list = rememberLazyListState()
    val overArt by remember(list) { derivedStateOf { list.firstVisibleItemIndex == 0 } }
    ArtUnderStatusBar(overArt && feed.heroes.isNotEmpty())
    RefreshBox(indicatorTop = statusInset() + FrameSpacing.S8, Modifier.fillMaxSize()) {
        GlassSource(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = top, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S24),
            ) {
                if (feed.heroes.isNotEmpty()) {
                    item(key = "hero", contentType = "hero") { HeroPager(feed.heroes, heroHeight) }
                }
                if (feed.continueWatching.isNotEmpty()) {
                    item(key = "continue", contentType = "continue") {
                        ContinueRow(feed.continueWatching, cardWidth)
                    }
                }
                items(feed.rows, key = { "${it.kind}:${it.label}" }, contentType = { "row" }) { TitleRow(it) }
            }
        }
    }
}

/** A section title with the short underline of images 04 and 11. */
@Composable
private fun HomeHeader(title: String, onViewAll: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4)) {
        SectionHeader(title, onViewAll = onViewAll)
        Spacer(
            Modifier
                .padding(horizontal = FrameSpacing.S16)
                .size(HeaderLineWidth, HeaderLineHeight)
                .background(FrameColors.White, FrameShapes.Chip),
        )
    }
}

@Composable
private fun ContinueRow(items: List<ContinueItem>, cardWidth: Dp) {
    val navigator = LocalNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(HeaderGap)) {
        HomeHeader(stringResource(R.string.app_continue_watching))
        LazyRow(
            contentPadding = PaddingValues(horizontal = FrameSpacing.S16),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
        ) {
            items(items, key = { it.titleId }) { item ->
                LandscapeCard(
                    title = item.name,
                    imagePath = item.imagePath,
                    onClick = { navigator.play(item.play) },
                    modifier = Modifier
                        .width(cardWidth)
                        .cardMenu { item.cardTarget() },
                    episodeLabel = item.episodeLabel,
                    // The newest episode the owner has can say when the next one comes instead.
                    subtitle = item.upcoming?.let { upcomingHint(it) } ?: item.episodeTitle,
                    progress = item.progress.takeIf { it > 0f },
                    timeLeftMs = item.timeLeftMs.takeIf { item.progress > 0f },
                    newEpisode = item.newEpisode,
                    hideImage = item.hideImage,
                )
            }
        }
    }
}

/** A row of posters. Videos have 16:9 frames and no poster, so theirs are landscape cards. */
@Composable
private fun TitleRow(row: HomeRow) {
    val navigator = LocalNavigator.current
    val title = when (row.kind) {
        HomeRowKind.TOP_RATED -> stringResource(R.string.app_top_rated)
        HomeRowKind.GENRE -> row.label.orEmpty()
        HomeRowKind.COLLECTION -> row.label ?: stringResource(R.string.app_collection)
        HomeRowKind.VIDEOS -> stringResource(R.string.app_section_videos)
        HomeRowKind.RECENT -> stringResource(R.string.app_sort_recent)
        HomeRowKind.WATCHLIST -> stringResource(R.string.app_filter_watchlist)
    }
    val source = "home:${row.kind}"
    Column(verticalArrangement = Arrangement.spacedBy(HeaderGap)) {
        HomeHeader(
            title = title,
            onViewAll = if (row.moreCount > 0) {
                { navigator.push(Route.Row(row.kind, row.label)) }
            } else {
                null
            },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = FrameSpacing.S16),
            horizontalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            items(row.titles, key = { it.id }) { card ->
                if (row.kind == HomeRowKind.VIDEOS) {
                    TitleFrame(card, source, Modifier.width(FrameSizes.LandscapeWidth))
                } else {
                    TitlePoster(card, source, Modifier.width(FrameSizes.PosterWidth))
                }
            }
        }
    }
}
