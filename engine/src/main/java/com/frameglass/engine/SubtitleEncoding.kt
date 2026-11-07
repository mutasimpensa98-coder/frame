package com.frameglass.engine

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Reads a subtitle file in any common encoding and returns it as a UTF-8 file (F-P8). */
object SubtitleEncoding {
    private val CP1256 = Charset.forName("windows-1256")
    private val CP1252 = Charset.forName("windows-1252")

    private const val REPLACEMENT = '�'
    private const val BOM = "\uFEFF"

    /** The charsets a person may force from the subtitle menu, in the order the menu shows them. */
    val choices: List<String> =
        listOf("UTF-8", "windows-1256", "ISO-8859-6", "UTF-16LE", "UTF-16BE", "windows-1252").filter(Charset::isSupported)

    private val scored: List<Charset> = listOf(CP1256, Charset.forName("ISO-8859-6"), CP1252)

    fun detect(bytes: ByteArray): Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return Charsets.UTF_8
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return Charsets.UTF_16LE
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return Charsets.UTF_16BE
        if (isValid(bytes, Charsets.UTF_8)) return Charsets.UTF_8
        return scored.maxBy { arabicScore(String(bytes, it)) }
    }

    private fun isValid(bytes: ByteArray, charset: Charset): Boolean = try {
        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }

    /** Arabic letters count for a charset, and the marks of a wrong guess count against it. */
    private fun arabicScore(text: String): Int {
        var score = 0
        for (c in text) {
            when {
                c in 'ء'..'ي' -> score += 2
                c == REPLACEMENT || c in '\u0080'..'\u009F' -> score -= 4
                c in 'À'..'ÿ' -> score -= 1
            }
        }
        return score
    }

    /**
     * Returns [source] unchanged if it is plain UTF-8, otherwise a UTF-8 copy in the cache.
     * A non-null [forced] charset overrides detection, for the manual picker.
     */
    fun toUtf8(context: Context, source: File, forced: String? = null): File {
        val bytes = source.readBytes()
        val charset = forced?.takeIf(Charset::isSupported)?.let(Charset::forName) ?: detect(bytes)
        val hasBom = bytes.size >= 3 && bytes[0] == 0xEF.toByte()
        if (forced == null && charset == Charsets.UTF_8 && !hasBom) return source
        val text = String(bytes, charset).removePrefix(BOM)
        val dir = File(context.cacheDir, "subs").apply { mkdirs() }
        val out = File(dir, "${source.absolutePath.hashCode()}-${charset.name()}-${source.name}")
        out.writeText(text, Charsets.UTF_8)
        return out
    }
}
