package com.frameglass.engine

import androidx.media3.common.MimeTypes
import java.util.Locale
import kotlin.math.roundToLong

/** A stretch of a subtitle file where someone is meant to be speaking, in the file's own time. */
data class CueSpan(val startMs: Long, val endMs: Long)

/**
 * Cue times read straight from subtitle text, for sync, and the same text with its times stretched.
 * Pure, so it runs on the JVM. Only SRT, VTT and ASS or SSA are understood.
 */
object SubtitleCues {
    enum class Kind { SRT, VTT, ASS }

    fun kindOf(mimeType: String): Kind? = when (mimeType) {
        MimeTypes.APPLICATION_SUBRIP -> Kind.SRT
        MimeTypes.TEXT_VTT -> Kind.VTT
        MimeTypes.TEXT_SSA -> Kind.ASS
        else -> null
    }

    private val ARROW = Regex(
        """(?:(\d+):)?(\d{1,2}):(\d{1,2})[,.](\d{1,3})\s*-->\s*(?:(\d+):)?(\d{1,2}):(\d{1,2})[,.](\d{1,3})""",
    )
    private val ASS_TIME = Regex("""^\s*(\d+):(\d{1,2}):(\d{1,2})[.:](\d{1,3})\s*$""")
    private val ASS_DEFAULT_FORMAT = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")

    /** Style, name or effect words that mark a line as a sign, a song or a title rather than dialogue. */
    private val NOT_DIALOGUE_PARTS = listOf("sign", "song", "kara", "lyric", "title", "credit", "insert")
    private val NOT_DIALOGUE_WORDS = setOf("op", "ed", "opening", "ending")
    // Android's ICU regex rejects a bare "}" that the JVM accepts, so both braces are escaped.
    private val ASS_TAGS = Regex("""\{[^}]*\}""")
    private val ASS_DRAWING = Regex("""\\p[1-9]""")
    private val ASS_KARAOKE = Regex("""\\[kK][fo]?\d""")
    private val HTML_TAGS = Regex("""<[^>]*>""")
    private val SOUND_ONLY = Regex("""^[\s\-]*[\[(（][^\])）]*[\])）][\s.]*$""")
    private const val MUSIC_NOTES = "♪♫♬"

    /**
     * No line of dialogue stays up longer. A credit or watermark line that runs the whole video, or an end time typed
     * as 99:59:59, would otherwise swallow every cue after it into one span.
     */
    const val MAX_CUE_MS = 15_000L

    /** The spans where speech is expected, sorted, with overlaps joined. Empty when nothing usable was found. */
    fun spans(text: String, kind: Kind): List<CueSpan> {
        val raw = when (kind) {
            Kind.SRT, Kind.VTT -> arrowCues(text)
            Kind.ASS -> assCues(text)
        }
        return merge(raw)
    }

    private fun merge(spans: List<CueSpan>): List<CueSpan> {
        val sorted = spans.filter { it.endMs > it.startMs && it.startMs >= 0 && it.endMs - it.startMs <= MAX_CUE_MS }
            .sortedBy { it.startMs }
        val out = ArrayList<CueSpan>()
        for (span in sorted) {
            val last = out.lastOrNull()
            if (last != null && span.startMs <= last.endMs) {
                out[out.lastIndex] = CueSpan(last.startMs, maxOf(last.endMs, span.endMs))
            } else {
                out += span
            }
        }
        return out
    }

    private fun arrowCues(text: String): List<CueSpan> {
        val out = ArrayList<CueSpan>()
        val lines = text.lines()
        var index = 0
        while (index < lines.size) {
            val match = ARROW.find(lines[index])
            index++
            if (match == null) continue
            val body = StringBuilder()
            while (index < lines.size && lines[index].isNotBlank() && ARROW.find(lines[index]) == null) {
                body.append(lines[index]).append('\n')
                index++
            }
            val words = body.toString().replace(HTML_TAGS, "").replace(ASS_TAGS, "")
            if (isSpeech(words)) out += CueSpan(arrowStart(match), arrowEnd(match))
        }
        return out
    }

    private fun arrowStart(match: MatchResult): Long = clock(match.groupValues, 1)

    private fun arrowEnd(match: MatchResult): Long = clock(match.groupValues, 5)

    private fun clock(groups: List<String>, from: Int): Long {
        val hours = groups[from].toLongOrNull() ?: 0L
        val minutes = groups[from + 1].toLong()
        val seconds = groups[from + 2].toLong()
        val fraction = groups[from + 3].padEnd(3, '0').toLong()
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + fraction
    }

    private fun isSpeech(text: String): Boolean {
        val plain = text.trim()
        if (plain.isEmpty()) return false
        if (plain.any { it in MUSIC_NOTES }) return false
        return plain.lines().filter(String::isNotBlank).any { !SOUND_ONLY.matches(it) }
    }

    /** A Dialogue or Comment line of the Events section, with the column range of each field. */
    private class AssLine(val line: String, val kind: String, val fields: List<IntRange>) {
        fun field(index: Int): String = if (index in fields.indices) line.substring(fields[index].first, fields[index].last + 1) else ""
    }

