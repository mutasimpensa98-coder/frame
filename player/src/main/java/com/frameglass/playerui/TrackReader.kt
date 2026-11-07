package com.frameglass.playerui

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

/** A subtitle file beside the video or copied into app storage, in MediaItem order. */
internal data class SubtitleFile(val path: String, val label: String, val language: String?, val mimeType: String)

/** Turns Media3 tracks into rows. Sidecars come back as merged sources, so their id starts "1:", "2:", and so on. */
@OptIn(UnstableApi::class)
internal object TrackReader {
    fun rows(tracks: Tracks, type: Int, files: List<SubtitleFile>): List<TrackRow> = buildList {
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == type) {
                for (index in 0 until group.length) {
                    val format = group.getTrackFormat(index)
                    val file = fileOf(format, files)
                    val label = file?.label ?: format.label
                    add(
                        TrackRow(
                            group = groupIndex,
                            track = index,
                            language = file?.language ?: format.language?.takeUnless { it == C.LANGUAGE_UNDETERMINED },
                            label = label,
                            codec = codecName(format),
                            channels = format.channelCount.takeIf { it != Format.NO_VALUE } ?: 0,
                            forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
                            signs = TrackPicker.looksLikeSigns(label),
                            sidecarPath = file?.path,
                            isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
                            selected = group.isTrackSelected(index),
                            supported = group.isTrackSupported(index, true),
                        ),
                    )
                }
            }
        }
    }

    private fun fileOf(format: Format, files: List<SubtitleFile>): SubtitleFile? {
        val source = format.id?.substringBefore(':', "")?.toIntOrNull() ?: return null
        return if (source >= 1) files.getOrNull(source - 1) else null
    }

    fun codecName(format: Format): String? {
        val mime = if (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) format.codecs else format.sampleMimeType
        return when (mime) {
            null -> null
            MimeTypes.VIDEO_H264 -> "H.264"
            MimeTypes.VIDEO_H265 -> "HEVC"
            MimeTypes.VIDEO_H266 -> "VVC"
            MimeTypes.VIDEO_AV1 -> "AV1"
            MimeTypes.VIDEO_VP9 -> "VP9"
            MimeTypes.VIDEO_VP8 -> "VP8"
            MimeTypes.VIDEO_MP4V -> "MPEG-4"
            MimeTypes.VIDEO_MPEG, MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
            MimeTypes.VIDEO_VC1 -> "VC-1"
            MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
            MimeTypes.AUDIO_AC3 -> "AC-3"
            MimeTypes.AUDIO_E_AC3 -> "E-AC-3"
            MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3 Atmos"
            MimeTypes.AUDIO_AC4 -> "AC-4"
            MimeTypes.AUDIO_TRUEHD -> "TrueHD"
            MimeTypes.AUDIO_DTS -> "DTS"
            MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
            MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_VORBIS -> "Vorbis"
            MimeTypes.AUDIO_ALAC -> "ALAC"
            MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
            MimeTypes.AUDIO_RAW -> "PCM"
            MimeTypes.TEXT_SSA -> "ASS"
            MimeTypes.APPLICATION_SUBRIP -> "SRT"
            MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_MP4VTT -> "VTT"
            MimeTypes.APPLICATION_TX3G -> "mov_text"
            MimeTypes.APPLICATION_PGS -> "PGS"
            MimeTypes.APPLICATION_VOBSUB -> "VobSub"
            else -> mime.substringAfter('/').removePrefix("x-").uppercase()
        }
    }

    fun firstFormat(tracks: Tracks, type: Int): Format? {
        for (group in tracks.groups) {
            if (group.type == type && group.length > 0) return group.getTrackFormat(0)
        }
        return null
    }
}
