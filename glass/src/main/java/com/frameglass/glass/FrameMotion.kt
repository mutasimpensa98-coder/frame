package com.frameglass.glass

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.sqrt

/** Spring tokens and motion limits from brief 7.6. */
object FrameMotion {
    /** Press and small toggles: damping 0.6, stiffness 800. */
    fun <T> fastSpatial(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Tab pill and sheets: damping 0.8, stiffness 380. */
    fun <T> defaultSpatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Large morphs: damping 0.8, stiffness 200. */
    fun <T> slowSpatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    /** Alpha and tint: damping 1.0, stiffness 1600. */
    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    /**
     * Something carried by a finger, such as the tab pill under a drag: damping 1.0, stiffness 1600. It catches up
     * with the finger in a few frames and never passes it.
     */
    fun <T> follow(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = FollowStiffness)

    /** Stiffness of [follow]. */
    const val FollowStiffness = 1600f

    /** Nearer to the finger than this share of where it started, something that follows is on it. */
    const val FollowDone = 0.002f

    /**
     * The share of its distance to a finger that something on [follow], at rest when the finger took it, still has
     * to go after [seconds]: 1 at the start and 0 once it is on the finger. The tab pill closes its distance this
     * way, and the pages a drag along the bar carries use it to close theirs in step with the pill.
     */
    fun followLeft(seconds: Float): Float {
        if (seconds <= 0f) return 1f
        val turns = sqrt(FollowStiffness) * seconds
        val left = (1f + turns) * exp(-turns)
        return if (left < FollowDone) 0f else left
    }

    /**
     * Whole pages sliding into place: damping 1.0, stiffness 380. The pace of [defaultSpatial] with no overshoot,
     * because a page that overshoots shows the edge of a third one.
     */
    fun <T> page(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 380f)

    /** Scale a glass control swells to while pressed. */
    const val PressScale = 1.04f

    /** Share of its width the tab pill may stretch mid-move. */
    const val PillStretch = 0.18f

    /** Duration of a contrast tint change. */
    const val TintMillis = 200

    /** Furthest the rim highlight moves with device tilt. */
    val MaxTiltShift = 6.dp
}
