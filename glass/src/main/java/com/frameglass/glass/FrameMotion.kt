package com.frameglass.glass

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

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

    /** Scale a glass control swells to while pressed. */
    const val PressScale = 1.04f

    /** Share of its width the tab pill may stretch mid-move. */
    const val PillStretch = 0.18f

    /** Duration of a contrast tint change. */
    const val TintMillis = 200

    /** Furthest the rim highlight moves with device tilt. */
    val MaxTiltShift = 6.dp
}
