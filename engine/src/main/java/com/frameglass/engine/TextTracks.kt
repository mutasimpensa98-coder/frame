package com.frameglass.engine

import android.content.Context
import android.net.Uri
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.CueDecoder
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * A second subtitle as plain text cues, for an overlay of the app's own beside Media3's single text renderer.
 * Every cue loses its position, size and box, so the caller can place it; an ASS file or track keeps only its words.
 * Bitmap tracks (PGS, VobSub, DVB) give nothing.
 */
@OptIn(UnstableApi::class)
object TextTracks {
    private const val THREAD_NAME = "second-subtitle"
    private const val PROGRESS_EVERY_MS = 1_500L
    private const val BUFFER_STEP = 4_096

    /** About 4 minutes for a 10 GB file, and far above what playback of any video on the phone reads. */
    const val READ_BYTES_PER_SECOND = 40L * 1024 * 1024
    private const val PAUSE_STEP_MS = 250L
    private const val MIN_SLEEP_MS = 20L

    /** [file] must be UTF-8, see [SubtitleEncoding.toUtf8]. Null when it holds no text to show. */
    fun readFile(file: File, mimeType: String): CueTimeline<Cue>? {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return null
        val format = Format.Builder().setSampleMimeType(mimeType).build()
        val parsers = DefaultSubtitleParserFactory()
        if (!parsers.supportsFormat(format)) return null
        val timed = TimedCues()
        parsers.create(format).parse(bytes, SubtitleParser.OutputOptions.allCues()) { timed.add(it) }
        return timed.timeline().takeUnless { it.isEmpty }
    }

