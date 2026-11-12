package com.frameglass.player.tour

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frameglass.glass.DarkLook
import com.frameglass.glass.EffectsLevel
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameType
import com.frameglass.glass.WhitePill
import com.frameglass.glass.rememberEffectsLevel
import com.frameglass.player.R
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.Route
import com.frameglass.player.ui.ArtUnderStatusBar
import kotlinx.coroutines.launch
import kotlin.math.min

// The storyboard draws each illustration in a 260 by 300 box; it scales with the room the page leaves.
private const val StageWidth = 260f
private const val StageHeight = 300f
private const val MaxStageScale = 1.45f
private val TopRoom = 64.dp
private val BottomRoom = 120.dp
private val DotSize = 6.dp
private val DotOnWidth = 18.dp
private const val DotOffAlpha = 0.22f

/** The six pages of the tour, each with its own light at the top, as in the approved storyboard. */
internal enum class TourPage(@param:StringRes val title: Int, @param:StringRes val body: Int, val glows: List<Glow>) {
    WELCOME(
        R.string.tour_welcome_title,
        R.string.tour_welcome_body,
        listOf(Glow(Color(0xFF5C6AFF).copy(alpha = 0.60f), rx = 1.2f, ry = 0.6f, cx = 0.5f, cy = 0.3f)),
    ),
    LIBRARY(
        R.string.tour_library_title,
        R.string.tour_library_body,
        listOf(
            Glow(Color(0xFF0F6CDB).copy(alpha = 0.54f), rx = 0.9f, ry = 0.45f, cx = 0.8f, cy = 0.22f),
            Glow(Color(0xFFF5C518).copy(alpha = 0.32f), rx = 0.8f, ry = 0.4f, cx = 0.15f, cy = 0.48f),
        ),
    ),
    PLAYER(
        R.string.tour_player_title,
        R.string.tour_player_body,
        listOf(Glow(Color(0xFF26A69A).copy(alpha = 0.48f), rx = 1.1f, ry = 0.55f, cx = 0.5f, cy = 0.3f)),
    ),
    CONTINUE(
        R.string.tour_continue_title,
        R.string.tour_continue_body,
        listOf(Glow(Color(0xFF8A5BD6).copy(alpha = 0.54f), rx = 1.1f, ry = 0.55f, cx = 0.4f, cy = 0.3f)),
    ),
    TONIGHT(
        R.string.tour_tonight_title,
        R.string.tour_tonight_body,
        listOf(Glow(Color(0xFFFFA640).copy(alpha = 0.45f), rx = 1.1f, ry = 0.55f, cx = 0.6f, cy = 0.3f)),
    ),
    PRIVACY(
        R.string.tour_privacy_title,
        R.string.tour_privacy_body,
        listOf(Glow(Color(0xFF2E9D6A).copy(alpha = 0.48f), rx = 1.1f, ry = 0.55f, cx = 0.5f, cy = 0.3f)),
    ),
}

/**
 * The feature tour: six full-screen pages with a swipe, Next and Skip. Pages run right to left in Arabic. Back
 * goes to the page before, and on the first page leaves the tour as Back leaves any page. It keeps the dark look
 * in every theme, as the storyboard shows it.
 */
@Composable
internal fun TourScreen(route: Route.Tour) {
    val navigator = LocalNavigator.current
    val pages = TourPage.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val reduced = rememberEffectsLevel() == EffectsLevel.Degraded
    val posters = rememberTourPosters()
    // Each page plays its entrance once, the first time it becomes the current page.
    val entered = remember { mutableStateListOf<Int>() }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page -> if (page !in entered) entered += page }
    }
    val current = pager.currentPage
    val last = current == pages.lastIndex
    val leave = { navigator.leaveTour(route) }
    val goTo = { page: Int ->
        scope.launch {
            if (reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page, animationSpec = FrameMotion.defaultSpatial())
        }
    }

    ArtUnderStatusBar(active = true)
    BackHandler(enabled = current > 0) { goTo(current - 1) }

    DarkLook {
        val colors = FrameTheme.colors
        Box(Modifier.fillMaxSize().background(colors.canvas)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { index ->
                TourPageView(pages[index], posters, entered = index in entered, reduced = reduced)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = FrameSpacing.S24, vertical = FrameSpacing.S16),
            ) {
                AnimatedVisibility(
                    visible = !last,
                    modifier = Modifier.align(Alignment.TopEnd),
                    enter = fadeIn(FrameMotion.effects()),
                    exit = fadeOut(FrameMotion.effects()),
                ) {
                    Text(
                        text = stringResource(R.string.app_onboarding_skip),
                        style = FrameType.Body,
                        color = colors.secondaryContent,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(FrameShapes.Chip)
                            .clickable(role = Role.Button, onClick = leave)
                            .padding(horizontal = FrameSpacing.S12, vertical = FrameSpacing.S8),
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FrameSpacing.S24),
                ) {
                    PageDots(pages.size, current, reduced)
                    WhitePill(
                        text = stringResource(if (last) R.string.tour_start else R.string.tour_next),
                        onClick = { if (last) leave() else goTo(current + 1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, reduced: Boolean) {
    val colors = FrameTheme.colors
    val description = stringResource(R.string.tour_page, current + 1, count)
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(DotSize),
    ) {
        repeat(count) { index ->
            val on = index == current
            val width by animateDpAsState(
                targetValue = if (on) DotOnWidth else DotSize,
                animationSpec = if (reduced) snap<Dp>() else FrameMotion.effects<Dp>(),
                label = "tourDot",
            )
            Box(
                Modifier
                    .size(width, DotSize)
                    .background(if (on) colors.content else colors.content.copy(alpha = DotOffAlpha), FrameShapes.Chip),
            )
        }
    }
}

/** One page: its light, the illustration in the room left over, then the title and body. */
@Composable
private fun TourPageView(page: TourPage, posters: List<TourPoster>, entered: Boolean, reduced: Boolean) {
    val colors = FrameTheme.colors
    Box(Modifier.fillMaxSize().tourGlow(page.glows)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = FrameSpacing.S24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(TopRoom))
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                val scale = min(maxWidth.value / StageWidth, maxHeight.value / StageHeight).coerceAtMost(MaxStageScale)
                val stage = Stage(scale, entered, reduced)
                Box(Modifier.size(stage.dp(StageWidth), stage.dp(StageHeight))) {
                    when (page) {
                        TourPage.WELCOME -> WelcomeArt(stage)
                        TourPage.LIBRARY -> LibraryArt(stage, posters)
                        TourPage.PLAYER -> PlayerArt(stage)
                        TourPage.CONTINUE -> ContinueArt(stage)
                        TourPage.TONIGHT -> TonightArt(stage, posters)
                        TourPage.PRIVACY -> PrivacyArt(stage)
                    }
                }
            }
            Spacer(Modifier.height(FrameSpacing.S24))
            Text(
                text = stringResource(page.title),
                style = FrameType.Display,
                color = colors.content,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(FrameSpacing.S12))
            Text(
                text = stringResource(page.body),
                style = FrameType.Body,
                color = colors.secondaryContent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(BottomRoom))
        }
    }
}
