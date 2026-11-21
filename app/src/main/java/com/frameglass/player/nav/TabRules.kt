package com.frameglass.player.nav

import com.frameglass.data.api.BarSection
import kotlin.math.abs

/** Which tab shows, and where Back and a tap on the bar lead. Pure, so it is tested without a screen. */
internal object TabRules {
    /** The section in view: the one [wanted] while it has a tab, else the first tab. */
    fun shown(wanted: BarSection?, visible: List<BarSection>): BarSection =
        wanted?.takeIf { it in visible } ?: visible.first()

    /** Where Back goes from [current]: to the first tab from any other, and null on the first, where it leaves. */
    fun backTarget(current: BarSection, visible: List<BarSection>): BarSection? =
        visible.firstOrNull()?.takeIf { it != current }

    /** True when [target] is an open page of its own and not a tab, so opening it pushes a route. */
    fun opensAsPage(target: BarSection, visible: List<BarSection>): Boolean = target !in visible

    /**
     * The page the pager cuts to before it slides to [target], when the bar asks for a tab that is not next to
     * [from]: the neighbour of the target on the near side. One page then slides in from the side the pill came
     * from, and the pages between are never drawn.
     */
    fun runUp(from: Int, target: Int): Int = when {
        abs(target - from) <= 1 -> from
        target > from -> target - 1
        else -> target + 1
    }
}

/** Who a horizontal drag that began on a scroller inside a tab belongs to. */
internal enum class SwipeOwner {
    /** Nothing has moved sideways yet. */
    Undecided,

    /** The scroller inside the tab. The pages stay where they are until the finger lifts. */
    Row,

    /** The pages: the scroller had nowhere to go when the drag began. */
    Pages,
}

/**
 * When a swipe between the tabs begins. A drag that starts where nothing scrolls sideways reaches the pager itself
 * and is a page swipe whenever [pagerEnabled]. A drag that starts on a row, a chip line or the hero is reported by
 * that scroller move by move, and [owner] decides from its first move.
 */
internal object TabSwipeRules {
    // Scrollers report whole pixels; anything smaller is rounding.
    private const val MOVED = 0.5f

    /** No swipe between the tabs while one of them is selecting, a sheet or menu is open, or a tab asked for a lock. */
    fun locked(selecting: Boolean, overlayOpen: Boolean, tabLock: Boolean): Boolean = selecting || overlayOpen || tabLock

    /** A drag that reaches the pager itself moves the pages unless [locked]. */
    fun pagerEnabled(locked: Boolean): Boolean = !locked

    /**
     * The owner after one sideways move of an inner scroller, which used [consumed] of it and left [leftOver].
     * The first move decides for the whole gesture. A scroller that could take any of it keeps the gesture, even
     * after it reaches its end. One that was already at its end, and so took nothing while the drag went on past
     * it, hands the gesture to the pages, unless they are [locked].
     */
    fun owner(current: SwipeOwner, consumed: Float, leftOver: Float, locked: Boolean): SwipeOwner = when {
        current != SwipeOwner.Undecided -> current
        abs(consumed) >= MOVED -> SwipeOwner.Row
        abs(leftOver) >= MOVED -> if (locked) SwipeOwner.Row else SwipeOwner.Pages
        else -> SwipeOwner.Undecided
    }

    /**
     * How much of a move the pages take before the scroller under the finger sees it, once the gesture is theirs.
     * [wanted] is the move and [offset] how far the pages stand from the nearest whole tab, both in the pages' own
     * direction; [page] is the width of a tab. Standing whole they take nothing here: the scroller is asked first
     * and the pages get what it leaves. Standing between two tabs they take the move, as far as either tab.
     */
    fun pagesFirst(wanted: Float, offset: Float, page: Float): Float {
        if (abs(offset) < MOVED || page <= 0f) return 0f
        val back = -offset
        val on = if (offset > 0f) page - offset else -page - offset
        return wanted.coerceIn(minOf(back, on), maxOf(back, on))
    }

    /** True when what an inner scroller left over may move the pages. Otherwise it is dropped. */
    fun passesToPages(owner: SwipeOwner): Boolean = owner == SwipeOwner.Pages

    /**
     * A finger's sideways speed or move as that of the pages, where forward is toward the next tab: a finger moving
     * left brings the next tab in, and right-to-left it is the other way round. Applied twice it gives the finger's
     * own direction back.
     */
    fun pageward(fingerX: Float, rtl: Boolean): Float = if (rtl) fingerX else -fingerX
}
