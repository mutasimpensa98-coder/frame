package com.frameglass.playerui

import android.content.res.Resources
import android.icu.text.ListFormatter
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.frameglass.data.api.SubtitleResult
import com.frameglass.data.api.frameServices
import com.frameglass.engine.HdrKind
import com.frameglass.engine.SubtitleEncoding
import com.frameglass.glass.ActivityLine
import com.frameglass.glass.FilterChips
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassRow
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.timeLeftLabel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SPEED_STEP = 0.05f
private val SPEED_PRESETS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f)
private val SLEEP_MINUTES = listOf(15, 30, 45, 60, 90)
private const val SMALL_DELAY_STEP_MS = 50L
private const val LARGE_DELAY_STEP_MS = 500L
private const val SCALE_STEP = 0.1f
private const val MIN_SCALE = 0.6f
private const val MAX_SCALE = 2f
private const val POSITION_STEP = 0.02f
private const val MAX_POSITION = 0.3f
private const val DIMMED = 0.4f
private const val MEGA = 1_000_000.0
private const val STEREO = 2
private const val SURROUND_5_1 = 6
private const val SURROUND_7_1 = 8

private val RowShape = RoundedCornerShape(16.dp)
private val RowFill = Color.White.copy(alpha = SheetLook.ROW_FILL_ALPHA)
private val StepperValueWidth = 88.dp

private class SubtitleColor(@StringRes val label: Int, val argb: Int)

private val SUBTITLE_COLORS = listOf(
    SubtitleColor(R.string.player_color_white, 0xFFFFFFFF.toInt()),
    SubtitleColor(R.string.player_color_yellow, 0xFFFFE27A.toInt()),
    SubtitleColor(R.string.player_color_cyan, 0xFF9CECFF.toInt()),
    SubtitleColor(R.string.player_color_green, 0xFFB6F2A8.toInt()),
)

/**
 * Layer 4 of the stack: one sheet that changes content. It opens only after the controls have faded, so no
 * control shows through it, as the note on image 08 asks (F-P12). The video does show through, dimmed.
 */
@Composable
internal fun SheetsLayer(session: PlayerSession, ui: PlayerUiState, actions: PlayerActions) {
    var open by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<PlayerSheet?>(null) }
    LaunchedEffect(ui.sheet) {
        val wanted = ui.sheet
        if (wanted == null) {
            open = false
        } else {
            if (!open) delay(ControlsFadeMillis.toLong())
            shown = wanted
            open = true
        }
    }
    PlayerSheetFrame(open, title = shown?.let { stringResource(it.title) }, onClose = { ui.closeSheet() }) {
        val parent = shown?.parent
        BackHandler(enabled = parent != null) { ui.openSheet(parent ?: return@BackHandler) }
        when (shown) {
            PlayerSheet.SUBTITLES -> SubtitlesSheet(session, ui, actions)
            PlayerSheet.SUBTITLE_STYLE -> SubtitleStyleSheet(session, actions)
            PlayerSheet.SUBTITLE_ENCODING -> EncodingSheet(session, ui)
            PlayerSheet.SUBTITLE_ONLINE -> OnlineSheet(session, ui)
            PlayerSheet.AUDIO -> AudioSheet(session, ui)
            PlayerSheet.SPEED -> SpeedSheet(session, ui)
            PlayerSheet.EPISODES -> EpisodesSheet(session, ui)
            PlayerSheet.MORE -> MoreSheet(session, ui, actions)
            PlayerSheet.INFO -> InfoSheet(session)
            PlayerSheet.SLEEP -> SleepSheet(session, ui)
            PlayerSheet.INTRO -> IntroSheet(session, ui)
            null -> Unit
        }
    }
}

@Composable
private fun languageName(tag: String?): String? {
    val locale = LocalConfiguration.current.locales[0]
    return tag?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale).takeIf(String::isNotBlank) }
}

@Composable
private fun trackTitle(row: TrackRow): String {
    val language = languageName(row.language)
    val label = row.label
    return when {
        row.sidecarPath != null -> label ?: language ?: stringResource(R.string.player_track_unknown)
        language != null && label != null && !label.equals(language, ignoreCase = true) -> "$language • $label"
        language != null -> language
        label != null -> label
        else -> stringResource(R.string.player_track_unknown)
    }
}

