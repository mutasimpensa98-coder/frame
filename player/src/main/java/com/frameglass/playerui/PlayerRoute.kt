package com.frameglass.playerui

import android.content.Context
import android.content.Intent
import com.frameglass.data.api.PlayRef

/** How the rest of the app opens the player. */
object PlayerRoute {
    private const val EXTRA_PLAYABLE_KEY = "playableKey"
    private const val EXTRA_FILE_ROW_ID = "fileRowId"
    private const val EXTRA_URI = "uri"

    fun intent(context: Context, ref: PlayRef): Intent =
        Intent(context, PlayerActivity::class.java).apply {
            ref.playableKey?.let { putExtra(EXTRA_PLAYABLE_KEY, it) }
            ref.fileRowId?.let { putExtra(EXTRA_FILE_ROW_ID, it) }
            ref.uri?.let { putExtra(EXTRA_URI, it) }
        }

    /** Null when the intent names nothing to play, such as a tap on the media notification. */
    internal fun refOf(intent: Intent): PlayRef? {
        intent.getStringExtra(EXTRA_PLAYABLE_KEY)?.let { return PlayRef(playableKey = it) }
        if (intent.hasExtra(EXTRA_FILE_ROW_ID)) return PlayRef(fileRowId = intent.getLongExtra(EXTRA_FILE_ROW_ID, 0L))
        intent.getStringExtra(EXTRA_URI)?.let { return PlayRef(uri = it) }
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let { return PlayRef(uri = it.toString()) }
        return null
    }
}
