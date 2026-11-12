package com.frameglass.data.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoilerRuleTest {
    @Test
    fun anUnstartedEpisodeWithItsOwnPictureIsBlurred() {
        assertTrue(SpoilerRule.hide(on = true, ownImage = "/still.jpg", watched = false, progress = null))
        assertTrue(SpoilerRule.hide(on = true, ownImage = "/still.jpg", watched = false, progress = 0f))
    }

    @Test
    fun startedOrWatchedEpisodesShowTheirPicture() {
        assertFalse(SpoilerRule.hide(on = true, ownImage = "/still.jpg", watched = false, progress = 0.2f))
        assertFalse(SpoilerRule.hide(on = true, ownImage = "/still.jpg", watched = true, progress = null))
    }

    @Test
    fun aStandInPictureOrTheSwitchOffIsNeverBlurred() {
        assertFalse(SpoilerRule.hide(on = true, ownImage = null, watched = false, progress = null))
        assertFalse(SpoilerRule.hide(on = false, ownImage = "/still.jpg", watched = false, progress = null))
    }
}
