package com.frameglass.playerui

import android.content.Context
import com.frameglass.data.api.PlayRef

/**
 * Holds the one [PlayerSession] of the process. The activity and the service both hold it, and it is
 * released when neither does, so background audio can outlive the activity and the other way round.
 */
internal object PlaybackHost {
    private var session: PlayerSession? = null
    private var activities = 0
    private var serviceRunning = false

    /**
     * Set by the player window while it is a pop-up (in picture-in-picture). A second "Play in pop-up" hands its
     * video to this, so the pop-up plays it where it stands and no new window opens over the library.
     */
    var popUp: ((PlayRef) -> Unit)? = null

    private fun session(context: Context): PlayerSession =
        session ?: PlayerSession(context).also { session = it }

    fun attachActivity(context: Context): PlayerSession {
        activities++
        return session(context)
    }

    /** A new player can open while the last one is still closing, so closing must not end its playback. */
    val isLastActivity: Boolean get() = activities == 1

    fun detachActivity() {
        activities--
        releaseIfUnused()
    }

    fun attachService(context: Context): PlayerSession {
        serviceRunning = true
        return session(context)
    }

    fun detachService() {
        serviceRunning = false
        releaseIfUnused()
    }

    private fun releaseIfUnused() {
        if (activities > 0 || serviceRunning) return
        session?.release()
        session = null
    }
}
