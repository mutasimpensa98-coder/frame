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
}
