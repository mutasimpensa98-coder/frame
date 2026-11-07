package com.frameglass.player.media

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.frameglass.data.api.DeletePlan
import com.frameglass.data.api.DeleteResult
import com.frameglass.data.api.answerTrashConsent
import com.frameglass.data.api.frameServices
import com.frameglass.data.api.trashConsents
import com.frameglass.glass.ActivityLine
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.Skeleton
import com.frameglass.glass.WhitePill
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.model.DeleteFlow
import com.frameglass.player.model.DeleteStage
import com.frameglass.player.ui.DangerPill
import com.frameglass.player.ui.fileSizeLabel
import com.frameglass.player.ui.localNumber
import com.frameglass.player.ui.metaLine
import com.frameglass.player.ui.rememberUiModel

private const val PlanSkeletonRatio = 3f

/**
 * The one confirmation before any delete. Android's own trash request runs from here too: without a collector the
 * files it covers stay on storage, so this stays composed for the life of the activity.
 */
@Composable
internal fun DeleteSheet() {
    val flow = rememberUiModel().deletes
    val media = LocalContext.current.frameServices.media
    val sheet = rememberGlassSheetState()
    val stage = flow.stage

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        media.answerTrashConsent(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(media) {
        media.trashConsents.collect { sender -> consent.launch(IntentSenderRequest.Builder(sender).build()) }
    }
    LaunchedEffect(stage) {
        if (stage != null) sheet.open() else sheet.close()
    }
    // Closed by a drag, the scrim or back. A running delete comes back with its message.
    LaunchedEffect(sheet.isOpen) {
        if (!sheet.isOpen) flow.dismiss()
    }

    // The sheet is still sliding away when the stage clears, so it keeps showing the last one.
    var last by remember { mutableStateOf(stage) }
    if (stage != null) last = stage
    val shownStage = last

    GlassSheet(sheet, title = sheetTitle(shownStage)) {
        when (shownStage) {
            null -> Unit
            is DeleteStage.Planning -> {
                val shown = shownStage.shown
                if (shown == null) {
                    Skeleton(Modifier.fillMaxWidth().aspectRatio(PlanSkeletonRatio), FrameShapes.Poster)
                } else {
                    PlanBody(shown, flow, confirmable = false)
                }
            }
            is DeleteStage.Ready -> PlanBody(shownStage.plan, flow, confirmable = true)
            is DeleteStage.Running -> {
                Text(
                    text = stringResource(R.string.app_delete_running),
                    style = FrameType.Body,
                    color = FrameColors.SecondaryText,
                    modifier = Modifier.padding(horizontal = FrameSpacing.S12),
                )
                ActivityLine(Modifier.fillMaxWidth().padding(horizontal = FrameSpacing.S12))
            }
            is DeleteStage.Finished -> ResultBody(shownStage.result, flow)
        }
    }
}

@Composable
private fun sheetTitle(stage: DeleteStage?): String? {
    val plan = when (stage) {
        is DeleteStage.Planning -> stage.shown
        is DeleteStage.Ready -> stage.plan
        is DeleteStage.Running -> stage.plan
        else -> null
    }
    return when {
        stage is DeleteStage.Finished -> stringResource(R.string.app_delete_done_title)
        plan != null -> pluralStringResource(R.plurals.app_delete_title, plan.fileCount, plan.fileCount)
        else -> stringResource(R.string.app_delete_title_plain)
    }
}

@Composable
private fun PlanBody(plan: DeletePlan, flow: DeleteFlow, confirmable: Boolean) {
    Column(
        modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
    ) {
        plan.sampleNames.forEach { name ->
            Text(
                text = bidiIsolate(name),
                style = FrameType.Body,
                color = FrameColors.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val more = plan.fileCount - plan.sampleNames.size
        if (more > 0) {
            Text(
                text = pluralStringResource(R.plurals.app_delete_more, more, more),
                style = FrameType.Label,
                color = FrameColors.SecondaryText,
            )
        }
    }
    val bytes = plan.totalBytes + if (plan.includeSidecars) plan.sidecarBytes else 0
    Column(
        modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
    ) {
        PlanFact(stringResource(R.string.app_delete_files), localNumber(plan.fileCount))
        PlanFact(stringResource(R.string.app_delete_size), fileSizeLabel(bytes))
    }
    Recovery(plan)
    if (plan.sidecarCount > 0) {
        Row(
            modifier = Modifier.padding(horizontal = FrameSpacing.S12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_delete_sidecars),
                    style = FrameType.Body,
                    color = FrameColors.White,
                )
                Text(
                    text = metaLine(
                        listOf(
                            pluralStringResource(R.plurals.app_delete_sidecars_hint, plan.sidecarCount, plan.sidecarCount),
                            fileSizeLabel(plan.sidecarBytes),
                        ),
                    ),
                    style = FrameType.Caption,
                    color = FrameColors.SecondaryText,
                )
            }
            Switch(checked = plan.includeSidecars, onCheckedChange = flow::includeSidecars)
        }
    }
    Text(
        text = stringResource(R.string.app_delete_keeps),
        style = FrameType.Caption,
        color = FrameColors.SecondaryText,
        modifier = Modifier.padding(horizontal = FrameSpacing.S12),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = FrameSpacing.S8),
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
    ) {
        OutlinePill(
            text = stringResource(R.string.app_cancel),
            onClick = flow::dismiss,
            modifier = Modifier.weight(1f),
        )
        DangerPill(
            text = stringResource(R.string.app_menu_delete),
            onClick = flow::confirm,
            modifier = Modifier.weight(1f),
            enabled = confirmable && plan.entries.isNotEmpty(),
        )
    }
}

