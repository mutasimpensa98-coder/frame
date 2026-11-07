package com.frameglass.player.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.GlassTab
import com.frameglass.glass.LiquidTabBar
import com.frameglass.player.R
import com.frameglass.player.home.HomeScreen
import com.frameglass.player.library.LibraryScreen
import com.frameglass.player.search.SearchScreen
import com.frameglass.player.settings.SettingsScreen

private const val HomeTab = 0
private const val SearchTab = 1
private const val LibraryTab = 2

/** The four tabs of F-U1. Back on any other tab returns to Home first, then leaves the app. */
@Composable
internal fun TabsScreen(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        GlassTab(stringResource(R.string.app_tab_home), Icons.Rounded.Home),
        GlassTab(stringResource(R.string.app_tab_search), Icons.Rounded.Search),
        GlassTab(stringResource(R.string.app_tab_library), Icons.Rounded.VideoLibrary),
        GlassTab(stringResource(R.string.app_tab_settings), Icons.Rounded.Settings),
    )
    val saved = rememberSaveableStateHolder()
    BackHandler(enabled = selected != HomeTab) { onSelect(HomeTab) }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selected,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(FrameMotion.effects()) togetherWith fadeOut(FrameMotion.effects()) },
            label = "tabs",
        ) { tab ->
            saved.SaveableStateProvider(tab) {
                when (tab) {
                    HomeTab -> HomeScreen(onOpenLibrary = { onSelect(LibraryTab) })
                    SearchTab -> SearchScreen()
                    LibraryTab -> LibraryScreen()
                    else -> SettingsScreen()
                }
            }
        }
        LiquidTabBar(tabs, selected, onSelect, Modifier.align(Alignment.BottomCenter))
    }
}
