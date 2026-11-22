package com.frameglass.glass

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/** What holds the pill of [LiquidTabBar] at one instant. */
internal enum class PillTie {
    /** Nothing: the pill goes to its tab by itself, on a spring. */
    None,

    /** A finger dragged along the bar. */
    Finger,

    /** The pages behind the bar, while a hand swipes them and while they rest. */
    Pages,

    /** Its own tab, which slides while another tab arrives or leaves. */
    Tab,
}

/**
 * What holds the pill at one instant: [tie] at [unit], and [goal], the unit it rests on when nothing holds it.
 * [anchor] names what the holder itself stands on, such as the selected tab. A holder whose anchor changes has
 * moved to another place in one step, which is a new hold and not a move to follow.
 */
internal data class PillHold(val tie: PillTie, val unit: Float, val goal: Float, val anchor: Any? = null) {
    companion object {
        /**
         * One source of truth for the pill: the finger on the bar, then the tab just asked for, then the pages,
         * then its own tab. [centers] are the tabs' centres in units and [rest] the index of the tab the pill
         * rests on, which is the tab asked for while [pending]; [restKey] tells that tab from the others when the
         * list changes. [drag] is the finger's unit or NaN, [page] the place of the pages in tabs or null, and
         * [resizing] is true while a tab arrives or leaves.
         */
        fun of(
            centers: FloatArray,
            rest: Int,
            restKey: Any?,
            drag: Float,
            pending: Boolean,
            page: Float?,
            resizing: Boolean,
        ): PillHold {
            if (centers.isEmpty()) return PillHold(PillTie.None, 0.5f, 0.5f)
            val goal = centers[rest.coerceIn(0, centers.lastIndex)]
            return when {
                !drag.isNaN() -> PillHold(PillTie.Finger, TabBarMath.clamp(drag, centers), goal)
                // A tab the bar asked for: the pill goes there by itself, ahead of the pages.
                pending -> PillHold(PillTie.None, goal, goal)
                page != null -> PillHold(PillTie.Pages, TabBarMath.unitAt(page, centers), goal, restKey)
                // While a tab arrives or leaves, the selected one slides, and the pill stays on it.
                resizing -> PillHold(PillTie.Tab, goal, goal, restKey)
                else -> PillHold(PillTie.None, goal, goal)
            }
        }
    }
}

/**
 * Where the pill of [LiquidTabBar] is, in units ([TabBarMath]), as plain numbers, so it is tested without a screen.
 *
 * While something holds the pill it is drawn exactly where that thing is, read at the moment of drawing: [at] is the
 * holder's place plus a lag that only ever shrinks to nothing. So a finger or a page that moves on every frame is
 * followed on every frame. An animation toward the finger, started again at each move, never gets past its first
 * frame, and the pill then stands still until the finger does (seen on a phone on 2026-11-22). The lag is what is
 * left of the distance between the pill and its holder at the moment it was taken, so that taking it is not a jump.
 *
 * With nothing holding it the pill is on a spring toward the tab it rests on. [step] moves the lag and the spring
 * by the time of one frame. [at] and [step] change only this object, and [at] gives the same answer however often
 * it is asked within a frame.
 */
internal class PillMotion(start: Float) {
    /** What holds the pill, as last seen by [at]. */
    var tie = PillTie.None
        private set

    /** Where the pill is. */
    var shown = start
        private set

    /** How fast it moves, in units per second: the spring's own speed, or the holder's, smoothed. */
    var speed = 0f
        private set

    private var anchor: Any? = null
    private var lag = 0f
    private var lagSpeed = 0f
    private var velocity = 0f

    // Where the pill was at the end of the last step, for the speed of a holder.
    private var stepped = start

    // The spring being run by [run].
    private var x = 0f
    private var v = 0f

