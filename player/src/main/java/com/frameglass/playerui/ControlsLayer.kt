package com.frameglass.playerui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.frameglass.data.api.SegmentKind
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassKind
import com.frameglass.glass.GlassSurface
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.PressableGlass
import com.frameglass.glass.WhitePill
import com.frameglass.glass.bidiIsolate
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val AutoHideMillis = 3_500L

/** How long the controls take to fade, which is also how long a sheet waits before it opens (F-P12). */
internal const val ControlsFadeMillis = 160

private const val FeedbackMillis = 700L
private const val LongFeedbackMillis = 3_500L
private const val LongFeedbackLines = 3
private const val UnlockMillis = 2_500L
private const val HintMillis = 1_500L
private const val BufferingDelayMillis = 400L

/** Drag across the full width moves this far. */
private const val SeekWindowMs = 120_000f

/** Drag across the full height moves a level this far. */
private const val LevelGain = 1.2f

private const val ScrimAlpha = 0.60f
private const val PlayedTrack = 1f
private const val BufferedTrack = 0.45f
private const val EmptyTrack = 0.26f

private val ScrimColor = Color.Black.copy(alpha = ScrimAlpha)
private val ScrimRamp = 64.dp
private val TopScrimHeight = 120.dp
private val EdgeGutter = FrameSpacing.S24
private val MinEdgeZone = 24.dp
private val RailThin = 3.dp
private val RailThick = 6.dp
private val SeekTouchHeight = 28.dp
private val RowIconSize = 44.dp
private val IconSize = 24.dp
private val SkipButtonSize = 56.dp
private val PlayButtonSize = 72.dp
private val SkipIconSize = 32.dp
private val PlayIconSize = 40.dp
private val TransportGap = FrameSpacing.S48
private val PreviewWidth = 200.dp
private val PreviewHeight = 112.dp
private val PreviewShape = RoundedCornerShape(12.dp)
private val LevelBarHeight = 120.dp
private val LevelBarWidth = 4.dp
private val FeedbackInset = 72.dp
private val NextCardWidth = 280.dp
private val WideRowWidth = 520.dp

internal val AspectMode.label: Int
    @StringRes get() = when (this) {
        AspectMode.FIT -> R.string.player_aspect_fit
        AspectMode.FILL -> R.string.player_aspect_fill
        AspectMode.RATIO_16_9 -> R.string.player_aspect_16_9
        AspectMode.RATIO_4_3 -> R.string.player_aspect_4_3
        AspectMode.CROP -> R.string.player_aspect_crop
    }

/** Elapsed style time in the device's digits, wrapped by the caller so it always reads left to right. */
internal fun formatTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val rest = seconds % 60L
    val locale = Locale.getDefault()
    return if (hours > 0L) {
        String.format(locale, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(locale, "%02d:%02d", minutes, rest)
    }
}

/** A speed or zoom factor in the device's digits, with no trailing zeros. */
internal fun formatSpeed(value: Float): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)

/** Layer 2 of the stack: touch surface, then controls, then everything shown over the picture. */
@Composable
internal fun ControlsLayer(session: PlayerSession, ui: PlayerUiState, actions: PlayerActions) {
    if (ui.inPip) return
    val state = session.state
    val failure = state.failure
    LaunchedEffect(ui.controlsVisible, state.isPlaying, ui.interaction, ui.sheet, ui.scrub != null) {
        if (ui.controlsVisible && state.isPlaying && ui.sheet == null && ui.scrub == null) {
            delay(AutoHideMillis)
            ui.controlsVisible = false
        }
    }
    val showChrome = ui.controlsVisible && ui.sheet == null && !ui.locked && failure == null
    val chromeAlpha by animateFloatAsState(if (showChrome) 1f else 0f, tween(ControlsFadeMillis), label = "controls")
    LaunchedEffect(showChrome, ui.chromeHeight) {
        ui.subtitleClearance = if (showChrome) ui.chromeHeight else 0
        if (!showChrome) ui.blurRects.clear()
    }

    Box(Modifier.fillMaxSize()) {
        if (failure == null) GestureLayer(session, ui, actions)
        if (chromeAlpha > 0f) {
            Chrome(session, ui, actions, Modifier.graphicsLayer { alpha = chromeAlpha })
        }
        if (failure == null && ui.sheet == null) {
            if (ui.locked) UnlockButton(ui, actions)
            BufferingMark(state, ui)
            ScrubLayer(session, ui, showChrome)
            FeedbackLayer(state, ui)
            SkipAndNext(session, ui, showChrome)
        }
        if (failure != null) FailureCard(failure, state, actions)
    }
}

