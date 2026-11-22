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

/** The pages of the tabs at one moment, as [TabLeadRules] needs them. */
internal data class PagesNow(
    /** The tab nearest to where the pages stand. */
    val page: Int,
    /** True when the pages stand whole on [page], and not between two tabs. */
    val whole: Boolean,
    /** True while the pager runs a scroll of any kind: a finger's drag, a fling, a slide. */
    val scrolling: Boolean,
    /** True while a drag handed over by a scroller inside a tab holds the pages, until they have settled. */
    val handed: Boolean,
    /** True while a finger is down on the pages. */
    val fingerDown: Boolean,
    /** True while a move the bar or a link asked for is under way. */
    val led: Boolean,
)

/**
 * Who the pages follow, and who is told what. Two things can move them: a hand, and a request from the bar, Back or
 * a link. The pages are the truth of what is in view, so a hand's swipe is reported when it rests. A request is the
 * other way round: from the moment it is made until the pages stand whole on the tab it named, or a hand takes them,
 * the selection is the truth, nothing is reported, and whatever cut the move short, the pages are brought there.
 * That time is `asked` below.
 */
internal object TabLeadRules {
    private fun moving(now: PagesNow): Boolean = now.scrolling || now.handed

    private fun wholeOn(now: PagesNow, page: Int): Boolean = now.whole && now.page == page

    /** A hand has taken the pages: a finger is down and they move, and no request is driving them. */
    fun handTook(now: PagesNow): Boolean = !now.led && now.fingerDown && moving(now)

    /** Whether a request made earlier still stands. It ends when a hand takes the pages, or when it is met. */
    fun stillAsked(asked: Boolean, now: PagesNow, selected: Int): Boolean =
        asked && !handTook(now) && !(!now.led && !moving(now) && wholeOn(now, selected))

    /**
     * The tab the caller should hear of, or null. Only pages standing whole with nothing moving them are a tab in
     * view, and never while a request stands: pages still on the tab they were asked to leave are not news.
     */
    fun report(now: PagesNow, asked: Boolean, selected: Int): Int? =
        now.page.takeIf { !asked && !now.led && !moving(now) && now.whole && it != selected }

    /**
     * Where the pages must be brought by the rest guard, or null while all is well or it must stand down. It stands
     * down while a request is under way, while a finger is down, and while anything moves the pages. Otherwise pages
     * between two tabs go to the nearer one, and pages a request never reached go to the tab that was asked for.
     */
    fun guard(now: PagesNow, asked: Boolean, selected: Int): Int? {
        if (now.led || now.fingerDown || moving(now)) return null
        val target = if (asked) selected else now.page
        return target.takeIf { !wholeOn(now, it) }
    }

    /**
     * True when the last move the bar asked for has ended, by itself or cut short, and the pages are neither whole on
     * [target] nor in a hand: they are set there at once, so no sequence of taps leaves the app between two tabs.
     */
    fun snapAfterLed(now: PagesNow, target: Int): Boolean = !now.fingerDown && !moving(now) && !wholeOn(now, target)

    /**
     * True while the bar's pill travels with the pages: while something moves them, and while they rest on the
     * selected tab. Led by a request, or standing on another tab than the selected one, the pill goes to the
     * selected tab by itself.
     */
    fun pillFollowsPages(now: PagesNow, selected: Int): Boolean = !now.led && (moving(now) || now.page == selected)
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

    /**
     * True while a handed-over drag holds the pages: from the move that gave it to them until their settle has
     * ended. Nothing else is remembered, so this cannot stay true by itself: an owner of Pages always ends in a
     * settle, by the scroller's report of the release, by the watchdog after it, or by the next touch.
     */
    fun handActive(owner: SwipeOwner, settling: Boolean): Boolean = owner == SwipeOwner.Pages || settling

    /**
     * True when a new touch finds the pages still owned by the last gesture with no settle running: the scroller
     * that held that gesture never reported its release, as when its row left the screen in mid-drag. The pages
     * settle now, before the new gesture is looked at.
     */
    fun settlesOnDown(owner: SwipeOwner, settling: Boolean): Boolean = owner == SwipeOwner.Pages && !settling

    /**
     * A mode of a tab, such as an open search, holds the tabs only while its tab is the one in view. A tab that is
     * on screen because a swipe toward it is passing must not stop that swipe.
     */
    fun modeHolds(open: Boolean, tabInView: Boolean): Boolean = open && tabInView

    /** A search field asks for the keyboard only on the tab in view, and only while it is empty. */
    fun raisesKeyboard(autoFocus: Boolean, tabInView: Boolean, query: String): Boolean =
        autoFocus && tabInView && query.isEmpty()

    /** True when what an inner scroller left over may move the pages. Otherwise it is dropped. */
    fun passesToPages(owner: SwipeOwner): Boolean = owner == SwipeOwner.Pages

    /**
     * A finger's sideways speed or move as that of the pages, where forward is toward the next tab: a finger moving
     * left brings the next tab in, and right-to-left it is the other way round. Applied twice it gives the finger's
     * own direction back.
     */
    fun pageward(fingerX: Float, rtl: Boolean): Float = if (rtl) fingerX else -fingerX
}