private fun channelsName(resources: Resources, channels: Int): String? = when (channels) {
    0 -> null
    1 -> resources.getString(R.string.player_channels_mono)
    STEREO -> resources.getString(R.string.player_channels_stereo)
    SURROUND_5_1 -> "5.1"
    SURROUND_7_1 -> "7.1"
    else -> resources.getQuantityString(R.plurals.player_channels, channels, channels)
}

@Composable
private fun trackDetails(row: TrackRow): String? {
    val resources = LocalResources.current
    val parts = listOfNotNull(
        row.codec,
        channelsName(resources, row.channels),
        stringResource(R.string.player_track_forced).takeIf { row.forced },
        stringResource(R.string.player_track_file).takeIf { row.sidecarPath != null },
        stringResource(R.string.player_track_unplayable).takeIf { !row.supported },
    )
    return parts.joinToString(" • ").ifEmpty { null }
}

@Composable
private fun TrackList(rows: List<TrackRow>, onSelect: (TrackRow) -> Unit) {
    rows.forEach { row ->
        GlassRow(
            text = trackTitle(row),
            supporting = trackDetails(row),
            selected = row.selected,
            onClick = { onSelect(row) },
        )
    }
}

@Composable
private fun SubtitlesSheet(session: PlayerSession, ui: PlayerUiState, actions: PlayerActions) {
    val state = session.state
    GlassRow(
        text = stringResource(R.string.player_off),
        selected = state.textTracks.none { it.selected },
        onClick = {
            session.subtitlesOff()
            ui.closeSheet()
        },
    )
    TrackList(state.textTracks) { row ->
        session.selectTrack(C.TRACK_TYPE_TEXT, row)
        ui.closeSheet()
    }
    OnlineSearchRow(session, ui)
    GlassRow(
        text = stringResource(R.string.player_add_subtitle),
        icon = Icons.Rounded.FolderOpen,
        onClick = {
            ui.closeSheet()
            actions.pickSubtitleFile()
        },
    )
    DelayStepper(R.string.player_subtitle_delay, state.subtitleDelayMs, session::setSubtitleDelay)
    SyncSection(session)
    GlassRow(
        text = stringResource(R.string.player_subtitle_style),
        icon = Icons.Rounded.TextFields,
        onClick = { ui.openSheet(PlayerSheet.SUBTITLE_STYLE) },
    )
    if (state.hasTextFiles) {
        GlassRow(
            text = stringResource(R.string.player_subtitle_encoding),
            supporting = state.subtitleEncoding ?: stringResource(R.string.player_encoding_auto),
            icon = Icons.Rounded.Language,
            onClick = { ui.openSheet(PlayerSheet.SUBTITLE_ENCODING) },
        )
    }
}

/** Always shown, so the search is never missing: when it cannot run, the row says why instead. */
@Composable
private fun OnlineSearchRow(session: PlayerSession, ui: PlayerUiState) {
    val state = session.state
    val item = state.item ?: return
    // A private item never reaches a subtitle provider.
    val blocked = when {
        item.isPrivate -> R.string.player_online_private
        !state.onlineAvailable -> R.string.player_online_off
        else -> null
    }
    if (blocked != null) {
        GlassRow(
            text = stringResource(R.string.player_online_search),
            supporting = stringResource(blocked),
            icon = Icons.Rounded.Search,
            modifier = Modifier.alpha(DIMMED).semantics { disabled() },
            onClick = {},
        )
        return
    }
    GlassRow(
        text = stringResource(R.string.player_online_search),
        icon = Icons.Rounded.Search,
        onClick = {
            session.searchOnline()
            ui.openSheet(PlayerSheet.SUBTITLE_ONLINE)
        },
    )
}

