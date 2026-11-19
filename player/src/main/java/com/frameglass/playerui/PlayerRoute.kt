package com.frameglass.playerui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.frameglass.data.api.PlayRef
import java.util.UUID

/** What a player intent asks for. [popUp] is the "Play in pop-up" row: play, then go straight to picture-in-picture. */
internal data class PlayRequest(val ref: PlayRef?, val popUp: Boolean)

/** How the rest of the app opens the player. */
object PlayerRoute {
    private const val EXTRA_PLAYABLE_KEY = "playableKey"
    private const val EXTRA_FILE_ROW_ID = "fileRowId"
    private const val EXTRA_URI = "uri"
    private const val EXTRA_POP_UP = "popUp"

    /**
     * The value of the pop-up extra: known to this run of the app alone. The player is exported for "Open with", so any
     * app can send it an intent, and one that carries the extra without this value plays as a normal intent does.
     */
    private val popUpPass: String = UUID.randomUUID().toString()

    fun intent(context: Context, ref: PlayRef, popUp: Boolean = false): Intent =
        Intent(context, PlayerActivity::class.java).apply {
            for ((name, value) in extras(ref, popUp)) {
                when (value) {
                    is String -> putExtra(name, value)
                    is Long -> putExtra(name, value)
                }
            }
        }

    /**
     * Plays [ref] in a pop-up. A pop-up already on screen takes the new video where it stands; otherwise the player
     * opens and moves into picture-in-picture as soon as it knows the picture.
     */
    fun playInPopUp(context: Context, ref: PlayRef, options: Bundle? = null) {
        val running = PlaybackHost.popUp
        if (running != null) running(ref) else context.startActivity(intent(context, ref, popUp = true), options)
    }

    /** The extras as plain values, so what an intent carries is tested without an Intent. */
    internal fun extras(ref: PlayRef, popUp: Boolean): Map<String, Any> = buildMap {
        ref.playableKey?.let { put(EXTRA_PLAYABLE_KEY, it) }
        ref.fileRowId?.let { put(EXTRA_FILE_ROW_ID, it) }
        ref.uri?.let { put(EXTRA_URI, it) }
        if (popUp) put(EXTRA_POP_UP, popUpPass)
    }

    /** Reads [extras] back. [viewUri] is the data of an "Open with" intent, used when no extra names a video. */
    internal fun request(extras: Map<String, Any>, viewUri: String? = null): PlayRequest {
        val key = extras[EXTRA_PLAYABLE_KEY] as? String
        val row = extras[EXTRA_FILE_ROW_ID] as? Long
        val uri = extras[EXTRA_URI] as? String ?: viewUri
        val ref = when {
            key != null -> PlayRef(playableKey = key)
            row != null -> PlayRef(fileRowId = row)
            uri != null -> PlayRef(uri = uri)
            else -> null
        }
        return PlayRequest(ref, popUp = ref != null && extras[EXTRA_POP_UP] == popUpPass)
    }

    /** A request with no ref names nothing to play, such as a tap on the media notification. */
    internal fun requestOf(intent: Intent): PlayRequest {
        val extras = buildMap<String, Any> {
            intent.getStringExtra(EXTRA_PLAYABLE_KEY)?.let { put(EXTRA_PLAYABLE_KEY, it) }
            if (intent.hasExtra(EXTRA_FILE_ROW_ID)) put(EXTRA_FILE_ROW_ID, intent.getLongExtra(EXTRA_FILE_ROW_ID, 0L))
            intent.getStringExtra(EXTRA_URI)?.let { put(EXTRA_URI, it) }
            // Read as a string: another app's Boolean here comes back null, which is no pass.
            intent.getStringExtra(EXTRA_POP_UP)?.let { put(EXTRA_POP_UP, it) }
        }
        val viewUri = if (intent.action == Intent.ACTION_VIEW) intent.data?.toString() else null
        return request(extras, viewUri)
    }
}
