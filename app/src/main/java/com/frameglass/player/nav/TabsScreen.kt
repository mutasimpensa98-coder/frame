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
import androidx.compose.runtime.Stable
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

// How long the pages may stand between two tabs, or short of a tab that was asked for, with nothing moving them
// before they are brought to rest. A fling that follows a drag starts within this, and then nothing is done.
private const val AdriftMillis = 150L

// How long a request from the bar waits for a hand's swipe to settle before it takes the pages anyway. A settle is
// done in a third of this; the limit is there so that nothing a hand left behind can hold a request for good.
private const val HandWaitMillis = 1_500L

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
    val lead = remember(pager, arbiter) { TabLead(pager, arbiter) }
    val veil = remember { Animatable(1f) }
    // Every request made here goes through this, so it stands from the same instant the selection changes, and the
    // pages still on the tab they are leaving are never reported back as a swipe.
    val ask: (BarSection) -> Unit = remember(lead) {
        { section ->
            lead.asked = true
            select(section)
        }
    }

    // The section in view changed by something other than a swipe: a tap on the bar, Back, a link.
    LaunchedEffect(pager, selectedIndex) {
        // A swipe that settled here was reported below, and that is how this ran: there is nothing to bring.
        if (lead.wholeOn(selectedIndex)) {
            lead.asked = false
            return@LaunchedEffect
        }
        // This move owns the pages by a token of its own. A move it replaced ends after this one has begun, and
        // must find that it is no longer the leader, so it touches neither the token nor the veil.
        val me = Any()
        lead.asked = true
        lead.leader = me
        lead.guard?.cancel()
        try {
            // A swipe that is still settling finishes first, and the pages go on from where it left them. The
            // wait has a limit: nothing a hand left behind may hold a request for good.
            withTimeoutOrNull(HandWaitMillis) { snapshotFlow { lead.handMoving() }.first { !it } }
            bringPage(pager, selectedIndex, reduced, veil)
        } finally {
            if (lead.leader === me) {
                lead.leader = null
                // A finger on the pages took them from this move: where they rest is its to say.
                if (arbiter.fingerDown) lead.asked = false
                // The veil is the leader's while there is one: a move that begins before this runs keeps it.
                if (veil.value < 1f) scope.launch { if (lead.leader == null) veil.fadeTo(1f) }
                // Whatever cut the move short, the pages do not stay between two tabs or short of the one asked
                // for. Not while this effect is being replaced or leaves: then the next one, or nobody, has them.
                if (coroutineContext.isActive && TabLeadRules.snapAfterLed(lead.now(), selectedIndex)) {
                    runCatching { pager.scrollToPage(selectedIndex) }
                }
            }
        }
    }
    // A swipe ended on another tab, a hand took the pages from a request, or a request was met. It follows the
    // pages alone: a change of the selection is never by itself a reason to report where they stand.
    LaunchedEffect(lead, sections) {
        snapshotFlow { lead.now() }.collect { now ->
            lead.asked = TabLeadRules.stillAsked(lead.asked, now, selectedNow)
            TabLeadRules.report(now, lead.asked, selectedNow)?.let { page -> sections.getOrNull(page)?.let { select(it) } }
        }
    }
    // The rest guard: with nothing moving them, the pages never stay between two tabs, and never short of a tab
    // that was asked for. It stands down while a request is under way or a finger is down.
    LaunchedEffect(lead, reduced) {
        snapshotFlow { TabLeadRules.guard(lead.now(), lead.asked, selectedNow) }.collectLatest { target ->
            if (target == null) return@collectLatest
            delay(AdriftMillis)
            // In a job of its own. The slide changes what this flow reads, and inside this block it would be
            // cancelled by its own first step. It runs below the priority of a request and of a finger, so it
            // can interrupt neither.
            lead.guard?.cancel()
            lead.guard = scope.launch {
                if (reduced) pager.scrollToPage(target) else pager.slideToPage(target, MutatePriority.Default)
            }
        }
    }
    // A section that left the bar starts afresh if it comes back.
    LaunchedEffect(sections) {
        BarSection.entries.filter { it !in sections }.forEach { saved.removeState(it.id) }
    }

    val open: (BarSection) -> Unit = remember(sections, navigator, ask) {
        { section ->
            if (TabRules.opensAsPage(section, sections)) navigator.push(Route.Section(section)) else ask(section)
        }
    }
    // While Settings has no tab, its gear goes to the first tab's top bar.
    val extras = remember { BarExtras { SettingsGearButton() } }.takeIf { BarSection.SETTINGS !in sections }
    val barShown = sections.size > 1
    val tabs = sections.map { GlassTab(stringResource(it.label), it.icon, key = it.id) }
    // The pill follows the pages while a hand moves them and while they rest. While they are led, and for the one
    // frame between a request and its start, it goes to the selected tab by itself.
    val pagePosition = remember(pager, lead) {
        {
            if (TabLeadRules.pillFollowsPages(lead.now(), selectedNow)) {
                pager.currentPage + pager.currentPageOffsetFraction
            } else {
                null
            }
        }
    }

    val back = TabRules.backTarget(selected, sections)
    BackHandler(enabled = back != null) { back?.let(ask) }

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
                onSelect = { index -> sections.getOrNull(index)?.let(ask) },
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
            veil.fadeTo(0f)
            pager.scrollToPage(target)
            veil.fadeTo(1f)
        }
        // The move before this one may have been cut short behind its fade, so every branch ends with the veil up.
        runUp == from -> coroutineScope {
            launch { veil.fadeTo(1f) }
            pager.slideToPage(target, MutatePriority.UserInput)
        }
        else -> {
            veil.fadeTo(0f)
            pager.scrollToPage(runUp)
            coroutineScope {
                launch { veil.fadeTo(1f) }
                pager.slideToPage(target, MutatePriority.UserInput)
            }
        }
    }
}

