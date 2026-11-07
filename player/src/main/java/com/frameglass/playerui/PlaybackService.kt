package com.frameglass.playerui

import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Its own class, so the manifest entry cannot collide with a FileProvider from a library. */
class PlayerFileProvider : FileProvider()

/**
 * F-P16. Android 17 silences audio from an app that is neither visible, in picture-in-picture nor running
 * a foreground service, so Media3 runs this as a mediaPlayback foreground service while playing.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var session: PlayerSession? = null
    private var buttonLocales: LocaleList? = null

    override fun onCreate() {
        super.onCreate()
        val owned = PlaybackHost.attachService(this)
        session = owned
        buttonLocales = resources.configuration.locales
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, owned.player)
            .setId(SESSION_ID)
            .setSessionActivity(open)
            .setMediaButtonPreferences(mediaButtons())
            .build()
    }

    private fun mediaButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setDisplayName(getString(R.string.player_back_10))
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setDisplayName(getString(R.string.player_forward_10))
            .build(),
    )

    /** Lets the notification follow a language change while playback runs. Nothing here touches the player. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.locales == buttonLocales) return
        buttonLocales = newConfig.locales
        mediaSession?.setMediaButtonPreferences(mediaButtons())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** After a process death Android may restart this with nothing to play, so it leaves at once. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null && session?.state?.item == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Swiping the app away from recents ends playback, background audio included. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.savePosition()
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        session = null
        PlaybackHost.detachService()
        super.onDestroy()
    }

    private companion object {
        const val SESSION_ID = "frame-glass"
    }
}
