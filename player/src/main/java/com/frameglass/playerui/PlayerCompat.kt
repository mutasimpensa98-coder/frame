package com.frameglass.playerui

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import java.util.UUID

/**
 * What the player window asks of Android that Android 11 and 12 answer differently (waiver of 2026-11-19). Kept out of
 * PlayerActivity so that file holds one-line calls only.
 */
internal object PlayerCompat {
    /** True from Android 12, where the system itself moves a playing video into picture-in-picture on Home. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val autoEntersPip: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * For the picture-in-picture buttons, which only this app sends. The not-exported flag arrives in API 33. Below it
     * the registration is a plain one, which any app could send to, so there [sign] and [isOwn] do the flag's job.
     * ContextCompat's fallback is not used, because it needs a permission the manifest removes on purpose.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerOwnReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    /** Known to this process alone, and new each time it starts. */
    private val ownToken: String = UUID.randomUUID().toString()

    /** Marks a broadcast as this app's own. The buttons are remade with each state change, so a new token reaches them. */
    fun sign(intent: Intent): Intent = intent.putExtra(EXTRA_OWN_TOKEN, ownToken)

    /** From API 33 the system lets no other app reach the receiver. Below it only a signed broadcast counts. */
    fun isOwn(intent: Intent): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || intent.getStringExtra(EXTRA_OWN_TOKEN) == ownToken

    private const val EXTRA_OWN_TOKEN = "com.frameglass.playerui.OWN"

    /** Both calls arrive in API 31. Android 11 enters from onUserLeaveHint instead. */
    fun autoEnter(builder: PictureInPictureParams.Builder, playing: Boolean): PictureInPictureParams.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(playing).setSeamlessResizeEnabled(false)
        }
        return builder
    }
}