/** Automatic sync: what it found or why it changed nothing, Undo after a change, and a row to run it again. */
@Composable
private fun SyncSection(session: PlayerSession) {
    val state = session.state
    val sync = state.sync
    val running = sync == SyncState.Running
    if (sync != null) Note(SyncText.summary(LocalResources.current, LocalLocale.current.platformLocale, sync))
    if (running) ActivityLine(Modifier.fillMaxWidth().padding(horizontal = FrameSpacing.S12))
    if (state.syncUndoable && !running) {
        GlassRow(
            text = stringResource(R.string.player_sync_undo),
            icon = Icons.AutoMirrored.Rounded.Undo,
            onClick = session::undoSync,
        )
    }
    if (!running && session.canSync()) {
        GlassRow(
            text = stringResource(R.string.player_sync_now),
            supporting = stringResource(R.string.player_sync_now_note),
            icon = Icons.Rounded.GraphicEq,
            onClick = session::syncNow,
        )
    }
}

/** F-S9. Whatever goes wrong is said here, and the sheet stays open so another result can be tried. */
@Composable
private fun OnlineSheet(session: PlayerSession, ui: PlayerUiState) {
    val online = session.state.online
    val results = online.results
    val note = SitesNote.of(session.state.onlineSites, session.state.item)
    online.failure?.let { failure ->
        val message = if (failure == OnlineFailure.NO_KEY && note.filmsNeedKey) R.string.player_online_films_key else failure.message
        Text(
            text = stringResource(message),
            style = FrameType.Label,
            color = FrameTheme.colors.error,
            modifier = Modifier.padding(horizontal = FrameSpacing.S12, vertical = FrameSpacing.S4),
        )
    }
    when {
        online.searching -> {
            Note(stringResource(R.string.player_online_searching))
            ActivityLine(Modifier.fillMaxWidth().padding(horizontal = FrameSpacing.S12))
        }
        results == null -> GlassRow(
            text = stringResource(R.string.player_online_retry),
            icon = Icons.Rounded.Refresh,
            onClick = { session.searchOnline(again = true) },
        )
        results.isEmpty() -> Note(stringResource(R.string.player_online_none))
        else -> results.forEach { result -> OnlineRow(session, ui, result) }
    }
    if (!online.searching && (results != null || online.failure == OnlineFailure.NO_KEY)) SitesLine(note, ui)
}

/** One quiet line. It opens the keys page only when the app offers a safe way there; otherwise it is just text. */
@Composable
private fun SitesLine(note: SitesNote, ui: PlayerUiState) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val searched = note.searched.map { site ->
        val name = bidiIsolate(site.name)
        if (site.seriesOnly) stringResource(R.string.player_online_series_only, name) else name
    }
    val parts = buildList {
        if (searched.isNotEmpty()) add(stringResource(R.string.player_online_searched, ListFormatter.getInstance(locale).format(searched)))
        if (note.withFreeKey.isNotEmpty()) {
            val names = ListFormatter.getInstance(locale).format(note.withFreeKey.map { bidiIsolate(it.name) })
            add(stringResource(R.string.player_online_more_sites, names))
        }
    }
    if (parts.isEmpty()) return
    val keysPage = remember(context) { context.subtitleKeysIntent() }
    val text = parts.joinToString(" ")
    if (keysPage == null || note.withFreeKey.isEmpty()) {
        Note(text)
        return
    }
    Text(
        text = text,
        style = FrameType.Caption,
        color = FrameTheme.colors.accent,
        modifier = Modifier
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.player_online_open_keys)) {
                // A stale or foreign target must not crash the player.
                runCatching { context.startActivity(keysPage) }.onSuccess { ui.closeSheet() }
            }
            .padding(horizontal = FrameSpacing.S12, vertical = FrameSpacing.S4),
    )
}

@Composable
private fun OnlineRow(session: PlayerSession, ui: PlayerUiState, result: SubtitleResult) {
    val loading = session.state.online.loading
    GlassRow(
        text = result.release.ifBlank { stringResource(R.string.player_online_unnamed) },
        supporting = if (loading == result) stringResource(R.string.player_online_downloading) else onlineDetails(result),
        icon = if (result.hashMatch) Icons.Rounded.Verified else Icons.Rounded.Subtitles,
        modifier = if (loading != null && loading != result) Modifier.alpha(DIMMED) else Modifier,
        onClick = {
            session.chooseOnline(result) {
                if (ui.sheet == PlayerSheet.SUBTITLE_ONLINE) ui.closeSheet()
            }
        },
    )
}

