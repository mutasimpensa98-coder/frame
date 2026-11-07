package com.frameglass.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Audio through the detector and an SRT through the parser, then both through the aligner, as on the phone. */
class SyncPipelineTest {
    private val rate = 8_000
    private val duration = 20 * 60_000L

    /** Voice-band noise in syllables while people talk, a held chord now and then, and hiss under everything. */
    private fun heard(talk: List<CueSpan>): ByteArray {
        val detector = SpeechDetector(duration)
        val random = Random(5)
        for (window in SyncPlan.windows(duration)) {
            detector.begin(rate)
            var low = 0.0
            var lower = 0.0
            var next = 0
            val chunk = FloatArray(512)
            var t0 = window.startMs * 1000
            while (t0 < window.endMs * 1000) {
                for (i in chunk.indices) {
                    val us = t0 + i * 1_000_000L / rate
                    val ms = us / 1000
                    while (next < talk.size && talk[next].endMs <= ms) next++
                    val white = random.nextDouble(-1.0, 1.0)
                    low += 0.5 * (white - low)
                    lower += 0.08 * (white - lower)
                    var x = 0.003 * white
                    val t = us / 1e6
                    if (next < talk.size && ms >= talk[next].startMs) {
                        val syllables = 0.5 + 0.5 * sin(2 * PI * 4.2 * t)
                        x += (low - lower) * syllables * syllables
                    }
                    if ((ms / 20_000) % 3 == 0L) x += 0.05 * (sin(2 * PI * 440 * t) + sin(2 * PI * 554 * t))
                    chunk[i] = x.toFloat()
                }
                detector.feed(t0, chunk, chunk.size)
                t0 += chunk.size * 1_000_000L / rate
            }
            detector.end()
        }
        return detector.mask
    }

    private fun srt(cues: List<CueSpan>): String = cues.mapIndexed { index, cue ->
        "${index + 1}\n${clock(cue.startMs)} --> ${clock(cue.endMs)}\nLine ${index + 1}\n"
    }.joinToString("\n")

    private fun clock(ms: Long) = String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)

    @Test
    fun aShiftedSubtitleFileIsFoundFromTheAudio() {
        val talk = SyncFixtures.talk(21, duration)
        val cues = SubtitleCues.spans(srt(SyncFixtures.subtitle(21, talk, offsetMs = 3_250)), SubtitleCues.Kind.SRT)
        val fit = requireNotNull(SyncAligner.align(heard(talk), cues, FrameRateFix.candidates(23.976f)))
        assertTrue("strength ${fit.strength}", fit.confident)
        assertEquals(FrameRateFix.NONE, fit.fix)
        assertTrue("offset ${fit.offsetMs}", abs(fit.offsetMs - 3_250) <= 150)
    }

    @Test
    fun theWrongFilmsSubtitleChangesNothing() {
        val talk = SyncFixtures.talk(22, duration)
        val other = SubtitleCues.spans(srt(SyncFixtures.subtitle(99, SyncFixtures.talk(99, duration), 0)), SubtitleCues.Kind.SRT)
        assertFalse(SyncAligner.align(heard(talk), other)?.confident ?: false)
    }
}
