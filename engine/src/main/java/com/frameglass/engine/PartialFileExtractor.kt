package com.frameglass.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SniffFailure
import androidx.media3.extractor.mkv.MatroskaExtractor
import java.io.EOFException

/** The file stops before its container says it should, as a download that has not finished does. */
@OptIn(UnstableApi::class)
class TruncatedFileException(cause: Throwable?) :
    ParserException("The file stops before its container says it should", cause, true, C.DATA_TYPE_MEDIA)

/**
 * Plays a Matroska file that stops early, up to where it stops.
 *
 * The extractor wants the Cues before it reads any cluster. They normally sit at the end, so in a partial
 * download it seeks past the last byte and the file fails to open. This starts the extractor over with
 * FLAG_DISABLE_SEEK_FOR_CUES instead, which gives up seeking and keeps everything that is on disk.
 *
 * Where the data stops is then an error and not an end of stream. Media3 ends playback only at the duration
 * in the header, so an end of stream would hold the last frame for the whole missing part. The error waits
 * in the loader until the renderers have used up what was read, so nothing on disk is lost to it.
 */
@OptIn(UnstableApi::class)
class PartialFileExtractor(private val create: (flags: Int) -> Extractor) : Extractor {
    private var delegate = create(0)
    private lateinit var output: ExtractorOutput

    /** Reading away from the clusters, which is where the index is. */
    private var atIndex = false

    /** The index was given up on, so this file is known to stop early. */
    private var withoutIndex = false

    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun getSniffFailureDetails(): List<SniffFailure> = delegate.sniffFailureDetails

    override fun init(output: ExtractorOutput) {
        this.output = output
        delegate.init(output)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val result = try {
            delegate.read(input, seekPosition)
        } catch (end: EOFException) {
            return dataStopped(seekPosition, end)
        } catch (zeros: IllegalStateException) {
            // The EBML reader's complaint about a zero byte: a preallocated file not filled in yet.
            return dataStopped(seekPosition, zeros)
        } catch (malformed: ParserException) {
            if (!atIndex) throw malformed
            return dataStopped(seekPosition, malformed)
        }
        return when {
            result == Extractor.RESULT_SEEK && isPastEnd(input, seekPosition) -> {
                atIndex = true
                dataStopped(seekPosition, null)
            }
            result == Extractor.RESULT_SEEK -> {
                atIndex = seekPosition.position > input.position
                result
            }
            result == Extractor.RESULT_END_OF_INPUT && (atIndex || withoutIndex) -> dataStopped(seekPosition, null)
            else -> result
        }
    }

    private fun isPastEnd(input: ExtractorInput, seekPosition: PositionHolder): Boolean =
        input.length != C.LENGTH_UNSET.toLong() && seekPosition.position >= input.length

    /** At the index this costs only seeking, and reading starts over. Anywhere else playback ends here. */
    private fun dataStopped(seekPosition: PositionHolder, cause: Throwable?): Int {
        if (!atIndex || withoutIndex) throw TruncatedFileException(cause)
        atIndex = false
        withoutIndex = true
        delegate.release()
        delegate = create(MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)
        delegate.init(output)
        seekPosition.position = 0
        return Extractor.RESULT_SEEK
    }

    override fun seek(position: Long, timeUs: Long) {
        atIndex = false
        delegate.seek(position, timeUs)
    }

    override fun release() = delegate.release()

    override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation
}
