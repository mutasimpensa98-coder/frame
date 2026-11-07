package com.frameglass.parser

import java.text.Normalizer

// Patterns spell out ASCII classes on purpose. \b, \w, \d and \s are Unicode-aware under ICU on
// ART and ASCII-only on the JVM, so using them would make the phone parse differently from tests.
internal const val WORD_START = "(?<![A-Za-z0-9])"
internal const val WORD_END = "(?![A-Za-z0-9])"

/** Token left where a bracket was removed. A title never runs across it. */
internal const val GAP = "\u0001"

internal class Bracket(val text: String, val atStart: Boolean, val square: Boolean)

internal object NameText {
    private val IGNORE_CASE = RegexOption.IGNORE_CASE

    private val VIDEO_EXTENSIONS = setOf(
        "mkv", "mp4", "m4v", "avi", "mov", "wmv", "flv", "f4v", "webm", "ts", "m2ts", "mts", "mpg",
        "mpeg", "mpe", "vob", "ogv", "ogm", "3gp", "3g2", "rm", "rmvb", "divx", "asf", "iso",
    )

    private const val ANY_TLD =
        "com|net|org|info|tv|cc|co|me|io|xyz|site|online|live|club|vip|pro|ws|biz|tube|best|cam|one|" +
            "top|app|fun|to|ly|eg|sa|ae|ma"

    // Without a dash after it, a bare domain in a dotted name could be title words ("Mad.TV").
    private const val DOTTED_TLD = "com|net|org|info|xyz|site|online|tube"

    private val WWW_SITE =
        Regex("${WORD_START}www[.][A-Za-z0-9-]+[.][A-Za-z]{2,10}(?:[.][A-Za-z]{2})?$WORD_END", IGNORE_CASE)
    private val LEADING_SITE =
        Regex("^[A-Za-z0-9-]{2,}[.](?:$ANY_TLD)[ ._]*[-|][ ._]*", IGNORE_CASE)

    // The look-aheads keep "The.Net.1995" and "The.Net.S01E02" whole: nothing would be left as a title.
    private val LEADING_DOTTED_SITE = Regex(
        "^[A-Za-z0-9-]{2,}[.](?:$DOTTED_TLD)[ ._]+(?=[^0-9 ._])(?!S[0-9]{1,4}E[0-9])",
        IGNORE_CASE,
    )
    private val TRAILING_SITE =
        Regex("[ ._]+-[ ._]+[A-Za-z0-9-]{2,}[.](?:$ANY_TLD)$", IGNORE_CASE)
    private val BRACKET_SITE =
        Regex("(?:www[.])?[A-Za-z0-9-]+(?:[.][A-Za-z0-9-]+)*[.][A-Za-z]{2,10}", IGNORE_CASE)

    private val BRACKET_PAIRS = mapOf(
        '[' to ']', '(' to ')', '{' to '}', '【' to '】', '「' to '」', '『' to '』',
        '（' to '）', '［' to '］', '〔' to '〕', '《' to '》',
    )
    private val SQUARE_OPENERS = setOf('[', '【', '［', '〔')
    private val STRAY_CLOSERS = BRACKET_PAIRS.values.toSet()

    private val DASHES = setOf('‐', '‑', '‒', '–', '—', '―', '−')
    private val SPACED_SEPARATORS = setOf('|', '｜', '~', '〜', '～')

    private val SPELLING_FIXES = listOf(
        Regex("$WORD_START([HX]) (26[45])$WORD_END", IGNORE_CASE) to "\$1\$2",
        Regex("${WORD_START}WEB (DL|Rip)$WORD_END", IGNORE_CASE) to "WEB-\$1",
        Regex("${WORD_START}Blu Ray$WORD_END", IGNORE_CASE) to "BluRay",
        Regex("${WORD_START}DTS (HD|X)$WORD_END", IGNORE_CASE) to "DTS-\$1",
        Regex("$WORD_START(10|8) ?bits?$WORD_END", IGNORE_CASE) to "\$1bit",
        Regex("${WORD_START}Dual Audio$WORD_END", IGNORE_CASE) to "Dual-Audio",
        Regex(
            "$WORD_START(AAC|AC3|EAC3|DDP?|DTS|TrueHD|FLAC|Opus) ([0-9][.][0-9])$WORD_END",
            IGNORE_CASE,
        ) to "\$1\$2",
        Regex(" -(?=[0-9])") to " - ",
        Regex("(?<=[A-Za-z0-9])- ") to " - ",
    )