/** The language leads: it is in the reader's own script, so it sets the direction of the whole line. */
@Composable
private fun onlineDetails(result: SubtitleResult): String = listOfNotNull(
    languageName(result.language) ?: result.language,
    bidiIsolate(result.provider),
    stringResource(R.string.player_online_exact).takeIf { result.hashMatch },
    stringResource(R.string.player_online_pack).takeIf { result.seasonPack },
    result.format?.let { bidiIsolate(it.uppercase()) },
).joinToString(" • ")

/** Positive means later, so one signed value reads the same for subtitles and audio. */
@Composable
private fun DelayStepper(@StringRes title: Int, valueMs: Long, onChange: (Long) -> Unit) {
    Stepper(
        title = stringResource(title),
        value = stringResource(R.string.player_delay_value, String.format(LocalLocale.current.platformLocale, "%+d", valueMs)),
        onMinus = { onChange(valueMs - SMALL_DELAY_STEP_MS) },
        onPlus = { onChange(valueMs + SMALL_DELAY_STEP_MS) },
        onLongMinus = { onChange(valueMs - LARGE_DELAY_STEP_MS) },
        onLongPlus = { onChange(valueMs + LARGE_DELAY_STEP_MS) },
    )
}

@Composable
private fun Stepper(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onLongMinus: (() -> Unit)? = null,
    onLongPlus: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(RowFill, RowShape)
            .heightIn(min = FrameSpacing.S48)
            .padding(horizontal = FrameSpacing.S16, vertical = FrameSpacing.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(bidiIsolate(title), Modifier.weight(1f), style = FrameType.Body, color = FrameColors.OnMedia)
        GlassIconButton(Icons.Rounded.Remove, stringResource(R.string.player_less), onMinus, onLongClick = onLongMinus)
        Text(
            text = bidiIsolate(value),
            modifier = Modifier.widthIn(min = StepperValueWidth),
            style = FrameType.Label,
            color = FrameColors.OnMedia,
            textAlign = TextAlign.Center,
        )
        GlassIconButton(Icons.Rounded.Add, stringResource(R.string.player_more_value), onPlus, onLongClick = onLongPlus)
    }
}

@Composable
private fun SubtitleStyleSheet(session: PlayerSession, actions: PlayerActions) {
    val state = session.state
    val settings = state.settings
    Stepper(
        title = stringResource(R.string.player_style_size),
        value = stringResource(R.string.player_percent, (settings.subtitleScale * 100).roundToInt()),
        onMinus = {
            val value = (settings.subtitleScale - SCALE_STEP).coerceAtLeast(MIN_SCALE)
            session.updateSettings { it.copy(subtitleScale = value) }
        },
        onPlus = {
            val value = (settings.subtitleScale + SCALE_STEP).coerceAtMost(MAX_SCALE)
            session.updateSettings { it.copy(subtitleScale = value) }
        },
    )
    Stepper(
        title = stringResource(R.string.player_style_position),
        value = stringResource(R.string.player_percent, (settings.subtitleBottomOffset * 100).roundToInt()),
        onMinus = {
            val value = (settings.subtitleBottomOffset - POSITION_STEP).coerceAtLeast(0f)
            session.updateSettings { it.copy(subtitleBottomOffset = value) }
        },
        onPlus = {
            val value = (settings.subtitleBottomOffset + POSITION_STEP).coerceAtMost(MAX_POSITION)
            session.updateSettings { it.copy(subtitleBottomOffset = value) }
        },
    )
    FilterChips(
        labels = SUBTITLE_COLORS.map { stringResource(it.label) },
        selectedIndex = SUBTITLE_COLORS.indexOfFirst { it.argb == settings.subtitleColor },
        onSelect = { index ->
            val argb = SUBTITLE_COLORS[index].argb
            session.updateSettings { it.copy(subtitleColor = argb) }
        },
        contentPadding = PaddingValues(0.dp),
    )
    GlassRow(
        text = stringResource(R.string.player_style_outline),
        selected = state.subtitleOutline,
        onClick = { session.setOutline(!state.subtitleOutline) },
    )
    GlassRow(
        text = stringResource(R.string.player_style_background),
        selected = settings.subtitleBackground,
        onClick = { session.updateSettings { it.copy(subtitleBackground = !it.subtitleBackground) } },
    )
    val font = settings.subtitleFontPath?.substringAfterLast('/')
    GlassRow(
        text = stringResource(R.string.player_style_font),
        supporting = font ?: stringResource(R.string.player_style_font_default),
        icon = Icons.Rounded.FolderOpen,
        onClick = actions::pickFont,
    )
    if (font != null) {
        GlassRow(
            text = stringResource(R.string.player_style_font_reset),
            onClick = { session.setCustomFont(null) },
        )
    }
    Note(stringResource(R.string.player_style_ass_note))
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = FrameType.Caption,
        color = FrameColors.OnMediaSecondary,
        modifier = Modifier.padding(horizontal = FrameSpacing.S12, vertical = FrameSpacing.S4),
    )
}

