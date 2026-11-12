package com.frameglass.player.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frameglass.data.api.EpisodeRow
import com.frameglass.data.api.FileVersion
import com.frameglass.data.api.frameServices
import com.frameglass.data.api.versions
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.PressableGlass
import com.frameglass.glass.ProgressLine
import com.frameglass.glass.RatingBadge
import com.frameglass.glass.WideArt
import com.frameglass.glass.bidiIsolate
import com.frameglass.player.R
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.ui.FactChip
import com.frameglass.player.ui.durationLabel
import com.frameglass.player.ui.fileSizeLabel
import com.frameglass.player.ui.toCard

private val StillWidth = 148.dp
private const val DimmedAlpha = 0.42f
/** Strong enough that faces and places cannot be read in a 148 dp still, light enough that it still looks like a picture. */
internal val SpoilerBlur = 14.dp

/**
 * F-U9: a 16:9 still, label, title, duration and progress. The whole row plays. With no still, as for every
 * episode matched through OMDb that has no file, the series [poster] stands in, pillarboxed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EpisodeListRow(row: EpisodeRow, poster: String?, onLongPress: () -> Unit) {
    val navigator = LocalNavigator.current
    val library = LocalContext.current.frameServices.library
    val playable = row.play?.playableKey
    val files by produceState(emptyList<FileVersion>(), playable) {
        value = playable?.let { library.versions(it) }.orEmpty()
    }
    val file = files.firstOrNull { it.isDefault } ?: files.firstOrNull()

    PressableGlass(
        onClick = { row.play?.let(navigator::play) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = FrameSpacing.S16, vertical = FrameSpacing.S4),
        shape = FrameShapes.Poster,
        enabled = row.play != null,
        onLongClick = onLongPress.takeIf { playable != null },
        highlight = false,
    ) {
        Row(
            modifier = if (row.available && !row.missing) Modifier else Modifier.alpha(DimmedAlpha),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(StillWidth).aspectRatio(FrameSizes.LandscapeRatio).clip(FrameShapes.Poster)) {
                WideArt(
                    row.imagePath,
                    Modifier.matchParentSize().then(if (row.hideImage) Modifier.blur(SpoilerBlur) else Modifier),
                    fallback = poster,
                )
                row.progress?.let {
                    ProgressLine(
                        progress = it,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(FrameSpacing.S4),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (row.label.isNotEmpty()) {
                    Text(
                        text = bidiIsolate(row.label),
                        style = FrameType.Caption,
                        color = FrameColors.SecondaryText,
                        maxLines = 1,
                    )
                }
                Text(
                    text = bidiIsolate(row.title),
                    style = FrameType.Body.copy(fontWeight = FontWeight.W600),
                    color = FrameColors.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    row.rating?.let { RatingBadge(it.toCard()) }
                    val minutes = ((row.durationMs + 30_000L) / 60_000L).toInt()
                    if (minutes > 0) {
                        Text(
                            text = durationLabel(minutes),
                            style = FrameType.Caption,
                            color = FrameColors.SecondaryText,
                            maxLines = 1,
                        )
                    }
                    if (file?.isNew == true) FactChip(stringResource(R.string.app_chip_new))
                    if (file?.hasSubtitle == true) FactChip(stringResource(R.string.app_chip_sub))
                    file?.let { FactChip(fileSizeLabel(it.sizeBytes)) }
                    if (files.size > 1) {
                        FactChip(pluralStringResource(R.plurals.app_versions, files.size, files.size))
                    }
                }
                if (row.missing) {
                    Text(
                        text = stringResource(R.string.app_episode_missing),
                        style = FrameType.Caption,
                        color = FrameColors.SecondaryText,
                        maxLines = 1,
                    )
                }
            }
            if (row.watched) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    stringResource(R.string.app_watched),
                    Modifier.size(20.dp),
                    tint = FrameColors.White,
                )
            }
        }
    }
}
