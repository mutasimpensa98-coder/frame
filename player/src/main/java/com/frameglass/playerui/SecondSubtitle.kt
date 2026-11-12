package com.frameglass.playerui

import com.frameglass.engine.CueTimeline

/** Where the second subtitle sits: at the top of the picture, or just above the main one. */
internal enum class SecondPlacement { TOP, ABOVE_MAIN }

/**
 * Which track plays as the second subtitle. A file is known by its path, which survives a reopen, and an embedded
 * track by its place among the container's text tracks, because group numbers change when sidecars are added.
 */
internal data class SecondKey(val path: String?, val embeddedOrdinal: Int?) {
    companion object {
        fun of(row: TrackRow): SecondKey? = when {
            row.sidecarPath != null -> SecondKey(row.sidecarPath, null)
            row.embeddedOrdinal != null -> SecondKey(null, row.embeddedOrdinal)
            else -> null
        }
    }
}

/** What the second subtitle shows now. [loading] stays true while an embedded track is still being read. */
internal data class SecondShown(
    val key: SecondKey,
    val language: String?,
    val label: String?,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

internal object SecondPicker {
    /** Bitmap formats: the cue overlay cannot draw them, and they have no words to reflow. */
    private val BITMAP_CODECS = setOf("PGS", "VOBSUB", "DVBSUBS", "DVB")

    fun isText(row: TrackRow): Boolean = row.codec?.uppercase() !in BITMAP_CODECS

    /**
     * Every other text track the overlay can draw, files and embedded alike. [main] is the subtitle playing now, so
     * it is never offered twice. The same file listed twice (a sidecar that is also a live load) shows once.
     */
    fun candidates(rows: List<TrackRow>, main: TrackRow?): List<TrackRow> {
        val mainKey = main?.let(SecondKey::of)
        return rows
            .filter { it.supported && isText(it) }
            .filter { row -> SecondKey.of(row).let { it != null && it != mainKey } }
            .distinctBy { SecondKey.of(it) }
    }

    /** F-S6 for the second subtitle: by language, as the main one is remembered, among the candidates of this file. */
    fun remembered(rows: List<TrackRow>, main: TrackRow?, stored: String?): TrackRow? =
        TrackMemory.find(candidates(rows, main), stored)

    fun encode(rows: List<TrackRow>, main: TrackRow?, chosen: TrackRow): String =
        TrackMemory.encode(candidates(rows, main), chosen)

    /** Per title, so every episode of a series shares it. A video with no title remembers by itself. */
    fun memoryKey(titleId: Long?, playableKey: String): String =
        if (titleId != null) "second.t$titleId" else "second.p$playableKey"
}

/**
 * The second subtitle's clock. Positive delay shows it later, as the main one. A frame rate fix found by sync
 * stretches its times by [ratio], so video time = ratio × subtitle time + delay.
 */
internal object SecondTiming {
    fun delayMs(followsMain: Boolean, mainDelayMs: Long, ownDelayMs: Long): Long = if (followsMain) mainDelayMs else ownDelayMs

    /** The time to look up in the timeline for the video at [positionMs]. */
    fun lookupUs(positionMs: Long, delayMs: Long): Long = (positionMs - delayMs) * 1000

    /** An embedded track is timed for this video already, so it starts on its own clock with no delay. */
    fun followsMainAtStart(isFile: Boolean): Boolean = isFile

    fun <T> stretched(timeline: List<CueTimeline.Entry<T>>, ratio: Double): List<CueTimeline.Entry<T>> =
        if (ratio == 1.0) {
            timeline
        } else {
            timeline.map { entry ->
                val end = if (entry.endUs == Long.MAX_VALUE) Long.MAX_VALUE else (entry.endUs * ratio).toLong()
                entry.copy(startUs = (entry.startUs * ratio).toLong(), endUs = end)
            }
        }
}

/** When the background pass over the video must wait, so playback of the same file keeps the storage. */
internal object SecondRead {
    /** Below this much video read ahead, playback comes first. */
    const val MIN_AHEAD_MS = 5_000L

    fun mustWait(buffering: Boolean, playing: Boolean, bufferedAheadMs: Long): Boolean =
        buffering || (playing && bufferedAheadMs < MIN_AHEAD_MS)

    /** The file and its version, so a changed file is read again. Null where nothing may be kept. */
    fun cacheIdentity(uri: String, isPrivate: Boolean, length: Long, modifiedMs: Long): String? = when {
        isPrivate -> null
        length > 0 -> "$uri|$length|$modifiedMs"
        else -> uri
    }
}
