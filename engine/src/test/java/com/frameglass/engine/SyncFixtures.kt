package com.frameglass.engine

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Invented films for the aligner: where people talk, what an imperfect detector makes of it in the stretches
 * read, and a subtitle file timed the way people time one, with leads, long tails, joined lines and stray signs.
 */
internal object SyncFixtures {
    private const val BIN = SpeechDetector.BIN_MS

    fun talk(seed: Long, durationMs: Long): List<CueSpan> {
        val random = Random(seed)
        val out = ArrayList<CueSpan>()
        var t = 4_000L
        while (t < durationMs - 6_000) {
            if (random.nextDouble() < 0.05) t += random.nextLong(15_000, 70_000)
            val length = random.nextLong(500, 4_000)
            if (t + length >= durationMs) break
            out += CueSpan(t, t + length)
            t += length + random.nextLong(250, 4_500)
        }
        return out
    }

    /** The detector's view: misses, ragged edges and false alarms, inside [windows] only. */
    fun heard(
        seed: Long,
        durationMs: Long,
        talk: List<CueSpan>,
        windows: List<SyncPlan.Window>,
        missShare: Double = 0.15,
        falseAlarmsPerMinute: Double = 6.0,
    ): ByteArray {
        val random = Random(seed * 31 + 7)
        val mask = ByteArray(SpeechDetector.binsFor(durationMs))
        val read = BooleanArray(mask.size)
        for (window in windows) {
            for (b in (window.startMs / BIN).toInt() until (window.endMs / BIN).toInt().coerceAtMost(mask.size)) {
                read[b] = true
                mask[b] = SpeechDetector.QUIET
            }
        }
        fun mark(from: Long, to: Long) {
            for (b in (from / BIN).toInt() until (to / BIN).toInt()) if (b in mask.indices && read[b]) mask[b] = SpeechDetector.SPEECH
        }
        for (span in talk) {
            if (random.nextDouble() < missShare) continue
            mark(span.startMs + random.nextLong(-100, 101), span.endMs + random.nextLong(-150, 151))
        }
        val readMinutes = windows.sumOf { it.endMs - it.startMs } / 60_000.0
        repeat((readMinutes * falseAlarmsPerMinute).toInt()) {
            val window = windows[random.nextInt(windows.size)]
            val start = random.nextLong(window.startMs, window.endMs)
            mark(start, start + random.nextLong(300, 2_000))
        }
        return mask
    }

    /** A subtitle for [talk], in its own time: video time = ratio × subtitle time + offset. */
    fun subtitle(seed: Long, talk: List<CueSpan>, offsetMs: Long, ratio: Double = 1.0): List<CueSpan> {
        val random = Random(seed * 17 + 3)
        val video = ArrayList<CueSpan>()
        var index = 0
        while (index < talk.size) {
            var span = talk[index]
            index++
            if (random.nextDouble() < 0.10) continue
            if (index < talk.size && random.nextDouble() < 0.2 && talk[index].startMs - span.endMs < 1_500) {
                span = CueSpan(span.startMs, talk[index].endMs)
                index++
            }
            video += CueSpan(span.startMs - random.nextLong(0, 150), span.endMs + random.nextLong(200, 1_000))
            if (random.nextDouble() < 0.03) {
                val at = span.endMs + random.nextLong(5_000, 30_000)
                video += CueSpan(at, at + 2_500)
            }
        }
        return video.map { CueSpan(((it.startMs - offsetMs) / ratio).roundToLong(), ((it.endMs - offsetMs) / ratio).roundToLong()) }
            .filter { it.startMs >= 0 }
            .sortedBy { it.startMs }
    }
}
