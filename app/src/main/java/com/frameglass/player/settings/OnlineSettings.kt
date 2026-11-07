package com.frameglass.player.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.ApiKeyKind
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.frameServices
import com.frameglass.glass.FrameColors
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.GlassSheetState
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.WhitePill
import com.frameglass.glass.glassSheetOrigin
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.model.FallbackNote
import com.frameglass.player.model.ServiceSwitch
import com.frameglass.player.ui.FrameTextField
import com.frameglass.player.ui.rememberServiceState
import com.frameglass.player.ui.rememberUiModel
import kotlinx.coroutines.launch

private const val DefaultTmdbUrl = "https://api.themoviedb.org"
private val SwitchDividerHeight = 32.dp

@Composable
internal fun OnlineSettingsScreen() {
    val model = rememberUiModel()
    val loaded by model.settings.collectAsStateWithLifecycle()
    val settings = loaded ?: return
    val services = rememberServiceState()

    SettingsPage(stringResource(R.string.app_page_online)) {
        item(key = "online") {
            SettingsSection(stringResource(R.string.app_settings_online)) {
                ToggleRow(
                    title = stringResource(R.string.app_online),
                    description = stringResource(R.string.app_online_hint),
                    checked = settings.onlineFeatures,
                    onChange = { value -> model.updateSettings { it.copy(onlineFeatures = value) } },
                )
                SettingsDivider()
                val fallback = when (services?.tmdbFallback) {
                    FallbackNote.THROUGH_IMDB -> stringResource(R.string.app_service_through_imdb)
                    FallbackNote.NOTHING -> stringResource(R.string.app_service_nothing)
                    FallbackNote.NONE, null -> null
                }
                ServiceRow(
                    kind = ApiKeyKind.TMDB,
                    title = stringResource(R.string.app_service_tmdb),
                    sheetTitle = stringResource(R.string.app_key_tmdb),
                    description = stringResource(R.string.app_key_tmdb_hint),
                    service = services?.tmdb,
                    offMeaning = stringResource(R.string.app_service_tmdb_off),
                    extraNote = fallback,
                    onEnabled = { value -> model.updateSettings { it.copy(tmdbEnabled = value) } },
                )
                SettingsDivider()
                ServerRow(settings, model::updateSettings)
            }
        }
        item(key = "imdb") {
            SettingsSection(stringResource(R.string.app_settings_imdb)) {
                ServiceRow(
                    kind = ApiKeyKind.OMDB,
                    title = stringResource(R.string.app_service_omdb),
                    sheetTitle = stringResource(R.string.app_key_omdb),
                    description = stringResource(R.string.app_service_omdb_hint),
                    service = services?.imdb,
                    offMeaning = stringResource(R.string.app_service_omdb_off),
                    onEnabled = { value -> model.updateSettings { it.copy(imdbEnabled = value) } },
                )
            }
        }
        item(key = "subtitles") {
            SettingsSection(stringResource(R.string.app_online_subtitles)) {
                KeyRow(
                    ApiKeyKind.OPENSUBTITLES,
                    stringResource(R.string.app_key_opensubtitles),
                    stringResource(R.string.app_key_subtitles_hint),
                )
                SettingsDivider()
                KeyRow(
                    ApiKeyKind.SUBDL,
                    stringResource(R.string.app_key_subdl),
                    stringResource(R.string.app_key_subtitles_hint),
                )
                SettingsDivider()
                KeyRow(
                    ApiKeyKind.SUBSOURCE,
                    stringResource(R.string.app_key_subsource),
                    stringResource(R.string.app_key_subtitles_hint),
                )
                SettingsDivider()
                ToggleRow(
                    title = stringResource(R.string.app_send_release),
                    description = stringResource(R.string.app_send_release_hint),
                    checked = settings.sendReleaseName,
                    onChange = { value -> model.updateSettings { it.copy(sendReleaseName = value) } },
                )
                SettingsDivider()
                ToggleRow(
                    title = stringResource(R.string.app_send_hash),
                    description = stringResource(R.string.app_send_hash_hint),
                    checked = settings.sendFileHash,
                    onChange = { value -> model.updateSettings { it.copy(sendFileHash = value) } },
                )
            }
        }
    }
}

/**
 * A metadata service: the row opens its key sheet, and the switch beside the key's state turns the service on or
 * off without touching the key. With no key the switch is disabled, and a tap on it falls through to the sheet.
 * [offMeaning] is always shown, so the owner reads what off does before turning it off.
 */
