package com.frameglass.player.nav

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// How long after the finger lifts the scroller may take to report it, before the pages settle by themselves.
private const val LostReleaseMillis = 250L

/**
 * Sits between the tabs' pager and the scrollers inside its pages (the pager's page nested-scroll connection),
 * and decides what a sideways drag that began on one of those scrollers does to the pages. The decision itself is
 * [TabSwipeRules.owner]; this class only applies it.
 *
 * - A scroller that could move keeps the gesture: what it leaves over at its end, and whatever its fling leaves
 *   over, is dropped here and never reaches the pager.
 * - A scroller that was already at its end hands the gesture over: what it leaves over goes on to the pager, which
 *   moves with the finger. While the pages stand between two tabs they take every move first, so a drag that
 *   turns back makes them whole again before the scroller moves. When the finger lifts, the pages settle with
 *   the pager's own fling, by this class, because the scroller that reported the drag may leave the screen before
 *   a settle it owned had finished.
 */
@Stable
internal class TabSwipeArbiter(
    private val pager: PagerState,
    private val scope: CoroutineScope,
    /** Read when a gesture is decided, so a lock set after the finger went down still holds for that gesture. */
    private val locked: () -> Boolean,
    private val rtl: () -> Boolean,
    /** The pager's own fling, which settles the pages the same way after either kind of swipe. */
    private val fling: () -> FlingBehavior,
) : NestedScrollConnection {
    private var owner by mutableStateOf(SwipeOwner.Undecided)
    private var settling: Job? = null
    private var settlingNow by mutableStateOf(false)
    private var gesture = 0

    /** True while a finger is down on the pages, whatever it is doing there. */
    var fingerDown by mutableStateOf(false)
        private set

    /**
     * True from the moment an inner drag starts to move the pages until they have settled. The pager does not
     * count such a drag as a scroll in progress, so whoever needs to know that a hand is moving the pages asks here.
     * It is worked out from the owner and the settle, and every path out of an owner of Pages ends in a settle, so
     * it cannot stay true. Whoever waits on it still waits with a time limit.
     */
    val active: Boolean get() = TabSwipeRules.handActive(owner, settlingNow)

    fun onDown() {
        gesture++
        fingerDown = true
        // The last gesture moved the pages and its scroller never said the finger had left: the watchdog below
        // only covers a pause, and this touch came inside it.
        if (TabSwipeRules.settlesOnDown(owner, settling?.isActive == true)) settle(0f)
        owner = SwipeOwner.Undecided
    }

    /** The finger left. A scroller that never reports it must not leave the pages between two tabs. */
    fun onUp() {
        fingerDown = false
        if (owner != SwipeOwner.Pages || settling?.isActive == true) return
        val lifted = gesture
        scope.launch {
            delay(LostReleaseMillis)
            if (gesture == lifted && TabSwipeRules.settlesOnDown(owner, settling?.isActive == true)) settle(0f)
        }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (owner != SwipeOwner.Pages || source != NestedScrollSource.UserInput) return Offset.Zero
        val page = (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing).toFloat()
        val wanted = TabSwipeRules.pageward(available.x, rtl())
        val taken = TabSwipeRules.pagesFirst(wanted, pager.currentPageOffsetFraction * page, page)
        if (taken == 0f) return Offset.Zero
        // The same fold turns the pages' own direction back into the finger's.
        return Offset(TabSwipeRules.pageward(pager.dispatchRawDelta(taken), rtl()), 0f)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) {
            // A fling that reaches the end of its row stops there, as under the pager's stock connection. Left
            // running it would go on with nothing to move, and the next tap on the row would only stop it.
            if (available.x != 0f) throw CancellationException("The fling ends with its row")
            return Offset.Zero
        }
        val before = owner
        owner = TabSwipeRules.owner(before, consumed.x, available.x, locked())
        if (owner == SwipeOwner.Pages && before != SwipeOwner.Pages) {
            // A settle still running from the last swipe would hold the pages against the finger.
            settling?.cancel()
            settling = null
            settlingNow = false
        }
        // Zero lets the pager behind this connection take what is left; anything else is dropped here.
        return if (TabSwipeRules.passesToPages(owner)) Offset.Zero else Offset(available.x, 0f)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (owner != SwipeOwner.Pages) return Velocity.Zero
        settle(available.x)
        // The scroller keeps nothing to fling with: the gesture was the pages'.
        return Velocity(available.x, 0f)
    }

    // A fling inside a tab never carries over into a change of tab.
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity(available.x, 0f)

    private fun settle(fingerVelocity: Float) {
        val flingNow = fling()
        val velocity = TabSwipeRules.pageward(fingerVelocity, rtl())
        settling?.cancel()
        settlingNow = true
        settling = scope.launch {
            try {
                // Held as firmly as a finger holds the pages. When the scroller's own fling ends, the pager snaps by
                // itself with no speed at all, and at a lower priority that snap would cut this settle short.
                pager.scroll(MutatePriority.UserInput) { with(flingNow) { performFling(velocity) } }
            } finally {
                if (settling === coroutineContext[Job]) finish()
            }
        }
    }

    private fun finish() {
        settlingNow = false
        if (owner == SwipeOwner.Pages) owner = SwipeOwner.Undecided
    }
}

/** Tells [arbiter] where each gesture on the pager begins and ends. It watches and takes nothing. */
internal fun Modifier.watchTabGestures(arbiter: TabSwipeArbiter): Modifier = pointerInput(arbiter) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        arbiter.onDown()
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } finally {
            arbiter.onUp()
        }
    }
}
