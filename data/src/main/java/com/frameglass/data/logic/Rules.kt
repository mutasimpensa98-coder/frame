package com.frameglass.data.logic

import com.frameglass.data.api.SkipSegment
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.IntroSegment
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.WatchState
import java.util.concurrent.TimeUnit

/** F-S3. */
internal object WatchRules {
    const val WATCHED_RATIO = 0.90

    fun onPosition(prev: WatchState?, key: String, titleId: Long?, positionMs: Long, durationMs: Long, now: Long): WatchState {
        val reached = durationMs > 0 && positionMs >= durationMs * WATCHED_RATIO
        return WatchState(
            playableKey = key,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = if (durationMs > 0) durationMs else prev?.durationMs ?: 0,
            watched = prev?.watched == true || reached,
            lastPlayedAt = now,
            titleId = titleId ?: prev?.titleId,
        )
    }

    fun onFinished(prev: WatchState?, key: String, titleId: Long?, durationMs: Long, now: Long): WatchState =
        WatchState(
            playableKey = key,
            positionMs = 0,
            durationMs = if (durationMs > 0) durationMs else prev?.durationMs ?: 0,
            watched = true,
            lastPlayedAt = now,
            titleId = titleId ?: prev?.titleId,
        )

    /** A hand mark must not make the item look recently played, so lastPlayedAt stays as it was. */
    fun onManual(prev: WatchState?, key: String, titleId: Long?, watched: Boolean): WatchState =
        WatchState(
            playableKey = key,
            positionMs = 0,
            durationMs = prev?.durationMs ?: 0,
            watched = watched,
            lastPlayedAt = prev?.lastPlayedAt ?: 0,
            titleId = titleId ?: prev?.titleId,
        )

    /**
     * Watched titles restart from 0, except a rewatch cut off mid-way, which F-P15 must restore. Past the
     * watched mark there is nothing worth restoring, so that case restarts as well.
     */
    fun resumeMs(state: WatchState?, sessionLive: Boolean): Long {
        if (state == null) return 0
        if (!state.watched) return state.positionMs
        val midway = state.durationMs > 0 && state.positionMs < state.durationMs * WATCHED_RATIO
        return if (sessionLive && midway) state.positionMs else 0
    }

    fun merge(a: WatchState?, b: WatchState): WatchState {
        if (a == null) return b
        val newer = if (b.lastPlayedAt >= a.lastPlayedAt) b else a
        val older = if (newer === b) a else b
        return newer.copy(
            watched = a.watched || b.watched,
            durationMs = maxOf(a.durationMs, b.durationMs),
            positionMs = if (newer.positionMs > 0) newer.positionMs else older.positionMs,
        )
    }
}

/** F-S10. [items] must be in episode order. */
internal object ResumeLogic {
    fun pick(items: List<Playable>): Playable? {
        val latest = items.filter { it.lastPlayedAt > 0 }.maxByOrNull { it.lastPlayedAt }
        if (latest != null && !latest.watched && latest.available) return latest
        val anchor = latest ?: items.lastOrNull { it.watched }
        val start = if (anchor == null) 0 else items.indexOf(anchor) + 1
        return (items.drop(start) + items.take(start)).firstOrNull { !it.watched && it.available }
    }
}

/** Clips against titles. A match always wins, so Fix match can still turn a clip into a film. */
internal object ClipRules {
    val SHORT_MS: Long = TimeUnit.MINUTES.toMillis(20)
    val FILM_MS: Long = TimeUnit.MINUTES.toMillis(40)

    private fun isShort(durationMs: Long) = durationMs in 1 until SHORT_MS

    /** Episodes stay in Series at any length: a 12 minute anime is not a clip. */
    fun isClip(kind: TitleKind, matched: Boolean, clipName: Boolean, durationMs: Long): Boolean =
        !matched && (clipName || (kind == TitleKind.MOVIE && isShort(durationMs)))

    fun isFilm(kind: TitleKind, matched: Boolean, clip: Boolean, durationMs: Long): Boolean =
        kind == TitleKind.MOVIE && !clip && (matched || durationMs >= FILM_MS)

    /**
     * A clip must never reach TMDB, and a home video called Birthday Party must not come back as a film. A year
     * or an episode number earns a short video one lookup: if that finds nothing, [isClip] takes it from there.
     */
    fun skipsLookup(kind: TitleKind, year: Int?, clipName: Boolean, durationMs: Long): Boolean =
        clipName || (kind == TitleKind.MOVIE && year == null && isShort(durationMs))
}

/** F-L9. */
internal object Versions {
    fun pick(files: List<MediaFile>): MediaFile {
        val pool = files.filter { it.available }.ifEmpty { files }
        return pool.maxWith(compareBy<MediaFile>({ it.height }, { it.size }).thenBy { -it.rowId })
    }
}

/** F-M11. */
internal object CachePolicy {
    const val REFRESH_DAYS = 150L
    const val DELETE_DAYS = 175L
    private val DAY = TimeUnit.DAYS.toMillis(1)

