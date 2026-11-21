package com.frameglass.player.nav

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TabSwipeLock { var locked by mutableStateOf(false) }

val LocalTabSwipeLock = compositionLocalOf<TabSwipeLock?> { null }
