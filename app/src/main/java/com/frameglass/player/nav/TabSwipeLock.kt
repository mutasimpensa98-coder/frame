package com.frameglass.player.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A screen inside the tabs sets [locked] while a gesture of its own must not turn into a swipe between the tabs,
 * such as the Library chips while one is dragged to a new place. It sets it back when the gesture ends.
 *
 * A screen that is in a mode for longer, such as an open search, holds the tabs through [HoldTabSwipe] instead, so
 * the two never undo each other.
 */
class TabSwipeLock {
    var locked by mutableStateOf(false)

    private val holders = mutableStateListOf<Any>()

    /** True while [locked] is set or anything holds the tabs. This is what the tabs obey. */
    val held: Boolean get() = locked || holders.isNotEmpty()

    internal fun hold(owner: Any) {
        holders += owner
    }

    internal fun release(owner: Any) {
        holders -= owner
    }
}

/** The lock of the tabs this screen sits in, or null where it was opened outside them. Provided by TabsScreen. */
val LocalTabSwipeLock = compositionLocalOf<TabSwipeLock?> { null }

/** While [active], the tabs do not swipe. It lets go by itself when the screen leaves. */
@Composable
internal fun HoldTabSwipe(active: Boolean) {
    val lock = LocalTabSwipeLock.current ?: return
    DisposableEffect(lock, active) {
        val owner = Any()
        if (active) lock.hold(owner)
        onDispose { lock.release(owner) }
    }
}

/**
 * False on a tab's page while another tab is the one in view, as for the moment two pages are on screen during a
 * swipe. A page uses it to keep its Back handlers to itself until it is the tab in view: Back belongs to the tab
 * the owner is on. True outside the tabs.
 */
internal val LocalTabInView = compositionLocalOf { true }