    enum class Action { KEEP, REFRESH, PURGE }

    fun decide(fetchedAt: Long?, now: Long): Action {
        if (fetchedAt == null) return Action.KEEP
        val age = now - fetchedAt
        return when {
            age >= DELETE_DAYS * DAY -> Action.PURGE
            age >= REFRESH_DAYS * DAY -> Action.REFRESH
            else -> Action.KEEP
        }
    }

    fun refreshCutoff(now: Long): Long = now - REFRESH_DAYS * DAY
    fun purgeCutoff(now: Long): Long = now - DELETE_DAYS * DAY
}

/** The metadata worker retries after 1 h, 6 h and 24 h, and keeps retrying daily after that. */
internal object RetryPolicy {
    private val STEPS = listOf(1L, 6L, 24L).map { TimeUnit.HOURS.toMillis(it) }

    fun delayMs(attempts: Int): Long = STEPS[(attempts - 1).coerceIn(0, STEPS.lastIndex)]
}

/** F-M7. */
internal object FramePicker {
    private val FRACTIONS = listOf(0.10, 0.20, 0.30)
    private val NO_DURATION_TIMES = listOf(1_000L, 5_000L, 15_000L)

    fun sampleTimesMs(durationMs: Long): List<Long> =
        if (durationMs > 0) FRACTIONS.map { (durationMs * it).toLong() } else NO_DURATION_TIMES

    private fun luma(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun meanLuma(pixels: IntArray): Double {
        if (pixels.isEmpty()) return 0.0
        var sum = 0.0
        for (p in pixels) sum += luma(p)
        return sum / pixels.size
    }

    fun lumaVariance(pixels: IntArray): Double {
        if (pixels.isEmpty()) return 0.0
        val mean = meanLuma(pixels)
        var sum = 0.0
        for (p in pixels) {
            val d = luma(p) - mean
            sum += d * d
        }
        return sum / pixels.size
    }
}

/** F-S4 order among stored rows: manual for this season, manual for all seasons, then stored results. */
internal object SegmentRules {
    const val MANUAL = "manual"

    fun pick(rows: List<IntroSegment>, season: Int?, episodeId: Long?): List<SkipSegment> =
        rows.groupBy { it.kind }.values.mapNotNull { best(it, season, episodeId) }
            .sortedBy { it.startMs }
            .map { SkipSegment(it.kind, it.startMs, it.endMs, it.source) }

    private fun best(rows: List<IntroSegment>, season: Int?, episodeId: Long?): IntroSegment? {
        val manual = rows.filter { it.source == MANUAL }
        manual.firstOrNull { it.season != null && it.season == season }?.let { return it }
        manual.firstOrNull { it.season == null }?.let { return it }
        return rows.filter { it.source != MANUAL }
            .filter { row ->
                if (row.episodeId != null) row.episodeId == episodeId
                else row.season == null || row.season == season
            }
            .maxByOrNull { it.confidence }
    }
}

internal data class AudioTrackInfo(val codec: String?, val channels: Int)

internal object AudioSummary {
    private val NAMES = mapOf(
        "aac" to "AAC", "ac3" to "AC-3", "eac3" to "E-AC-3", "dts" to "DTS", "truehd" to "TrueHD", "flac" to "FLAC",
        "opus" to "Opus", "vorbis" to "Vorbis", "mp3" to "MP3", "mp2" to "MP2", "alac" to "ALAC", "wmav2" to "WMA",
    )

    private fun channels(count: Int): String? = when {
        count <= 0 -> null
        count == 1 -> "1.0"
        count == 2 -> "2.0"
        count == 6 -> "5.1"
        count == 8 -> "7.1"
        else -> "${count}ch"
    }

    private fun label(track: AudioTrackInfo): String? {
        val raw = track.codec?.lowercase()
        val codec = when {
            raw == null -> null
            raw.startsWith("pcm") -> "PCM"
            else -> NAMES[raw] ?: raw.uppercase()
        }
        return listOfNotNull(codec, channels(track.channels)).joinToString(" ").ifEmpty { null }
    }

    fun of(tracks: List<AudioTrackInfo>): String? =
        tracks.mapNotNull(::label).distinct().take(3).joinToString(", ").ifEmpty { null }
}

internal object SearchText {
    /** Input is already an ArabicNormalizer key. Keeps letters and digits and turns the rest into spaces. */
    fun clean(key: String): String {
        val out = StringBuilder(key.length)
        var i = 0
        while (i < key.length) {
            val cp = key.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isLetterOrDigit(cp)) {
                out.appendCodePoint(cp)
            } else if (out.isNotEmpty() && out.last() != ' ') {
                out.append(' ')
            }
        }
        return out.toString().trim()
    }

    /** Prefix match on every word, so results appear while typing. */
    fun match(cleaned: String): String? =
        cleaned.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { "$it*" }.ifEmpty { null }
}
