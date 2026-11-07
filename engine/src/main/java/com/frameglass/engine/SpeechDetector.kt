package com.frameglass.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where speech falls in decoded audio, one value per [BIN_MS]: [SPEECH], [QUIET] or [UNKNOWN] for audio never read.
 *
 * A band-limited energy detector. Speech carries most of its energy between about 300 and 3,000 Hz and rises and
 * falls with the syllables, while a held note, a hum or steady noise does not. Each stretch of audio is judged
 * against its own level, because a film's loudness changes from scene to scene. Pure, so it runs on the JVM.
 */
class SpeechDetector(durationMs: Long) {
    val mask = ByteArray(binsFor(durationMs))

    private val energies = ArrayList<Double>()
    private var firstBin = -1L
    private var sampleRate = 0
    private var samplesPerBin = 0
    private var expectedUs = 0L
    private var sum = 0.0
    private var count = 0
    private val highPass = Biquad()
    private val lowPass = Biquad()

    /** Starts a stretch of audio. Filters restart, so stretches never leak into each other. */
    fun begin(sampleRate: Int) {
        flush()
        // An unknown rate waits for the decoder's output format, and nothing is fed until then.
        this.sampleRate = sampleRate
        if (sampleRate <= 0) return
        samplesPerBin = max(1, sampleRate * BIN_MS / 1000)
        highPass.highPass(HIGH_PASS_HZ, sampleRate)
        lowPass.lowPass(min(LOW_PASS_HZ, sampleRate * 0.45), sampleRate)
    }

    /** Mono samples from -1 to 1 that start at [ptsUs]. A jump in time ends the stretch and starts another. */
    fun feed(ptsUs: Long, samples: FloatArray, size: Int) {
        if (sampleRate <= 0 || size <= 0) return
        if (firstBin >= 0 && abs(ptsUs - expectedUs) > GAP_US) begin(sampleRate)
        if (firstBin < 0) {
            firstBin = (ptsUs + BIN_US / 2) / BIN_US
            expectedUs = ptsUs
        }
        for (i in 0 until size) {
            val y = lowPass.step(highPass.step(samples[i].toDouble()))
            sum += y * y
            if (++count == samplesPerBin) {
                energies += sum / count
                sum = 0.0
                count = 0
            }
        }
        expectedUs = ptsUs + size * 1_000_000L / sampleRate
    }

    /** Judges what has been fed since [begin]. */
    fun end() = flush()

    private fun flush() {
        if (firstBin >= 0) judge(energies.toDoubleArray(), firstBin)
        energies.clear()
        firstBin = -1
        sum = 0.0
        count = 0
    }

    private fun judge(power: DoubleArray, startBin: Long) {
        val n = power.size
        if (n < MIN_BINS) return
        val db = DoubleArray(n) { 10 * log10(power[it] + 1e-12) }
        val usable = db.copyOfRange(WARMUP_BINS, n)
        val floor = percentile(usable, FLOOR_PERCENTILE)
        val top = percentile(usable, TOP_PERCENTILE)
        val values = ByteArray(n) { QUIET }
        if (top >= SILENCE_DB) {
            // Too little contrast to tell speech from anything else, so this stretch says nothing.
            if (top - floor < MIN_RANGE_DB) return
            val threshold = floor + THRESHOLD_SHARE * (top - floor)
            val raw = BooleanArray(n) { it >= WARMUP_BINS && db[it] > threshold && localSpread(db, it) >= MIN_SPREAD_DB }
            closeGaps(raw, MAX_GAP_BINS)
            dropShortRuns(raw, MIN_RUN_BINS)
            for (i in 0 until n) values[i] = if (raw[i]) SPEECH else QUIET
        }
        for (i in WARMUP_BINS until n) {
            val bin = startBin + i
            if (bin in mask.indices) mask[bin.toInt()] = values[i]
        }
    }

    /** How much the level moves around this bin. Syllables make it move a lot, a held note hardly at all. */
    private fun localSpread(db: DoubleArray, at: Int): Double {
        val from = max(0, at - SPREAD_RADIUS)
        val to = min(db.lastIndex, at + SPREAD_RADIUS)
        var mean = 0.0
        for (i in from..to) mean += db[i]
        mean /= (to - from + 1)
        var variance = 0.0
        for (i in from..to) variance += (db[i] - mean) * (db[i] - mean)
        return sqrt(variance / (to - from + 1))
    }

    private class Biquad {
        private var b0 = 1.0
        private var b1 = 0.0
        private var b2 = 0.0
        private var a1 = 0.0
        private var a2 = 0.0
        private var z1 = 0.0
        private var z2 = 0.0

        fun highPass(hz: Double, rate: Int) = design(hz, rate, high = true)

        fun lowPass(hz: Double, rate: Int) = design(hz, rate, high = false)

        /** Butterworth, from the RBJ cookbook. */
        private fun design(hz: Double, rate: Int, high: Boolean) {
            val w = 2 * PI * hz / rate
            val alpha = sin(w) / (2 * BUTTERWORTH_Q)
            val c = cos(w)
            val a0 = 1 + alpha
            val edge = if (high) (1 + c) / 2 else (1 - c) / 2
            b0 = edge / a0
            b1 = (if (high) -(1 + c) else 1 - c) / a0
            b2 = edge / a0
            a1 = -2 * c / a0
            a2 = (1 - alpha) / a0
            z1 = 0.0
            z2 = 0.0
        }

        fun step(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    companion object {
        const val BIN_MS = 50
        private const val BIN_US = BIN_MS * 1000L
        const val SPEECH: Byte = 1
        const val QUIET: Byte = -1
        const val UNKNOWN: Byte = 0

        private const val HIGH_PASS_HZ = 300.0
        private const val LOW_PASS_HZ = 3_000.0
        private const val BUTTERWORTH_Q = 0.7071
        private const val GAP_US = 200_000L

        /** Under 2 s there is no level to judge against. */
        private const val MIN_BINS = 40

        /** Bins right after a seek hold the decoder's and the filters' start-up. */
        private const val WARMUP_BINS = 2
        private const val FLOOR_PERCENTILE = 0.10
        private const val TOP_PERCENTILE = 0.95
        private const val SILENCE_DB = -60.0
        private const val MIN_RANGE_DB = 8.0
        private const val THRESHOLD_SHARE = 0.45
        private const val SPREAD_RADIUS = 5
        private const val MIN_SPREAD_DB = 3.0
        private const val MAX_GAP_BINS = 4
        private const val MIN_RUN_BINS = 3

        fun binsFor(durationMs: Long): Int = (durationMs.coerceAtLeast(0) / BIN_MS + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        internal fun percentile(values: DoubleArray, share: Double): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sortedArray()
            return sorted[((sorted.size - 1) * share).toInt()]
        }

        /** Joins runs of speech split by pauses of up to [maxGap] bins, the breath between words. */
        internal fun closeGaps(flags: BooleanArray, maxGap: Int) {
            var lastOn = -1
            for (i in flags.indices) {
                if (!flags[i]) continue
                if (lastOn >= 0 && i - lastOn - 1 in 1..maxGap) for (j in lastOn + 1 until i) flags[j] = true
                lastOn = i
            }
        }

        /** A click or a door is too short to be a word. */
        internal fun dropShortRuns(flags: BooleanArray, minRun: Int) {
            var i = 0
            while (i < flags.size) {
                if (!flags[i]) {
                    i++
                    continue
                }
                var j = i
                while (j < flags.size && flags[j]) j++
                if (j - i < minRun) for (k in i until j) flags[k] = false
                i = j
            }
        }
    }
}