@Composable
private fun EncodingSheet(session: PlayerSession, ui: PlayerUiState) {
    val current = session.state.subtitleEncoding
    GlassRow(
        text = stringResource(R.string.player_encoding_auto),
        selected = current == null,
        onClick = {
            session.setSubtitleEncoding(null)
            ui.closeSheet()
        },
    )
    SubtitleEncoding.choices.forEach { name ->
        GlassRow(
            text = name,
            selected = current == name,
            onClick = {
                session.setSubtitleEncoding(name)
                ui.closeSheet()
            },
        )
    }
}

@Composable
private fun AudioSheet(session: PlayerSession, ui: PlayerUiState) {
    val state = session.state
    TrackList(state.audioTracks) { row ->
        session.selectTrack(C.TRACK_TYPE_AUDIO, row)
        ui.closeSheet()
    }
    GlassRow(
        text = stringResource(R.string.player_off),
        selected = state.audioTracks.isNotEmpty() && state.audioTracks.none { it.selected },
        onClick = {
            session.audioOff()
            ui.closeSheet()
        },
    )
    DelayStepper(R.string.player_audio_delay, state.audioDelayMs, session::setAudioDelay)
    state.audioDevice?.let { device -> Note(stringResource(R.string.player_audio_delay_device, bidiIsolate(device))) }
    GlassRow(
        text = stringResource(R.string.player_night_audio),
        supporting = stringResource(R.string.player_night_audio_note),
        selected = state.settings.nightAudio,
        onClick = { session.updateSettings { it.copy(nightAudio = !it.nightAudio) } },
    )
}

@Composable
private fun speedLabel(speed: Float): String {
    val text = stringResource(R.string.player_times, formatSpeed(speed))
    return if (speed == 1f) stringResource(R.string.player_speed_normal, text) else text
}

@Composable
private fun SpeedSheet(session: PlayerSession, ui: PlayerUiState) {
    val speed = session.state.speed
    Stepper(
        title = stringResource(R.string.player_speed),
        value = stringResource(R.string.player_times, formatSpeed(speed)),
        onMinus = { session.setSpeed(speed - SPEED_STEP) },
        onPlus = { session.setSpeed(speed + SPEED_STEP) },
    )
    SPEED_PRESETS.forEach { preset ->
        GlassRow(
            text = speedLabel(preset),
            selected = abs(preset - speed) < SPEED_STEP / 2f,
            onClick = {
                session.setSpeed(preset)
                ui.closeSheet()
            },
        )
    }
}

