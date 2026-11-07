package com.frameglass.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/** The F-S8 test from the brief: nothing passes 0 dBFS and peaks drop by at least 6 dB. */
class NightCompressorTest {
    private val rate = 48_000
    private val stereo = 2

    private fun tone(level: Float, seconds: Float): FloatArray {
        val frames = (rate * seconds).toInt()
        val out = FloatArray(frames * stereo)
        for (frame in 0 until frames) {
            val value = level * sin(2.0 * PI * 440.0 * frame / rate).toFloat()
            for (channel in 0 until stereo) out[frame * stereo + channel] = value
        }
        return out
    }

    private fun run(samples: FloatArray): FloatArray {
        val dsp = NightCompressor(rate, stereo)
        dsp.enabled = true
        val copy = samples.copyOf()
        var offset = 0
        val block = 1024 * stereo
        val scratch = FloatArray(block)
        while (offset < copy.size) {
            val size = minOf(block, copy.size - offset)
            System.arraycopy(copy, offset, scratch, 0, size)
            dsp.process(scratch, size / stereo)
            System.arraycopy(scratch, 0, copy, offset, size)
            offset += size
        }
        return copy
    }

    private fun peak(samples: FloatArray, from: Int = 0): Float {
        var max = 0f
        for (i in from until samples.size) max = maxOf(max, abs(samples[i]))
        return max
    }

    private fun db(value: Float) = 20 * log10(value)

    @Test
    fun loudPeaksDropBySixDbAndNeverPassFullScale() {
        val clip = tone(0.01f, 1f) + tone(1f, 2f)
        val out = run(clip)
        assertTrue("output passed 0 dBFS", peak(out) <= 1f)
        val settled = out.size - rate
        assertTrue(db(peak(out, settled)) <= db(peak(clip, settled)) - 6f)
    }

    @Test
    fun dialogueLevelIsLifted() {
        val out = run(tone(0.03f, 2f))
        assertTrue(db(peak(out, out.size - rate)) >= db(0.03f) + 3f)
    }

    @Test
    fun suddenFullScaleBurstCannotClipOnItsFirstSample() {
        val input = tone(0.001f, 1f) + FloatArray(4800) { 1f } + tone(0.001f, 0.2f)
        assertTrue(peak(run(input)) <= 1f)
    }

    @Test
    fun switchedOffStaysBitExact() {
        val dsp = NightCompressor(rate, stereo)
        val input = tone(0.5f, 0.1f)
        val copy = input.copyOf()
        dsp.process(copy, copy.size / stereo)
        assertTrue(dsp.idle)
        assertEquals(input.toList(), copy.toList())
    }
}
