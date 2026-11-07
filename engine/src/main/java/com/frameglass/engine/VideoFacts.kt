package com.frameglass.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

enum class HdrKind { SDR, HDR10, HLG, DOLBY_VISION }

/**
 * F-P20. [base] is what really reaches the display. This phone reports HDR10, HLG and HDR10+ and no
 * Dolby Vision path, so a Dolby Vision file can only ever show its base layer.
 */
data class HdrReport(
    val kind: HdrKind,
    val base: HdrKind,
    val dolbyVisionProfile: Int? = null,
    /** Profile 5 has no compatible base layer, and profile 7 loses its enhancement layer. */
    val warning: Boolean = false,
)

@OptIn(UnstableApi::class)
object VideoFacts {
    private val DOLBY_PREFIXES = listOf("dvhe.", "dvh1.", "dvav.", "dva1.", "dav1.")

    fun hdr(format: Format?): HdrReport {
        if (format == null) return HdrReport(HdrKind.SDR, HdrKind.SDR)
        val base = when (format.colorInfo?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> HdrKind.HDR10
            C.COLOR_TRANSFER_HLG -> HdrKind.HLG
            else -> HdrKind.SDR
        }
        val profile = dolbyVisionProfile(format)
        return when {
            profile != null -> HdrReport(HdrKind.DOLBY_VISION, base, profile, profile == 5 || profile == 7)
            format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> HdrReport(HdrKind.DOLBY_VISION, base, null, true)
            else -> HdrReport(base, base)
        }
    }

    fun dolbyVisionProfile(format: Format): Int? {
        val codec = format.codecs?.split(',')?.map { it.trim() }
            ?.firstOrNull { part -> DOLBY_PREFIXES.any { part.startsWith(it) } }
            ?: return null
        return codec.split('.').getOrNull(1)?.toIntOrNull()
    }

    /** Average bits per second from size and duration, for containers that state no bitrate. */
    fun estimateBitrate(fileBytes: Long, durationMs: Long): Long =
        if (fileBytes > 0 && durationMs > 0) fileBytes * 8_000L / durationMs else 0L
}
