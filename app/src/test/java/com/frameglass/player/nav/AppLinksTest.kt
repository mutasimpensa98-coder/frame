package com.frameglass.player.nav

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinksTest {
    @Test
    fun theSubtitleKeysLinkOpensTheOnlineSettingsPage() {
        assertEquals(Route.Settings(SettingsTopic.ONLINE), AppLinks.routeOf(AppLinks.ACTION_SUBTITLE_KEYS))
    }

    @Test
    fun aPlainLaunchOpensNoPage() {
        assertNull(AppLinks.routeOf(Intent.ACTION_MAIN))
        assertNull(AppLinks.routeOf(null))
        assertNull(AppLinks.routeOf("com.frameglass.player.action.SOMETHING_ELSE"))
    }

    @Test
    fun theLinkOpensOnTopOnceAndNeverOverOnboarding() {
        val keys = Route.Settings(SettingsTopic.ONLINE)
        assertEquals(true, AppLinks.shouldOpen(listOf(Route.Tabs, Route.Details(3)), keys))
        assertEquals(false, AppLinks.shouldOpen(listOf(Route.Tabs, keys), keys))
        assertEquals(false, AppLinks.shouldOpen(listOf(Route.Onboarding), keys))
        assertEquals(true, AppLinks.shouldOpen(emptyList(), keys))
    }

    @Test
    fun theLinkNeverOpensOverTheFirstRunTourButDoesOverTheOneFromSettings() {
        val keys = Route.Settings(SettingsTopic.ONLINE)
        assertEquals(false, AppLinks.shouldOpen(listOf(Route.Tour(setup = true)), keys))
        assertEquals(true, AppLinks.shouldOpen(listOf(Route.Tabs, Route.Tour(setup = false)), keys))
    }
}
