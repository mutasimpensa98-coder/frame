package com.frameglass.data.logic

import com.frameglass.data.api.AutoBackupMode
import com.frameglass.data.api.CleanupItem
import com.frameglass.data.api.CleanupReason
import com.frameglass.data.api.CleanupSuggestions
import com.frameglass.data.api.DayTotal
import com.frameglass.data.api.SeriesTotal
import com.frameglass.data.api.TitleKind
import com.frameglass.data.api.WatchStats
import com.frameglass.data.db.WatchLog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Which part of the time between two position saves counts as watched, in video time. The player saves about every
 * 5 s while playing and on every pause, at [speed]. A step the wall clock explains at that speed counts whole. A
 * forward skip inside a short step counts only the playback around it, about the wall time at that speed, so a
 * 10 s skip drops just the skip. A jump after a pause, a seek back or a clock change counts nothing.
 */
internal object WatchTally {
    /** How far the position may run ahead of the wall clock at the playing speed before the step counts as a jump. */
    const val TOLERANCE = 1.5
    const val SLACK_MS = 1_500L
    val MAX_GAP_MS: Long = TimeUnit.SECONDS.toMillis(60)

    /** A step longer than this held a pause, so a jump inside it cannot be told from playback and counts nothing. */
    val PLAYING_STEP_MS: Long = TimeUnit.SECONDS.toMillis(15)
    private const val MIN_SPEED = 0.25f

    fun countedMs(previousPositionMs: Long, previousAt: Long, positionMs: Long, now: Long, speed: Float = 1f): Long {
        val wall = now - previousAt
        val moved = positionMs - previousPositionMs
        if (wall <= 0 || moved <= 0) return 0
        val rate = speed.coerceAtLeast(MIN_SPEED).toDouble()
        val limit = TOLERANCE * rate * minOf(wall, MAX_GAP_MS) + SLACK_MS
        if (moved <= limit) return moved
        if (wall > PLAYING_STEP_MS) return 0
        return minOf(moved, (wall * rate).toLong())
    }
}

/**
 * A title leaves the watchlist once every playable of it was finished after it joined the list (F-S3). Watched flags
 * are sticky, so an episode seen before only counts when it is seen again: one rewatched episode does not end a
 * series. [justMarked] are keys the owner marked watched by hand just now, which count as finished.
 */
internal object WatchlistRules {
    fun done(playables: List<Playable>, since: Long, justMarked: Set<String> = emptySet()): Boolean =
        playables.isNotEmpty() && playables.all { it.watched && (it.lastPlayedAt >= since || it.key in justMarked) }
}

/** The stats page from the watch log and the library. Private-folder time is in the totals and never under a name. */
internal object WatchStatsBuilder {
    const val TOP_SERIES = 5
    const val DAYS = 7

    fun build(logs: List<WatchLog>, s: Snapshot, today: LocalDate, firstDayOfWeek: DayOfWeek): WatchStats {
        val byDay = logs.groupBy({ it.day }, { it.watchedMs }).mapValues { (_, values) -> values.sum() }
        val end = today.toString()
        fun since(start: LocalDate): Long {
            val from = start.toString()
            return byDay.entries.sumOf { (day, ms) -> if (day >= from && day <= end) ms else 0L }
        }
        val days = (DAYS - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong()).toString()
            DayTotal(day, byDay[day] ?: 0L)
        }
        val series = logs.filter { it.titleId != WatchLog.NO_TITLE }
            .groupBy({ it.titleId }, { it.watchedMs })
            .mapNotNull { (titleId, values) ->
                val info = s.infos[titleId] ?: return@mapNotNull null
                if (info.title.kind == TitleKind.MOVIE || info.isClip) return@mapNotNull null
                SeriesTotal(titleId, info.title.name, values.sum()).takeIf { it.watchedMs > 0 }
            }
            .sortedWith(compareByDescending<SeriesTotal> { it.watchedMs }.thenBy { it.titleId })
            .take(TOP_SERIES)
        return WatchStats(
            weekMs = since(today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))),
            monthMs = since(today.withDayOfMonth(1)),
            allTimeMs = byDay.values.sum(),
            lastSevenDays = days,
            topSeries = series,
            filmsFinished = s.infos.values.count { it.isFilm && it.watched },
        )
    }
}