@Composable
private fun GestureLayer(session: PlayerSession, ui: PlayerUiState, actions: PlayerActions) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.systemGestures
    val minimum = with(density) { MinEdgeZone.toPx() }
    val target = remember(session, ui, actions) { PlayerGestureTarget(session, ui, actions) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(target) {
                detectPlayerGestures(target) {
                    GestureEdges(
                        left = max(insets.getLeft(density, direction).toFloat(), minimum),
                        top = max(insets.getTop(density).toFloat(), minimum),
                        right = max(insets.getRight(density, direction).toFloat(), minimum),
                        bottom = max(insets.getBottom(density).toFloat(), minimum),
                    )
                }
            },
    )
}

private class PlayerGestureTarget(
    private val session: PlayerSession,
    private val ui: PlayerUiState,
    private val actions: PlayerActions,
) : GestureTarget {
    private var seekFrom = 0L

    override val locked: Boolean get() = ui.locked

    override fun tap() {
        if (ui.locked) ui.unlockVisible = true else ui.toggleControls()
    }

    override fun skip(forward: Boolean) {
        val step = session.state.settings.doubleTapSeconds
        if (!session.seekBy(if (forward) step * 1000L else -step * 1000L)) return
        val current = ui.feedback as? Feedback.Skip
        val total = if (current?.forward == forward) current.seconds + step else step
        ui.feedback = Feedback.Skip(total, forward)
    }

    override fun holdStart() = session.beginHoldSpeed()

    override fun holdEnd() = session.endHoldSpeed()

    override fun seekStart() {
        seekFrom = session.state.positionMs
    }

    override fun seekDrag(fraction: Float) {
        val duration = session.state.durationMs
        if (duration <= 0L) return
        val position = (seekFrom + (fraction * SeekWindowMs).toLong()).coerceIn(0L, duration)
        ui.scrub = Scrub(position, fromGesture = true)
        ui.feedback = Feedback.Seek(position - seekFrom)
        session.previews.request(position)
    }

    override fun seekEnd() {
        val scrub = ui.scrub
        ui.scrub = null
        ui.feedback = null
        scrub?.let { session.seekTo(it.positionMs) }
        session.previews.endScrub()
    }

    override fun level(leftSide: Boolean, delta: Float) {
        val change = delta * LevelGain
        ui.feedback = if (leftSide) {
            Feedback.Brightness(actions.adjustBrightness(change))
        } else {
            Feedback.Volume(actions.adjustVolume(change))
        }
    }

    override fun levelEnd() = actions.endLevelGesture()

    override fun pinch(zoomChange: Float, pan: Offset) {
        actions.pinch(zoomChange, pan.x, pan.y)
        ui.feedback = Feedback.Zoom(session.state.zoom)
    }

    override fun pinchEnd() = actions.pinchEnded()
}

