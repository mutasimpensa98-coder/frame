package com.frameglass.player.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.frameglass.data.api.HeroItem
import com.frameglass.glass.CoverImage
import com.frameglass.glass.EffectsLevel
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameType
import com.frameglass.glass.PosterWash
import com.frameglass.glass.StagePoster
import com.frameglass.glass.WhitePill
import com.frameglass.glass.coverGlow
import com.frameglass.glass.rememberEffectsLevel
import com.frameglass.glass.rememberPortrait
import com.frameglass.player.R
import com.frameglass.player.nav.ArtRef
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.sharedArt
import com.frameglass.player.ui.TitleMark
import com.frameglass.player.ui.kindLabel
import com.frameglass.player.ui.localNumber
import com.frameglass.player.ui.metaLine
import com.frameglass.player.ui.statusInset
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val AdvanceMillis = 8_000L
private val DotSize = 8.dp
private val DotPillWidth = 32.dp
private val DotGap = 8.dp
private val DotsBottom = 16.dp
private val TextBottom = 40.dp
private val PillMinWidth = 150.dp
private val LogoHeight = 96.dp
private val StageLogoHeight = 56.dp
private const val LogoWidthShare = 0.72f
private val MetaText = FrameType.Body.copy(fontWeight = FontWeight.W500)

// The dark top belongs to the art under the status bar. The bottom fades into the canvas of the theme, and on
// light it starts higher: the title here is dark, so the art must be washed out before the text begins.
@Composable
private fun rememberHeroFade(): Brush {
    val colors = FrameTheme.colors
    return remember(colors) {
        val canvas = colors.canvas
        if (colors.isLight) {
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.28f),
                0.16f to Color.Transparent,
                0.3f to Color.Transparent,
                0.5f to canvas.copy(alpha = 0.72f),
                0.7f to canvas.copy(alpha = 0.94f),
                1f to canvas,
            )
        } else {
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.28f),
                0.16f to Color.Transparent,
                0.46f to Color.Transparent,
                0.76f to canvas.copy(alpha = 0.86f),
                1f to canvas,
            )
        }
    }
}

// Behind a standing poster the wash is a halo round it, and it has gone into the canvas before the title starts.
@Composable
private fun rememberHeroStageFade(): Brush {
    val colors = FrameTheme.colors
    return remember(colors) {
        val canvas = colors.canvas
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.28f),
            0.16f to Color.Transparent,
            0.38f to Color.Transparent,
            0.6f to canvas.copy(alpha = 0.9f),
            1f to canvas,
        )
    }
}

/** F-U5: full-bleed art fading into the canvas, swiped by hand or every 8 s while nobody touches it. */
@Composable
internal fun HeroPager(heroes: List<HeroItem>, height: Dp) {
    val pager = rememberPagerState(pageCount = { heroes.size })
    val reduced = rememberEffectsLevel() == EffectsLevel.Degraded
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    var touching by remember { mutableStateOf(false) }

    LaunchedEffect(pager, heroes.size, reduced, touching, dragged) {
        if (touching || dragged) return@LaunchedEffect
        // A touch or a new list can stop the advance between two pages, so it settles on the nearer one first.
        if (pager.currentPageOffsetFraction != 0f && !pager.isScrollInProgress) {
            if (reduced) {
                pager.scrollToPage(pager.currentPage)
            } else {
                pager.animateScrollToPage(pager.currentPage, animationSpec = FrameMotion.defaultSpatial())
            }
        }
        if (heroes.size < 2 || reduced) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(AdvanceMillis)
                pager.animateScrollToPage(
                    page = (pager.currentPage + 1) % heroes.size,
                    animationSpec = FrameMotion.slowSpatial(),
                )
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touching = true
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        touching = false
                    }
                }
            },
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { heroes.getOrNull(it)?.titleId ?: it },
        ) { page ->
            heroes.getOrNull(page)?.let { HeroPage(it) }
        }
        if (heroes.size > 1) {
            PagerDots(
                pager,
                heroes.size,
                Modifier.align(Alignment.BottomCenter).padding(bottom = DotsBottom),
            )
        }
    }
}

