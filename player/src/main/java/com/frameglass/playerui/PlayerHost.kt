package com.frameglass.playerui

import android.content.Context
import android.content.Intent

/**
 * Optional, implemented by the Application. :player cannot see the app's screens, so the subtitle sheet asks the host
 * for an intent to the subtitle keys page. Without it, or when the intent would leave this app, the sheet shows text only.
 */
interface PlayerHost {
    fun subtitleKeysIntent(context: Context): Intent?
}

/** Only an explicit intent into this very app is started from the sheet. */
internal fun Context.subtitleKeysIntent(): Intent? {
    val intent = (applicationContext as? PlayerHost)?.subtitleKeysIntent(this) ?: return null
    val target = intent.component?.packageName ?: return null
    return intent.takeIf { target == packageName }
}
