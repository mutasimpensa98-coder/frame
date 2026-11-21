package com.frameglass.player.select

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The picks of one screen and whether it is in selection mode. It knows nothing about grids, lists or cards: a
 * screen keeps one, and every card asks it. Picks are kept by key in pick order, and keys stay valid across list
 * updates, so a scan cannot drop a selection.
 */
@Stable
internal class SelectionState<T : Any>(private val keyOf: (T) -> String) {
    var picked: Map<String, T> by mutableStateOf(emptyMap())
        private set
    var active by mutableStateOf(false)
        private set

    val count: Int get() = picked.size

    /** The picks, in the order they were made. */
    val items: List<T> get() = picked.values.toList()

    fun isPicked(item: T): Boolean = keyOf(item) in picked

    /** Enters selection mode with [item] picked, as the "Select" row of a card menu does. */
    fun start(item: T) {
        active = true
        picked = picked + (keyOf(item) to item)
    }

    /** Enters selection mode with nothing picked yet. */
    fun enter() {
        active = true
    }

    /** A tap on a card while selecting. Letting go of the last pick leaves selection mode. */
    fun toggle(item: T) {
        picked = SelectionRules.toggle(picked, keyOf(item), item)
        if (picked.isEmpty()) active = false
    }

    fun selectAll(all: List<T>) {
        if (all.isEmpty()) return
        active = true
        picked = SelectionRules.selectAll(picked, all, keyOf)
    }

    /** "Clear all": nothing is picked, and the screen stays in selection mode for a new pick. */
    fun clearPicks() {
        picked = emptyMap()
    }

    /** The list changed under the picks. When the last pick has left the list, so does selection mode. */
    fun retain(listed: List<T>) {
        if (picked.isEmpty()) return
        val kept = SelectionRules.retain(picked, listed, keyOf)
        if (kept != picked) picked = kept
        if (kept.isEmpty()) active = false
    }

    /** Leaves selection mode. */
    fun clear() {
        picked = emptyMap()
        active = false
    }
}