@Composable
private fun HeroPage(hero: HeroItem) {
    val navigator = LocalNavigator.current
    val art = hero.backdropPath ?: hero.coverPath
    val artKey = remember(hero.titleId) { "hero:${hero.titleId}" }
    // A poster alone is shown whole: cropped to the hero it would lose its own title and the faces on it.
    val portrait = rememberPortrait(art)
    val poster = art.takeIf { portrait == true }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = null,
                indication = null,
                onClickLabel = hero.name,
                onClick = { navigator.details(hero.titleId, ArtRef(artKey, art)) },
            ),
    ) {
        if (poster == null) {
            Box(Modifier.fillMaxSize().sharedArt(artKey)) {
                CoverImage(art, null, Modifier.matchParentSize(), RectangleShape)
                Box(Modifier.matchParentSize().background(rememberHeroFade()))
                Box(Modifier.matchParentSize().coverGlow(hero.dominantColor))
            }
            HeroText(
                hero = hero,
                titleStyle = FrameType.Display,
                logoHeight = LogoHeight,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = FrameSpacing.S24, end = FrameSpacing.S24, bottom = TextBottom),
            )
        } else {
            PosterWash(poster, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(rememberHeroStageFade()))
            Box(Modifier.matchParentSize().coverGlow(hero.dominantColor))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = FrameSpacing.S24,
                        end = FrameSpacing.S24,
                        top = statusInset() + FrameSpacing.S16,
                        bottom = TextBottom,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
            ) {
                // The poster takes whatever height the title block leaves, so a two-line title shrinks it a little.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxHeight().aspectRatio(FrameSizes.PosterRatio, matchHeightConstraintsFirst = true)) {
                        Box(Modifier.matchParentSize().sharedArt(artKey)) {
                            StagePoster(poster, Modifier.matchParentSize())
                        }
                    }
                }
                HeroText(hero = hero, titleStyle = FrameType.TitleLarge, logoHeight = StageLogoHeight)
            }
        }
    }
}

@Composable
private fun HeroText(hero: HeroItem, titleStyle: TextStyle, logoHeight: Dp, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
    ) {
        TitleMark(
            logoPath = hero.logoPath,
            name = hero.name,
            logoModifier = Modifier.fillMaxWidth(LogoWidthShare).height(logoHeight),
            style = titleStyle,
        )
        val kind = if (hero.clip) null else kindLabel(hero.kind)
        val meta = listOfNotNull(kind, hero.genre, hero.year?.let { localNumber(it) })
        if (meta.isNotEmpty()) {
            Text(
                text = metaLine(meta),
                style = MetaText,
                color = FrameColors.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val play = hero.play
        WhitePill(
            text = stringResource(if (hero.resumable) R.string.app_resume else R.string.app_play),
            onClick = { play?.let(navigator::play) },
            modifier = Modifier.widthIn(min = PillMinWidth).padding(top = FrameSpacing.S4),
            icon = Icons.Rounded.PlayArrow,
            enabled = play != null,
        )
    }
}

/** The dots of images 01 and 04: the current page stretches into a short pill as the pager moves. */
@Composable
private fun PagerDots(pager: PagerState, count: Int, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val width = DotPillWidth + (DotSize + DotGap) * (count - 1)
    // Read while drawing, so a swipe redraws this and recomposes nothing.
    Canvas(modifier.size(width, DotSize)) {
        val dot = DotSize.toPx()
        val pill = DotPillWidth.toPx()
        val gap = DotGap.toPx()
        val position = pager.currentPage + pager.currentPageOffsetFraction
        var travelled = 0f
        repeat(count) { index ->
            val active = (1f - abs(position - index)).coerceIn(0f, 1f)
            val itemWidth = lerp(dot, pill, active)
            val left = if (rtl) size.width - travelled - itemWidth else travelled
            drawRoundRect(
                color = FrameColors.White,
                topLeft = Offset(left, 0f),
                size = Size(itemWidth, dot),
                cornerRadius = CornerRadius(dot / 2f),
            )
            travelled += itemWidth + gap
        }
    }
}
