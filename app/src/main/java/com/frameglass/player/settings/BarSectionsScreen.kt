package com.frameglass.player.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.BarSection
import com.frameglass.data.api.BarSections
import com.frameglass.data.api.visibleSections
import com.frameglass.data.api.withSections
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassTab
import com.frameglass.glass.LiquidTabBar
import com.frameglass.glass.TileHue
import com.frameglass.player.R
import com.frameglass.player.nav.TabRules
import com.frameglass.player.nav.icon
import com.frameglass.player.nav.label
import com.frameglass.player.ui.rememberUiModel

private val BarSection.hue: TileHue
    get() = when (this) {
        BarSection.HOME -> TileHue.Blue
        BarSection.FOLDERS -> TileHue.Amber
        BarSection.LIBRARY -> TileHue.Indigo
        BarSection.SEARCH -> TileHue.Teal
        BarSection.SETTINGS -> TileHue.Grey
    }

@get:StringRes
private val BarSection.hint: Int
    get() = when (this) {
        BarSection.HOME -> R.string.app_bar_home_hint
        BarSection.FOLDERS -> R.string.app_bar_folders_hint
        BarSection.LIBRARY -> R.string.app_bar_library_hint
        BarSection.SEARCH -> R.string.app_bar_search_hint
        BarSection.SETTINGS -> R.string.app_bar_settings_hint
    }

// The preview bar and its caption stand over the end of the list.
private val PreviewSpace = FrameSizes.TabBarShownClearance + FrameSpacing.S32

/**
 * «أقسام الشريط» (owner request of 2026-11-22): which of the main pages have a tab. A switch applies at once. The
 * last page of videos that is on cannot be turned off, and its row says so. The bar at the bottom is the real one,
 * fed with the choice as it stands, so its new width and its tabs are seen before leaving the page; a tap or a drag
 * on it moves only its own pill.
 */
@Composable
internal fun BarSectionsScreen() {
    val model = rememberUiModel()
    val settings by model.settings.collectAsStateWithLifecycle()
    val enabled = settings?.visibleSections() ?: BarSections.Default
    var tried by remember { mutableStateOf<BarSection?>(null) }
    val pillOn = TabRules.shown(tried, enabled)

    Box(Modifier.fillMaxSize()) {
        SettingsPage(stringResource(R.string.app_settings_bar)) {
            item(key = "sections") {
                SettingsSection(stringResource(R.string.app_bar_sections)) {
                    BarSection.entries.forEachIndexed { index, section ->
                        if (index > 0) SettingsDivider(LinkDividerInset)
                        val on = section in enabled
                        val held = on && !BarSections.canTurnOff(section, enabled)
                        SettingsSwitchLink(
                            icon = section.icon,
                            hue = section.hue,
                            title = stringResource(section.label),
                            description = stringResource(if (held) R.string.app_bar_last else section.hint),
                            checked = on,
                            enabled = !held,
                            // Worked out from what is stored at that moment, so two quick taps cannot empty the bar.
                            onChange = { want ->
                                model.updateSettings { it.withSections(BarSections.toggle(it.visibleSections(), section, want)) }
                            },
                        )
                    }
                }
            }
            item(key = "note") {
                Text(
                    text = stringResource(R.string.app_bar_note),
                    style = FrameType.Label,
                    color = FrameColors.SecondaryText,
                    modifier = Modifier.padding(horizontal = FrameSpacing.S8, vertical = FrameSpacing.S8),
                )
            }
            item(key = "previewSpace") { Spacer(Modifier.height(PreviewSpace)) }
        }
        AnimatedVisibility(
            visible = enabled.size > 1,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(FrameMotion.effects()),
            exit = fadeOut(FrameMotion.effects()),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
            ) {
                Text(
                    text = stringResource(R.string.app_bar_preview),
                    style = FrameType.Caption,
                    color = FrameColors.SecondaryText,
                )
                LiquidTabBar(
                    tabs = enabled.map { GlassTab(stringResource(it.label), it.icon, key = it.id) },
                    selectedIndex = enabled.indexOf(pillOn).coerceAtLeast(0),
                    onSelect = { index -> tried = enabled.getOrNull(index) },
                )
            }
        }
    }
}