/** The fallback of brief 7.5 for now: scrims and tinted pills, with no blur over the video. */
@Composable
private fun Chrome(session: PlayerSession, ui: PlayerUiState, actions: PlayerActions, modifier: Modifier) {
    val density = LocalDensity.current
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TopScrimHeight)
                .background(Brush.verticalGradient(listOf(ScrimColor, Color.Transparent))),
        )
        val bottomScrim = with(density) { (ui.chromeHeight.toDp() + ScrimRamp) }
        val rampEnd = ScrimRamp / max(bottomScrim, ScrimRamp)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomScrim)
                .background(Brush.verticalGradient(0f to Color.Transparent, rampEnd to ScrimColor, 1f to ScrimColor)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = EdgeGutter, vertical = FrameSpacing.S12),
        ) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.player_back), actions::exit)
                GlassIconButton(Icons.Rounded.Lock, stringResource(R.string.player_lock), { actions.lock(true) })
            }
            Transport(session, ui, Modifier.align(Alignment.Center))
            BottomCluster(session, ui, actions, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Never mirrored (decision D6): back stays left and forward stays right in every language. */
@Composable
private fun Transport(session: PlayerSession, ui: PlayerUiState, modifier: Modifier) {
    val playing = session.state.playWhenReady
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(TransportGap), verticalAlignment = Alignment.CenterVertically) {
            TransportButton(Icons.Rounded.Replay10, R.string.player_back_10, SkipButtonSize, SkipIconSize) {
                session.seekBy(-SEEK_BUTTON_MS)
                ui.touch()
            }
            TransportButton(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) R.string.player_pause else R.string.player_play,
                PlayButtonSize,
                PlayIconSize,
            ) {
                session.togglePlay()
                ui.touch()
            }
            TransportButton(Icons.Rounded.Forward10, R.string.player_forward_10, SkipButtonSize, SkipIconSize) {
                session.seekBy(SEEK_BUTTON_MS)
                ui.touch()
            }
        }
    }
}

private const val SEEK_BUTTON_MS = 10_000L

@Composable
private fun TransportButton(icon: ImageVector, @StringRes label: Int, size: Dp, iconSize: Dp, onClick: () -> Unit) {
    PressableGlass(onClick = onClick, modifier = Modifier.size(size)) {
        GlassSurface(GlassKind.Control, CircleShape, blur = false) {
            Icon(icon, stringResource(label), Modifier.align(Alignment.Center).size(iconSize), tint = FrameColors.OnMedia)
        }
    }
}

