package com.frameglass.data.repo

import androidx.room.withTransaction
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.WatchLog
import com.frameglass.data.logic.Assembler
import com.frameglass.data.logic.Clock
import com.frameglass.data.logic.EpisodeRows
import com.frameglass.data.logic.WatchTally
import com.frameglass.data.logic.WatchlistRules
import java.util.concurrent.ConcurrentHashMap

/**
 * Adds watched time to the local log from the position saves, so the player needs no change. Only forward playback
 * between two saves counts, under [WatchTally]. The last save of each item lives in memory, so the first save after
 * the app starts only sets the mark.
 */
internal class WatchLogger(private val db: FrameDb, private val clock: Clock) {
    private class Mark(val positionMs: Long, val at: Long)

    private val marks = ConcurrentHashMap<String, Mark>()

    /** [titleId] is null for an item with no title, and must be null for a private-folder video. */
    suspend fun onPosition(key: String, titleId: Long?, positionMs: Long) {
        val now = clock.now()
        val previous = marks.put(key, Mark(positionMs, now)) ?: return
        add(titleId, WatchTally.countedMs(previous.positionMs, previous.at, positionMs, now), now)
    }

    /** The stretch from the last save to the end, when the item played through. */
    suspend fun onFinished(key: String, titleId: Long?, durationMs: Long) {
        val now = clock.now()
        val previous = marks.remove(key) ?: return
        if (durationMs > 0) add(titleId, WatchTally.countedMs(previous.positionMs, previous.at, durationMs, now), now)
    }

    private suspend fun add(titleId: Long?, ms: Long, now: Long) {
        if (ms <= 0) return
        val day = EpisodeRows.today(now)
        val id = titleId ?: WatchLog.NO_TITLE
        val watch = db.watch()
        db.withTransaction {
            val old = watch.logRow(day, id)
            watch.upsertLog(WatchLog(day, id, (old?.watchedMs ?: 0) + ms))
        }
    }
}

/** F-S3 for the watchlist: once a title is all watched, it leaves the list. Unwatching does not bring it back. */
internal object WatchlistKeeper {
    suspend fun settle(db: FrameDb, titleId: Long?) {
        val id = titleId ?: return
        val titles = db.titles()
        if (titles.title(id)?.watchlistAt == null) return
        val states = db.watch().ofTitle(id).associateBy { it.playableKey }
        val playables = Assembler.playables(id, db.scan().filesOfTitle(id), titles.episodeRows(id)) { states[it] }
        if (WatchlistRules.done(playables)) titles.setWatchlist(id, null)
    }
}
