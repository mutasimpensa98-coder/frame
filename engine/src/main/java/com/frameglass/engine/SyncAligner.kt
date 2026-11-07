package com.frameglass.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val FILM_FPS = 24000.0 / 1001

/** A frame rate mismatch: subtitle times made for [from] fps played over video at [to] fps. */
enum class FrameRateFix(val from: Double, val to: Double) {
    NONE(1.0, 1.0),
    FROM_25_TO_23976(25.0, FILM_FPS),
    FROM_23976_TO_25(FILM_FPS, 25.0),
    FROM_24_TO_23976(24.0, FILM_FPS),
    FROM_23976_TO_24(FILM_FPS, 24.0),
    FROM_25_TO_24(25.0, 24.0),
    FROM_24_TO_25(24.0, 25.0),
    ;

    /** Video time is this times subtitle time. */
    val ratio: Double get() = from / to

    fun closeTo(other: FrameRateFix): Boolean = abs(ratio / other.ratio - 1) < CLOSE_RATIO

    companion object {
        private const val CLOSE_RATIO = 0.005
        private const val FPS_MATCH = 0.01
        private const val DOUBLED_ABOVE = 40.0

        /** Only fixes that end at the video's own rate, or all of them when the file states no rate. */
        fun candidates(videoFps: Float?): List<FrameRateFix> {
            val stated = videoFps?.takeIf { it > 0f }?.toDouble() ?: return entries
            val fps = if (stated > DOUBLED_ABOVE) stated / 2 else stated
            return entries.filter { it == NONE || abs(it.to - fps) < FPS_MATCH }
        }
    }
}

/** Video time = [fix] ratio × subtitle time + [offsetMs]. */
data class SyncFit(
    val offsetMs: Long,
    val fix: FrameRateFix,
    /** How far the best offset stands above all the others, in standard deviations. */
    val strength: Double,
    /** The best peak elsewhere as a share of the chosen one, from below 0 to 1. */
    val runnerUp: Double,
    /** Cues that fell inside the audio that was read. */
    val matchedCues: Int,
    val confident: Boolean,
) {
    fun agrees(other: SyncFit): Boolean = fix.closeTo(other.fix) && abs(offsetMs - other.offsetMs) <= AGREE_MS

    private companion object {
        const val AGREE_MS = 100L
    }
}

/**
 * Finds the delay, and a frame rate fix if one is needed, that lines subtitle cues up with speech.
 *
 * Both sides become masks of [SpeechDetector.BIN_MS] bins, +1 for speech and -1 for none, with their means removed
 * so a mostly quiet film scores no offset higher for being quiet. Audio never read is 0 and counts for nothing.
 * One FFT cross-correlation per candidate ratio scores every offset up to [MAX_OFFSET_MS] each way. A cue usually
 * runs past the words, which leaves the peak a little wide, so the last step matches cue starts to speech onsets.
 */
object SyncAligner {
    const val MAX_OFFSET_MS = 60_000L
    private const val BIN = SpeechDetector.BIN_MS

    private const val MIN_SPEECH_BINS = 600
    private const val MIN_QUIET_BINS = 600
    private const val MIN_CUES = 20
    private const val MIN_MATCHED_CUES = 25

    /** Offsets within this of the peak belong to the peak, not to a rival. */
    private const val PEAK_RADIUS_BINS = 100
    private const val DETREND_RADIUS_BINS = 100
    private const val MIN_STRENGTH = 7.0
    private const val MAX_RUNNER_UP = 0.6
    private const val MAX_RIVAL = 0.8

    /** A frame rate fix has to beat no fix by this much, because it changes more than a delay does. */
    private const val FIX_MARGIN = 1.15

    private const val REFINE_RADIUS_BINS = 20
    private const val ISOLATED_MS = 300L
    private const val MIN_VOTERS = 12
    private const val MIN_VOTE_SHARE = 0.25

