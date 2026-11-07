package com.frameglass.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import java.io.File

/**
 * A subtitle file that arrives during playback. Media3 fixes a file's tracks at prepare, so a normal sidecar
 * means opening the file again. These two go straight to the overlays already on screen, and the video never stops.
 */
@OptIn(UnstableApi::class)
sealed interface LiveSubtitle {
    /** For the cue view, fed by the caller from [timeline]. */
    class Cues(val timeline: CueTimeline<Cue>) : LiveSubtitle

    /** For libass, through [FramePlayer.showLiveScript]. */
    class Script(val bytes: ByteArray) : LiveSubtitle

    companion object {
        /** [file] must be UTF-8, see [SubtitleEncoding.toUtf8]. Null when it holds nothing to show. */
        fun read(file: File, mimeType: String): LiveSubtitle? {
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return null
            if (mimeType == MimeTypes.TEXT_SSA) return Script(bytes)
            val format = Format.Builder().setSampleMimeType(mimeType).build()
            val parsers = DefaultSubtitleParserFactory()
            if (!parsers.supportsFormat(format)) return null
            val entries = ArrayList<CueTimeline.Entry<Cue>>()
            parsers.create(format).parse(bytes, SubtitleParser.OutputOptions.allCues()) { timed ->
                val end = if (timed.endTimeUs == C.TIME_UNSET) Long.MAX_VALUE else timed.endTimeUs
                timed.cues.forEach { entries += CueTimeline.Entry(timed.startTimeUs, end, CueBidi.fix(it)) }
            }
            return CueTimeline(entries).takeUnless { it.isEmpty }?.let(::Cues)
        }
    }
}
