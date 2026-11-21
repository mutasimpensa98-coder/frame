package com.frameglass.player.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.frameglass.data.api.BarSection
import com.frameglass.glass.GlassIconButton
import com.frameglass.player.R

/**
 * Buttons the tabs hand to the first tab for its top bar. Today that is one: the gear that stands in for Settings
 * while Settings has no place in the bar (owner request of 2026-11-22). A screen that can be a tab calls
 * [BarExtrasSlot] first among its top bar actions; it draws nothing where the tabs handed over nothing.
 */
@Stable
internal class BarExtras(val content: @Composable () -> Unit) {
    private var hosts by mutableIntStateOf(0)

    /** True while some top bar shows [content]. Until one does, the tabs keep the buttons reachable themselves. */
    val hosted: Boolean get() = hosts > 0

    fun attach() {
        hosts++
    }

    fun detach() {
        hosts--
    }
}

/** Null on every tab but the first, on a screen opened outside the tabs, and while Settings has its own tab. */
internal val LocalBarExtras = compositionLocalOf<BarExtras?> { null }

/** The place in a top bar for what [LocalBarExtras] holds. */
@Composable
internal fun BarExtrasSlot() {
    val extras = LocalBarExtras.current ?: return
    DisposableEffect(extras) {
        extras.attach()
        onDispose { extras.detach() }
    }
    extras.content()
}

/** Opens Settings as a page of its own, for a bar that has no Settings tab. */
@Composable
internal fun SettingsGearButton(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    GlassIconButton(
        icon = Icons.Rounded.Settings,
        contentDescription = stringResource(R.string.app_tab_settings),
        onClick = { navigator.push(Route.Section(BarSection.SETTINGS)) },
        modifier = modifier,
    )
}
