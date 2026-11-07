package com.frameglass.player.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.VaultProgress
import com.frameglass.data.api.frameServices
import com.frameglass.glass.ActivityLine
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.ProgressLine
import com.frameglass.glass.WhitePill
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.model.MoveOutcome
import com.frameglass.player.ui.metaLine
import com.frameglass.player.ui.rememberUiModel
import kotlin.math.roundToInt

private const val Percent = 100

/** The file being moved, how far along, and how many are left. */
@Composable
internal fun MoveProgress(progress: VaultProgress, modifier: Modifier = Modifier) {
    val fraction = if (progress.totalBytes > 0) {
        (progress.doneBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4)) {
        Text(
            text = bidiIsolate(progress.name),
            style = FrameType.Label,
            color = FrameColors.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // ProgressLine always runs left to right for playback (D6). A file copy reads from the start edge.
        ProgressLine(
            progress = fraction,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { if (rtl) scaleX = -1f },
            track = FrameColors.Elevated,
        )
        Text(
            text = metaLine(
                listOf(
                    stringResource(R.string.app_percent, (fraction * Percent).roundToInt()),
                    pluralStringResource(R.plurals.app_move_left, progress.filesLeft, progress.filesLeft),
                ),
            ),
            style = FrameType.Caption,
            color = FrameColors.SecondaryText,
            maxLines = 1,
        )
    }
}

/** A move goes on when the page closes, so its progress and final message live with the whole app. */
@Composable
internal fun MoveSheet() {
    val flow = rememberUiModel().vault
    val sheet = rememberGlassSheetState()
    val running = flow.runningMoves > 0
    val ended = flow.outcome

    LaunchedEffect(running, ended) {
        if (running || ended != null) sheet.open() else sheet.close()
    }
    LaunchedEffect(sheet.isOpen) {
        if (!sheet.isOpen) flow.clearOutcome()
    }

    // The sheet is still sliding away when the outcome clears, so it keeps showing the last one.
    var last by remember { mutableStateOf(ended) }
    if (ended != null) last = ended
    val outcome = if (running) null else last

    val title = when {
        outcome is MoveOutcome.Failed -> R.string.app_move_failed_title
        outcome != null -> R.string.app_move_done_title
        flow.movingOut -> R.string.app_move_out_title
        else -> R.string.app_move_in_title
    }
    GlassSheet(sheet, title = stringResource(title)) {
        Column(
            modifier = Modifier.padding(horizontal = FrameSpacing.S12),
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
        ) {
            when (outcome) {
                null -> {
                    val vault = LocalContext.current.frameServices.vault
                    val progress by remember(vault) { vault.progress }.collectAsStateWithLifecycle(null as VaultProgress?)
                    val now = progress
                    if (now != null) MoveProgress(now) else ActivityLine(Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.app_move_leave_hint),
                        style = FrameType.Caption,
                        color = FrameColors.SecondaryText,
                    )
                }
                is MoveOutcome.Moved -> Text(
                    text = when {
                        outcome.count == 0 -> stringResource(R.string.app_move_nothing)
                        outcome.out -> pluralStringResource(R.plurals.app_moved_out, outcome.count, outcome.count)
                        else -> pluralStringResource(R.plurals.app_moved_in, outcome.count, outcome.count)
                    },
                    style = FrameType.Body,
                    color = FrameColors.White,
                )
                is MoveOutcome.Failed -> Text(
                    text = stringResource(R.string.app_move_failed),
                    style = FrameType.Body,
                    color = FrameColors.White,
                )
            }
        }
        if (outcome != null) {
            WhitePill(
                text = stringResource(R.string.app_ok),
                onClick = flow::clearOutcome,
                modifier = Modifier.fillMaxWidth().padding(top = FrameSpacing.S8),
            )
        }
    }
}
