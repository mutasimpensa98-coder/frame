package com.frameglass.player.nav

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import com.frameglass.data.api.BarSection
import com.frameglass.glass.EffectsLevel
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.GlassTab
import com.frameglass.glass.LiquidTabBar
import com.frameglass.glass.LocalTabBarClearance
import com.frameglass.glass.isGlassOverlayOpen
import com.frameglass.glass.rememberEffectsLevel
import com.frameglass.player.R
import com.frameglass.player.home.HomeScreen
import com.frameglass.player.library.FoldersScreen
import com.frameglass.player.library.LibraryScreen
import com.frameglass.player.search.SearchScreen
import com.frameglass.player.select.LocalSelectionChrome
import com.frameglass.player.select.SelectionChrome
import com.frameglass.player.settings.SettingsScreen
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

// How long the pages may stand between two tabs with nothing moving them before they are brought to rest. A fling
// that follows a drag starts within this, and then nothing is done.
private const val AdriftMillis = 150L

// Closer to a whole tab than this, in tabs, is at rest.
private const val AtRest = 0.001f

// Only the page in view is composed while the tabs rest, as when they cross-faded. A neighbour is composed when a
// swipe toward it begins and dropped when it is out of sight again, so Home's hero does not advance unseen and a
// tab that is not in view watches nothing. Each tab's scroll and typed text are kept by the SaveableStateHolder.
private const val PagesKeptBeyondView = 0

/** The name of a section, on its tab and on its row in Settings. */
@get:StringRes
internal val BarSection.label: Int
    get() = when (this) {
        BarSection.HOME -> R.string.app_tab_home
        BarSection.FOLDERS -> R.string.app_tab_folders
        BarSection.LIBRARY -> R.string.app_tab_library
        BarSection.SEARCH -> R.string.app_tab_search
        BarSection.SETTINGS -> R.string.app_tab_settings
    }

internal val BarSection.icon: ImageVector
    get() = when (this) {
        BarSection.HOME -> Icons.Rounded.Home
        BarSection.FOLDERS -> Icons.Rounded.Folder
        BarSection.LIBRARY -> Icons.Rounded.VideoLibrary
        BarSection.SEARCH -> Icons.Rounded.Search
        BarSection.SETTINGS -> Icons.Rounded.Settings
    }

/**
 * The page of one section. [open] shows another section: its tab where it has one, else a page of its own. With
 * [asPage] the section is itself such a page, and Settings, Library and Folders carry a back arrow.
 */
@Composable
private fun SectionContent(section: BarSection, asPage: Boolean = false, open: (BarSection) -> Unit) {
    when (section) {
        BarSection.HOME -> HomeScreen(onOpenLibrary = { open(BarSection.LIBRARY) })
        BarSection.FOLDERS -> if (asPage) FoldersScreen(asPage = true) else FoldersTabHost()
        BarSection.LIBRARY -> LibraryScreen(asPage)
        BarSection.SEARCH -> SearchScreen()
        BarSection.SETTINGS -> SettingsScreen(asPage)
    }
}

/** A section opened as a page of its own, because it has no tab: Back leaves it. */
@Composable
internal fun SectionPage(section: BarSection) {
    val navigator = LocalNavigator.current
    Box(Modifier.fillMaxSize().background(FrameColors.Canvas)) {
        SectionContent(section, asPage = true) { navigator.push(Route.Section(it)) }
    }
}

/**
 * The main pages behind the floating bar (F-U1, as changed on 2026-11-22). [sections] are the ones the owner keeps
 * in the bar, and [selected] is the one in view.
 *
 * The pages sit in a pager, so a swipe from the middle of the screen moves between them and the bar's pill travels
 * with it; a tap or a drag on the bar moves the pages. The pager's position is the one truth for what is in view:
 * [onSelect] hears of a swipe when it has settled, and a section asked for from outside is brought into view here.
 * Back on any other tab returns to the first one, then leaves the app. With one section there is no bar.
 */
