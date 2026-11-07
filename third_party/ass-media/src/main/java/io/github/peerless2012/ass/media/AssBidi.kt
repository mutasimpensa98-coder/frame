package io.github.peerless2012.ass.media

/**
 * Local addition, not part of upstream.
 *
 * libass lays every line out with a left-to-right base direction unless the font encoding is -1.
 * Arabic lines then show closing punctuation on the wrong side. The `\fe-1` override makes libass
 * take the direction from the text. Some Arabic subtitles already move punctuation to the start
 * of the line to look right under that left-to-right layout, and those lines stay untouched.
 */
object AssBidi {
    private const val AUTO_DIRECTION = "{\\fe-1}"
    private const val CHUNK_TEXT_FIELD = 8 // ReadOrder,Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Text
    private const val DIALOGUE_TEXT_FIELD = 9 // Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
    private const val MOVED_TO_START = ".,!?…؟،؛:)]}»"
    private val overrides = Regex("\\{[^}]*\\}")
    private val lineBreaks = Regex("\\\\[Nnh]")

    private fun isArabic(c: Char) = c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' ||
        c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻿'

    private fun isExplicit(c: Char) = c == '‏' || c == '‫' || c == '‮' || c == '⁧'

    /** True when the event text is Arabic and is not laid out by hand for a left-to-right base. */
    fun needsAutoDirection(text: String): Boolean {
        val plain = lineBreaks.replace(overrides.replace(text, ""), " ").trim()
        if (plain.isEmpty() || plain.none(::isArabic)) return false
        if (plain.any(::isExplicit)) return false
        return plain[0] !in MOVED_TO_START
    }

    private fun mostlyArabic(text: String): Boolean {
        val plain = overrides.replace(text, "")
        return plain.count(::isArabic) >= plain.count { it in 'a'..'z' || it in 'A'..'Z' }
    }

    /** [line] holds [fields] comma separated fields before the event text. */
    private fun fix(line: String, fields: Int): String {
        var at = 0
        repeat(fields) {
            val comma = line.indexOf(',', at)
            if (comma < 0) return line
            at = comma + 1
        }
        val text = line.substring(at)
        if (!needsAutoDirection(text)) return line
        // A line such as "S01 هو الأول." starts with Latin letters but reads right to left.
        val mark = if (mostlyArabic(text)) "‏" else ""
        return line.substring(0, at) + AUTO_DIRECTION + mark + text
    }

    /** An embedded Matroska event: everything after the two timestamps of a Dialogue block. */
    fun fixChunk(data: ByteArray, offset: Int, length: Int): ByteArray =
        fix(String(data, offset, length, Charsets.UTF_8), CHUNK_TEXT_FIELD).toByteArray(Charsets.UTF_8)

    /** A whole external ASS file. */
    fun fixScript(data: ByteArray, offset: Int, length: Int): ByteArray =
        String(data, offset, length, Charsets.UTF_8).lineSequence().joinToString("\n") { line ->
            if (line.startsWith("Dialogue:")) "Dialogue:" + fix(line.substring(9), DIALOGUE_TEXT_FIELD) else line
        }.toByteArray(Charsets.UTF_8)
}
