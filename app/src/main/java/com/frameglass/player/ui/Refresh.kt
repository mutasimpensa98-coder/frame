package com.frameglass.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.RefreshSkip
import com.frameglass.glass.ActivityLine
import com.frameglass.glass.EffectsLevel
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassKind
import com.frameglass.glass.GlassSurface
import com.frameglass.glass.ProgressLine
import com.frameglass.glass.rememberEffectsLevel
import com.frameglass.player.R
import com.frameglass.player.model.RefreshLine
import com.frameglass.player.model.RefreshUi
import com.frameglass.player.model.fraction
import com.frameglass.player.model.line
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

// How far the pill travels with the finger up to the threshold, and how far past it a pull may carry it.
private val PillTravel = 56.dp
private const val OverPull = 1.4f

// The content grows by this share of its height at the threshold: a hint of rubber, not a zoom.
private const val Stretch = 0.035f

private val PillMinWidth = 208.dp
private val PillMaxWidth = 320.dp
private val GlyphSize = 18.dp
private const val ArrowTurn = 180f
private const val FadedOut = 0.01f

/**
 * Swipe down to refresh everything. A glass pill follows the finger and says when letting go will refresh, then
 * shows the live stages with a thin progress line, and the result for a few seconds. Wraps one scrolling list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefreshBox(
    indicatorTop: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val refreshes = rememberUiModel().refreshes
    val ui by refreshes.ui.collectAsStateWithLifecycle()
    val state = rememberPullToRefreshState()
    val motion = rememberEffectsLevel() == EffectsLevel.Full
    val haptics = LocalHapticFeedback.current
    val running = ui is RefreshUi.Running
    // After a run the box springs back from the threshold by itself. That return is not a pull, so a new pull counts
    // only once the box has come to rest, even while the last result is still showing.
    var rested by remember { mutableStateOf(true) }
    LaunchedEffect(state, running) {
        if (running) {
            rested = false
            return@LaunchedEffect
        }
        snapshotFlow { state.distanceFraction <= 0f }.first { it }
        rested = true
    }
    val pulling = !running && rested

    LaunchedEffect(state, pulling) {
        if (!pulling) return@LaunchedEffect
        snapshotFlow { state.distanceFraction >= 1f }
            .distinctUntilChanged()
            .drop(1)
            .collect { armed -> if (armed) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }

    val pull by remember(state) { derivedStateOf { state.distanceFraction.coerceIn(0f, OverPull) } }
    // The box holds the pull at the threshold while a refresh runs, so only a real pull stretches.
    val stretch by animateFloatAsState(
        targetValue = if (motion && pulling) pull else 0f,
        animationSpec = if (pulling) snap() else FrameMotion.fastSpatial(),
        label = "refreshStretch",
    )

    PullToRefreshBox(
        isRefreshing = ui is RefreshUi.Running,
        onRefresh = refreshes::refresh,
        modifier = modifier,
        state = state,
        indicator = {
            RefreshPill(
                line = ui.line(),
                pull = { if (pulling) pull else 0f },
                held = running,
                motion = motion,
                top = indicatorTop,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleY = 1f + Stretch * stretch
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
            content = content,
        )
    }
}

@Composable
private fun RefreshPill(line: RefreshLine?, pull: () -> Float, held: Boolean, motion: Boolean, top: Dp, modifier: Modifier) {
    // Kept, so the result keeps its words while the pill fades out.
    var last by remember { mutableStateOf<RefreshLine?>(null) }
    if (line != null) last = line
    val fade by animateFloatAsState(if (line != null) 1f else 0f, FrameMotion.effects(), label = "refreshFade")
    val pulled = pull()
    // A new pull takes over from a result still on screen; a running refresh keeps its stages.
    val shown = when {
        pulled > 0f && !held -> null
        line != null -> line
        else -> last.takeIf { pulled == 0f && fade > FadedOut }
    }
    // Pulling, the pill follows the finger. Once released it rests where the threshold left it.
    val travel = if (shown == null) pulled else 1f
    val alpha = if (shown == null) pulled.coerceIn(0f, 1f) else fade
    if (alpha <= FadedOut) return
    val armed = pulled >= 1f

    GlassSurface(
        kind = GlassKind.Badge,
        shape = FrameShapes.Chip,
        blur = false,
        modifier = modifier
            .padding(top = top + FrameSpacing.S8)
            .graphicsLayer {
                translationY = (PillTravel * (travel - 1f)).toPx()
                this.alpha = alpha
            }
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = PillMinWidth, max = PillMaxWidth)
                .padding(horizontal = FrameSpacing.S16, vertical = FrameSpacing.S8),
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
        ) {
            if (shown == null) {
                PullRow(armed, motion)
            } else {
                AnimatedContent(
                    targetState = shown,
                    contentKey = { it.kind() },
                    transitionSpec = { fadeIn(FrameMotion.effects()) togetherWith fadeOut(FrameMotion.effects()) },
                    label = "refreshStage",
                ) { now -> StageRow(now) }
                val fraction = shown.fraction()
                if (fraction == null) {
                    ActivityLine(Modifier.fillMaxWidth())
                } else {
                    val filled by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = if (motion) FrameMotion.defaultSpatial() else snap(),
                        label = "refreshFill",
                    )
                    ProgressLine(filled, Modifier.fillMaxWidth(), track = FrameColors.Divider)
                }
            }
        }
    }
}

@Composable
private fun PullRow(armed: Boolean, motion: Boolean) {
    val turn by animateFloatAsState(
        targetValue = if (armed) ArrowTurn else 0f,
        animationSpec = if (motion) FrameMotion.fastSpatial() else snap(),
        label = "refreshArrow",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        Icon(
            imageVector = Icons.Rounded.ArrowDownward,
            contentDescription = null,
            modifier = Modifier.size(GlyphSize).graphicsLayer { rotationZ = turn },
            tint = if (armed) FrameColors.Accent else FrameColors.White,
        )
        Text(
            text = stringResource(if (armed) R.string.app_refresh_release else R.string.app_refresh_pull),
            style = FrameType.Caption,
            color = FrameColors.White,
            maxLines = 1,
        )
    }
}

/** Changes only between stages, so counts tick over in place and only a new stage cross-fades. */
private fun RefreshLine.kind(): Int = when (this) {
    RefreshLine.Starting -> 0
    is RefreshLine.Scanning -> 1
    is RefreshLine.Fetching -> 2
    is RefreshLine.Result -> 3
}