    /**
     * Reads the text track at [ordinal] among the container's text tracks, in the order the container lists them,
     * which is the order Media3 gives their groups. The first time it reads the whole file once on a background
     * thread of its own, so the cues arrive from the start onward: each emission holds every cue found so far, and
     * the last one holds them all. A read that fails part way ends the flow with what was found.
     *
     * The read is capped at [READ_BYTES_PER_SECOND] and waits while [competing] says playback needs the storage.
     * A complete read is kept in [cacheDir] under [cacheIdentity], and the next open reads that instead. With no
     * [cacheIdentity], as for a private video, nothing is written anywhere.
     */
    fun readEmbedded(
        context: Context,
        video: Uri,
        ordinal: Int,
        cacheDir: File?,
        cacheIdentity: String?,
        competing: () -> Boolean,
    ): Flow<CueTimeline<Cue>> {
        val app = context.applicationContext
        val thread = backgroundThread()
        val cacheFile = if (cacheDir != null && cacheIdentity != null) CueCache.fileFor(cacheDir, "$cacheIdentity#$ordinal") else null
        return flow {
            cacheFile?.let(CueCache::read)?.let { cached ->
                emit(TimedCues().apply { cached.forEach(::addPlain) }.timeline())
                return@flow
            }
            val reader = EmbeddedReader(ordinal)
            val pace = ReadPace(READ_BYTES_PER_SECOND)
            var lastEmitAt = System.currentTimeMillis()
            var emitted = 0
            var windowStart = System.currentTimeMillis()
            var windowBytes = 0L
            val complete = reader.run(app, video) { bytes ->
                currentCoroutineContext().ensureActive()
                windowBytes += bytes
                if (competing()) {
                    while (competing()) delay(PAUSE_STEP_MS)
                    // A pause is not credit for a burst afterwards.
                    windowStart = System.currentTimeMillis()
                    windowBytes = 0L
                }
                val wait = pace.waitMs(windowBytes, System.currentTimeMillis() - windowStart)
                // Short waits gather until they are worth a sleep.
                if (wait >= MIN_SLEEP_MS) delay(wait)
                val now = System.currentTimeMillis()
                if (now - lastEmitAt >= PROGRESS_EVERY_MS && reader.cues.size > emitted) {
                    lastEmitAt = now
                    emitted = reader.cues.size
                    emit(reader.cues.timeline())
                }
            }
            emit(reader.cues.timeline())
            if (complete && cacheFile != null && cacheDir != null && CueCache.write(cacheFile, reader.cues.plainEntries())) {
                CueCache.trim(cacheDir)
            }
        }.flowOn(thread).onCompletion { thread.close() }
    }
    private fun backgroundThread(): ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, THREAD_NAME)
    }.asCoroutineDispatcher()

    /** Cues with their times. A cue with no end lasts until the next one starts, as Media3 shows it. */
    private class TimedCues {
        private val starts = ArrayList<Long>()
        private val ends = ArrayList<Long>()
        private val values = ArrayList<Cue>()

        val size: Int get() = values.size

        fun add(timed: CuesWithTiming) {
            for (cue in timed.cues) {
                val text = cue.text?.takeIf { it.isNotBlank() } ?: continue
                starts += timed.startTimeUs
                ends += timed.endTimeUs
                values += CueBidi.fix(Cue.Builder().setText(text).build())
            }
        }

        /** From the cache: the text already carries its direction marks. */
        fun addPlain(entry: CueCache.Entry) {
            starts += entry.startUs
            ends += entry.endUs
            values += Cue.Builder().setText(entry.text).build()
        }

        /** Words only, for the cache. Styling spans do not survive, and the overlay draws words only anyway. */
        fun plainEntries(): List<CueCache.Entry> =
            values.indices.map { CueCache.Entry(starts[it], ends[it], values[it].text?.toString().orEmpty()) }

        fun timeline(): CueTimeline<Cue> {
            val ordered = values.indices.sortedBy { starts[it] }
            // The first later start after each place, found walking back once.
            val laterStart = LongArray(ordered.size)
            var later = Long.MAX_VALUE
            for (place in ordered.indices.reversed()) {
                laterStart[place] = later
                val start = starts[ordered[place]]
                if (place == 0 || starts[ordered[place - 1]] < start) later = start
            }
            val entries = ordered.mapIndexed { place, index ->
                val end = ends[index].takeIf { it != C.TIME_UNSET } ?: laterStart[place]
                CueTimeline.Entry(starts[index], end, values[index])
            }
            return CueTimeline(entries)
        }
    }

    /** Runs a Media3 extractor over the file with text transcoding on, keeping only the wanted track's samples. */
    private class EmbeddedReader(private val ordinal: Int) : ExtractorOutput {
        val cues = TimedCues()
        private val textIds = ArrayList<Int>()
        private val outputs = HashMap<Int, TrackOutput>()

        override fun track(id: Int, type: Int): TrackOutput = outputs.getOrPut(id) {
            if (type == C.TRACK_TYPE_TEXT) textIds += id
            if (type == C.TRACK_TYPE_TEXT && textIds.size - 1 == ordinal) CueCapture(cues) else DiscardingTrackOutput()
        }

        override fun endTracks() = Unit

        override fun seekMap(seekMap: SeekMap) = Unit

        /** True when the file was read to its end, so the cues are complete. */
        suspend fun run(context: Context, video: Uri, afterEachRead: suspend (bytes: Long) -> Unit): Boolean {
            // Media3 1.11 transcodes text tracks by default, with its own parsers, so ASS comes out as plain cues.
            val factory = DefaultExtractorsFactory()
            val source = DefaultDataSource.Factory(context).createDataSource()
            try {
                var input = open(source, video, 0)
                val extractor = sniff(factory.createExtractors(video, emptyMap()), input) ?: return false
                extractor.init(this)
                val seek = PositionHolder()
                try {
                    while (true) {
                        val before = input.position
                        val result = try {
                            extractor.read(input, seek)
                        } catch (_: IOException) {
                            // Malformed, cut short or unreadable: what was read so far stays, and is not cached.
                            return false
                        }
                        if (result == Extractor.RESULT_END_OF_INPUT) return true
                        val bytes = (input.position - before).coerceAtLeast(0)
                        if (result == Extractor.RESULT_SEEK) {
                            source.close()
                            input = open(source, video, seek.position)
                        }
                        afterEachRead(bytes)
                    }
                } finally {
                    extractor.release()
                }
            } catch (_: IOException) {
                // The file could not be opened: nothing to show.
                return false
            } finally {
                runCatching { source.close() }
            }
        }

        private fun open(source: DataSource, video: Uri, position: Long): ExtractorInput {
            val length = source.open(DataSpec.Builder().setUri(video).setPosition(position).build())
            return DefaultExtractorInput(source, position, if (length == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET.toLong() else position + length)
        }

        private fun sniff(extractors: Array<Extractor>, input: ExtractorInput): Extractor? {
            for (extractor in extractors) {
                val found = try {
                    extractor.sniff(input)
                } catch (_: EOFException) {
                    false
                } finally {
                    input.resetPeekPosition()
                }
                if (found) return extractor
            }
            return null
        }
    }

    /**
     * Collects the samples of one transcoded text track. Each sample is one encoded cue group, see [CueDecoder].
     * A track Media3 could not transcode keeps its own mime type, and its samples are dropped.
     */
    private class CueCapture(private val cues: TimedCues) : TrackOutput {
        private val decoder = CueDecoder()
        private var buffer = ByteArray(BUFFER_STEP)
        private var written = 0
        private var transcoded = false

        override fun format(format: Format) {
            transcoded = format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES
        }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            ensure(length)
            val read = input.read(buffer, written, length)
            if (read == C.RESULT_END_OF_INPUT) {
                if (allowEndOfInput) return C.RESULT_END_OF_INPUT
                throw EOFException()
            }
            written += read
            return read
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            ensure(length)
            data.readBytes(buffer, written, length)
            written += length
        }

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            val end = written - offset
            val start = end - size
            if (transcoded && start >= 0 && size > 0) {
                runCatching { cues.add(decoder.decode(timeUs, buffer, start, size)) }
            }
            // Bytes after this sample belong to the next one.
            val left = (written - end).coerceAtLeast(0)
            if (left > 0 && end > 0) System.arraycopy(buffer, end, buffer, 0, left)
            written = left
        }

        private fun ensure(more: Int) {
            if (written + more <= buffer.size) return
            buffer = buffer.copyOf(maxOf(buffer.size * 2, written + more + BUFFER_STEP))
        }
    }
}
