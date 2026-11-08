package com.frameglass.player.settings

import android.content.Context
import android.graphics.fonts.Font
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.AutoSubtitleMode
import com.frameglass.player.R
import com.frameglass.player.ui.rememberUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

// The player's subtitle sheet moves these in finer steps, so a row shows the real value and marks the nearest choice.
private val SubtitleSizes = listOf(0.8f, 1f, 1.2f, 1.4f, 1.6f, 2f)
private val SubtitleLifts = listOf(0f, 0.04f, 0.08f, 0.12f, 0.2f, 0.3f)
private const val SubtitleWhite = 0xFFFFFFFF.toInt()
private const val SubtitleYellow = 0xFFFFE27A.toInt()
private const val SubtitleCyan = 0xFF9CECFF.toInt()
private const val SubtitleGreen = 0xFFB6F2A8.toInt()
private val FontExtensions = setOf("ttf", "otf")

@Composable
internal fun PlaybackSettingsScreen() {
    val model = rememberUiModel()
    val loaded by model.settings.collectAsStateWithLifecycle()
    val settings = loaded ?: return

    SettingsPage(stringResource(R.string.app_settings_playback)) {
        item(key = "playback") {
            SettingsSection(stringResource(R.string.app_playback_section)) {
                ChoiceRow(
                    title = stringResource(R.string.app_double_tap),
                    options = listOf(5, 10, 15, 30).map {
                        it to pluralStringResource(R.plurals.app_seconds, it, it)
                    },
                    selected = settings.doubleTapSeconds,
                    onSelect = { value -> model.updateSettings { it.copy(doubleTapSeconds = value) } },
                )
                SettingsDivider()
                ToggleRow(
                    title = stringResource(R.string.app_auto_next),
                    checked = settings.autoPlayNext,
                    onChange = { value -> model.updateSettings { it.copy(autoPlayNext = value) } },
                )
                SettingsDivider()
                ToggleRow(
                    title = stringResource(R.string.app_background_audio),
                    description = stringResource(R.string.app_background_audio_hint),
                    checked = settings.backgroundAudio,
                    onChange = { value -> model.updateSettings { it.copy(backgroundAudio = value) } },
                )
                SettingsDivider()
                ToggleRow(
                    title = stringResource(R.string.app_night_audio),
                    description = stringResource(R.string.app_night_audio_hint),
                    checked = settings.nightAudio,
                    onChange = { value -> model.updateSettings { it.copy(nightAudio = value) } },
                )
            }
        }
    }
}

@Composable
internal fun SubtitleSettingsScreen() {
    val model = rememberUiModel()
    val loaded by model.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fontFailed by remember { mutableStateOf(false) }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyFont(context, uri) }
                fontFailed = path == null
                if (path != null) model.updateSettings { it.copy(subtitleFontPath = path) }
            }
        }
    }
    val settings = loaded ?: return

    SettingsPage(stringResource(R.string.app_settings_subtitles)) {
        item(key = "look") {
            SettingsSection(stringResource(R.string.app_subtitle_section)) {
                ChoiceRow(
                    title = stringResource(R.string.app_subtitle_size),
                    options = SubtitleSizes.map { it to percentLabel(it) },
                    selected = nearest(SubtitleSizes, settings.subtitleScale),
                    shown = percentLabel(settings.subtitleScale),
                    onSelect = { value -> model.updateSettings { it.copy(subtitleScale = value) } },
                )
                SettingsDivider()
                ChoiceRow(
                    title = stringResource(R.string.app_subtitle_colour),
                    options = listOf(
                        SubtitleWhite to stringResource(R.string.app_colour_white),
                        SubtitleYellow to stringResource(R.string.app_colour_yellow),
                        SubtitleCyan to stringResource(R.string.app_colour_cyan),
                        SubtitleGreen to stringResource(R.string.app_colour_green),
                    ),
                    selected = settings.subtitleColor,
                    onSelect = { value -> model.updateSettings { it.copy(subtitleColor = value) } },
                )
                SettingsDivider()
                ChoiceRow(
                    title = stringResource(R.string.app_subtitle_position),
                    options = SubtitleLifts.map { it to liftLabel(it) },
                    selected = nearest(SubtitleLifts, settings.subtitleBottomOffset),
                    shown = liftLabel(settings.subtitleBottomOffset),
                    onSelect = { value -> model.updateSettings { it.copy(subtitleBottomOffset = value) } },
                )
                SettingsDivider()
                ToggleRow(
                    title = stringResource(R.string.app_subtitle_background),
                    checked = settings.subtitleBackground,
                    onChange = { value -> model.updateSettings { it.copy(subtitleBackground = value) } },
                )
                SettingsDivider()
                val fontName = settings.subtitleFontPath?.substringAfterLast('/')
                val pickLabel = stringResource(R.string.app_subtitle_font_pick)
                val defaultLabel = stringResource(R.string.app_subtitle_font_default)
                ChoiceRow(
                    title = stringResource(R.string.app_subtitle_font),
                    description = stringResource(
                        if (fontFailed) R.string.app_subtitle_font_bad else R.string.app_subtitle_font_hint,
                    ),
                    options = listOfNotNull(
                        settings.subtitleFontPath?.let { it to fontName.orEmpty() },
                        "" to defaultLabel,
                        "pick" to pickLabel,
                    ),
                    selected = settings.subtitleFontPath ?: "",
                    onSelect = { value ->
                        when (value) {
                            "pick" -> pickFont.launch(arrayOf("font/*", "application/x-font-ttf", "application/octet-stream"))
                            else -> model.updateSettings { it.copy(subtitleFontPath = null) }
                        }
                    },
                )
            }
        }
        item(key = "auto") {
            SettingsSection(stringResource(R.string.app_subtitle_online_section)) {
                ChoiceRow(
                    title = stringResource(R.string.app_auto_subtitles),
                    description = stringResource(R.string.app_auto_subtitles_hint),
                    options = listOf(
                        AutoSubtitleMode.OFF to stringResource(R.string.app_auto_subtitles_off),
                        AutoSubtitleMode.ARABIC to stringResource(R.string.app_auto_subtitles_arabic),
                        AutoSubtitleMode.ARABIC_THEN_ENGLISH to stringResource(R.string.app_auto_subtitles_both),
                    ),
                    selected = settings.autoSubtitles,
                    onSelect = { value -> model.updateSettings { it.copy(autoSubtitles = value) } },
                )
            }
        }
    }
}

@Composable
private fun percentLabel(value: Float): String = stringResource(R.string.app_percent, (value * 100).roundToInt())

@Composable
private fun liftLabel(value: Float): String =
    if (value < 0.005f) stringResource(R.string.app_position_bottom) else percentLabel(value)

private fun nearest(options: List<Float>, value: Float): Float = options.minBy { abs(it - value) }

// F-P9: the owner's font is copied in, so it works after the picked file moves.
private fun copyFont(context: Context, uri: Uri): String? = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: return null
    if (name.substringAfterLast('.').lowercase() !in FontExtensions) return null
    val folder = File(context.filesDir, "fonts").apply { mkdirs() }
    val target = File(folder, File(name).name)
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { input.copyTo(it) }
    } ?: return null
    // Throws on anything that is not a real font.
    Font.Builder(target).build()
    target.path
}.getOrNull()