@Composable
private fun StageRow(line: RefreshLine) {
    val done = line is RefreshLine.Result
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        Icon(
            imageVector = if (done) Icons.Rounded.CheckCircle else Icons.Rounded.Sync,
            contentDescription = null,
            modifier = Modifier.size(GlyphSize),
            tint = if (done) FrameColors.Accent else FrameColors.White,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = line.title(),
                style = FrameType.Caption,
                color = FrameColors.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            line.note()?.let { note ->
                Text(
                    text = note,
                    style = FrameType.Label,
                    color = FrameColors.SecondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RefreshLine.title(): String = when (this) {
    RefreshLine.Starting -> stringResource(R.string.app_refresh_starting)
    is RefreshLine.Scanning -> if (files > 0) {
        pluralStringResource(R.plurals.app_refresh_scanning, files, files)
    } else {
        stringResource(R.string.app_refresh_scanning_start)
    }
    is RefreshLine.Fetching -> if (total > 0) {
        stringResource(R.string.app_refresh_details, done, total)
    } else {
        stringResource(R.string.app_refresh_details_start)
    }
    is RefreshLine.Result -> {
        val found = if (newVideos > 0) pluralStringResource(R.plurals.app_refresh_new, newVideos, newVideos) else null
        val fresh = if (updated > 0) pluralStringResource(R.plurals.app_refresh_updated, updated, updated) else null
        when {
            found != null && fresh != null -> stringResource(R.string.app_refresh_both, found, fresh)
            found != null -> found
            fresh != null -> fresh
            // Not "up to date" while details were skipped or are still coming.
            skipped != null || left > 0 -> stringResource(R.string.app_refresh_no_new)
            else -> stringResource(R.string.app_refresh_up_to_date)
        }
    }
}

@Composable
private fun RefreshLine.note(): String? {
    if (this !is RefreshLine.Result) return null
    return when (skipped) {
        RefreshSkip.ONLINE_OFF -> stringResource(R.string.app_refresh_skip_offline)
        RefreshSkip.NO_NETWORK -> stringResource(R.string.app_refresh_skip_network)
        RefreshSkip.NO_SERVICE -> stringResource(R.string.app_refresh_skip_service)
        null -> if (left > 0) pluralStringResource(R.plurals.app_refresh_left, left, left) else null
    }
}
