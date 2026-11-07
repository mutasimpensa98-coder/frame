package com.frameglass.player.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.AccentColor
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.ThemeMode
import com.frameglass.data.api.TitleKind
import com.frameglass.glass.AccentSwatch
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.ProgressLine
import com.frameglass.glass.ThemeSwatch
import com.frameglass.glass.WhitePill
import com.frameglass.glass.coverGlow
import com.frameglass.glass.label
import com.frameglass.player.R
import com.frameglass.player.ui.kindLabel
import com.frameglass.player.ui.localNumber
import com.frameglass.player.ui.metaLine
import com.frameglass.player.ui.rememberUiModel
import com.frameglass.player.ui.toFrame

private val ThemeOrder = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM)

// Stands in for the colour of a cover, so the preview shows what the tint does.
private const val SampleCover = 0xFF3D6FD6.toInt()
private const val SampleYear = 2026
private const val SampleProgress = 0.4f

/** Theme, accent and cover tint. A choice applies at the root at once, so this page is its own live preview. */
@Composable
internal fun AppearanceScreen() {
    val model = rememberUiModel()
    val loaded by model.settings.collectAsStateWithLifecycle()
    val settings = loaded ?: return

    SettingsPage(stringResource(R.string.app_settings_appearance)) {
        item(key = "preview") { AppearancePreview() }
        item(key = "theme") { ThemePicker(settings, model::updateSettings) }
        item(key = "accent") { AccentPicker(settings, model::updateSettings) }
        item(key = "tint") {
            SettingsSection(stringResource(R.string.app_appearance_art)) {
                ToggleRow(
                    title = stringResource(R.string.app_cover_tint),
                    description = stringResource(R.string.app_cover_tint_hint),
                    checked = settings.coverTint,
                    onChange = { value -> model.updateSettings { it.copy(coverTint = value) } },
                )
            }
        }
    }
}

/** Real components on a sample title, with nothing to tap. */
@Composable
private fun AppearancePreview() {
    val title = stringResource(R.string.app_preview_title)
    Column(
        modifier = Modifier.padding(top = FrameSpacing.S16),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        SectionLabel(stringResource(R.string.app_appearance_preview))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FrameShapes.Card)
                .background(FrameColors.Canvas)
                .border(0.5.dp, FrameColors.Divider, FrameShapes.Card)
                .coverGlow(SampleCover)
                .clearAndSetSemantics { contentDescription = title }
                .padding(FrameSpacing.S16),
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
        ) {
            Text(title, style = FrameType.Title, color = FrameColors.White, maxLines = 1)
            Text(
                text = metaLine(
                    listOf(
                        kindLabel(TitleKind.MOVIE),
                        stringResource(R.string.app_preview_genre),
                        localNumber(SampleYear),
                    ),
                ),
                style = FrameType.Caption,
                color = FrameColors.SecondaryText,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12)) {
                WhitePill(
                    text = stringResource(R.string.app_play),
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.PlayArrow,
                )
                OutlinePill(
                    text = stringResource(R.string.app_save),
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Add,
                )
            }
            ProgressLine(SampleProgress, Modifier.fillMaxWidth(), FrameColors.Elevated)
        }
    }
}

@Composable
private fun ThemePicker(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    Column(
        modifier = Modifier.padding(top = FrameSpacing.S16),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        SectionLabel(stringResource(R.string.app_appearance_theme))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
        ) {
            ThemeOrder.forEach { mode ->
                val frame = mode.toFrame()
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
                ) {
                    ThemeSwatch(
                        mode = frame,
                        accent = settings.accent.toFrame(),
                        modifier = Modifier.fillMaxWidth(),
                        selected = settings.themeMode == mode,
                        onClick = { update { it.copy(themeMode = mode) } },
                    )
                    Text(
                        text = frame.label(),
                        style = FrameType.Label,
                        color = if (settings.themeMode == mode) FrameColors.White else FrameColors.SecondaryText,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentPicker(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    Column(
        modifier = Modifier.padding(top = FrameSpacing.S16),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        SectionLabel(stringResource(R.string.app_appearance_accent))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AccentColor.entries.forEach { accent ->
                AccentSwatch(
                    accent = accent.toFrame(),
                    selected = settings.accent == accent,
                    onClick = { update { it.copy(accent = accent) } },
                )
            }
        }
    }
}