    /** Walks the Events section. [onLine] gets each event, [onOther] every other line, in order. */
    private fun walkAss(text: String, onLine: (AssLine, List<String>) -> Unit, onOther: (String) -> Unit) {
        var inEvents = false
        var format = ASS_DEFAULT_FORMAT
        for (line in text.split('\n')) {
            val content = line.trimEnd('\r')
            val trimmed = content.trimStart()
            if (trimmed.startsWith("[")) {
                inEvents = trimmed.startsWith("[Events]", ignoreCase = true)
                onOther(line)
                continue
            }
            val colon = trimmed.indexOf(':')
            val key = if (colon > 0) trimmed.substring(0, colon).trim() else ""
            if (inEvents && key.equals("Format", ignoreCase = true)) {
                format = trimmed.substring(colon + 1).split(',').map { it.trim().lowercase() }
                onOther(line)
                continue
            }
            val event = key.equals("Dialogue", ignoreCase = true) || key.equals("Comment", ignoreCase = true)
            if (!inEvents || !event) {
                onOther(line)
                continue
            }
            val valueStart = content.indexOf(':') + 1
            onLine(AssLine(line, key.lowercase(), fieldRanges(content, valueStart, format.size)), format)
        }
    }

    /** Splits at the first [count] - 1 commas, so commas inside the text stay in the last field. */
    private fun fieldRanges(line: String, from: Int, count: Int): List<IntRange> {
        val ranges = ArrayList<IntRange>(count)
        var start = from
        for (i in 0 until count - 1) {
            val comma = line.indexOf(',', start)
            if (comma < 0) break
            ranges += start until comma
            start = comma + 1
        }
        ranges += start until line.length
        return ranges
    }

    private fun assCues(text: String): List<CueSpan> {
        val out = ArrayList<CueSpan>()
        walkAss(text, onLine = { event, format ->
            if (event.kind != "dialogue") return@walkAss
            val start = assTime(event.field(format.indexOf("start"))) ?: return@walkAss
            val end = assTime(event.field(format.indexOf("end"))) ?: return@walkAss
            val labels = listOf("style", "name", "effect").map { event.field(format.indexOf(it)) }
            if (labels.any(::marksNotDialogue)) return@walkAss
            val textIndex = format.indexOf("text").takeIf { it >= 0 } ?: format.lastIndex
            val body = event.field(textIndex)
            if (ASS_DRAWING.containsMatchIn(body) || ASS_KARAOKE.containsMatchIn(body)) return@walkAss
            val words = body.replace(ASS_TAGS, "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
            if (isSpeech(words)) out += CueSpan(start, end)
        }, onOther = {})
        return out
    }

    private fun marksNotDialogue(label: String): Boolean {
        val lower = label.lowercase()
        if (NOT_DIALOGUE_PARTS.any { lower.contains(it) }) return true
        return lower.split(Regex("[^a-z]+")).any { it in NOT_DIALOGUE_WORDS }
    }

    /** libass reads the last part as centiseconds whatever its length, so this does too. */
    private fun assTime(value: String): Long? {
        val match = ASS_TIME.matchEntire(value) ?: return null
        val (h, m, s, cs) = match.destructured
        return ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + cs.toLong() * 10
    }

    /** The same file with every cue time multiplied by [ratio]. Line endings and all other text stay as they were. */
    fun retime(text: String, kind: Kind, ratio: Double): String = when (kind) {
        Kind.SRT -> retimeArrows(text, ratio, ',')
        Kind.VTT -> retimeArrows(text, ratio, '.')
        Kind.ASS -> retimeAss(text, ratio)
    }

    private fun retimeArrows(text: String, ratio: Double, separator: Char): String =
        ARROW.replace(text) { match ->
            val start = scale(arrowStart(match), ratio)
            val end = scale(arrowEnd(match), ratio)
            "${arrowClock(start, separator)} --> ${arrowClock(end, separator)}"
        }

    private fun scale(ms: Long, ratio: Double): Long = (ms * ratio).roundToLong()

    /** Hours are always written: SRT needs them, and VTT allows them. ROOT keeps the digits Latin in Arabic. */
    private fun arrowClock(ms: Long, separator: Char): String = String.format(
        Locale.ROOT, "%02d:%02d:%02d%c%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, separator, ms % 1000,
    )

    private fun retimeAss(text: String, ratio: Double): String {
        val out = ArrayList<String>()
        walkAss(text, onLine = { event, format ->
            val targets = listOf(format.indexOf("start"), format.indexOf("end")).filter { it in event.fields.indices }
            var line = event.line
            // Right to left, so the earlier ranges stay valid while the later one changes length.
            for (index in targets.sortedDescending()) {
                val range = event.fields[index]
                val time = assTime(line.substring(range.first, range.last + 1)) ?: continue
                line = line.replaceRange(range, assClock(scale(time, ratio)))
            }
            out += line
        }, onOther = { out += it })
        return out.joinToString("\n")
    }

    private fun assClock(ms: Long): String {
        val centis = (ms + 5) / 10
        val hours = centis / 360_000
        val minutes = centis / 6_000 % 60
        val seconds = centis / 100 % 60
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis % 100)
    }
}
