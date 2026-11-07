package com.frameglass.engine

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

enum class SyncProblem {
    /** No audio track this phone can decode, such as DTS or TrueHD, or a container it cannot open. */
    NO_AUDIO,

    /** The subtitle has too few timed lines of dialogue to compare. */
    NO_CUES,

    /** The hard time limit ran out first. */
    TIMED_OUT,
}

sealed interface SyncOutcome {
    /** Confident. Video time = [fix] ratio × subtitle time + [offsetMs]. */
    data class Fit(val offsetMs: Long, val fix: FrameRateFix) : SyncOutcome

    /** The audio was read, and no offset stood out enough to trust. */
    data object Unsure : SyncOutcome

    data class Unavailable(val problem: SyncProblem) : SyncOutcome
}

/**
 * Automatic subtitle sync, all on the phone. A second reader decodes stretches of the video's audio on a
 * background thread of its own, apart from playback: its own MediaExtractor and a software MediaCodec where the
 * phone has one, so the player's decoder is never shared. It stops at cancellation, and [HARD_LIMIT_MS] bounds it
 * even when a read blocks. It writes nothing anywhere and logs nothing, so a private video leaves no trace.
 */
object SubtitleSync {
    /** No new stretch of audio starts after this. */
    const val SOFT_LIMIT_MS = 15_000L
    const val HARD_LIMIT_MS = 25_000L

    private const val MIN_CUES = 20
    private const val CODEC_WAIT_US = 10_000L
    private const val PRE_ROLL_US = 1_000_000L
    private const val THREAD_NAME = "subtitle-sync"

    /**
     * [subtitle] must be UTF-8 or in the charset the player reads it with, see [SubtitleEncoding.toUtf8].
     * [audioOrdinal] is the audio track playing, counted among audio tracks only, and is tried first.
     */
    suspend fun run(
        context: Context,
        video: Uri,
        subtitle: File,
        mimeType: String,
        durationMs: Long,
        videoFps: Float?,
        audioOrdinal: Int?,
    ): SyncOutcome {
        val app = context.applicationContext
        val thread = backgroundThread()
        // Not a child of the caller: a read stuck in the platform must not hold the caller past the limit.
        val work = CoroutineScope(thread).async { analyse(app, video, subtitle, mimeType, durationMs, videoFps, audioOrdinal) }
        return try {
            withTimeoutOrNull(HARD_LIMIT_MS) { work.await() } ?: SyncOutcome.Unavailable(SyncProblem.TIMED_OUT)
        } finally {
            work.cancel()
            thread.close()
        }
    }

