package com.frameglass.player.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.GlassSource
import com.frameglass.glass.TileHue
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.glassScrolled
import com.frameglass.player.R
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.Route
import com.frameglass.player.nav.SettingsTopic
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.statusInset

/** F-U14, as a clear main list in the style of image 05. Every row opens its own page. */
@Composable
internal fun SettingsScreen() {
    val navigator = LocalNavigator.current
    val list = rememberLazyListState()
    val language = rememberLanguageLabel()

    Box(Modifier.fillMaxSize()) {
        GlassSource(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = FrameSpacing.S16,
                    end = FrameSpacing.S16,
                    top = statusInset() + FrameSizes.TopBarHeight,
                    bottom = navigationInset() + FrameSizes.TabBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
            ) {
                item(key = "general") {
                    SettingsSection(stringResource(R.string.app_settings_group_general)) {
                        SettingsLink(
                            icon = Icons.Rounded.Palette,
                            hue = TileHue.Pink,
                            title = stringResource(R.string.app_settings_appearance),
                            description = stringResource(R.string.app_settings_appearance_hint),
                            onClick = { navigator.push(Route.Settings(SettingsTopic.APPEARANCE)) },
                        )
                        SettingsDivider(LinkDividerInset)
                        SettingsLink(
                            icon = Icons.Rounded.Language,
                            hue = TileHue.Blue,
                            title = stringResource(R.string.app_settings_language),
                            description = stringResource(R.string.app_settings_language_hint),
                            value = language,
                            onClick = { navigator.push(Route.Settings(SettingsTopic.LANGUAGE)) },
                        )
                    }
                }
                item(key = "watching") {
                    SettingsSection(stringResource(R.string.app_settings_group_watching)) {
                        SettingsLink(
                            icon = Icons.Rounded.VideoLibrary,
                            hue = TileHue.Indigo,
                            title = stringResource(R.string.app_settings_library),
                            description = stringResource(R.string.app_settings_library_hint),
                            onClick = { navigator.push(Route.Settings(SettingsTopic.LIBRARY)) },
                        )
                        SettingsDivider(LinkDividerInset)
                        SettingsLink(
                            icon = Icons.Rounded.PlayCircle,
                            hue = TileHue.Red,
                            title = stringResource(R.string.app_settings_playback),
                            description = stringResource(R.string.app_settings_playback_hint),
                            onClick = { navigator.push(Route.Settings(SettingsTopic.PLAYBACK)) },
                        )
                        SettingsDivider(LinkDividerInset)
                        SettingsLink(
                            icon = Icons.Rounded.Subtitles,
                            hue = TileHue.Teal,
                            title = stringResource(R.string.app_settings_subtitles),
                            description = stringResource(R.string.app_settings_subtitles_hint),
                            onClick = { navigator.push(Route.Settings(SettingsTopic.SUBTITLES)) },
                        )
                    }
                }
                item(key = "storage") {
                    SettingsSection(stringResource(R.string.app_settings_group_storage)) {
                        SettingsLink(
                            icon = Icons.Rounded.Storage,
                            hue = TileHue.Orange,
                            title = stringResource(R.string.app_settings_media),
                            description = stringResource(R.string.app_settings_media_hint),
                            onClick = { navigator.push(Route.MediaManager) },
                        )
                        SettingsDivider(LinkDividerInset)
                        SettingsLink(
                            icon = Icons.Rounded.Lock,
                            hue = TileHue.Violet,
                            title = stringResource(R.string.app_settings_private),
                            description = stringResource(R.string.app_settings_private_hint),
                            onClick = { navigator.push(Route.PrivateFolder) },
                        )
                    }
                }
                item(key = "app") {
                    SettingsSection(stringResource(R.string.app_settings_group_app)) {
                        SettingsLink(
                            icon = Icons.Rounded.Key,
                            hue = TileHue.Amber,
                            title = stringResource(R.string.app_settings_online),
                            description = stringResource(R.string.app_settings_online_hint),
                            onClick = { navigator.push(Route.Settings(SettingsTopic.ONLINE)) },
                        )
                        SettingsDivider(LinkDividerInset)
                        SettingsLink(
                            icon = Icons.Rounded.Restore,
                            hue = TileHue.Green,
                            title = stringResource(R.string.app_settings_backup),
                            description = stringResource(R.string.app_settings_backup_hint),
                            onClick = { navigator.push(Route.Settings(SettingsTopic.BACKUP)) },
                        )
                        SettingsDivider(LinkDividerInset)
                        SettingsLink(
                            icon = Icons.Rounded.Explore,
                            hue = TileHue.Sky,
                            title = stringResource(R.string.tour_settings),
                            description = stringResource(R.string.tour_settings_hint),
                            onClick = { navigator.push(Route.Tour(setup = false)) },
                        )
                        SettingsDivider(LinkDividerInset)
                        SettingsLink(
                            icon = Icons.Rounded.Info,
                            hue = TileHue.Grey,
                            title = stringResource(R.string.app_settings_about),
                            description = stringResource(R.string.app_about_hint),
                            onClick = { navigator.push(Route.About) },
                        )
                    }
                }
            }
        }
        TopGlassBar(
            title = stringResource(R.string.app_tab_settings),
            scrolled = { list.glassScrolled() },
        )
    }
}
