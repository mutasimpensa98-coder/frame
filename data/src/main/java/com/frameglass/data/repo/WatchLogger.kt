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
    suspend fun onPosition(key: String, titleId: Long?, positionMs: Long, speed: Float = 1f) {
        val now = clock.now()
        val previous = marks.put(key, Mark(positionMs, now)) ?: return
        add(titleId, WatchTally.countedMs(previous.positionMs, previous.at, positionMs, now, speed), now)
    }

    /**
     * The item ended or was marked finished at its credits, or the countdown moved on. Nothing is added: the credits
     * and the rest of a file the countdown skipped were not watched, and the last save is at most one step behind.
     */
    fun onFinished(key: String) {
        marks.remove(key)
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

/** F-S3 for the watchlist, under [WatchlistRules]. Unwatching does not bring a title back. */
internal object WatchlistKeeper {
    suspend fun settle(db: FrameDb, titleId: Long?) {
        settle(db, titleId, emptySet())
    }

    /** [justMarked] are keys the owner just marked watched by hand. */
    suspend fun settle(db: FrameDb, titleId: Long?, justMarked: Set<String>) {
        val id = titleId ?: return
        val titles = db.titles()
        val since = titles.title(id)?.watchlistAt ?: return
        val states = db.watch().ofTitle(id).associateBy { it.playableKey }
        val playables = Assembler.playables(id, db.scan().filesOfTitle(id), titles.episodeRows(id)) { states[it] }
        if (WatchlistRules.done(playables, since, justMarked)) titles.setWatchlist(id, null)
    }
}