    /** Null when the audio read so far holds too little speech, or the file too few cues, to judge anything. */
    fun align(
        speech: ByteArray,
        cues: List<CueSpan>,
        fixes: List<FrameRateFix> = FrameRateFix.entries,
        maxOffsetMs: Long = MAX_OFFSET_MS,
    ): SyncFit? {
        if (fixes.isEmpty()) return null
        val cues = withinReach(cues, speech.size, fixes, maxOffsetMs)
        if (cues.size < MIN_CUES) return null
        var known = 0
        var talk = 0
        for (value in speech) {
            if (value == SpeechDetector.UNKNOWN) continue
            known++
            if (value == SpeechDetector.SPEECH) talk++
        }
        if (talk < MIN_SPEECH_BINS || known - talk < MIN_QUIET_BINS) return null

        val maxLag = (maxOffsetMs / BIN).toInt()
        val longestRatio = fixes.maxOf { it.ratio }
        val cueBins = (cues.maxOf { it.endMs } * longestRatio / BIN).toInt() + 2
        val size = nextPowerOfTwo(max(speech.size, cueBins) + maxLag + 1)
        val fft = Fft(size)

        val mean = (2.0 * talk - known) / known
        val aRe = DoubleArray(size)
        val aIm = DoubleArray(size)
        for (i in speech.indices) if (speech[i] != SpeechDetector.UNKNOWN) aRe[i] = speech[i] - mean
        fft.forward(aRe, aIm)

        val curves = fixes.map { fix -> Curve(fix, correlate(fft, aRe, aIm, cueMask(cues, fix.ratio, size), maxLag)) }
        var best = curves.maxBy { it.strength }
        val plain = curves.firstOrNull { it.fix == FrameRateFix.NONE }
        if (plain != null && best !== plain && best.strength < plain.strength * FIX_MARGIN) best = plain
        val rival = curves.filterNot { it.fix.closeTo(best.fix) }.maxOfOrNull { it.strength } ?: 0.0

        val coarse = best.peak - maxLag
        val shift = refine(speech, cues, best.fix.ratio, coarse)
        val matched = matchedCues(speech, cues, best.fix.ratio, shift)
        val confident = best.strength >= MIN_STRENGTH &&
            best.runnerUp <= MAX_RUNNER_UP &&
            rival <= best.strength * MAX_RIVAL &&
            matched >= MIN_MATCHED_CUES
        return SyncFit(shift.toLong() * BIN, best.fix, best.strength, best.runnerUp, matched, confident)
    }

    /**
     * The cues that can still meet audio of [speechBins] bins at some offset and ratio, each cut off where the
     * reach ends. A cue past the video's end can never match, and one that starts inside it but ends at 99:59:59
     * would otherwise size the FFT for days.
     */
    internal fun withinReach(cues: List<CueSpan>, speechBins: Int, fixes: List<FrameRateFix>, maxOffsetMs: Long): List<CueSpan> {
        val reachMs = speechBins.toLong() * BIN + maxOffsetMs
        val limitMs = (reachMs / fixes.minOf { it.ratio }).toLong()
        return cues.filter { it.startMs < limitMs }.map { if (it.endMs > limitMs) CueSpan(it.startMs, limitMs) else it }
    }

    /**
     * One ratio's scores for every offset, from -maxLag to +maxLag bins, with its peak judged against the rest.
     * Long scenes without talk add broad hills to the scores; a true match is a sharp peak, so the hills go first.
     */
    private class Curve(val fix: FrameRateFix, raw: DoubleArray) {
        private val scores = detrend(raw, DETREND_RADIUS_BINS)
        val peak: Int = scores.indices.maxBy { scores[it] }
        val strength: Double
        val runnerUp: Double

        init {
            var sum = 0.0
            var squares = 0.0
            var count = 0
            var next = Double.NEGATIVE_INFINITY
            for (i in scores.indices) {
                if (abs(i - peak) <= PEAK_RADIUS_BINS) continue
                sum += scores[i]
                squares += scores[i] * scores[i]
                count++
                if (scores[i] > next) next = scores[i]
            }
            if (count < 2) {
                strength = 0.0
                runnerUp = 1.0
            } else {
                val mean = sum / count
                val spread = sqrt(max(squares / count - mean * mean, 1e-12))
                val height = scores[peak] - mean
                strength = height / spread
                runnerUp = if (height > 0) (next - mean) / height else 1.0
            }
        }
    }

    /** Each value minus the mean of its neighbours within [radius], fewer at the ends. */
    internal fun detrend(values: DoubleArray, radius: Int): DoubleArray {
        val prefix = DoubleArray(values.size + 1)
        for (i in values.indices) prefix[i + 1] = prefix[i] + values[i]
        return DoubleArray(values.size) { i ->
            val from = max(0, i - radius)
            val to = minOf(values.size, i + radius + 1)
            values[i] - (prefix[to] - prefix[from]) / (to - from)
        }
    }

