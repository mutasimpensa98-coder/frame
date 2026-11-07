package com.frameglass.playerui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.frameglass.glass.EffectsLevel
import com.frameglass.glass.FrameAccent
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameThemeMode
import com.frameglass.glass.FrameType
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.rememberEffectsLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val LANDSCAPE_SHARE = 0.58f
private const val PORTRAIT_SHARE = 0.85f
private const val BACK_SHRINK = 0.06f
private val SheetMaxWidth = 560.dp
private val EnterDistance = 48.dp

/** The player stays dark in every theme, and white is its one accent, whatever the app around it uses. */
@Composable
internal fun PlayerTheme(content: @Composable () -> Unit) {
    FrameTheme(mode = FrameThemeMode.Dark, accent = FrameAccent.White, content = content)
}

/** Text on a sheet rests on the scrim alone, so these two numbers carry the contrast rule. See SheetLookTest. */
internal object SheetLook {
    const val SCRIM_ALPHA = 0.78f

    /** What GlassRow paints on the dark palette, and what the steppers match. */
    const val ROW_FILL_ALPHA = 0.06f
}

private val Scrim = Color.Black.copy(alpha = SheetLook.SCRIM_ALPHA)

/**
 * Image 08: no panel, just the video dimmed behind a large title and rounded rows. A SurfaceView gives blur
 * nothing to sample, so GlassSheet would have to go solid here. From the start side in landscape, the bottom in portrait.
 */
@Composable
internal fun PlayerSheetFrame(
    open: Boolean,
    title: String?,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f).apply { updateBounds(lowerBound = 0f) } }
    val back = remember { Animatable(0f) }
    val close by rememberUpdatedState(onClose)
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(open) {
        if (open) {
            visible = true
            back.snapTo(0f)
            progress.animateTo(1f, FrameMotion.defaultSpatial())
        } else {
            progress.animateTo(0f, FrameMotion.defaultSpatial())
            visible = false
        }
    }
    PredictiveBackHandler(enabled = open) { events ->
        try {
            events.collect { back.snapTo(it.progress) }
            close()
        } catch (cancelled: CancellationException) {
            scope.launch { back.animateTo(0f, FrameMotion.effects()) }
            throw cancelled
        }
    }
    if (!visible) return

    val reduced = rememberEffectsLevel() == EffectsLevel.Degraded
    val enterDistance = with(LocalDensity.current) { EnterDistance.toPx() }
    val closeLabel = stringResource(R.string.player_close)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(Scrim)
                .pointerInput(Unit) { detectTapGestures { close() } }
                .semantics {
                    onClick(closeLabel) {
                        close()
                        true
                    }
                },
        )
        Column(
            Modifier
                .align(if (landscape) Alignment.TopStart else Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        if (landscape) {
                            WindowInsetsSides.Start + WindowInsetsSides.Vertical
                        } else {
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        },
                    ),
                )
                .then(
                    if (landscape) {
                        Modifier.width(min(maxWidth * LANDSCAPE_SHARE, SheetMaxWidth))
                    } else {
                        Modifier.fillMaxWidth().heightIn(max = maxHeight * PORTRAIT_SHARE)
                    },
                )
                .graphicsLayer {
                    val opened = progress.value
                    val shrink = 1f - back.value * BACK_SHRINK
                    alpha = (opened * 3f).coerceIn(0f, 1f)
                    scaleX = shrink
                    scaleY = shrink
                    translationY = if (reduced) 0f else (1f - opened) * enterDistance
                }
                // Keeps a tap between two rows from closing the sheet.
                .pointerInput(Unit) { }
                .semantics { title?.let { paneTitle = it } },
        ) {
            if (title != null) {
                Text(
                    text = bidiIsolate(title),
                    style = FrameType.TitleLarge,
                    color = FrameColors.OnMedia,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = FrameSpacing.S24,
                        end = FrameSpacing.S24,
                        top = FrameSpacing.S24,
                        bottom = FrameSpacing.S12,
                    ),
                )
            }
            // A child sheet starts at its own top, wherever its parent was scrolled to.
            val scroll = key(title) { rememberScrollState() }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scroll)
                    .padding(start = FrameSpacing.S24, end = FrameSpacing.S24, bottom = FrameSpacing.S16),
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
                content = content,
            )
        }
    }
}
