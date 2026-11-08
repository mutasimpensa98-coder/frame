package com.frameglass.player.nav

import android.content.Context
import android.content.Intent
import com.frameglass.player.MainActivity

/**
 * Pages other parts of the app open in MainActivity by an explicit intent. The player lives in :player and cannot see
 * these screens, so it asks the Application for the intent through PlayerHost.
 */
internal object AppLinks {
    const val ACTION_SUBTITLE_KEYS = "com.frameglass.player.action.SUBTITLE_KEYS"

    /** Clear-top brings back the MainActivity below the player and closes the player, which saves its position. */
    fun subtitleKeys(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_SUBTITLE_KEYS)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** The page an intent action asks for, or null for a plain launch. */
    fun routeOf(action: String?): Route? = when (action) {
        ACTION_SUBTITLE_KEYS -> Route.Settings(SettingsTopic.ONLINE)
        else -> null
    }
}