    /** +1 inside cues and -1 between them, from the first cue to the last, mean removed. 0 outside. */
    private fun cueMask(cues: List<CueSpan>, ratio: Double, size: Int): DoubleArray {
        val mask = DoubleArray(size)
        val first = bin(cues.first().startMs, ratio)
        val last = bin(cues.last().endMs, ratio).coerceAtMost(size - 1)
        for (i in first..last) mask[i] = -1.0
        for (cue in cues) {
            val end = bin(cue.endMs, ratio).coerceAtMost(last + 1)
            for (i in bin(cue.startMs, ratio) until end) mask[i] = 1.0
        }
        var sum = 0.0
        for (i in first..last) sum += mask[i]
        val mean = sum / (last - first + 1)
        for (i in first..last) mask[i] -= mean
        return mask
    }

    private fun bin(ms: Long, ratio: Double): Int = (ms * ratio / BIN).roundToInt()

    /** Score at lag k = sum over n of cue[n] × speech[n + k], so a positive lag means the cues must come later. */
    private fun correlate(fft: Fft, aRe: DoubleArray, aIm: DoubleArray, cue: DoubleArray, maxLag: Int): DoubleArray {
        val size = fft.size
        val cIm = DoubleArray(size)
        fft.forward(cue, cIm)
        for (i in 0 until size) {
            val re = aRe[i] * cue[i] + aIm[i] * cIm[i]
            val im = aIm[i] * cue[i] - aRe[i] * cIm[i]
            cue[i] = re
            cIm[i] = im
        }
        fft.inverse(cue, cIm)
        return DoubleArray(2 * maxLag + 1) { j -> cue[(j - maxLag + size) % size] }
    }

    /**
     * Each cue that follows a pause votes for every speech onset within a second of its start. The lag most of them
     * agree on replaces the coarse one; with no clear agreement the coarse one stays.
     */
    private fun refine(speech: ByteArray, cues: List<CueSpan>, ratio: Double, coarse: Int): Int {
        val votes = IntArray(2 * REFINE_RADIUS_BINS + 1)
        var voters = 0
        var previousEnd = Long.MIN_VALUE / 2
        for (cue in cues) {
            val isolated = cue.startMs - previousEnd >= ISOLATED_MS
            previousEnd = cue.endMs
            if (!isolated) continue
            val at = bin(cue.startMs, ratio) + coarse
            var voted = false
            for (d in -REFINE_RADIUS_BINS..REFINE_RADIUS_BINS) {
                val onset = at + d
                if (onset < 1 || onset >= speech.size) continue
                if (speech[onset] == SpeechDetector.SPEECH && speech[onset - 1] == SpeechDetector.QUIET) {
                    votes[d + REFINE_RADIUS_BINS]++
                    voted = true
                }
            }
            if (voted) voters++
        }
        if (voters < MIN_VOTERS) return coarse
        var bestIndex = -1
        var bestScore = -1
        for (i in votes.indices) {
            val score = 2 * votes[i] + (votes.getOrNull(i - 1) ?: 0) + (votes.getOrNull(i + 1) ?: 0)
            val nearer = bestIndex >= 0 && score == bestScore && abs(i - REFINE_RADIUS_BINS) < abs(bestIndex - REFINE_RADIUS_BINS)
            if (score > bestScore || nearer) {
                bestScore = score
                bestIndex = i
            }
        }
        val near = (bestIndex - 1..bestIndex + 1).sumOf { votes.getOrNull(it) ?: 0 }
        if (near < voters * MIN_VOTE_SHARE) return coarse
        return coarse + bestIndex - REFINE_RADIUS_BINS
    }

    private fun matchedCues(speech: ByteArray, cues: List<CueSpan>, ratio: Double, shift: Int): Int = cues.count { cue ->
        val at = bin(cue.startMs, ratio) + shift
        at in speech.indices && speech[at] != SpeechDetector.UNKNOWN
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var size = 2
        while (size < value) size = size shl 1
        return size
    }
}

/** In-place radix-2 complex FFT of one fixed power-of-two size. */
internal class Fft(val size: Int) {
    private val levels = Integer.numberOfTrailingZeros(size)
    private val cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2 * PI * it / size) }

    init {
        require(size >= 2 && size and (size - 1) == 0) { "size must be a power of two" }
    }

    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = false)

    fun inverse(re: DoubleArray, im: DoubleArray) {
        transform(re, im, inverse = true)
        val scale = 1.0 / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        for (i in 0 until size) {
            val j = Integer.reverse(i) ushr (32 - levels)
            if (j > i) {
                val r = re[i]
                re[i] = re[j]
                re[j] = r
                val m = im[i]
                im[i] = im[j]
                im[j] = m
            }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val l = j + half
                    val wr = cosTable[k]
                    val wi = if (inverse) sinTable[k] else -sinTable[k]
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += length
            }
            length = length shl 1
        }
    }
}
