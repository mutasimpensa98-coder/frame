package com.frameglass.playerui

/**
 * A-B repeat (waiver of 2026-11-12). Playback goes back to [aMs] whenever it reaches [bMs]. It survives pause and
 * seeking, so a seek past B lands back at A, and only Clear or a new item ends it.
 */
internal data class AbLoop(val aMs: Long? = null, val bMs: Long? = null) {
    val active: Boolean get() = aMs != null && bMs != null

    /** A new A at or past B drops B, so the loop never runs backwards. */
    fun withA(positionMs: Long): AbLoop {
        val a = positionMs.coerceAtLeast(0)
        return if (bMs != null && bMs - a < MIN_LENGTH_MS) AbLoop(a, null) else AbLoop(a, bMs)
    }

    /** Null when there is no A yet, or B would leave less than [MIN_LENGTH_MS] after it. */
    fun withB(positionMs: Long): AbLoop? {
        val a = aMs ?: return null
        return if (positionMs - a < MIN_LENGTH_MS) null else AbLoop(a, positionMs)
    }

    /** Where to seek at [positionMs], or null to play on. */
    fun seekTarget(positionMs: Long): Long? {
        val a = aMs ?: return null
        val b = bMs ?: return null
        return if (positionMs >= b) a else null
    }

    /** How long to wait before the next check, so B is met within a few milliseconds at any speed. */
    fun waitMs(positionMs: Long, speed: Float): Long {
        val b = bMs ?: return MAX_WAIT_MS
        val left = ((b - positionMs) / speed.coerceAtLeast(MIN_SPEED)).toLong()
        return left.coerceIn(MIN_WAIT_MS, MAX_WAIT_MS)
    }

    companion object {
        const val MIN_LENGTH_MS = 1_000L
        const val MIN_WAIT_MS = 10L
        const val MAX_WAIT_MS = 250L
        private const val MIN_SPEED = 0.25f
    }
}
