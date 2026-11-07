package com.frameglass.player.nav

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.frameglass.glass.EffectsLevel
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.rememberEffectsLevel

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

private val ArtBounds = BoundsTransform { _, _ -> FrameMotion.slowSpatial<Rect>() }

/** A cover that flies between Home or Library and Details. Heavy motion is off while degraded (brief 7.9). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedArt(key: String?): Modifier {
    val shared = LocalSharedScope.current
    if (key == null || shared == null || rememberEffectsLevel() == EffectsLevel.Degraded) return this
    val scope = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedArt.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scope,
            enter = fadeIn(FrameMotion.effects()),
            exit = fadeOut(FrameMotion.effects()),
            boundsTransform = ArtBounds,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop, Alignment.Center),
        )
    }
}