@Composable
internal fun TabsScreen(sections: List<BarSection>, selected: BarSection, onSelect: (BarSection) -> Unit) {
    val navigator = LocalNavigator.current
    val saved = rememberSaveableStateHolder()
    val chrome = remember { SelectionChrome() }
    val swipeLock = remember { TabSwipeLock() }
    val scope = rememberCoroutineScope()
    val reduced = rememberEffectsLevel() == EffectsLevel.Degraded
    val selectedIndex = sections.indexOf(selected).coerceAtLeast(0)
    val selectedNow by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)

    // A pager of its own for each list of sections. A page number means nothing once the list has changed, and the
    // section in view is the caller's to keep.
    val pager = remember(sections) { PagerState(currentPage = selectedIndex) { sections.size } }
    val fling by rememberUpdatedState(PagerDefaults.flingBehavior(pager))
    val locked by rememberUpdatedState(TabSwipeRules.locked(chrome.active, isGlassOverlayOpen(), swipeLock.held))
    val rtl by rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    val arbiter = remember(pager, scope) { TabSwipeArbiter(pager, scope, { locked }, { rtl }, { fling }) }
    // True while the pages follow the bar or a link, and the pill leads them with its own spring.
    var led by remember(pager) { mutableStateOf(false) }
    val veil = remember { Animatable(1f) }

    // The section in view changed by something other than a swipe: a tap on the bar, Back, a link.
    LaunchedEffect(pager, selectedIndex) {
        // A swipe that settled here was reported below, and that is how this ran: there is nothing to bring.
        if (pager.currentPage == selectedIndex && abs(pager.currentPageOffsetFraction) < AtRest) return@LaunchedEffect
        led = true
        try {
            // A swipe that is still settling finishes first, and the pages go on from where it left them.
            snapshotFlow { pager.isScrollInProgress || arbiter.active }.first { !it }
            bringPage(pager, selectedIndex, reduced, veil)
        } finally {
            led = false
            if (veil.value < 1f) scope.launch { veil.animateTo(1f, FrameMotion.effects()) }
        }
    }
    // A swipe ended on another tab. Only pages standing whole are reported: a move cut short in mid-flight, such as
    // by a second tap on the bar, names no tab.
    LaunchedEffect(pager, sections) {
        snapshotFlow {
            val resting = !led && !pager.isScrollInProgress && !arbiter.active &&
                abs(pager.currentPageOffsetFraction) < AtRest
            if (resting) pager.currentPage else -1
        }.collect { page -> if (page >= 0 && page != selectedNow) sections.getOrNull(page)?.let { select(it) } }
    }
    // Whatever interrupted a move, the pages never stay between two tabs.
    LaunchedEffect(pager, reduced) {
        snapshotFlow {
            !led && !pager.isScrollInProgress && !arbiter.active && abs(pager.currentPageOffsetFraction) >= AtRest
        }.collectLatest { adrift ->
            if (adrift) {
                delay(AdriftMillis)
                if (reduced) pager.scrollToPage(pager.currentPage) else pager.slideToPage(pager.currentPage)
            }
        }
    }
    // A section that left the bar starts afresh if it comes back.
    LaunchedEffect(sections) {
        BarSection.entries.filter { it !in sections }.forEach { saved.removeState(it.id) }
    }

    val open: (BarSection) -> Unit = remember(sections, navigator) {
        { section ->
            if (TabRules.opensAsPage(section, sections)) navigator.push(Route.Section(section)) else select(section)
        }
    }
    // While Settings has no tab, its gear goes to the first tab's top bar.
    val extras = remember { BarExtras { SettingsGearButton() } }.takeIf { BarSection.SETTINGS !in sections }
    val barShown = sections.size > 1
    val tabs = sections.map { GlassTab(stringResource(it.label), it.icon, key = it.id) }
    // The pill follows the pages while a hand moves them and while they rest. While they are led, and for the one
    // frame between a request and its start, it goes to the selected tab by itself.
    val pagePosition = remember(pager, arbiter) {
        {
            val moving = pager.isScrollInProgress || arbiter.active
            if (led || (!moving && pager.currentPage != selectedNow)) {
                null
            } else {
                pager.currentPage + pager.currentPageOffsetFraction
            }
        }
    }

    val back = TabRules.backTarget(selected, sections)
    BackHandler(enabled = back != null) { back?.let(onSelect) }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalSelectionChrome provides chrome,
            LocalTabSwipeLock provides swipeLock,
            LocalTabBarClearance provides
                if (barShown) FrameSizes.TabBarShownClearance else FrameSizes.TabBarHiddenClearance,
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxSize()
                    .watchTabGestures(arbiter)
                    .graphicsLayer { alpha = veil.value },
                beyondViewportPageCount = PagesKeptBeyondView,
                flingBehavior = fling,
                // A drag that starts where nothing scrolls sideways comes here, and is a swipe between the tabs.
                userScrollEnabled = TabSwipeRules.pagerEnabled(locked),
                key = { sections.getOrNull(it)?.id ?: it },
                // A drag that starts on a row, a chip line or the hero is theirs, unless TabSwipeArbiter says otherwise.
                pageNestedScrollConnection = arbiter,
                // The first and the last tab end firmly, like the bar's pill.
                overscrollEffect = null,
            ) { page ->
                val section = sections.getOrNull(page) ?: return@HorizontalPager
                saved.SaveableStateProvider(section.id) {
                    Box(Modifier.fillMaxSize()) {
                        val pageExtras = extras.takeIf { page == 0 }
                        CompositionLocalProvider(
                            LocalBarExtras provides pageExtras,
                            // Back belongs to the tab in view, also while a neighbour is on screen during a swipe.
                            LocalTabInView provides (page == selectedIndex),
                        ) {
                            SectionContent(section, open = open)
                        }
                        // A tab in a mode of its own, selecting or searching, has swapped its top bar for the
                        // mode's, and shows no gear until it is out of it.
                        if (pageExtras != null && !chrome.active && !swipeLock.held) UnhostedExtras(pageExtras)
                    }
                }
            }
        }
        // A tab that is selecting puts its action bar here, so the tab bar steps aside until the mode ends.
        if (barShown && !chrome.active) {
            LiquidTabBar(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onSelect = { index -> sections.getOrNull(index)?.let(onSelect) },
                modifier = Modifier.align(Alignment.BottomCenter),
                pagePosition = pagePosition,
            )
        }
    }
}

