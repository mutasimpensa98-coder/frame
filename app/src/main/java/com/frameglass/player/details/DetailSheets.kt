package com.frameglass.player.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.frameglass.data.api.EpisodeRow
import com.frameglass.data.api.FileVersion
import com.frameglass.data.api.MatchChoice
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.WatchTarget
import com.frameglass.data.api.frameServices
import com.frameglass.data.api.versions
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassRow
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.GlassSheetState
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.PressableGlass
import com.frameglass.glass.Skeleton
import com.frameglass.glass.WhitePill
import com.frameglass.glass.bidiIsolate
import com.frameglass.player.R
import com.frameglass.player.model.Identifier
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.ui.FrameTextField
import com.frameglass.player.ui.fileSizeLabel
import com.frameglass.player.ui.kindLabel
import com.frameglass.player.ui.localNumber
import com.frameglass.player.ui.metaLine
import com.frameglass.player.ui.rememberServiceState
import kotlinx.coroutines.launch

private val ChoicePosterWidth = 64.dp

/**
 * F-M3: search again and apply a choice, which then counts as a manual match. The search goes to TMDB, or to
 * OMDb while TMDB is off, so the empty message names the service that answered.
 */
@Composable
internal fun FixMatchSheet(state: GlassSheetState, titleId: Long, name: String) {
    val library = LocalContext.current.frameServices.library
    val scope = rememberCoroutineScope()
    val services = rememberServiceState()
    GlassSheet(state, title = stringResource(R.string.app_fix_match)) {
        var text by rememberSaveable(titleId) { mutableStateOf(name) }
        var searched by remember { mutableStateOf<String?>(null) }
        val choices by produceState<List<MatchChoice>?>(null, state.isOpen, searched) {
            value = null
            if (state.isOpen) value = library.matchChoices(titleId, searched)
        }
        FrameTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = stringResource(R.string.app_fix_match_hint),
            imeAction = ImeAction.Search,
            onSubmit = { searched = text },
        )
        val found = choices
        when {
            found == null -> repeat(3) {
                Skeleton(Modifier.fillMaxWidth().aspectRatio(5f), FrameShapes.Poster)
            }
            found.isEmpty() -> Text(
                text = stringResource(
                    when (services?.identifier) {
                        Identifier.IMDB -> R.string.app_fix_match_nothing_imdb
                        Identifier.NONE -> R.string.app_fix_match_off
                        Identifier.OFFLINE -> R.string.app_fix_match_online_off
                        Identifier.TMDB, null -> R.string.app_fix_match_nothing
                    },
                ),
                style = FrameType.Body,
                color = FrameColors.SecondaryText,
                modifier = Modifier.padding(FrameSpacing.S12),
            )
            else -> found.forEach { choice ->
                ChoiceRow(choice) {
                    scope.launch {
                        library.applyMatch(titleId, choice)
                        state.close()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(choice: MatchChoice, onClick: () -> Unit) {
    PressableGlass(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = FrameShapes.Poster,
        highlight = false,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FrameSpacing.S8, vertical = FrameSpacing.S4),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = choice.posterUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(ChoicePosterWidth)
                    .aspectRatio(FrameSizes.PosterRatio)
                    .clip(FrameShapes.Poster)
                    .background(FrameColors.Elevated),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = bidiIsolate(choice.name),
                    style = FrameType.Body.copy(fontWeight = FontWeight.W600),
                    color = FrameColors.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metaLine(listOfNotNull(kindLabel(choice.kind), choice.year?.let { localNumber(it) })),
                    style = FrameType.Caption,
                    color = FrameColors.SecondaryText,
                    maxLines = 1,
                )
                choice.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = bidiIsolate(it),
                        style = FrameType.Caption,
                        color = FrameColors.SecondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Remove from library keeps progress and the match, so adding the folder back restores everything. */
@Composable
internal fun RemoveSheet(state: GlassSheetState, titleId: Long, onRemoved: () -> Unit) {
    val library = LocalContext.current.frameServices.library
    val scope = rememberCoroutineScope()
    GlassSheet(state, title = stringResource(R.string.app_remove_title)) {
        Text(
            text = stringResource(R.string.app_remove_body),
            style = FrameType.Body,
            color = FrameColors.SecondaryText,
            modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = FrameSpacing.S8),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
        ) {
            OutlinePill(
                text = stringResource(R.string.app_cancel),
                onClick = state::close,
                modifier = Modifier.weight(1f),
            )
            WhitePill(
                text = stringResource(R.string.app_remove),
                onClick = {
                    scope.launch {
                        library.removeFromLibrary(titleId)
                        state.close()
                        onRemoved()
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** F-L9: pick which file of a playable to open. */
@Composable
internal fun VersionSheet(state: GlassSheetState, playableKey: String) {
    GlassSheet(state, title = stringResource(R.string.app_choose_version)) {
        VersionRows(playableKey, onPicked = state::close)
    }
}

@Composable
private fun VersionRows(playableKey: String, onPicked: () -> Unit) {
    val library = LocalContext.current.frameServices.library
    val navigator = LocalNavigator.current
    val files by produceState(emptyList<FileVersion>(), playableKey) {
        value = library.versions(playableKey)
    }
    files.forEach { file ->
        GlassRow(
            text = versionLabel(file),
            supporting = file.name,
            selected = file.isDefault,
            onClick = {
                onPicked()
                if (file.available) navigator.play(PlayRef(fileRowId = file.fileRowId))
            },
        )
    }
}

@Composable
private fun versionLabel(file: FileVersion): String = metaLine(
    listOfNotNull(
        if (file.height > 0) stringResource(R.string.app_resolution, localNumber(file.height)) else null,
        file.videoCodec,
        file.hdrType,
        fileSizeLabel(file.sizeBytes),
        if (file.available) null else stringResource(R.string.app_unavailable),
    ),
)

/** Long press on an episode: mark it, or open one of its other files. */
@Composable
internal fun EpisodeSheet(state: GlassSheetState, row: EpisodeRow?) {
    val library = LocalContext.current.frameServices.library
    val scope = rememberCoroutineScope()
    val key = row?.play?.playableKey
    GlassSheet(state, title = row?.let { it.label.ifEmpty { it.title } }) {
        if (row != null && key != null) {
            GlassRow(
                text = stringResource(if (row.watched) R.string.app_mark_unwatched else R.string.app_mark_watched),
                onClick = {
                    state.close()
                    scope.launch { library.setWatched(WatchTarget.Playable(key), !row.watched) }
                },
            )
            VersionRows(key, onPicked = state::close)
        }
    }
}
