package com.frameglass.player.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.AccentColor
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.CustomThemeDefaults
import com.frameglass.data.api.ThemeId
import com.frameglass.data.api.TitleKind
import com.frameglass.glass.AccentSwatch
import com.frameglass.glass.AnyAccentSwatch
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameLook
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameThemeId
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.GlassSheetState
import com.frameglass.glass.HueSaturationField
import com.frameglass.glass.HueSlider
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.ProgressLine
import com.frameglass.glass.ShadeSlider
import com.frameglass.glass.SurfaceSlider
import com.frameglass.glass.ThemeAccentSwatch
import com.frameglass.glass.ThemeSwatch
import com.frameglass.glass.WhitePill
import com.frameglass.glass.accentArgb
import com.frameglass.glass.accentHueSaturation
import com.frameglass.glass.coverGlow
import com.frameglass.glass.label
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.ui.customTheme
import com.frameglass.player.ui.kindLabel
import com.frameglass.player.ui.localNumber
import com.frameglass.player.ui.metaLine
import com.frameglass.player.ui.rememberUiModel
import com.frameglass.player.ui.toFrame
import com.frameglass.player.ui.toLook

// The eight ready themes in the order of the approved storyboard, four to a row.
private val ThemeRows = listOf(
    listOf(ThemeId.BLACK, ThemeId.PURE_BLACK, ThemeId.MIDNIGHT, ThemeId.VIOLET),
    listOf(ThemeId.FOREST, ThemeId.COFFEE, ThemeId.LIGHT, ThemeId.PAPER),
).map { row -> row.mapNotNull { id -> id.toFrame()?.let { id to it } } }

// The accents in the order of the storyboard, after the theme's own colour and before "any colour".
private val AccentOrder = listOf(
    AccentColor.WHITE,
    AccentColor.BLUE,
    AccentColor.RED,
    AccentColor.GREEN,
    AccentColor.AMBER,
    AccentColor.VIOLET,
    AccentColor.PINK,
    AccentColor.CYAN,
)
private const val AccentsPerRow = 5

// Stands in for the colour of a cover, so the preview shows what the tint does.
private const val SampleCover = 0xFF3D6FD6.toInt()
private const val SampleYear = 2026
private const val SampleProgress = 0.4f

private val CustomSwatchWidth = 92.dp
private val PickerSwatchWidth = 76.dp

private typealias Update = ((AppSettings) -> AppSettings) -> Unit

/**
 * Ready themes, the accent, the custom theme, follow the phone and cover tint. A choice applies at the root at once
 * and fades in, so this page is its own live preview.
 */