/**
 * Fades the veil over the pages. An animation on it that something else starts ends this one with a cancellation
 * that is not this caller's own, and that must not end the move the caller is making: only the caller's own
 * cancellation goes on up.
 */
private suspend fun Animatable<Float, AnimationVector1D>.fadeTo(alpha: Float) {
    if (value == alpha && !isRunning) return
    try {
        animateTo(alpha, FrameMotion.effects())
    } catch (interrupted: CancellationException) {
        currentCoroutineContext().ensureActive()
    }
}

/**
 * What moves the pages besides a hand, kept in one place: the request under way, whether one still stands, and the
 * rest guard's own slide. The rules that read it are [TabLeadRules].
 */
@Stable
private class TabLead(private val pager: PagerState, private val arbiter: TabSwipeArbiter) {
    /** The token of the move the bar or a link asked for, while one is under way. Only its owner clears it. */
    var leader by mutableStateOf<Any?>(null)

    /** True from a request until the pages stand whole on the tab it named, or a hand takes them. */
    var asked by mutableStateOf(false)

    /** The slide of the rest guard, so that a request can end it before it takes the pages. */
    var guard: Job? = null

    fun now() = PagesNow(
        page = pager.currentPage,
        whole = abs(pager.currentPageOffsetFraction) < AtRest,
        scrolling = pager.isScrollInProgress,
        handed = arbiter.active,
        fingerDown = arbiter.fingerDown,
        led = leader != null,
    )

    fun wholeOn(page: Int): Boolean = pager.currentPage == page && abs(pager.currentPageOffsetFraction) < AtRest

    /** True while a scroll of any kind runs, or a handed-over drag holds the pages. */
    fun handMoving(): Boolean = pager.isScrollInProgress || arbiter.active
}

/**
 * Slides the pages to [target] and leaves them exactly on it. A request slides at [MutatePriority.UserInput], and
 * holds the pages as firmly as a finger does: when a fling inside a tab ends, the pager snaps by itself, and that
 * must not cut the slide short; a finger on the pages still takes them. The rest guard slides at
 * [MutatePriority.Default], which gives way to both.
 */
private suspend fun PagerState.slideToPage(target: Int, priority: MutatePriority) {
    val page = layoutInfo.pageSize + layoutInfo.pageSpacing
    if (page > 0) {
        val distance = (target - currentPage - currentPageOffsetFraction) * page
        scroll(priority) {
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