/**
 * The pages go to [target] because the bar or a link asked. A neighbour slides in. A tab further away is cut to
 * behind a short fade, one tab short of it, and slides in from the side the pill came from, so the tabs between
 * are never drawn. With reduced motion there is only the fade.
 */
private suspend fun bringPage(
    pager: PagerState,
    target: Int,
    reduced: Boolean,
    veil: Animatable<Float, AnimationVector1D>,
) {
    val from = pager.currentPage
    val runUp = TabRules.runUp(from, target)
    when {
        reduced -> {
            veil.animateTo(0f, FrameMotion.effects())
            pager.scrollToPage(target)
            veil.animateTo(1f, FrameMotion.effects())
        }
        runUp == from -> pager.slideToPage(target)
        else -> {
            veil.animateTo(0f, FrameMotion.effects())
            pager.scrollToPage(runUp)
            coroutineScope {
                launch { veil.animateTo(1f, FrameMotion.effects()) }
                pager.slideToPage(target)
            }
        }
    }
}

/**
 * Slides the pages to [target] and leaves them exactly on it. The slide holds the pages as firmly as a finger does:
 * when a fling inside a tab ends, the pager snaps by itself, and that must not cut a slide short. A finger on the
 * pages still takes them.
 */
private suspend fun PagerState.slideToPage(target: Int) {
    val page = layoutInfo.pageSize + layoutInfo.pageSpacing
    if (page > 0) {
        val distance = (target - currentPage - currentPageOffsetFraction) * page
        scroll(MutatePriority.UserInput) {
            var done = 0f
            animate(0f, distance, animationSpec = FrameMotion.page()) { value, _ -> done += scrollBy(value - done) }
        }
    }
    // Before the first layout there is nothing to slide, and after a slide a part of a pixel may be left.
    if (currentPage != target || abs(currentPageOffsetFraction) >= AtRest) scrollToPage(target)
}

/**
 * Keeps [extras] reachable on a first tab whose top bar has no [BarExtrasSlot] yet: the buttons float above the bar
 * at the end side. A top bar that takes them makes this draw nothing.
 */
@Composable
private fun BoxScope.UnhostedExtras(extras: BarExtras) {
    // A top bar says it has taken them right after it is first composed, so the first frame is not asked.
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        asked = true
    }
    if (asked && !extras.hosted) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = FrameSpacing.S16, bottom = FrameSizes.TabBarClearance),
        ) {
            extras.content()
        }
    }
}