    fun clean(raw: String): String {
        val composed = Normalizer.normalize(raw, Normalizer.Form.NFC)
        val out = StringBuilder(composed.length)
        for (ch in composed) {
            when {
                isInvisibleMark(ch) -> Unit
                ch.isWhitespace() -> out.append(' ')
                ch.isISOControl() -> Unit
                else -> out.append(asciiDigit(ch))
            }
        }
        return out.toString().trim()
    }

    fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return name
        return if (name.substring(dot + 1).lowercase() in VIDEO_EXTENSIONS) name.substring(0, dot) else name
    }

    fun stripSites(stem: String): String {
        var text = WWW_SITE.replace(stem, " ").trim(' ', '-', '.', '_', '|')
        text = LEADING_SITE.replaceFirst(text, "")
        text = LEADING_DOTTED_SITE.replaceFirst(text, "")
        return TRAILING_SITE.replace(text, "")
    }

    fun isSite(bracketText: String): Boolean = BRACKET_SITE.matches(bracketText)

    /** Returns the text outside brackets, with [GAP] tokens in their place, and the brackets. */
    fun splitBrackets(text: String): Pair<String, List<Bracket>> {
        val plain = StringBuilder(text.length)
        val brackets = ArrayList<Bracket>()
        var hasPlainText = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val closer = BRACKET_PAIRS[ch]
            val end = if (closer != null) text.indexOf(closer, i + 1) else -1
            when {
                end > i -> {
                    val inner = text.substring(i + 1, end).trim()
                    brackets += Bracket(inner, atStart = !hasPlainText, square = ch in SQUARE_OPENERS)
                    plain.append(' ').append(GAP).append(' ')
                    i = end
                }
                closer != null || ch in STRAY_CLOSERS -> plain.append(' ')
                else -> {
                    plain.append(ch)
                    if (ch != ' ' && ch != '.' && ch != '_' && ch != '-') hasPlainText = true
                }
            }
            i++
        }
        return plain.toString() to brackets
    }

    fun normalizeSeparators(text: String): String {
        val out = StringBuilder(text.length + 8)
        for (i in text.indices) {
            val ch = text[i]
            val previous = text.getOrNull(i - 1)
            val next = text.getOrNull(i + 1)
            when {
                ch == '_' -> out.append(' ')
                ch == '.' -> out.append(if (isDecimalDot(text, i)) '.' else ' ')
                ch in SPACED_SEPARATORS -> out.append(" - ")
                ch == '-' || ch in DASHES -> {
                    // A hyphen touching Arabic is a separator: Latin compounds like WEB-DL stay whole.
                    val joinsArabic = isArabicLetter(previous) || isArabicLetter(next)
                    out.append(if (joinsArabic) ' ' else '-')
                }
                isArabicLetter(ch) && next != null && next in '0'..'9' -> out.append(ch).append(' ')
                ch in '0'..'9' && isArabicLetter(next) -> out.append(ch).append(' ')
                else -> out.append(ch)
            }
        }
        var fixed = out.toString()
        for ((pattern, replacement) in SPELLING_FIXES) fixed = pattern.replace(fixed, replacement)
        return collapseSpaces(fixed)
    }

    /** Keeps the dot of 5.1 and 13.5, and splits 2049.2017 and S01.E02. */
    private fun isDecimalDot(text: String, dot: Int): Boolean {
        var left = dot
        while (left > 0 && text[left - 1] in '0'..'9') left--
        var right = dot + 1
        while (right < text.length && text[right] in '0'..'9') right++
        return dot - left in 1..2 && right - dot - 1 == 1
    }

    private fun collapseSpaces(text: String): String {
        val out = StringBuilder(text.length)
        for (ch in text) {
            if (ch != ' ' || (out.isNotEmpty() && out.last() != ' ')) out.append(ch)
        }
        return out.toString().trimEnd()
    }
}