@Composable
private fun EpisodesSheet(session: PlayerSession, ui: PlayerUiState) {
    val item = session.state.item ?: return
    val titleId = item.titleId ?: return
    val library = LocalContext.current.frameServices.library
    val title by remember(titleId) { library.title(titleId) }.collectAsState(initial = null)
    val seasons = title?.seasons.orEmpty()
    var season by remember(item.season) { mutableIntStateOf(item.season ?: seasons.firstOrNull() ?: 1) }
    val rows by remember(titleId, season) { library.episodes(titleId, season) }.collectAsState(initial = emptyList())
    if (seasons.size > 1) {
        FilterChips(
            labels = seasons.map { stringResource(R.string.player_season, it) },
            selectedIndex = seasons.indexOf(season),
            onSelect = { season = seasons[it] },
            contentPadding = PaddingValues(0.dp),
        )
    }
    rows.forEach { row ->
        val playable = row.play != null && row.available && !row.missing
        GlassRow(
            text = listOf(row.label, row.title).filter(String::isNotBlank).joinToString(" • "),
            supporting = stringResource(R.string.player_watched).takeIf { row.watched },
            selected = row.season == item.season && row.number == item.episode,
            modifier = if (playable) Modifier else Modifier.alpha(DIMMED),
            onClick = {
                val ref = row.play
                if (playable && ref != null) {
                    session.open(ref)
                    ui.closeSheet()
                }
            },
        )
    }
}

@Composable
private fun MoreSheet(session: PlayerSession, ui: PlayerUiState, actions: PlayerActions) {
    val state = session.state
    GlassRow(
        text = stringResource(R.string.player_info),
        icon = Icons.Rounded.Info,
        onClick = { ui.openSheet(PlayerSheet.INFO) },
    )
    if (state.item?.isPrivate != true) {
        GlassRow(
            text = stringResource(R.string.player_screenshot),
            icon = Icons.Rounded.PhotoCamera,
            onClick = {
                ui.closeSheet()
                actions.screenshot(withSubtitles = false)
            },
        )
        GlassRow(
            text = stringResource(R.string.player_screenshot_subtitles),
            icon = Icons.Rounded.Subtitles,
            onClick = {
                ui.closeSheet()
                actions.screenshot(withSubtitles = true)
            },
        )
    }
    GlassRow(
        text = stringResource(R.string.player_sleep_timer),
        supporting = sleepSummary(state.sleep),
        icon = Icons.Rounded.Bedtime,
        onClick = { ui.openSheet(PlayerSheet.SLEEP) },
    )
    GlassRow(
        text = stringResource(R.string.player_rotate),
        icon = Icons.Rounded.ScreenRotation,
        onClick = {
            ui.closeSheet()
            actions.rotate()
        },
    )
    if (state.item?.titleId != null) {
        GlassRow(
            text = stringResource(R.string.player_set_intro),
            icon = Icons.Rounded.Timer,
            onClick = { ui.openSheet(PlayerSheet.INTRO) },
        )
    }
    GlassRow(
        text = stringResource(if (state.markedWatched) R.string.player_mark_unwatched else R.string.player_mark_watched),
        icon = Icons.Rounded.CheckCircle,
        onClick = { session.setWatched(!state.markedWatched) },
    )
}

@Composable
private fun sleepSummary(sleep: SleepTimer?): String? {
    if (sleep == null) return null
    val ends = sleep.endsAtElapsed ?: return stringResource(R.string.player_sleep_end)
    return timeLeftLabel(ends - SystemClock.elapsedRealtime())
}

@Composable
private fun SleepSheet(session: PlayerSession, ui: PlayerUiState) {
    val sleep = session.state.sleep
    GlassRow(
        text = stringResource(R.string.player_off),
        selected = sleep == null,
        onClick = {
            session.setSleep(null)
            ui.closeSheet()
        },
    )
    SLEEP_MINUTES.forEach { minutes ->
        GlassRow(
            text = pluralStringResource(R.plurals.player_minutes, minutes, minutes),
            onClick = {
                session.setSleep(SleepTimer(SystemClock.elapsedRealtime() + minutes * 60_000L))
                ui.closeSheet()
            },
        )
    }
    GlassRow(
        text = stringResource(R.string.player_sleep_end),
        selected = sleep != null && sleep.endsAtElapsed == null,
        onClick = {
            session.setSleep(SleepTimer(null))
            ui.closeSheet()
        },
    )
}

