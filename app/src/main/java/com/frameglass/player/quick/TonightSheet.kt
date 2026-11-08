package com.frameglass.player.quick

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.PickOption
import com.frameglass.data.api.TitleDetail
import com.frameglass.data.api.frameServices
import com.frameglass.glass.CoverImage
import com.frameglass.glass.FilterChips
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.GlassSheetState
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.RatingBadge
import com.frameglass.glass.WhitePill
import com.frameglass.glass.bidiIsolate
import com.frameglass.player.R
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.ui.durationLabel
import com.frameglass.player.ui.localNumber
import com.frameglass.player.ui.toCard
import kotlinx.coroutines.flow.flowOf

private val PickPosterWidth = 112.dp
private const val PlotLines = 4
private val Kinds = listOf(PickKind.ANY, PickKind.FILM, PickKind.SERIES)

/** "What should I watch tonight?": one random unstarted title, with filters and another pick on tap. */
@Composable
internal fun TonightSheet(state: GlassSheetState) {
    val library = LocalContext.current.frameServices.library
    val navigator = LocalNavigator.current
    GlassSheet(state, title = stringResource(R.string.app_tonight)) {
        val pool by remember(library) { library.tonightPool }.collectAsStateWithLifecycle(null as List<PickOption>?)
        var kind by rememberSaveable { mutableStateOf(PickKind.ANY) }
        var genre by rememberSaveable { mutableStateOf<String?>(null) }
        var recent by remember { mutableStateOf(emptyList<Long>()) }
        var picked by remember { mutableStateOf<Long?>(null) }
        val options = pool
        fun pickAgain() {
            val next = options?.let { TonightPick.next(it, kind, genre, recent) }
            picked = next
            if (next != null) recent = TonightPick.remember(recent, next)
        }
        // A pick on open, and a new one whenever a filter changes. The pool arriving later counts as a change.
        LaunchedEffect(state.isOpen, kind, genre, options != null) {
            if (state.isOpen && options != null) pickAgain()
        }

        val genres = remember(options) { TonightPick.genres(options.orEmpty()) }
        FilterChips(
            labels = Kinds.map { kindLabel(it) },
            selectedIndex = Kinds.indexOf(kind),
            onSelect = { kind = Kinds[it] },
            contentPadding = PaddingValues(0.dp),
        )
        if (genres.isNotEmpty()) {
            val labels = listOf(stringResource(R.string.app_tonight_all_genres)) + genres
            FilterChips(
                labels = labels,
                selectedIndex = genre?.let { genres.indexOf(it) + 1 }?.takeIf { it > 0 } ?: 0,
                onSelect = { genre = if (it == 0) null else genres[it - 1] },
                modifier = Modifier.padding(top = FrameSpacing.S8),
                contentPadding = PaddingValues(0.dp),
            )
        }
        val id = picked
        val detail by remember(id) { if (id == null) flowOf(null) else library.title(id) }
            .collectAsStateWithLifecycle(null as TitleDetail?)
        val shown = detail?.takeIf { it.id == id }
        if (options != null && id == null) {
            Text(
                text = stringResource(R.string.app_tonight_none),
                style = FrameType.Body,
                color = FrameColors.SecondaryText,
                modifier = Modifier.padding(vertical = FrameSpacing.S24),
            )
        } else if (shown != null) {
            PickCard(shown)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = FrameSpacing.S16),
                horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
            ) {
                WhitePill(
                    text = stringResource(R.string.app_tonight_play),
                    onClick = {
                        shown.play?.let {
                            state.close()
                            navigator.play(it)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.PlayArrow,
                    enabled = shown.play != null,
                )
                OutlinePill(
                    text = stringResource(R.string.app_tonight_details),
                    onClick = {
                        state.close()
                        navigator.details(shown.id)
                    },
                    modifier = Modifier.weight(1f),
                )
                OutlinePill(
                    text = stringResource(R.string.app_tonight_another),
                    onClick = ::pickAgain,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Shuffle,
                )
            }
        }
    }
}

@Composable
private fun kindLabel(kind: PickKind): String = stringResource(
    when (kind) {
        PickKind.ANY -> R.string.app_tonight_any
        PickKind.FILM -> R.string.app_tonight_films
        PickKind.SERIES -> R.string.app_tonight_series
    },
)

@Composable
private fun PickCard(detail: TitleDetail) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = FrameSpacing.S16),
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
    ) {
        CoverImage(
            path = detail.coverPath,
            contentDescription = null,
            modifier = Modifier.width(PickPosterWidth).aspectRatio(FrameSizes.PosterRatio),
        )
        Column(verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8)) {
            Text(
                text = bidiIsolate(detail.name),
                style = FrameType.Title,
                color = FrameColors.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val facts = listOfNotNull(
                detail.year?.let { localNumber(it) },
                detail.runtimeMinutes?.takeIf { it > 0 }?.let { durationLabel(it) },
            )
            val rating = detail.rating ?: detail.imdbRating
            if (facts.isNotEmpty() || rating != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    facts.forEach { Text(bidiIsolate(it), style = FrameType.Label, color = FrameColors.White, maxLines = 1) }
                    rating?.let { RatingBadge(it.toCard()) }
                }
            }
            detail.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = FrameType.Body,
                    color = FrameColors.SecondaryText,
                    maxLines = PlotLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