@Composable
private fun BottomCluster(session: PlayerSession, ui: PlayerUiState, actions: PlayerActions, modifier: Modifier) {
    val state = session.state
    val item = state.item
    Column(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val root = coordinates.findRootCoordinates()
                ui.chromeHeight = (root.size.height - coordinates.boundsInRoot().top).roundToInt()
            },
    ) {
        if (item != null) {
            Text(
                text = bidiIsolate(item.title),
                style = FrameType.Title,
                color = FrameColors.OnMedia,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.let { line ->
                Text(
                    text = bidiIsolate(line),
                    style = FrameType.Label,
                    color = FrameColors.OnMedia,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SeekBar(session, ui)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= WideRowWidth) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconRow(session, ui, actions)
                    Spacer(Modifier.weight(1f))
                    TimePill(state, ui)
                }
            } else {
                // A portrait video has no room for both on one line.
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8)) {
                    TimePill(state, ui, Modifier.align(Alignment.End))
                    IconRow(session, ui, actions, Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun IconRow(session: PlayerSession, ui: PlayerUiState, actions: PlayerActions, modifier: Modifier = Modifier) {
    val item = session.state.item
    GlassSurface(GlassKind.Control, FrameShapes.Chip, modifier = modifier.blurRegion(ui, "icons"), blur = false) {
        Row(Modifier.padding(horizontal = FrameSpacing.S4)) {
            RowIcon(Icons.Rounded.ClosedCaption, R.string.player_subtitles) { ui.openSheet(PlayerSheet.SUBTITLES) }
            RowIcon(Icons.Rounded.Speaker, R.string.player_audio) { ui.openSheet(PlayerSheet.AUDIO) }
            RowIcon(Icons.Rounded.Speed, R.string.player_speed) { ui.openSheet(PlayerSheet.SPEED) }
            RowIcon(Icons.Rounded.AspectRatio, R.string.player_fit) {
                actions.cycleAspect()
                ui.touch()
            }
            if (item?.titleId != null && item.season != null) {
                RowIcon(Icons.Rounded.VideoLibrary, R.string.player_episodes) { ui.openSheet(PlayerSheet.EPISODES) }
            }
            RowIcon(Icons.Rounded.MoreHoriz, R.string.player_more) { ui.openSheet(PlayerSheet.MORE) }
        }
    }
}

/** Reports a cluster to [VideoBlurHook]. It does nothing until Gate 0 step 0.7 turns the hook on. */
private fun Modifier.blurRegion(ui: PlayerUiState, key: String): Modifier =
    if (VideoBlurHook.ENABLED) onGloballyPositioned { ui.blurRects[key] = it.boundsInWindow() } else this

/** Icon only, as image 06, with the name on long press (decision D8). */
@Composable
private fun RowIcon(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val text = stringResource(label)
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) {
        if (hint) {
            delay(HintMillis)
            hint = false
        }
    }
    Box(contentAlignment = Alignment.Center) {
        PressableGlass(
            onClick = onClick,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                hint = true
            },
            modifier = Modifier.size(RowIconSize),
        ) {
            // PressableGlass passes its size down, so the icon needs a box of its own to stay small.
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, text, Modifier.size(IconSize), tint = FrameColors.OnMedia)
            }
        }
        if (hint) {
            GlassSurface(
                GlassKind.Control,
                FrameShapes.Chip,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .wrapContentWidth(unbounded = true)
                    .offset(y = -RowIconSize),
                blur = false,
            ) {
                Text(
                    text = bidiIsolate(text),
                    style = FrameType.Label,
                    color = FrameColors.OnMedia,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = FrameSpacing.S12, vertical = FrameSpacing.S4),
                )
            }
        }
    }
}

@Composable
private fun TimePill(state: PlayerState, ui: PlayerUiState, modifier: Modifier = Modifier) {
    val position = ui.scrub?.positionMs ?: state.positionMs
    GlassSurface(GlassKind.Control, FrameShapes.Chip, modifier = modifier.blurRegion(ui, "time"), blur = false) {
        Text(
            text = bidiIsolate("${formatTime(position)} / ${formatTime(state.durationMs)}"),
            style = FrameType.Label,
            color = FrameColors.OnMedia,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = FrameSpacing.S12, vertical = FrameSpacing.S8),
        )
    }
}

/** Time runs left to right in every language (decision D6), so all of this uses raw coordinates. */
@Composable
private fun SeekBar(session: PlayerSession, ui: PlayerUiState) {
    val state = session.state
    Box(
        Modifier
            .fillMaxWidth()
            .height(SeekTouchHeight)
            .systemGestureExclusion()
            .onGloballyPositioned { ui.seekBarBounds = it.boundsInRoot() }
            .pointerInput(session, ui) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val duration = state.durationMs
                    if (duration <= 0L) return@awaitEachGesture
                    down.consume()
                    fun positionAt(x: Float) = ((x / size.width).coerceIn(0f, 1f) * duration).toLong()
                    var position = positionAt(down.position.x)
                    ui.scrub = Scrub(position, fromGesture = false)
                    session.previews.request(position)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        position = positionAt(change.position.x)
                        ui.scrub = Scrub(position, fromGesture = false)
                        session.previews.request(position)
                    }
                    session.seekTo(position)
                    ui.scrub = null
                    session.previews.endScrub()
                    ui.touch()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SeekRail(state, ui)
    }
}

