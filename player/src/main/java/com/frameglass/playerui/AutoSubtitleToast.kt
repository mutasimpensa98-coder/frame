package com.frameglass.playerui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassKind
import com.frameglass.glass.GlassSurface
import com.frameglass.glass.bidiIsolate
import kotlinx.coroutines.delay

private const val AutoNoticeMillis = 6_000L

/**
 * Says which subtitle arrived by itself, with Change to open the online results. It never blocks anything: it sits
 * below the top controls and leaves after a few seconds.
 */
@Composable
internal fun AutoSubtitleToast(session: PlayerSession, ui: PlayerUiState) {
    val state = session.state
    val notice = state.autoNotice ?: return
    LaunchedEffect(notice) {
        delay(AutoNoticeMillis)
        if (state.autoNotice == notice) state.autoNotice = null
    }
    val message = stringResource(
        if (TrackPicker.isArabic(notice.language)) R.string.player_auto_loaded_arabic else R.string.player_auto_loaded_english,
        bidiIsolate(notice.provider),
    )
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = FrameSpacing.S48 + FrameSpacing.S16)) {
        GlassSurface(GlassKind.Control, FrameShapes.Chip, modifier = Modifier.align(Alignment.TopCenter), blur = false) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
                modifier = Modifier.padding(start = FrameSpacing.S16, end = FrameSpacing.S4),
            ) {
                Text(message, style = FrameType.Label, color = FrameColors.OnMedia, maxLines = 2)
                Text(
                    text = stringResource(R.string.player_auto_change),
                    style = FrameType.Label,
                    color = FrameTheme.colors.mediaAccent,
                    modifier = Modifier
                        .clickable(role = Role.Button) {
                            state.autoNotice = null
                            session.searchOnline()
                            ui.openSheet(PlayerSheet.SUBTITLE_ONLINE)
                        }
                        .padding(horizontal = FrameSpacing.S12, vertical = FrameSpacing.S12),
                )
            }
        }
    }
}
