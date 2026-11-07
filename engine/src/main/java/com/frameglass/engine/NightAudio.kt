package com.frameglass.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * F-S8 signal path with no Android or Media3 types, so JVM tests can run it. All channels share one
 * gain, because a per-channel gain would drag the centre dialogue around the stereo image.
 *
 * The limiter gain applies on the same frame it is computed for, so no output sample can pass [CEILING].
 */
class NightCompressor(sampleRate: Int, private val channels: Int) {
    private val attack = smoothing(ATTACK_MS, sampleRate)
    private val release = smoothing(RELEASE_MS, sampleRate)
    private val limiterRelease = smoothing(LIMITER_RELEASE_MS, sampleRate)
    private val mixStep = 1f / max(1f, sampleRate * TOGGLE_MS / 1000f)

    private var envelope = 0f
    private var limiter = 1f
    private var mix = 0f

    /** Turning this off fades the effect out over a few milliseconds instead of clicking. */
    @Volatile var enabled = false

    /** True when the effect is off and has finished fading, so the audio can pass untouched. */
    val idle: Boolean get() = !enabled && mix == 0f

    /** Processes [frames] interleaved frames of [samples] in place. Full scale is 1.0. */
    fun process(samples: FloatArray, frames: Int) {
        val target = if (enabled) 1f else 0f
        var index = 0
        for (frame in 0 until frames) {
            var peak = 0f
            for (channel in 0 until channels) peak = max(peak, abs(samples[index + channel]))

            val coefficient = if (peak > envelope) attack else release
            envelope = peak + coefficient * (envelope - peak)
            var gain = dbToGain(MAKEUP_DB - reductionDb(gainToDb(envelope)))

            mix = if (mix < target) min(target, mix + mixStep) else max(target, mix - mixStep)
            gain = 1f + mix * (gain - 1f)

            limiter = 1f - limiterRelease * (1f - limiter)
            val shaped = peak * gain * limiter
            if (mix > 0f && shaped > CEILING) limiter *= CEILING / shaped
            gain *= limiter

            for (channel in 0 until channels) samples[index + channel] *= gain
            index += channels
        }
    }

    private fun reductionDb(levelDb: Float): Float {
        val over = levelDb - THRESHOLD_DB
        val slope = 1f - 1f / RATIO
        return when {
            2f * over <= -KNEE_DB -> 0f
            2f * over >= KNEE_DB -> over * slope
            else -> {
                val inKnee = over + KNEE_DB / 2f
                slope * inKnee * inKnee / (2f * KNEE_DB)
            }
        }
    }

    private companion object {
        const val THRESHOLD_DB = -30f
        const val RATIO = 4f
        const val KNEE_DB = 6f
        const val MAKEUP_DB = 10f
        const val ATTACK_MS = 5f
        const val RELEASE_MS = 200f
        const val LIMITER_RELEASE_MS = 80f
        const val TOGGLE_MS = 30f

        /** -1 dBFS, which leaves headroom for the conversions after this stage. */
        const val CEILING = 0.891f

        const val DB_PER_NEPER = 8.685889f
        const val FLOOR = 1e-6f

        fun smoothing(milliseconds: Float, sampleRate: Int): Float =
            exp(-1000f / (milliseconds * sampleRate))

        fun gainToDb(gain: Float): Float = DB_PER_NEPER * ln(max(gain, FLOOR))
        fun dbToGain(db: Float): Float = exp(db / DB_PER_NEPER)
    }
}

/** Night audio. It stays in the audio chain while off, so the toggle needs no flush or re-prepare. */
@OptIn(UnstableApi::class)
class NightAudioProcessor : BaseAudioProcessor() {
    private var dsp: NightCompressor? = null
    private var scratch = FloatArray(0)

    @Volatile var enabled = false
        set(value) {
            field = value
            dsp?.enabled = value
        }

    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val usable = format.encoding == C.ENCODING_PCM_16BIT || format.encoding == C.ENCODING_PCM_FLOAT
        return if (usable) format else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val format = outputAudioFormat
        dsp = if (format == AudioProcessor.AudioFormat.NOT_SET) {
            null
        } else {
            NightCompressor(format.sampleRate, format.channelCount).also { it.enabled = enabled }
        }
    }

    override fun onReset() {
        dsp = null
        scratch = FloatArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val current = dsp
        if (current == null || current.idle) {
            output.put(inputBuffer).flip()
            return
        }
        val channels = inputAudioFormat.channelCount
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            val count = size / Float.SIZE_BYTES
            val samples = buffer(count)
            for (i in 0 until count) samples[i] = input.getFloat()
            current.process(samples, count / channels)
            for (i in 0 until count) output.putFloat(samples[i])
        } else {
            val count = size / Short.SIZE_BYTES
            val samples = buffer(count)
            for (i in 0 until count) samples[i] = input.getShort() / SHORT_SCALE
            current.process(samples, count / channels)
            for (i in 0 until count) {
                val value = (samples[i] * SHORT_SCALE).toInt()
                output.putShort(value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private fun buffer(count: Int): FloatArray {
        if (scratch.size < count) scratch = FloatArray(count)
        return scratch
    }

    private companion object {
        const val SHORT_SCALE = 32768f
    }
}
