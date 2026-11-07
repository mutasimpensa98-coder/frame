package com.frameglass.player.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.frameServices
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.glassSheetOrigin
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.folders.FolderBrowser
import com.frameglass.player.ui.localNumber
import com.frameglass.player.ui.rememberUiModel

@Composable
internal fun LibrarySettingsScreen() {
    val model = rememberUiModel()
    val library = LocalContext.current.frameServices.library
    val loaded by model.settings.collectAsStateWithLifecycle()
    val excluded by remember(library) { library.excludedFolders }.collectAsStateWithLifecycle(emptyList())
    val hidden by remember(library) { library.hiddenTitles }.collectAsStateWithLifecycle(emptyList())
    val settings = loaded ?: return

    SettingsPage(stringResource(R.string.app_settings_library)) {
        item(key = "search") { SearchSection(settings) }
        item(key = "excluded") { ExcludedSection(excluded) }
        item(key = "hidden") { HiddenSection(hidden) }
        item(key = "show") { ShowSection(settings) }
    }
}

@Composable
private fun SearchSection(settings: AppSettings) {
    val model = rememberUiModel()
    val library = LocalContext.current.frameServices.library
    val folders by model.folders.collectAsStateWithLifecycle()
    val scan by model.scan.collectAsStateWithLifecycle()

    SettingsSection(stringResource(R.string.app_library_section_search)) {
        ToggleRow(
            title = stringResource(R.string.app_scan_whole_phone),
            description = stringResource(R.string.app_scan_whole_phone_hint),
            checked = settings.scanWholePhone,
            onChange = { value -> model.updateSettings { it.copy(scanWholePhone = value) } },
        )
        SettingsDivider()
        folders.orEmpty().forEach { folder ->
            SettingRow(
                title = folder.label,
                description = if (folder.available) {
                    pluralStringResource(R.plurals.app_videos, folder.videoCount, folder.videoCount)
                } else {
                    stringResource(R.string.app_unavailable)
                },
                trailing = {
                    IconButton(onClick = { model.launch { library.removeFolder(folder.path) } }) {
                        Icon(
                            Icons.Rounded.Close,
                            stringResource(R.string.app_folder_remove, folder.label),
                            tint = FrameColors.SecondaryText,
                        )
                    }
                },
            )
            SettingsDivider()
        }
        SettingRow(
            title = stringResource(R.string.app_scan_now),
            description = when {
                scan.running -> pluralStringResource(R.plurals.app_scan_found, scan.found, scan.found)
                scan.matching > 0 -> pluralStringResource(R.plurals.app_scan_matching, scan.matching, scan.matching)
                else -> stringResource(R.string.app_scan_hint)
            },
            onClick = model::scanNow,
        )
    }
}

@Composable
private fun ExcludedSection(excluded: List<String>) {
    val model = rememberUiModel()
    val library = LocalContext.current.frameServices.library
    val sheet = rememberGlassSheetState()

    SettingsSection(stringResource(R.string.app_excluded_title)) {
        excluded.forEach { path ->
            val name = path.substringAfterLast('/').ifEmpty { path }
            SettingRow(
                title = name,
                description = bidiIsolate(path),
                trailing = {
                    IconButton(onClick = { model.launch { library.includeFolder(path) } }) {
                        Icon(
                            Icons.Rounded.Close,
                            stringResource(R.string.app_excluded_remove, name),
                            tint = FrameColors.SecondaryText,
                        )
                    }
                },
            )
            SettingsDivider()
        }
        SettingRow(
            title = stringResource(R.string.app_excluded_add),
            description = stringResource(R.string.app_excluded_add_hint),
            onClick = sheet::open,
            modifier = Modifier.glassSheetOrigin(sheet),
        )
    }
    GlassSheet(sheet, title = stringResource(R.string.app_excluded_add)) {
        FolderBrowser(
            pickText = stringResource(R.string.app_excluded_pick),
            pickedText = stringResource(R.string.app_excluded_already),
            isPicked = { path -> path in excluded },
            onPick = { path ->
                sheet.close()
                model.launch { library.excludeFolder(path) }
            },
        )
    }
}

@Composable
private fun HiddenSection(hidden: List<TitleCard>) {
    val model = rememberUiModel()
    val library = LocalContext.current.frameServices.library

    SettingsSection(stringResource(R.string.app_hidden_title)) {
        if (hidden.isEmpty()) {
            SettingRow(title = stringResource(R.string.app_hidden_empty))
        }
        hidden.forEachIndexed { index, card ->
            if (index > 0) SettingsDivider()
            SettingRow(
                title = card.name,
                description = card.year?.let { localNumber(it) },
                trailing = {
                    TextButton(onClick = { model.launch { library.unhideTitle(card.id) } }) {
                        Text(
                            text = stringResource(R.string.app_hidden_restore),
                            style = FrameType.Label,
                            color = FrameColors.Accent,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ShowSection(settings: AppSettings) {
    val model = rememberUiModel()

    SettingsSection(stringResource(R.string.app_library_section_show)) {
        ToggleRow(
            title = stringResource(R.string.app_cinema_mode),
            description = stringResource(R.string.app_cinema_mode_hint),
            checked = settings.cinemaMode,
            onChange = { value -> model.updateSettings { it.copy(cinemaMode = value) } },
        )
        SettingsDivider()
        ToggleRow(
            title = stringResource(R.string.app_show_unavailable),
            description = stringResource(R.string.app_show_unavailable_hint),
            checked = settings.showUnavailable,
            onChange = { value -> model.updateSettings { it.copy(showUnavailable = value) } },
        )
        SettingsDivider()
        ToggleRow(
            title = stringResource(R.string.app_scan_hidden),
            checked = settings.scanHiddenFolders,
            onChange = { value -> model.updateSettings { it.copy(scanHiddenFolders = value) } },
        )
        SettingsDivider()
        ToggleRow(
            title = stringResource(R.string.app_scan_nomedia),
            checked = settings.scanNomediaFolders,
            onChange = { value -> model.updateSettings { it.copy(scanNomediaFolders = value) } },
        )
    }
}
