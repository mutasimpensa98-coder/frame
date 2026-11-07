package com.frameglass.engine

import kotlin.math.min

/**
 * Which stretches of the audio a sync reads. They are spread over the whole video and read in an order that keeps
 * them spread, so a stop at any point still covers the start, middle and end, which a frame rate fix needs.
 * Opening and closing credits are left out of a long video. Pure, so it runs on the JVM.
 */
object SyncPlan {
    data class Window(val startMs: Long, val endMs: Long)

    const val WINDOW_MS = 30_000L
    const val MAX_WINDOWS = 40
    private const val FIRST_TRY = 8
    private const val TRY_EVERY = 4
    private const val SKIP_START = 0.02
    private const val SKIP_END = 0.06
    private const val SHORT_VIDEO_WINDOWS = 6

    fun windows(durationMs: Long): List<Window> {
        if (durationMs <= 0) return emptyList()
        if (durationMs <= WINDOW_MS * SHORT_VIDEO_WINDOWS) {
            return (0 until ((durationMs + WINDOW_MS - 1) / WINDOW_MS).toInt()).map { i ->
                Window(i * WINDOW_MS, min(durationMs, (i + 1) * WINDOW_MS))
            }
        }
        val from = (durationMs * SKIP_START).toLong()
        val to = (durationMs * (1 - SKIP_END)).toLong()
        val slots = min(MAX_WINDOWS.toLong(), (to - from) / WINDOW_MS).toInt().coerceAtLeast(1)
        val slotMs = (to - from) / slots
        val length = min(WINDOW_MS, slotMs)
        return spreadOrder(slots).map { slot ->
            val start = from + slot * slotMs + (slotMs - length) / 2
            Window(start, start + length)
        }
    }

    /** True after the windows at which the aligner should try: the eighth, then every fourth, and the last one. */
    fun isCheckpoint(done: Int, total: Int): Boolean =
        done == total || (done >= FIRST_TRY && (done - FIRST_TRY) % TRY_EVERY == 0)

    /** Slots in van der Corput order: halves, then quarters, then eighths, and so on. */
    internal fun spreadOrder(slots: Int): List<Int> {
        val order = LinkedHashSet<Int>()
        var i = 0
        while (order.size < slots) {
            order += (radicalInverse(i) * slots).toInt().coerceIn(0, slots - 1)
            i++
            if (i > slots * 64) break
        }
        for (slot in 0 until slots) order += slot
        return order.toList()
    }

    private fun radicalInverse(index: Int): Double {
        var n = index
        var result = 0.0
        var base = 0.5
        while (n > 0) {
            if (n and 1 == 1) result += base
            n = n shr 1
            base /= 2
        }
        return result
    }
}
