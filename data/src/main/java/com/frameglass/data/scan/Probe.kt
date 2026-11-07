package com.frameglass.data.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.palette.graphics.Palette
import com.frameglass.data.logic.AudioSummary
import com.frameglass.data.logic.AudioTrackInfo
import com.frameglass.data.logic.FramePicker
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import java.io.File
import kotlin.math.max

internal data class ProbeFacts(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoCodec: String?,
    val hdrType: String?,
    val audioSummary: String?,
    val hasEmbeddedSub: Boolean,
)

internal data class CoverColor(val dominant: Int, val luma: Int)

/** MediaMetadataRetriever gives size and colour info. NextLib adds what it cannot see, such as audio codecs. */
internal object Prober {
    private class Basics(val durationMs: Long, val width: Int, val height: Int, val mime: String?, val hdr: String?)

    private class Streams(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val codec: String?,
        val audio: String?,
        val hasSubtitles: Boolean,
    )

    private fun basics(path: String): Basics? = try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val sideways = rotation == 90 || rotation == 270
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val transfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull()
            Basics(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                width = if (sideways) rawHeight else rawWidth,
                height = if (sideways) rawWidth else rawHeight,
                mime = mime,
                hdr = when {
                    mime == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> "Dolby Vision"
                    transfer == MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10"
                    transfer == MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
                    else -> null
                },
            )
        } finally {
            retriever.release()
        }
    } catch (_: Exception) {
        null
    }

    private fun streams(path: String): Streams? = try {
        val info = MediaInfoBuilder().from(path).build()
        try {
            val video = info?.videoStream
            info?.let {
                Streams(
                    durationMs = it.duration,
                    width = video?.frameWidth ?: 0,
                    height = video?.frameHeight ?: 0,
                    codec = video?.codecName,
                    audio = AudioSummary.of(it.audioStreams.map { a -> AudioTrackInfo(a.codecName, a.channels) }),
                    hasSubtitles = it.subtitleStreams.isNotEmpty(),
                )
            }
        } finally {
            info?.release()
        }
    } catch (_: Throwable) {
        // A missing native library is a LinkageError, and a file NextLib cannot open must not stop a scan.
        null
    }

    fun probe(path: String): ProbeFacts {
        val basics = basics(path)
        val streams = streams(path)
        return ProbeFacts(
            durationMs = basics?.durationMs?.takeIf { it > 0 } ?: streams?.durationMs ?: 0,
            width = basics?.width?.takeIf { it > 0 } ?: streams?.width ?: 0,
            height = basics?.height?.takeIf { it > 0 } ?: streams?.height ?: 0,
            videoCodec = streams?.codec ?: basics?.mime?.removePrefix("video/"),
            hdrType = basics?.hdr,
            audioSummary = streams?.audio,
            hasEmbeddedSub = streams?.hasSubtitles == true,
        )
    }
}

/** F-M15. BitmapFactory makes a software bitmap, which is what Palette needs. */
internal object CoverColors {
    private const val MAX_SIDE = 256
    private const val FALLBACK = 0xFF0D0D0D.toInt()

    fun fromFile(path: String): CoverColor? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        return try {
            fromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun fromBitmap(bitmap: Bitmap): CoverColor {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val small = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        if (small !== bitmap) small.recycle()
        return CoverColor(palette.getDominantColor(FALLBACK), FramePicker.meanLuma(pixels).toInt())
    }
}

/** F-M7. Samples at 10, 20 and 30 percent and keeps the frame with the highest luma variance. */
internal object FrameGrabber {
    private const val BOX = 854
    private const val QUALITY = 80

    private fun variance(bitmap: Bitmap): Double {
        val small = Bitmap.createScaledBitmap(bitmap, 32, 18, true)
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        if (small !== bitmap) small.recycle()
        return FramePicker.lumaVariance(pixels)
    }

    private fun fromRetriever(path: String, times: List<Long>): List<Bitmap> = try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 }
            times.mapNotNull { ms ->
                runCatching {
                    retriever.getScaledFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, BOX, BOX, params)
                }.getOrNull()
            }
        } finally {
            retriever.release()
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Software decode, for containers the platform retriever refuses, such as WMV and RealVideo. */
    private fun fromNextLib(path: String, times: List<Long>): List<Bitmap> = try {
        val info = MediaInfoBuilder().from(path).build()
        try {
            if (info == null || !info.supportsFrameLoading) {
                emptyList()
            } else {
                times.mapNotNull { ms -> runCatching { info.getFrameAt(ms) }.getOrNull() }.map(::fit)
            }
        } finally {
            info?.release()
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun fit(bitmap: Bitmap): Bitmap {
        val side = max(bitmap.width, bitmap.height)
        if (side <= BOX) return bitmap
        val scaled = Bitmap.createScaledBitmap(bitmap, bitmap.width * BOX / side, bitmap.height * BOX / side, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** Writes the best frame to [target] and returns its colours, or null when no frame could be read. */
    fun grab(path: String, durationMs: Long, target: File): CoverColor? {
        val times = FramePicker.sampleTimesMs(durationMs)
        val frames = fromRetriever(path, times).ifEmpty { fromNextLib(path, times) }
        val best = frames.maxByOrNull(::variance) ?: return null
        return try {
            target.parentFile?.mkdirs()
            val partial = File(target.path + ".part")
            val written = partial.outputStream().use { best.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (written && partial.renameTo(target)) CoverColors.fromBitmap(best) else null
        } catch (_: java.io.IOException) {
            null
        } finally {
            frames.forEach { it.recycle() }
        }
    }
}

/** App-private image storage. Nothing here is written into the owner's media folders. */
internal class ImageStore(context: Context) {
    private val root: File = context.filesDir
    private val frames = File(root, "frames")
    private val meta = File(root, "meta")
    private val covers = File(root, "covers")
    private val people = File(root, "people")

    fun frameFile(fileId: String) = File(frames, "$fileId.jpg")
    fun titleDir(titleId: Long) = File(meta, "t$titleId").apply { mkdirs() }
    fun personFile(personId: Long, stamp: Long) = File(people.apply { mkdirs() }, "p$personId-$stamp.jpg")
    fun coverFile(titleId: Long, stamp: Long, extension: String = "jpg") =
        File(covers.apply { mkdirs() }, "t$titleId-$stamp.$extension")

    fun deleteTitleDir(titleId: Long) {
        File(meta, "t$titleId").deleteRecursively()
    }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }
}