    /**
     * The pill's place with [tie] holding it at [unit]. A change of holder is taken here, by whoever sees it first:
     * the pill stays where it was, and the distance to the new holder becomes the lag. A holder whose [anchor]
     * changed, such as the pages of another list of tabs, is a new holder in the same way.
     */
    fun at(tie: PillTie, unit: Float, anchor: Any? = null): Float {
        val held = if (unit.isNaN()) PillTie.None else tie
        if (held != this.tie) {
            // The speed the pill had goes on: into the spring when it is let go, into the lag when it is taken.
            val carried = speed.coerceIn(-MaxCarriedSpeed, MaxCarriedSpeed)
            if (held == PillTie.None) {
                velocity = carried
                lag = 0f
                lagSpeed = 0f
            } else {
                lag = shown - unit
                lagSpeed = carried
            }
            this.tie = held
        } else if (held != PillTie.None && anchor != this.anchor) {
            lag = shown - unit
        }
        this.anchor = anchor
        if (held != PillTie.None) shown = unit + lag
        return shown
    }

    /** [at] for a whole hold. */
    fun at(hold: PillHold): Float = at(hold.tie, hold.unit, hold.anchor)

    /**
     * One frame of [seconds]. [goal] is the unit the pill rests on when nothing holds it. With [instant], for
     * reduced motion, a held pill has no lag and a free one goes to its tab with no overshoot.
     * Returns true while there is more to do on the next frame without anything else changing.
     */
    fun step(seconds: Float, tie: PillTie, unit: Float, goal: Float, instant: Boolean, anchor: Any? = null): Boolean {
        at(tie, unit, anchor)
        val time = seconds.coerceIn(0f, MaxStep)
        if (this.tie != PillTie.None) {
            if (instant) {
                lag = 0f
                lagSpeed = 0f
            } else if (lag != 0f || lagSpeed != 0f) {
                x = lag
                v = lagSpeed
                run(0f, FollowStiffness, FollowDamping, time)
                val rests = abs(x) < Rest && abs(v) < RestSpeed
                lag = if (rests) 0f else x
                lagSpeed = if (rests) 0f else v
            }
            shown = unit + lag
            val moved = shown - stepped
            if (instant) {
                speed = 0f
            } else if (time > 0f) {
                speed += (moved / time - speed) * (1f - exp(-time / SpeedSmoothing))
                if (moved == 0f && abs(speed) < RestSpeed) speed = 0f
            }
            stepped = shown
            return lag != 0f || speed != 0f
        }
        x = shown
        v = velocity
        if (instant) run(goal, FollowStiffness, FollowDamping, time) else run(goal, FreeStiffness, FreeDamping, time)
        val rests = abs(x - goal) < Rest && abs(v) < RestSpeed
        shown = if (rests) goal else x
        velocity = if (rests) 0f else v
        speed = velocity
        stepped = shown
        return !rests
    }

    // A damped spring on x and v toward [target], in slices short enough to stay exact at these stiffnesses.
    private fun run(target: Float, stiffness: Float, damping: Float, seconds: Float) {
        val drag = 2f * damping * sqrt(stiffness)
        var left = seconds
        while (left > 0f) {
            val slice = min(left, Slice)
            v += (-stiffness * (x - target) - drag * v) * slice
            x += v * slice
            left -= slice
        }
    }

    companion object {
        /** A frame longer than this, as after a stall, is stepped as this much. */
        const val MaxStep = 0.05f

        /** The time given to the first frame after the pill has been at rest, when there is no frame before it. */
        const val FirstStep = 1f / 120f

        /** Nearer to its goal than this, in units, and slower than [RestSpeed], the pill is on it. */
        const val Rest = 0.002f
        const val RestSpeed = 0.05f

        /**
         * The fastest a hand's speed is carried on when the pill changes hands, in units per second. At this speed
         * the spring passes its tab by less than a fifteenth of a slot, which stays inside the bar at either end.
         */
        const val MaxCarriedSpeed = 3f

        private const val Slice = 0.002f

        // The time over which a holder's speed is averaged, so the pill's stretch does not flicker with the finger.
        private const val SpeedSmoothing = 0.06f

        // FrameMotion.follow(): the lag closes in a few frames and never passes the holder.
        private const val FollowStiffness = FrameMotion.FollowStiffness
        private const val FollowDamping = 1f

        // FrameMotion.defaultSpatial(): the pill going to a tab by itself.
        private const val FreeStiffness = 380f
        private const val FreeDamping = 0.8f
    }
}