/** Reads the position while drawing, so the 4 Hz updates redraw this line and recompose nothing. */
@Composable
private fun SeekRail(state: PlayerState, ui: PlayerUiState) {
    val thickness by animateDpAsState(if (ui.scrub != null) RailThick else RailThin, label = "rail")
    Canvas(Modifier.fillMaxWidth().height(RailThick)) {
        val duration = state.durationMs.coerceAtLeast(1L).toFloat()
        val played = ((ui.scrub?.positionMs ?: state.positionMs) / duration).coerceIn(0f, 1f)
        val buffered = (state.bufferedMs / duration).coerceIn(played, 1f)
        val height = thickness.toPx()
        val top = (size.height - height) / 2f
        val radius = CornerRadius(height / 2f)
        fun bar(fraction: Float, alpha: Float) = drawRoundRect(
            color = FrameColors.OnMedia.copy(alpha = alpha),
            topLeft = Offset(0f, top),
            size = Size(size.width * fraction, height),
            cornerRadius = radius,
        )
        bar(1f, EmptyTrack)
        bar(buffered, BufferedTrack)
        bar(played, PlayedTrack)
    }
}

/** Shows the preview, and a bare bar and time when a swipe seeks with the controls hidden (image 09). */
@Composable
private fun ScrubLayer(session: PlayerSession, ui: PlayerUiState, chromeShown: Boolean) {
    val scrub = ui.scrub ?: return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ScrubOverlay(session, ui, scrub, chromeShown)
    }
}

@Composable
private fun ScrubOverlay(session: PlayerSession, ui: PlayerUiState, scrub: Scrub, chromeShown: Boolean) {
    val state = session.state
    Box(Modifier.fillMaxSize()) {
        if (!chromeShown) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = EdgeGutter, vertical = FrameSpacing.S16),
                horizontalAlignment = Alignment.End,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(SeekTouchHeight)
                        .onGloballyPositioned { ui.seekBarBounds = it.boundsInRoot() },
                    contentAlignment = Alignment.Center,
                ) {
                    SeekRail(state, ui)
                }
                TimePill(state, ui)
            }
        }
        ui.seekBarBounds?.let { bar -> PreviewCard(session, scrub, bar) }
    }
}

@Composable
private fun PreviewCard(session: PlayerSession, scrub: Scrub, bar: Rect) {
    val density = LocalDensity.current
    val duration = session.state.durationMs.coerceAtLeast(1L).toFloat()
    val image = session.previews.image
    val width = with(density) { PreviewWidth.toPx() }
    val height = with(density) { PreviewHeight.toPx() }
    val gap = with(density) { FrameSpacing.S8.toPx() }
    val centre = bar.left + bar.width * (scrub.positionMs / duration).coerceIn(0f, 1f)
    val x = (centre - width / 2f).coerceIn(bar.left, max(bar.left, bar.right - width))
    val y = bar.top - height - gap
    GlassSurface(
        GlassKind.Control,
        PreviewShape,
        modifier = Modifier
            .absoluteOffset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(PreviewWidth, PreviewHeight),
        blur = false,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    clip = true
                    shape = PreviewShape
                },
            )
        }
        Text(
            text = bidiIsolate(formatTime(scrub.positionMs)),
            style = FrameType.Label,
            color = FrameColors.OnMedia,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, ScrimColor)))
                .padding(vertical = FrameSpacing.S4),
        )
    }
}