@Composable
private fun PlanFact(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12)) {
        Text(
            text = label,
            style = FrameType.Label,
            color = FrameColors.SecondaryText,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = bidiIsolate(value),
            style = FrameType.Label.copy(fontWeight = FontWeight.W600),
            color = FrameColors.White,
            textAlign = TextAlign.End,
        )
    }
}

/** Says plainly whether each file can come back. Space held by the trash is not called freed. */
@Composable
private fun Recovery(plan: DeletePlan) {
    Column(
        modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
    ) {
        if (plan.recoverableCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.app_delete_to_trash, plan.recoverableCount, plan.recoverableCount),
                style = FrameType.Body,
                color = FrameColors.White,
            )
            Text(
                text = stringResource(R.string.app_delete_trash_space),
                style = FrameType.Caption,
                color = FrameColors.SecondaryText,
            )
        }
        if (plan.permanentCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.app_delete_gone, plan.permanentCount, plan.permanentCount),
                style = FrameType.Body.copy(fontWeight = FontWeight.W600),
                color = FrameColors.Error,
            )
        }
    }
}

@Composable
private fun ResultBody(result: DeleteResult, flow: DeleteFlow) {
    Column(
        modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
    ) {
        if (result.trashed > 0) {
            ResultLine(pluralStringResource(R.plurals.app_delete_done_trash, result.trashed, result.trashed))
        }
        if (result.deleted > 0) {
            ResultLine(pluralStringResource(R.plurals.app_delete_done_gone, result.deleted, result.deleted))
        }
        if (result.freedBytes > 0) {
            ResultLine(stringResource(R.string.app_delete_done_freed, fileSizeLabel(result.freedBytes)))
        }
        if (result.trashed == 0 && result.deleted == 0 && result.failed.isEmpty()) {
            ResultLine(stringResource(R.string.app_delete_done_nothing))
        }
        if (result.failed.isNotEmpty()) {
            Text(
                text = pluralStringResource(R.plurals.app_delete_done_failed, result.failed.size, result.failed.size),
                style = FrameType.Body.copy(fontWeight = FontWeight.W600),
                color = FrameColors.Error,
            )
            result.failed.take(MaxFailedNames).forEach { name ->
                Text(
                    text = bidiIsolate(name),
                    style = FrameType.Caption,
                    color = FrameColors.SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    WhitePill(
        text = stringResource(R.string.app_ok),
        onClick = flow::dismiss,
        modifier = Modifier.fillMaxWidth().padding(top = FrameSpacing.S8),
    )
}

@Composable
private fun ResultLine(text: String) {
    Text(text, style = FrameType.Body, color = FrameColors.White)
}

private const val MaxFailedNames = 5
