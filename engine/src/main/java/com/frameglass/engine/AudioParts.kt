package com.frameglass.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10

/** Passes audio through unchanged and measures its level, so the test harness can prove sound. */
@OptIn(UnstableApi::class)
class RmsTapProcessor : BaseAudioProcessor() {
    @Volatile private var sum = 0.0
    @Volatile private var count = 0L

    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (format.encoding == C.ENCODING_PCM_16BIT || format.encoding == C.ENCODING_PCM_FLOAT) format
        else AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val view = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var s = 0.0
        var n = 0L
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            while (view.remaining() >= 4) {
                val v = view.float.toDouble()
                s += v * v
                n++
            }
        } else {
            while (view.remaining() >= 2) {
                val v = view.short / 32768.0
                s += v * v
                n++
            }
        }
        sum += s
        count += n
        replaceOutputBuffer(size).put(inputBuffer).flip()
    }

    fun rmsDbfs(): Double = if (count == 0L) -200.0 else 10 * log10((sum / count).coerceAtLeast(1e-20))

    fun resetLevel() {
        sum = 0.0
        count = 0
    }
}

/**
 * Audio delay by offsetting the position the sink reports. A positive delay plays sound later
 * than the picture. The proof of this route is step 0.6 of the brief.
 */
@OptIn(UnstableApi::class)
class DelayAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    @Volatile var delayUs = 0L

    /** Position with no offset, used to measure the real audio and video gap. */
    @Volatile var rawPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
        private set

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val raw = super.getCurrentPositionUs(sourceEnded)
        rawPositionUs = raw
        if (raw == AudioSink.CURRENT_POSITION_NOT_SET) return raw
        return (raw + delayUs).coerceAtLeast(0)
    }
}