    private fun backgroundThread(): ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, THREAD_NAME)
    }.asCoroutineDispatcher()

    private suspend fun analyse(
        context: Context,
        video: Uri,
        subtitle: File,
        mimeType: String,
        durationMs: Long,
        videoFps: Float?,
        audioOrdinal: Int?,
    ): SyncOutcome {
        val kind = SubtitleCues.kindOf(mimeType) ?: return SyncOutcome.Unavailable(SyncProblem.NO_CUES)
        val cues = try {
            SubtitleCues.spans(subtitle.readText(), kind)
        } catch (failed: Throwable) {
            if (failed is CancellationException) throw failed
            emptyList()
        }
        if (cues.size < MIN_CUES) return SyncOutcome.Unavailable(SyncProblem.NO_CUES)

        val started = SystemClock.elapsedRealtime()
        val extractor = MediaExtractor()
        var audio: AudioReader? = null
        try {
            audio = AudioReader.open(context, extractor, video, audioOrdinal) ?: return SyncOutcome.Unavailable(SyncProblem.NO_AUDIO)
            val duration = maxOf(durationMs, audio.durationUs / 1000)
            val detector = SpeechDetector(duration)
            val windows = SyncPlan.windows(duration)
            val fixes = FrameRateFix.candidates(videoFps)
            var previous: SyncFit? = null
            var latest: SyncFit? = null
            var readSinceLatest = false
            for ((index, window) in windows.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (SystemClock.elapsedRealtime() - started > SOFT_LIMIT_MS) break
                audio.read(window, detector)
                readSinceLatest = true
                if (!SyncPlan.isCheckpoint(index + 1, windows.size)) continue
                val fit = align(detector.mask, cues, fixes).getOrElse { return SyncOutcome.Unsure }
                readSinceLatest = false
                // Two checks in a row that agree end it early. One alone could be luck in a few stretches.
                if (fit != null && fit.confident && previous?.confident == true && previous.agrees(fit)) {
                    return SyncOutcome.Fit(fit.offsetMs, fit.fix)
                }
                previous = fit
                latest = fit
            }
            val final = if (readSinceLatest) align(detector.mask, cues, fixes).getOrElse { return SyncOutcome.Unsure } else latest
            return if (final?.confident == true) SyncOutcome.Fit(final.offsetMs, final.fix) else SyncOutcome.Unsure
        } catch (failed: Throwable) {
            // An Error here, such as OutOfMemoryError, would otherwise reach the player's scope, which has no handler.
            if (failed is CancellationException) throw failed
            return SyncOutcome.Unavailable(SyncProblem.NO_AUDIO)
        } finally {
            audio?.release()
            extractor.release()
        }
    }

    /** A failure, even an OutOfMemoryError, means no answer: it must never end playback. */
    private fun align(speech: ByteArray, cues: List<CueSpan>, fixes: List<FrameRateFix>): Result<SyncFit?> = try {
        Result.success(SyncAligner.align(speech, cues, fixes))
    } catch (failed: Throwable) {
        if (failed is CancellationException) throw failed
        Result.failure(failed)
    }

    /** One audio track and its decoder, read a window at a time. */
    private class AudioReader(
        private val extractor: MediaExtractor,
        private val codec: MediaCodec,
        val durationUs: Long,
        private var layout: PcmLayout,
    ) {
        private val info = MediaCodec.BufferInfo()
        private var mono = FloatArray(0)

        suspend fun read(window: SyncPlan.Window, detector: SpeechDetector) {
            val startUs = window.startMs * 1000
            val endUs = window.endMs * 1000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec.flush()
            detector.begin(layout.rate)
            var inputDone = false
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) inputDone = queueInput(endUs)
                val index = codec.dequeueOutputBuffer(info, CODEC_WAIT_US)
                when {
                    index >= 0 -> {
                        val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.size > 0 && info.presentationTimeUs >= startUs - PRE_ROLL_US) {
                            val count = downmix(index)
                            if (count > 0) detector.feed(info.presentationTimeUs, mono, count)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (ended) break
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        layout = PcmLayout.of(codec.outputFormat, layout)
                        detector.begin(layout.rate)
                    }
                }
            }
            detector.end()
        }

        /** True once the end of the window, or of the file, has gone in. */
        private fun queueInput(endUs: Long): Boolean {
            val index = codec.dequeueInputBuffer(CODEC_WAIT_US)
            if (index < 0) return false
            val buffer = codec.getInputBuffer(index) ?: return false
            val size = extractor.readSampleData(buffer, 0)
            val time = extractor.sampleTime
            if (size < 0 || time < 0 || time >= endUs) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return true
            }
            codec.queueInputBuffer(index, 0, size, time, 0)
            extractor.advance()
            return false
        }

        /** Dialogue lives in the centre channel when there is one, so that channel alone is used. */
        private fun downmix(index: Int): Int {
            val buffer = codec.getOutputBuffer(index) ?: return 0
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            val channels = layout.channels.coerceAtLeast(1)
            val frames: Int
            if (layout.float) {
                val samples = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
                frames = samples.remaining() / channels
                ensure(frames)
                for (f in 0 until frames) mono[f] = pick(channels) { samples.get(f * channels + it) }
            } else if (layout.pcm16) {
                val samples = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                frames = samples.remaining() / channels
                ensure(frames)
                for (f in 0 until frames) mono[f] = pick(channels) { samples.get(f * channels + it) / 32768f }
            } else {
                return 0
            }
            return frames
        }

        private inline fun pick(channels: Int, sample: (Int) -> Float): Float {
            val center = layout.center
            if (center in 0 until channels) return sample(center)
            if (channels == 1) return sample(0)
            return (sample(0) + sample(1)) / 2
        }

        private fun ensure(frames: Int) {
            if (mono.size < frames) mono = FloatArray(frames)
        }

        fun release() {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }

        companion object {
            /** The playing track first, then every other audio track, each with software decoders first. */
            fun open(context: Context, extractor: MediaExtractor, video: Uri, preferred: Int?): AudioReader? {
                if (video.scheme == "file") extractor.setDataSource(video.path ?: return null) else extractor.setDataSource(context, video, null)
                val audio = (0 until extractor.trackCount).filter { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                val first = preferred?.let { audio.getOrNull(it) }
                val order = if (first != null) listOf(first) + (audio - first) else audio
                for (track in order) {
                    val format = extractor.getTrackFormat(track)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    for (name in decoders(mime)) {
                        val codec = runCatching { MediaCodec.createByCodecName(name) }.getOrNull() ?: continue
                        val started = runCatching {
                            codec.configure(format, null, null, 0)
                            codec.start()
                        }.isSuccess
                        if (!started) {
                            codec.release()
                            continue
                        }
                        extractor.selectTrack(track)
                        val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                        return AudioReader(extractor, codec, duration, PcmLayout.of(format, null))
                    }
                }
                return null
            }

            private fun decoders(mime: String): List<String> = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { info -> !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
                .sortedBy { if (it.isSoftwareOnly) 0 else 1 }
                .map { it.name }
        }
    }

    private class PcmLayout(val rate: Int, val channels: Int, val float: Boolean, val pcm16: Boolean, val center: Int) {
        companion object {
            fun of(format: MediaFormat, before: PcmLayout?): PcmLayout {
                val rate = format.intOr(MediaFormat.KEY_SAMPLE_RATE, before?.rate ?: 0)
                val channels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT, before?.channels ?: 1)
                val encoding = format.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                val mask = format.intOr(MediaFormat.KEY_CHANNEL_MASK, 0)
                val center = when {
                    mask and AudioFormat.CHANNEL_OUT_FRONT_CENTER != 0 && channels >= 3 ->
                        Integer.bitCount(mask and (AudioFormat.CHANNEL_OUT_FRONT_CENTER - 1))
                    // Android's default order for 3 and more channels starts left, right, centre.
                    mask == 0 && channels == 3 || mask == 0 && channels >= 5 -> 2
                    else -> -1
                }
                return PcmLayout(rate, channels, encoding == AudioFormat.ENCODING_PCM_FLOAT, encoding == AudioFormat.ENCODING_PCM_16BIT, center)
            }

            private fun MediaFormat.intOr(key: String, fallback: Int): Int = if (containsKey(key)) getInteger(key) else fallback
        }
    }
}