/** Physical sides on purpose: the skip marks follow the fingers and time, not the language. */
@Composable
private fun FeedbackLayer(state: PlayerState, ui: PlayerUiState) {
    val feedback = ui.feedback
    LaunchedEffect(feedback) {
        if (feedback != null && feedback !is Feedback.Seek) {
            delay(if ((feedback as? Feedback.Message)?.longer == true) LongFeedbackMillis else FeedbackMillis)
            ui.feedback = null
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = FeedbackInset)) {
            when (feedback) {
                is Feedback.Skip -> SkipMark(feedback.seconds, feedback.forward, Modifier.align(sideOf(feedback.forward)))
                is Feedback.Seek -> SeekMark(feedback.deltaMs, Modifier.align(sideOf(feedback.deltaMs >= 0L)))
                is Feedback.Brightness -> LevelMark(Icons.Rounded.BrightnessMedium, feedback.level, Modifier.align(Alignment.CenterStart))
                is Feedback.Volume -> LevelMark(Icons.AutoMirrored.Rounded.VolumeUp, feedback.level, Modifier.align(Alignment.CenterEnd))
                is Feedback.Zoom -> Chip(stringResource(R.string.player_times, formatSpeed(feedback.zoom)), Modifier.align(Alignment.TopCenter))
                is Feedback.Message -> Chip(feedback.text, Modifier.align(Alignment.TopCenter), if (feedback.longer) LongFeedbackLines else 1)
                null -> Unit
            }
            if (state.heldSpeed) {
                Chip(stringResource(R.string.player_times, formatSpeed(HELD_SPEED)), Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

private const val HELD_SPEED = 2f

private fun sideOf(forward: Boolean): Alignment = if (forward) Alignment.CenterEnd else Alignment.CenterStart

@Composable
private fun SkipMark(seconds: Int, forward: Boolean, modifier: Modifier) {
    val text = stringResource(if (forward) R.string.player_skip_forward else R.string.player_skip_back, seconds)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8)) {
        if (!forward) Icon(Icons.Rounded.ChevronLeft, null, Modifier.size(SkipIconSize), tint = FrameColors.OnMedia)
        Text(bidiIsolate(text), style = FrameType.TitleLarge, color = FrameColors.OnMedia)
        if (forward) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(SkipIconSize), tint = FrameColors.OnMedia)
    }
}

@Composable
private fun SeekMark(deltaMs: Long, modifier: Modifier) {
    val forward = deltaMs >= 0L
    val text = stringResource(
        if (forward) R.string.player_seek_forward else R.string.player_seek_back,
        formatTime(abs(deltaMs)),
    )
    Text(bidiIsolate(text), style = FrameType.TitleLarge, color = FrameColors.OnMedia, modifier = modifier)
}

@Composable
private fun LevelMark(icon: ImageVector, level: Float, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8)) {
        Canvas(Modifier.size(LevelBarWidth, LevelBarHeight)) {
            val radius = CornerRadius(size.width / 2f)
            drawRoundRect(FrameColors.OnMedia.copy(alpha = EmptyTrack), cornerRadius = radius)
            val filled = size.height * level.coerceIn(0f, 1f)
            drawRoundRect(
                FrameColors.OnMedia,
                topLeft = Offset(0f, size.height - filled),
                size = Size(size.width, filled),
                cornerRadius = radius,
            )
        }
        Icon(icon, null, Modifier.size(IconSize), tint = FrameColors.OnMedia)
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier, maxLines: Int = 1) {
    GlassSurface(GlassKind.Control, FrameShapes.Chip, modifier = modifier, blur = false) {
        Text(
            text = bidiIsolate(text),
            style = FrameType.Label,
            color = FrameColors.OnMedia,
            maxLines = maxLines,
            modifier = Modifier.padding(horizontal = FrameSpacing.S16, vertical = FrameSpacing.S8),
        )
    }
}

@Composable
private fun UnlockButton(ui: PlayerUiState, actions: PlayerActions) {
    LaunchedEffect(ui.unlockVisible) {
        if (ui.unlockVisible) {
            delay(UnlockMillis)
            ui.unlockVisible = false
        }
    }
    if (!ui.unlockVisible) return
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = EdgeGutter, vertical = FrameSpacing.S12),
    ) {
        GlassIconButton(
            Icons.Rounded.LockOpen,
            stringResource(R.string.player_unlock),
            { actions.lock(false) },
            Modifier.align(Alignment.TopEnd),
        )
    }
}