@Composable
private fun ServiceRow(
    kind: ApiKeyKind,
    title: String,
    sheetTitle: String,
    description: String,
    service: ServiceSwitch?,
    offMeaning: String,
    onEnabled: (Boolean) -> Unit,
    extraNote: String? = null,
) {
    val sheet = rememberGlassSheetState()
    val notes = listOfNotNull(
        stringResource(R.string.app_service_needs_key).takeIf { service?.switchable == false },
        extraNote,
    )
    SettingRow(
        title = title,
        description = description + "\n" + offMeaning,
        value = service?.let { stringResource(if (it.hasKey) R.string.app_key_state_added else R.string.app_key_state_none) },
        onClick = sheet::open,
        notes = notes,
        modifier = Modifier.glassSheetOrigin(sheet),
        trailing = {
            VerticalDivider(Modifier.height(SwitchDividerHeight), 0.5.dp, FrameColors.Divider)
            Switch(
                checked = service?.on == true,
                onCheckedChange = onEnabled,
                enabled = service?.switchable == true,
                modifier = Modifier.semantics { contentDescription = title },
            )
        },
    )
    KeySheet(sheet, kind, sheetTitle, service?.hasKey == true)
}

/** A key is typed once, stored encrypted, and never shown again. */
@Composable
private fun KeyRow(kind: ApiKeyKind, title: String, description: String) {
    val store = LocalContext.current.frameServices.settings
    val present by remember(kind) { store.hasKey(kind) }.collectAsStateWithLifecycle(false)
    val sheet = rememberGlassSheetState()
    SettingRow(
        title = title,
        description = description,
        value = stringResource(if (present) R.string.app_key_added else R.string.app_key_none),
        onClick = sheet::open,
        modifier = Modifier.glassSheetOrigin(sheet),
    )
    KeySheet(sheet, kind, title, present)
}

/** The typed text lives only in memory while the sheet is open, so a key never reaches saved state. */
@Composable
private fun KeySheet(state: GlassSheetState, kind: ApiKeyKind, title: String, present: Boolean) {
    val store = LocalContext.current.frameServices.settings
    val scope = rememberCoroutineScope()
    GlassSheet(state, title = title) {
        var text by remember { mutableStateOf("") }
        LaunchedEffect(state.isOpen) {
            if (!state.isOpen) text = ""
        }
        FrameTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = stringResource(R.string.app_key_placeholder),
            secret = true,
            autoCorrect = false,
            keyboardType = KeyboardType.Password,
        )
        WhitePill(
            text = stringResource(R.string.app_save),
            onClick = {
                val typed = text.trim()
                scope.launch {
                    store.setKey(kind, typed)
                    state.close()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = text.isNotBlank(),
        )
        if (present) {
            OutlinePill(
                text = stringResource(R.string.app_key_remove),
                onClick = {
                    scope.launch {
                        store.setKey(kind, null)
                        state.close()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** D5: the TMDB address stays editable, so a personal proxy can be added later. */
@Composable
private fun ServerRow(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val sheet = rememberGlassSheetState()
    val custom = settings.tmdbBaseUrl != DefaultTmdbUrl
    SettingRow(
        title = stringResource(R.string.app_tmdb_server),
        description = stringResource(R.string.app_tmdb_server_hint),
        value = if (custom) {
            settings.tmdbBaseUrl.removePrefix("https://")
        } else {
            stringResource(R.string.app_tmdb_server_default)
        },
        onClick = sheet::open,
        modifier = Modifier.glassSheetOrigin(sheet),
    )
    GlassSheet(sheet, title = stringResource(R.string.app_tmdb_server)) {
        var text by remember(settings.tmdbBaseUrl) { mutableStateOf(settings.tmdbBaseUrl) }
        val valid = text.trim().startsWith("https://") && text.trim().length > "https://".length
        FrameTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = DefaultTmdbUrl,
            autoCorrect = false,
            keyboardType = KeyboardType.Uri,
        )
        WhitePill(
            text = stringResource(R.string.app_save),
            onClick = {
                val typed = text.trim().trimEnd('/')
                update { it.copy(tmdbBaseUrl = typed) }
                sheet.close()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = valid,
        )
        if (custom) {
            OutlinePill(
                text = stringResource(R.string.app_tmdb_server_reset),
                onClick = {
                    update { it.copy(tmdbBaseUrl = DefaultTmdbUrl) }
                    sheet.close()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