/** F-S4 manual: mark two moments while watching, then choose how far the marks apply. */
@Composable
private fun IntroSheet(session: PlayerSession, ui: PlayerUiState) {
    val state = session.state
    val position = state.positionMs
    val start = ui.introStartMs
    val end = ui.introEndMs
    Note(stringResource(R.string.player_intro_note))
    GlassRow(
        text = stringResource(R.string.player_intro_start),
        supporting = formatTime(start ?: position),
        selected = start != null,
        onClick = { ui.introStartMs = position },
    )
    GlassRow(
        text = stringResource(R.string.player_intro_end),
        supporting = formatTime(end ?: position),
        selected = end != null,
        onClick = { ui.introEndMs = position },
    )
    if (start != null && end != null && end > start) {
        fun save(wholeSeries: Boolean) {
            session.saveIntro(start, end, wholeSeries)
            ui.introStartMs = null
            ui.introEndMs = null
            ui.closeSheet()
        }
        if (state.item?.season != null) {
            GlassRow(text = stringResource(R.string.player_intro_save_season), onClick = { save(false) })
        }
        GlassRow(text = stringResource(R.string.player_intro_save_series), onClick = { save(true) })
    }
}

@Composable
private fun InfoLine(@StringRes label: Int, value: String?) {
    if (value.isNullOrEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = FrameSpacing.S12, vertical = FrameSpacing.S4),
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
    ) {
        Text(stringResource(label), Modifier.weight(1f), style = FrameType.Label, color = FrameColors.OnMediaSecondary)
        Text(bidiIsolate(value), Modifier.weight(2f), style = FrameType.Label, color = FrameColors.OnMedia)
    }
}

@Composable
private fun hdrLine(facts: StreamFacts): String {
    val base = when (facts.hdr.base) {
        HdrKind.HDR10 -> "HDR10"
        HdrKind.HLG -> "HLG"
        else -> stringResource(R.string.player_hdr_sdr)
    }
    val profile = facts.hdr.dolbyVisionProfile
    return when {
        facts.hdr.kind != HdrKind.DOLBY_VISION -> base
        profile == null -> stringResource(R.string.player_hdr_dv_unknown)
        facts.hdr.warning -> stringResource(R.string.player_hdr_dv_warning, profile)
        else -> stringResource(R.string.player_hdr_dv_base, profile, base)
    }
}

/** F-T5 and F-P20: what is really happening, including the limits. */
@Composable
private fun InfoSheet(session: PlayerSession) {
    var facts by remember { mutableStateOf(session.facts()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            facts = session.facts()
        }
    }
    val resources = LocalResources.current
    val resolution = if (facts.width > 0) "${facts.width} × ${facts.height}" else null
    val locale = LocalLocale.current.platformLocale
    val frameRate = facts.frameRate.takeIf { it > 0f }?.let { String.format(locale, "%.3f", it) }
    val bitrate = facts.bitrate.takeIf { it > 0L }
        ?.let { stringResource(R.string.player_info_mbps, String.format(locale, "%.1f", it / MEGA)) }
    val decoderKind = stringResource(if (facts.hardware) R.string.player_info_hardware else R.string.player_info_software)
    InfoLine(R.string.player_info_codec, facts.videoCodec)
    InfoLine(R.string.player_info_resolution, resolution)
    InfoLine(R.string.player_info_frame_rate, frameRate)
    InfoLine(R.string.player_info_bitrate, bitrate)
    InfoLine(R.string.player_info_hdr, hdrLine(facts))
    InfoLine(R.string.player_info_decoder, facts.videoDecoder.ifEmpty { null }?.let { "$it ($decoderKind)" })
    InfoLine(R.string.player_info_dropped, facts.droppedFrames.toString())
    InfoLine(
        R.string.player_info_audio,
        listOfNotNull(
            facts.audioCodec,
            channelsName(resources, facts.channels),
            facts.sampleRate.takeIf { it > 0 }?.let { stringResource(R.string.player_info_khz, it / 1000f) },
        ).joinToString(" • "),
    )
    InfoLine(R.string.player_info_audio_decoder, facts.audioDecoder)
    if (!facts.hardware && facts.hdr.base != HdrKind.SDR) Note(stringResource(R.string.player_hdr_software_note))
}