/** Only for a wait the viewer can notice, so a quick seek shows nothing. */
@Composable
private fun BufferingMark(state: PlayerState, ui: PlayerUiState) {
    var visible by remember { mutableStateOf(false) }
    val waiting = state.buffering && ui.scrub == null
    LaunchedEffect(waiting) {
        if (waiting) delay(BufferingDelayMillis)
        visible = waiting
    }
    if (visible) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(FrameSpacing.S32), color = FrameColors.OnMedia, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun SkipAndNext(session: PlayerSession, ui: PlayerUiState, chromeShown: Boolean) {
    val state = session.state
    val density = LocalDensity.current
    val next = state.next
    val segment = state.segment
    val lift by animateDpAsState(with(density) { if (chromeShown) ui.chromeHeight.toDp() else 0.dp }, label = "skipLift")
    val skipLabel = when (segment?.kind) {
        null -> null
        SegmentKind.OP -> R.string.player_skip_intro
        SegmentKind.RECAP -> R.string.player_skip_recap
        SegmentKind.ED -> R.string.player_skip_credits.takeIf { state.item?.next == null }
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = EdgeGutter, vertical = FrameSpacing.S16),
    ) {
        Box(Modifier.align(Alignment.BottomEnd).padding(bottom = lift)) {
            AnimatedVisibility(visible = next != null, enter = fadeIn(), exit = fadeOut()) {
                if (next != null) NextEpisodeCard(session, next)
            }
            AnimatedVisibility(visible = next == null && skipLabel != null, enter = fadeIn(), exit = fadeOut()) {
                if (skipLabel != null) WhitePill(stringResource(skipLabel), session::skipSegment)
            }
        }
    }
}

@Composable
private fun NextEpisodeCard(session: PlayerSession, card: NextCard) {
    GlassSurface(GlassKind.Control, FrameShapes.Card, modifier = Modifier.widthIn(max = NextCardWidth), blur = false) {
        Column(
            Modifier.padding(FrameSpacing.S16),
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
        ) {
            val seconds = card.seconds
            Text(
                text = if (seconds != null) {
                    pluralStringResource(R.plurals.player_next_in, seconds, seconds)
                } else {
                    stringResource(R.string.player_next_episode)
                },
                style = FrameType.Label,
                color = FrameColors.OnMedia,
            )
            card.label?.let { label ->
                Text(
                    text = bidiIsolate(label),
                    style = FrameType.Body,
                    color = FrameColors.OnMedia,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8)) {
                WhitePill(stringResource(R.string.player_play_now), session::playNext)
                OutlinePill(stringResource(R.string.player_cancel), session::cancelNext)
            }
        }
    }
}

/** F-P2 and F-P18: a plain reason, never a blank screen, and only here another player is offered. */
@Composable
private fun FailureCard(failure: PlayerFailure, state: PlayerState, actions: PlayerActions) {
    val message = when (failure.kind) {
        FailureKind.MISSING -> stringResource(R.string.player_error_missing)
        FailureKind.DAMAGED -> stringResource(R.string.player_error_damaged)
        FailureKind.INCOMPLETE -> stringResource(R.string.player_error_incomplete)
        FailureKind.OTHER -> stringResource(R.string.player_error_other)
        FailureKind.UNSUPPORTED -> failure.codec?.let { stringResource(R.string.player_error_codec, bidiIsolate(it)) }
            ?: stringResource(R.string.player_error_unsupported)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(ScrimColor)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(FrameSpacing.S24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(FrameSpacing.S48), tint = FrameColors.OnMedia)
            Text(message, style = FrameType.Body, color = FrameColors.OnMedia, textAlign = TextAlign.Center)
            // A private file never leaves the app.
            if (state.item?.isPrivate == false) WhitePill(stringResource(R.string.player_open_elsewhere), actions::openElsewhere)
            OutlinePill(stringResource(R.string.player_close), actions::exit)
        }
    }
}