/**
 * The Suggestions section of Media Manager. Extra versions first, each with the version that stays, then watched
 * files biggest first. Clips are personal videos and never suggested. Private-folder files have no rows here.
 */
internal object CleanupRules {
    const val MAX_WATCHED = 30

    private val BIGGEST = compareByDescending<CleanupItem> { it.entry.sizeBytes }.thenBy { it.entry.id }

    fun build(s: Snapshot): CleanupSuggestions {
        val infos = (s.infos.values + s.hiddenInfos.values).filter { !it.isClip }
        val versions = ArrayList<CleanupItem>()
        val listed = HashSet<Long>()
        for (info in infos) {
            for (p in info.playables) {
                val live = p.files.filter { it.available }
                if (live.size < 2) continue
                val kept = Versions.pick(live)
                for (file in live) {
                    if (file.rowId == kept.rowId || !listed.add(file.rowId)) continue
                    versions += CleanupItem(
                        MediaViews.fileEntry(s, file), CleanupReason.EXTRA_VERSION, kept.relPath.substringAfterLast('/'),
                    )
                }
            }
        }
        // A playable with an extra version listed keeps its best copy out of every list, so selecting all can never
        // take each copy of one episode or film.
        val watched = infos.asSequence()
            .flatMap { info -> info.playables.asSequence().filter { it.watched } }
            .filter { p -> p.files.none { it.rowId in listed } }
            .flatMap { p -> p.files.asSequence().filter { it.available } }
            .distinctBy { it.rowId }
            .map { CleanupItem(MediaViews.fileEntry(s, it), CleanupReason.WATCHED) }
            .sortedWith(BIGGEST)
            .take(MAX_WATCHED)
            .toList()
        return CleanupSuggestions(versions.sortedWith(BIGGEST), watched)
    }
}

/**
 * Names and retention of automatic backups. Only files that match [isOurs] exactly are ever deleted, so anything
 * else in the owner's folder, a manual backup included, stays. Digits are Latin whatever the app language is.
 */
internal object AutoBackupRules {
    private const val PREFIX = "frame-glass-auto-"
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.ROOT)
    // [0-9] and not \d: ICU on Android matches Arabic-Indic digits with \d.
    private val NAME = Regex("""frame-glass-auto-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]\.zip""")

    /** Due a little early, so a job that runs late each time does not drift a whole period. */
    val GRACE_MS: Long = TimeUnit.HOURS.toMillis(2)

    fun fileName(at: Long, zone: ZoneId): String = PREFIX + STAMP.format(Instant.ofEpochMilli(at).atZone(zone)) + ".zip"

    fun isOurs(name: String): Boolean = NAME.matches(name)

    /** The names to delete so that [keep] of ours stay, newest by name, which is newest by time. */
    fun toDelete(names: List<String>, keep: Int): List<String> =
        names.filter(::isOurs).distinct().sortedDescending().drop(keep.coerceAtLeast(1))

    fun periodMs(mode: AutoBackupMode): Long? = when (mode) {
        AutoBackupMode.OFF -> null
        AutoBackupMode.DAILY -> TimeUnit.DAYS.toMillis(1)
        AutoBackupMode.WEEKLY -> TimeUnit.DAYS.toMillis(7)
    }

    fun due(mode: AutoBackupMode, lastSuccessAt: Long?, now: Long): Boolean {
        val period = periodMs(mode) ?: return false
        return lastSuccessAt == null || now - lastSuccessAt >= period - GRACE_MS
    }
}
