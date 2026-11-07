package com.frameglass.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SyncAlignerTest {
    private val duration = 45 * 60_000L

    /** The fit for a subtitle of film [seed] shifted by [offsetMs], heard in the first [windows] stretches. */
    private fun fit(seed: Long, offsetMs: Long, fix: FrameRateFix = FrameRateFix.NONE, windows: Int = 24): SyncFit? {
        val talk = SyncFixtures.talk(seed, duration)
        val heard = SyncFixtures.heard(seed, duration, talk, SyncPlan.windows(duration).take(windows))
        return SyncAligner.align(heard, SyncFixtures.subtitle(seed, talk, offsetMs, fix.ratio))
    }

    /** Cue starts land on speech onsets, so a subtitle that leads its words by up to 150 ms reads slightly later. */
    private fun assertClose(expectedMs: Long, fit: SyncFit) =
        assertTrue("offset ${fit.offsetMs} for $expectedMs", abs(fit.offsetMs - expectedMs) <= 150)

    @Test
    fun aLateSubtitleIsPulledEarlier() {
        val fit = requireNotNull(fit(seed = 3, offsetMs = -12_340))
        assertTrue(fit.confident)
        assertEquals(FrameRateFix.NONE, fit.fix)
        assertClose(-12_340, fit)
    }

    @Test
    fun anEarlySubtitleIsPushedLaterUpToAMinute() {
        val fit = requireNotNull(fit(seed = 8, offsetMs = 47_500))
        assertTrue(fit.confident)
        assertClose(47_500, fit)
    }

    @Test
    fun aPalSubtitleOverFilmVideoGetsTheRatio() {
        val fit = requireNotNull(fit(seed = 10, offsetMs = 800, fix = FrameRateFix.FROM_25_TO_23976))
        assertTrue(fit.confident)
        assertEquals(FrameRateFix.FROM_25_TO_23976, fit.fix)
        assertClose(800, fit)
    }

    @Test
    fun aFilmSubtitleOverPalVideoGetsTheOtherRatio() {
        val fit = requireNotNull(fit(seed = 18, offsetMs = -2_000, fix = FrameRateFix.FROM_23976_TO_25))
        assertTrue(fit.confident)
        assertEquals(FrameRateFix.FROM_23976_TO_25, fit.fix)
        assertClose(-2_000, fit)
    }

    @Test
    fun aSlowDriftOfOneThousandthIsFound() {
        val fit = requireNotNull(fit(seed = 5, offsetMs = 0, fix = FrameRateFix.FROM_24_TO_23976, windows = 40))
        assertTrue(fit.confident)
        assertEquals(FrameRateFix.FROM_24_TO_23976, fit.fix)
        assertClose(0, fit)
    }

    @Test
    fun aSubtitleForAnotherVideoIsNeverConfident() {
        for (seed in 1L..12L) {
            val talk = SyncFixtures.talk(seed, duration)
            val heard = SyncFixtures.heard(seed, duration, talk, SyncPlan.windows(duration))
            val other = SyncFixtures.subtitle(seed + 500, SyncFixtures.talk(seed + 500, duration), 0)
            val fit = SyncAligner.align(heard, other)
            assertFalse("seed $seed", fit?.confident ?: false)
        }
    }

    @Test
    fun anOffsetPastTheSearchIsNotForced() {
        for (seed in 1L..6L) assertFalse("seed $seed", fit(seed, offsetMs = 95_000, windows = 40)?.confident ?: false)
    }

    @Test
    fun tooLittleAudioGivesNoAnswer() {
        assertNull(fit(seed = 2, offsetMs = 1_000, windows = 1))
    }

    @Test
    fun tooFewCuesGiveNoAnswer() {
        val talk = SyncFixtures.talk(4, duration)
        val heard = SyncFixtures.heard(4, duration, talk, SyncPlan.windows(duration))
        assertNull(SyncAligner.align(heard, SyncFixtures.subtitle(4, talk, 0).take(10)))
    }

    @Test
    fun aMistypedFarCueIsIgnored() {
        val talk = SyncFixtures.talk(3, duration)
        val heard = SyncFixtures.heard(3, duration, talk, SyncPlan.windows(duration).take(24))
        val cues = SyncFixtures.subtitle(3, talk, -12_340) + CueSpan(359_999_000, 360_000_000)
        val fit = requireNotNull(SyncAligner.align(heard, cues))
        assertTrue(fit.confident)
        assertClose(-12_340, fit)
    }

    @Test
    fun aCueEndingAtATypedHundredHoursIsCutAtTheReach() {
        val talk = SyncFixtures.talk(3, duration)
        val heard = SyncFixtures.heard(3, duration, talk, SyncPlan.windows(duration).take(24))
        val far = CueSpan(duration - 60_000, 359_999_000)
        val cues = SyncFixtures.subtitle(3, talk, -12_340) + far
        val reach = SyncAligner.withinReach(cues, heard.size, FrameRateFix.entries, SyncAligner.MAX_OFFSET_MS)
        val shortest = FrameRateFix.entries.minOf { it.ratio }
        val limit = ((heard.size * SpeechDetector.BIN_MS + SyncAligner.MAX_OFFSET_MS) / shortest).toLong()
        assertEquals(CueSpan(far.startMs, limit), reach.last())
        val fit = requireNotNull(SyncAligner.align(heard, cues))
        assertTrue(fit.confident)
        assertClose(-12_340, fit)
    }

    @Test
    fun onlyFixesEndingAtTheVideoRateAreTried() {
        assertEquals(FrameRateFix.entries, FrameRateFix.candidates(null))
        assertEquals(
            listOf(FrameRateFix.NONE, FrameRateFix.FROM_23976_TO_25, FrameRateFix.FROM_24_TO_25),
            FrameRateFix.candidates(25f),
        )
        assertEquals(
            listOf(FrameRateFix.NONE, FrameRateFix.FROM_25_TO_23976, FrameRateFix.FROM_24_TO_23976),
            FrameRateFix.candidates(23.976f),
        )
        assertEquals(FrameRateFix.candidates(25f), FrameRateFix.candidates(50f))
        assertEquals(listOf(FrameRateFix.NONE), FrameRateFix.candidates(29.97f))
    }

    @Test
    fun ratiosPointTheRightWay() {
        assertEquals(25.0 / (24000.0 / 1001), FrameRateFix.FROM_25_TO_23976.ratio, 1e-12)
        assertTrue(FrameRateFix.FROM_24_TO_23976.closeTo(FrameRateFix.NONE))
        assertFalse(FrameRateFix.FROM_25_TO_23976.closeTo(FrameRateFix.NONE))
    }

    @Test
    fun detrendingFlattensSlopesAndKeepsSharpPeaks() {
        val slope = DoubleArray(50) { it * 2.0 }
        assertTrue(SyncAligner.detrend(slope, 5).slice(5 until 45).all { abs(it) < 1e-9 })
        val hill = DoubleArray(201) { 100.0 - abs(it - 100) * 0.5 }.also { it[60] += 30.0 }
        val flat = SyncAligner.detrend(hill, 20)
        assertEquals(60, flat.indices.maxBy { flat[it] })
    }

    @Test
    fun fftCorrelationMatchesTheDirectSum() {
        val random = Random(9)
        val size = 64
        val a = DoubleArray(size) { if (it < 40) random.nextDouble(-1.0, 1.0) else 0.0 }
        val c = DoubleArray(size) { if (it < 20) random.nextDouble(-1.0, 1.0) else 0.0 }
        val fft = Fft(size)
        val aRe = a.copyOf()
        val aIm = DoubleArray(size)
        val cRe = c.copyOf()
        val cIm = DoubleArray(size)
        fft.forward(aRe, aIm)
        fft.forward(cRe, cIm)
        val re = DoubleArray(size) { aRe[it] * cRe[it] + aIm[it] * cIm[it] }
        val im = DoubleArray(size) { aIm[it] * cRe[it] - aRe[it] * cIm[it] }
        fft.inverse(re, im)
        for (lag in -10..10) {
            var direct = 0.0
            for (n in 0 until size) if (n + lag in 0 until size) direct += c[n] * a[n + lag]
            assertEquals("lag $lag", direct, re[(lag + size) % size], 1e-9)
        }
    }
}
