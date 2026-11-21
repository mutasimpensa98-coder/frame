package com.frameglass.player.nav

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A screen inside the tabs sets [locked] while a gesture of its own must not turn into a swipe between the tabs,
 * such as the Library chips while one is dragged to a new place. It sets it back when the gesture ends.
 */
class TabSwipeLock {
    var locked by mutableStateOf(false)
}

/** The lock of the tabs this screen sits in, or null where it was opened outside them. Provided by TabsScreen. */
val LocalTabSwipeLock = compositionLocalOf<TabSwipeLock?> { null }
