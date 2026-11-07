package com.frameglass.player.settings

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.ApiKeyKind
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.frameServices
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.GlassSheetState
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.WhitePill
import com.frameglass.glass.glassSheetOrigin
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.ui.FrameTextField
import com.frameglass.player.ui.rememberUiModel
import kotlinx.coroutines.launch

private const val DefaultTmdbUrl = "https://api.themoviedb.org"

@Composable
internal fun OnlineSettingsScreen() {
    val model = rememberUiModel()
    val loaded by model.settings.collectAsStateWithLifecycle()
    val settings = loaded ?: return

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
                KeyRow(
                    ApiKeyKind.TMDB,
                    stringResource(R.string.app_key_tmdb),
                    stringResource(R.string.app_key_tmdb_hint),
                )
                SettingsDivider()
                ServerRow(settings, model::updateSettings)
            }
        }
        item(key = "imdb") {
            SettingsSection(stringResource(R.string.app_settings_imdb)) {
                KeyRow(
                    ApiKeyKind.OMDB,
                    stringResource(R.string.app_key_omdb),
                    stringResource(R.string.app_key_omdb_hint),
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
