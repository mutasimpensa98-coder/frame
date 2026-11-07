package com.frameglass.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SpeechDetectorTest {
    private val rate = 16_000
    private val bin = SpeechDetector.BIN_MS

    /** Noise shaped like voiced speech: a voice band, syllables at about 4.5 per second, quiet hiss underneath. */
    private fun audio(seconds: Int, talk: List<Pair<Double, Double>>, tone: Pair<Double, Double>? = null): FloatArray {
        val random = Random(1)
        var low = 0.0
        var lower = 0.0
        return FloatArray(seconds * rate) { i ->
            val t = i.toDouble() / rate
            val white = random.nextDouble(-1.0, 1.0)
            low += 0.35 * (white - low)
            lower += 0.05 * (white - lower)
            var x = 0.002 * white
            if (talk.any { t >= it.first && t < it.second }) {
                val syllables = 0.5 + 0.5 * sin(2 * PI * 4.5 * t)
                x += 1.2 * (low - lower) * syllables * syllables
            }
            if (tone != null && t >= tone.first && t < tone.second) x += 0.25 * sin(2 * PI * 1_000 * t)
            x.toFloat()
        }
    }

    private fun detect(samples: FloatArray, startMs: Long = 0, durationMs: Long = 120_000): ByteArray {
        val detector = SpeechDetector(durationMs)
        detector.begin(rate)
        // Fed in decoder-sized pieces, each stamped with its own time.
        var at = 0
        while (at < samples.size) {
            val size = minOf(1_024, samples.size - at)
            detector.feed(startMs * 1000 + at * 1_000_000L / rate, samples.copyOfRange(at, at + size), size)
            at += size
        }
        detector.end()
        return detector.mask
    }

    private fun share(mask: ByteArray, fromMs: Long, toMs: Long, value: Byte): Double {
        val range = (fromMs / bin).toInt() until (toMs / bin).toInt()
        return range.count { mask[it] == value }.toDouble() / range.count()
    }

    @Test
    fun syllablesInTheVoiceBandReadAsSpeech() {
        val talk = listOf(2.0 to 5.0, 8.0 to 11.5, 15.0 to 16.5, 20.0 to 26.0)
        val mask = detect(audio(30, talk))
        for ((from, to) in talk) {
            assertTrue("speech $from", share(mask, (from * 1000).toLong() + 200, (to * 1000).toLong() - 200, SpeechDetector.SPEECH) > 0.8)
        }
        assertTrue(share(mask, 5_400, 7_600, SpeechDetector.QUIET) > 0.9)
        assertTrue(share(mask, 26_400, 29_500, SpeechDetector.QUIET) > 0.9)
    }

    @Test
    fun aHeldToneIsNotSpeech() {
        val mask = detect(audio(30, listOf(14.0 to 18.0, 22.0 to 27.0), tone = 2.0 to 11.0))
        assertTrue(share(mask, 2_500, 10_500, SpeechDetector.SPEECH) < 0.1)
        assertTrue(share(mask, 22_200, 26_800, SpeechDetector.SPEECH) > 0.8)
    }

    @Test
    fun silenceIsQuietAndUnreadAudioStaysUnknown() {
        val mask = detect(FloatArray(10 * rate), startMs = 40_000)
        assertEquals(SpeechDetector.UNKNOWN, mask[(39_000 / bin)])
        assertTrue(share(mask, 40_200, 49_800, SpeechDetector.QUIET) == 1.0)
        assertEquals(SpeechDetector.UNKNOWN, mask[(55_000 / bin)])
    }

    @Test
    fun speechLandsAtItsOwnTimeInTheVideo() {
        val mask = detect(audio(20, listOf(5.0 to 9.0)), startMs = 60_000)
        assertTrue(share(mask, 65_200, 68_800, SpeechDetector.SPEECH) > 0.8)
        assertTrue(share(mask, 61_000, 64_600, SpeechDetector.QUIET) > 0.9)
    }

    @Test
    fun tooShortAStretchSaysNothing() {
        val mask = detect(audio(1, listOf(0.0 to 1.0)))
        assertTrue(mask.all { it == SpeechDetector.UNKNOWN })
    }

    @Test
    fun breathsJoinAndClicksGo() {
        val flags = booleanArrayOf(true, true, false, false, true, false, false, false, false, false, true, false)
        SpeechDetector.closeGaps(flags, 4)
        SpeechDetector.dropShortRuns(flags, 3)
        assertArrayEquals(
            booleanArrayOf(true, true, true, true, true, false, false, false, false, false, false, false),
            flags,
        )
    }
}
