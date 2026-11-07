package com.frameglass.glass

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Points the status and navigation bar icons at the theme. [FrameTheme] already calls this, so call it
 * yourself only to override [darkIcons] while a screen shows, such as light icons over dark art. The
 * theme's choice returns when that screen leaves.
 */
@Composable
fun FrameSystemBars(darkIcons: Boolean = FrameTheme.colors.isLight) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val themeIcons = FrameTheme.colors.isLight
    DisposableEffect(window, view, darkIcons, themeIcons) {
        val bars = WindowCompat.getInsetsController(window, view)
        bars.isAppearanceLightStatusBars = darkIcons
        bars.isAppearanceLightNavigationBars = darkIcons
        onDispose {
            bars.isAppearanceLightStatusBars = themeIcons
            bars.isAppearanceLightNavigationBars = themeIcons
        }
    }
}
