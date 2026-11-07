package com.frameglass.engine

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode

/** F-P18: what the phone really offers, read at runtime instead of assumed. */
object DecoderProbe {
    private val decoders: List<MediaCodecInfo> by lazy {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filterNot { it.isEncoder }
    }

    /** FFmpeg decoders are not in MediaCodecList, so an unknown name reads as software. */
    fun isHardware(decoderName: String): Boolean =
        decoders.firstOrNull { it.name == decoderName }?.isHardwareAccelerated == true
}

/**
 * The next decoder path after a failure. AUTO already means hardware, then software, then FFmpeg
 * at selection time, so the later steps exist for a decoder that was chosen and then broke at runtime.
 */
class DecoderLadder(private val steps: List<DecoderMode>) {
    private var index = 0

    fun advance(): DecoderMode? {
        if (index >= steps.lastIndex) return null
        index++
        return steps[index]
    }

    fun reset() {
        index = 0
    }

    companion object {
        fun video() = DecoderLadder(listOf(DecoderMode.AUTO, DecoderMode.SOFTWARE, DecoderMode.FFMPEG))
        fun audio() = DecoderLadder(listOf(DecoderMode.AUTO, DecoderMode.FFMPEG, DecoderMode.SOFTWARE))
    }
}
