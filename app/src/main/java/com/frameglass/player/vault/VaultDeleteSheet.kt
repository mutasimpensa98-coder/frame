package com.frameglass.player.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.frameglass.data.api.VaultDeletePlan
import com.frameglass.data.api.VaultDeleteResult
import com.frameglass.glass.ActivityLine
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.Skeleton
import com.frameglass.glass.WhitePill
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.media.PlanFact
import com.frameglass.player.model.VaultDeleteFlow
import com.frameglass.player.model.VaultDeleteStage
import com.frameglass.player.model.VaultSheetFace
import com.frameglass.player.model.VaultSheetRules
import com.frameglass.player.ui.DangerPill
import com.frameglass.player.ui.fileSizeLabel
import com.frameglass.player.ui.localNumber
import com.frameglass.player.ui.rememberUiModel

private const val MaxNames = 3
private const val PlanSkeletonRatio = 3.2f

/**
 * The confirmation every delete inside the private folder goes through. It says plainly that the delete is for
 * good, names the video when it is one, counts them and their size, and its primary button is Cancel. Closing it
 * any other way cancels too.
 */
@Composable
internal fun VaultDeleteSheet() {
    val flow = rememberUiModel().vaultDeletes
    val sheet = rememberGlassSheetState()
    val stage = flow.stage
    // What the flow last heard from the folder, which it hears even while the app is off the screen.
    val unlocked = flow.unlocked

    LaunchedEffect(stage) {
        if (stage != null) sheet.open() else sheet.close()
    }
    // Closed by a drag, the scrim or back: that is a cancel. A running delete comes back with its message.
    LaunchedEffect(sheet.isOpen) {
        if (!sheet.isOpen) flow.dismiss()
    }

    // The sheet is still sliding away when the stage clears, so it keeps showing the last one. What it may show of
    // it is decided again on every frame: once the folder locks, a plan and its names are not drawn at all, not
    // even while the sheet slides away over the lock screen.
    var last by remember { mutableStateOf(stage) }
    if (stage != null) last = stage
    val shown = last
    val face = VaultSheetRules.face(shown, unlocked)

    GlassSheet(sheet, title = sheetTitle(shown, face)) {
        when (face) {
            VaultSheetFace.NOTHING -> Unit
            VaultSheetFace.PLANNING -> Skeleton(Modifier.fillMaxWidth().aspectRatio(PlanSkeletonRatio), FrameShapes.Poster)
            VaultSheetFace.PLAN -> (shown as? VaultDeleteStage.Ready)?.let { PlanBody(it.plan, flow) }
            VaultSheetFace.RUNNING, VaultSheetFace.RUNNING_QUIET -> {
                Text(
                    text = stringResource(R.string.app_delete_running),
                    style = FrameType.Body,
                    color = FrameTheme.colors.secondaryContent,
                    modifier = Modifier.padding(horizontal = FrameSpacing.S12),
                )
                ActivityLine(Modifier.fillMaxWidth().padding(horizontal = FrameSpacing.S12))
            }
            VaultSheetFace.RESULT -> ResultBody((shown as? VaultDeleteStage.Finished)?.result, quiet = false, flow)
            VaultSheetFace.RESULT_QUIET -> ResultBody(null, quiet = true, flow)
        }
    }
}

/** The count is in the title only while the folder is open. */
@Composable
private fun sheetTitle(stage: VaultDeleteStage?, face: VaultSheetFace): String? = when (face) {
    VaultSheetFace.NOTHING -> null
    VaultSheetFace.PLANNING, VaultSheetFace.RUNNING_QUIET -> stringResource(R.string.app_private_delete)
    VaultSheetFace.PLAN, VaultSheetFace.RUNNING -> {
        val count = when (stage) {
            is VaultDeleteStage.Ready -> stage.plan.fileCount
            is VaultDeleteStage.Running -> stage.plan.fileCount
            else -> 0
        }
        pluralStringResource(R.plurals.app_private_delete_title, count, count)
    }
    VaultSheetFace.RESULT, VaultSheetFace.RESULT_QUIET -> stringResource(R.string.app_delete_done_title)
}

@Composable
private fun PlanBody(plan: VaultDeletePlan, flow: VaultDeleteFlow) {
    val colors = FrameTheme.colors
    Column(
        modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
    ) {
        // One video is named in full. Several show the first few, and the count says the rest.
        plan.names.filter { it.isNotBlank() }.take(MaxNames).forEach { name ->
            Text(
                text = bidiIsolate(name),
                style = FrameType.Body.copy(fontWeight = FontWeight.W600),
                color = colors.content,
                maxLines = if (plan.names.size == 1) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val more = plan.names.size - MaxNames
        if (more > 0) {
            Text(
                text = pluralStringResource(R.plurals.app_delete_more, more, more),
                style = FrameType.Label,
                color = colors.secondaryContent,
            )
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
    ) {
        PlanFact(stringResource(R.string.app_delete_files), localNumber(plan.fileCount))
        if (plan.subtitleCount > 0) {
            PlanFact(stringResource(R.string.app_private_delete_subtitles), localNumber(plan.subtitleCount))
        }
        PlanFact(stringResource(R.string.app_delete_size), fileSizeLabel(plan.totalBytes))
    }
    Column(
        modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
    ) {
        Text(
            text = stringResource(R.string.app_private_delete_warning),
            style = FrameType.Body.copy(fontWeight = FontWeight.W600),
            color = colors.error,
        )
        Text(
            text = stringResource(R.string.app_private_delete_hint),
            style = FrameType.Caption,
            color = colors.secondaryContent,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = FrameSpacing.S8),
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
    ) {
        // Cancel is the strong button: the safe answer is the one the eye and the thumb find first.
        WhitePill(
            text = stringResource(R.string.app_cancel),
            onClick = flow::dismiss,
            modifier = Modifier.weight(1f),
        )
        DangerPill(
            text = stringResource(R.string.app_private_delete),
            onClick = flow::confirm,
            modifier = Modifier.weight(1f),
        )
    }
}

/** With [quiet], after the folder locked, the sheet says the delete ended and gives no number. */
@Composable
private fun ResultBody(result: VaultDeleteResult?, quiet: Boolean, flow: VaultDeleteFlow) {
    val colors = FrameTheme.colors
    Column(
        modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
    ) {
        when {
            quiet -> Unit
            result == null -> Text(
                text = stringResource(R.string.app_private_delete_error),
                style = FrameType.Body.copy(fontWeight = FontWeight.W600),
                color = colors.error,
            )
            result.deleted == 0 && result.failed == 0 -> Text(
                text = stringResource(R.string.app_delete_done_nothing),
                style = FrameType.Body,
                color = colors.content,
            )
            else -> {
                if (result.deleted > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.app_delete_done_gone, result.deleted, result.deleted),
                        style = FrameType.Body,
                        color = colors.content,
                    )
                }
                if (result.freedBytes > 0) {
                    Text(
                        text = stringResource(R.string.app_delete_done_freed, fileSizeLabel(result.freedBytes)),
                        style = FrameType.Body,
                        color = colors.content,
                    )
                }
                if (result.failed > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.app_delete_done_failed, result.failed, result.failed),
                        style = FrameType.Body.copy(fontWeight = FontWeight.W600),
                        color = colors.error,
                    )
                }
            }
        }
    }
    WhitePill(
        text = stringResource(R.string.app_ok),
        onClick = flow::dismiss,
        modifier = Modifier.fillMaxWidth().padding(top = FrameSpacing.S8),
    )
}
