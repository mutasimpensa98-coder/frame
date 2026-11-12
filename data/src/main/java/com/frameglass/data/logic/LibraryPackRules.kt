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
 * Which part of the time between two position saves counts as watched. Only forward playback counts: a seek, a jump
 * or a resume elsewhere moves the position faster than about twice the wall clock, and that whole step is left out.
 * The wall time is capped, so a long pause followed by a jump cannot pass as playback.
 */
internal object WatchTally {
    const val MAX_RATE = 2.0
    const val SLACK_MS = 1_500L
    val MAX_GAP_MS: Long = TimeUnit.SECONDS.toMillis(60)

    fun countedMs(previousPositionMs: Long, previousAt: Long, positionMs: Long, now: Long): Long {
        val wall = now - previousAt
        val moved = positionMs - previousPositionMs
        if (wall <= 0 || moved <= 0) return 0
        val limit = MAX_RATE * minOf(wall, MAX_GAP_MS) + SLACK_MS
        return if (moved > limit) 0 else moved
    }
}

/** A title leaves the watchlist once everything of it the owner has is watched (F-S3). */
internal object WatchlistRules {
    fun done(playables: List<Playable>): Boolean = playables.isNotEmpty() && playables.all { it.watched }
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
        val watched = infos.asSequence()
            .flatMap { info -> info.playables.asSequence().filter { it.watched } }
            .flatMap { p -> p.files.asSequence().filter { it.available && it.rowId !in listed } }
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
    private val NAME = Regex("""frame-glass-auto-\d\d\d\d-\d\d-\d\d-\d\d\d\d\d\d\.zip""")

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