@Composable
internal fun AppearanceScreen() {
    val model = rememberUiModel()
    val loaded by model.settings.collectAsStateWithLifecycle()
    val settings = loaded ?: return
    val picker = rememberGlassSheetState()

    SettingsPage(stringResource(R.string.app_settings_appearance)) {
        item(key = "preview") { AppearancePreview() }
        item(key = "theme") { ThemePicker(settings, model::updateSettings) }
        item(key = "accent") { AccentPicker(settings, model::updateSettings, onPickAny = picker::open) }
        item(key = "custom") { CustomThemeEditor(settings, model::updateSettings) }
        item(key = "options") {
            SettingsSection(stringResource(R.string.app_appearance_options)) {
                ToggleRow(
                    title = stringResource(R.string.app_follow_phone),
                    description = stringResource(R.string.app_follow_phone_hint),
                    checked = settings.followSystem,
                    onChange = { value -> model.updateSettings { it.copy(followSystem = value) } },
                )
                SettingsDivider()
                ToggleRow(
                    title = stringResource(R.string.app_cover_tint),
                    description = stringResource(R.string.app_cover_tint_hint),
                    checked = settings.coverTint,
                    onChange = { value -> model.updateSettings { it.copy(coverTint = value) } },
                )
            }
        }
    }
    AccentPickerSheet(picker, settings, model::updateSettings)
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

// A ready theme as its card shows it: with the accent the owner would get on it.
private fun AppSettings.lookOf(theme: FrameThemeId): FrameLook =
    toLook().copy(theme = theme, custom = null, followSystem = false)

@Composable
private fun ThemePicker(settings: AppSettings, update: Update) {
    Column(
        modifier = Modifier.padding(top = FrameSpacing.S16).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        SectionLabel(stringResource(R.string.app_appearance_theme))
        ThemeRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
            ) {
                row.forEach { (id, frame) ->
                    val name = frame.label()
                    val selected = settings.theme == id
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
                    ) {
                        ThemeSwatch(
                            look = settings.lookOf(frame),
                            name = name,
                            modifier = Modifier.fillMaxWidth(),
                            selected = selected,
                            onClick = { update { it.copy(theme = id) } },
                        )
                        SwatchName(name, selected)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwatchName(name: String, selected: Boolean) {
    Text(
        text = name,
        style = FrameType.Label,
        color = if (selected) FrameColors.White else FrameColors.SecondaryText,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        // The swatch already says the name to a screen reader.
        modifier = Modifier.clearAndSetSemantics { },
    )
}

@Composable
private fun AccentPicker(settings: AppSettings, update: Update, onPickAny: () -> Unit) {
    val dots: List<@Composable () -> Unit> = buildList {
        add {
            ThemeAccentSwatch(
                selected = settings.accent == AccentColor.THEME,
                onClick = { update { it.copy(accent = AccentColor.THEME) } },
            )
        }
        AccentOrder.forEach { accent ->
            val frame = accent.toFrame() ?: return@forEach
            add {
                AccentSwatch(
                    accent = frame,
                    selected = settings.accent == accent,
                    onClick = { update { it.copy(accent = accent) } },
                )
            }
        }
        add {
            AnyAccentSwatch(
                argb = settings.customAccent,
                selected = settings.accent == AccentColor.CUSTOM,
                onClick = onPickAny,
            )
        }
    }
    Column(
        modifier = Modifier.padding(top = FrameSpacing.S16).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
    ) {
        SectionLabel(stringResource(R.string.app_appearance_accent))
        dots.chunked(AccentsPerRow).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                row.forEach { dot -> dot() }
            }
        }
    }
}

/**
 * The three sliders of the custom theme beside a picture of what they give. The picture follows the finger, and the
 * app takes the new colours when the finger lifts, so a drag never writes the settings file at every step.
 */
@Composable
private fun CustomThemeEditor(settings: AppSettings, update: Update) {
    val saved = settings.customTheme()
    var draft by remember(saved) { mutableStateOf(saved) }
    val selected = settings.theme == ThemeId.CUSTOM
    val name = stringResource(R.string.app_custom_theme_short)
    val use = {
        val wanted = draft
        update {
            it.copy(
                theme = ThemeId.CUSTOM,
                customHue = wanted.hue,
                customShade = wanted.shade,
                customSurface = wanted.surface,
            )
        }
    }

    SettingsSection(stringResource(R.string.app_custom_theme)) {
        Row(
            modifier = Modifier.padding(FrameSpacing.S16),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
        ) {
            Column(
                modifier = Modifier.width(CustomSwatchWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
            ) {
                ThemeSwatch(
                    look = settings.toLook().copy(custom = draft, followSystem = false),
                    name = name,
                    modifier = Modifier.fillMaxWidth(),
                    selected = selected,
                    onClick = use,
                )
                SwatchName(name, selected)
            }
            Column(Modifier.weight(1f)) {
                val hue = stringResource(R.string.app_custom_hue)
                SliderLabel(hue)
                HueSlider(draft.hue, { draft = draft.copy(hue = it) }, use, hue)
                val shade = stringResource(R.string.app_custom_shade)
                SliderLabel(shade)
                ShadeSlider(draft, { draft = draft.copy(shade = it) }, use, shade)
                val surface = stringResource(R.string.app_custom_surface)
                SliderLabel(surface)
                SurfaceSlider(draft, { draft = draft.copy(surface = it) }, use, surface)
            }
        }
        Text(
            text = stringResource(R.string.app_custom_hint),
            style = FrameType.Label,
            color = FrameColors.SecondaryText,
            modifier = Modifier.padding(horizontal = FrameSpacing.S24),
        )
        OutlinePill(
            text = stringResource(R.string.app_custom_reset),
            onClick = { update(::defaultLook) },
            modifier = Modifier.fillMaxWidth().padding(FrameSpacing.S16),
            icon = Icons.Rounded.Refresh,
        )
    }
}

/** "Reset to default": the dark look with its own accent, and the custom values back where they start. */
internal fun defaultLook(settings: AppSettings): AppSettings = settings.copy(
    theme = ThemeId.DEFAULT,
    accent = AccentColor.DEFAULT,
    customHue = CustomThemeDefaults.HUE,
    customShade = CustomThemeDefaults.SHADE,
    customSurface = CustomThemeDefaults.SURFACE,
    customAccent = CustomThemeDefaults.ACCENT,
)

@Composable
private fun SliderLabel(text: String) {
    Text(
        text = text,
        style = FrameType.Label,
        color = FrameColors.SecondaryText,
        // The slider under it carries the same words for a screen reader.
        modifier = Modifier.clearAndSetSemantics { },
    )
}

/** The "any colour" accent: a hue and saturation field, a picture of the result, and one button that takes it. */
@Composable
private fun AccentPickerSheet(state: GlassSheetState, settings: AppSettings, update: Update) {
    val title = stringResource(R.string.app_accent_any_title)
    GlassSheet(state, title = title) {
        var picked by remember { mutableStateOf(accentHueSaturation(settings.customAccent)) }
        val argb = accentArgb(picked.first, picked.second)
        Column(
            modifier = Modifier.padding(horizontal = FrameSpacing.S12),
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
        ) {
            HueSaturationField(
                hue = picked.first,
                saturation = picked.second,
                onChange = { hue, saturation -> picked = hue to saturation },
                label = title,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemeSwatch(
                    look = settings.toLook().copy(followSystem = false, accentArgb = argb),
                    name = stringResource(R.string.app_appearance_preview),
                    modifier = Modifier.width(PickerSwatchWidth),
                )
                Text(
                    text = stringResource(R.string.app_accent_any_hint),
                    style = FrameType.Label,
                    color = FrameColors.SecondaryText,
                    modifier = Modifier.weight(1f),
                )
            }
            WhitePill(
                text = stringResource(R.string.app_accent_any_use),
                onClick = {
                    update { it.copy(accent = AccentColor.CUSTOM, customAccent = argb) }
                    state.close()
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Check,
            )
        }
    }
}
