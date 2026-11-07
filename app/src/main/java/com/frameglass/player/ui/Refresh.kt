package com.frameglass.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassKind
import com.frameglass.glass.GlassSurface
import com.frameglass.player.R

/** Swipe down to scan again. A small chip then says how many videos arrived. Wraps one scrolling list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefreshBox(
    indicatorTop: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val model = rememberUiModel()
    val refreshing by model.refreshing.collectAsStateWithLifecycle()
    val found by model.newVideos.collectAsStateWithLifecycle()
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = model::refresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = indicatorTop),
                containerColor = FrameColors.Elevated,
                color = FrameColors.White,
            )
        },
    ) {
        content()
        // Kept, so the chip still has its text while it fades out.
        var shown by remember { mutableIntStateOf(0) }
        found?.let { shown = it }
        AnimatedVisibility(
            visible = found != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = indicatorTop + FrameSpacing.S8),
            enter = fadeIn(FrameMotion.effects()),
            exit = fadeOut(FrameMotion.effects()),
        ) {
            RefreshChip(shown)
        }
    }
}

@Composable
private fun RefreshChip(count: Int) {
    GlassSurface(GlassKind.Badge, FrameShapes.Poster, blur = false) {
        Text(
            text = if (count > 0) {
                pluralStringResource(R.plurals.app_refresh_new, count, count)
            } else {
                stringResource(R.string.app_refresh_none)
            },
            style = FrameType.Caption,
            color = FrameColors.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = FrameSpacing.S12, vertical = FrameSpacing.S8),
        )
    }
}
