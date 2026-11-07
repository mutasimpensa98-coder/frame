package com.frameglass.parser

/**
 * Personal and social clips: what a camera, a screen recorder or a chat app names a file, and the folders
 * those apps save into. Such a name holds no title worth looking up.
 *
 * Patterns run on the ASCII part of a name. Arabic is compared on [ArabicNormalizer] keys, as everywhere here.
 */
internal object ClipNames {
    private val IGNORE_CASE = RegexOption.IGNORE_CASE

    /** Words a device or an app puts right before its counter. */
    private const val COUNTER_WORDS =
        "vid|img(?:_e)?|pxl|mvimg|svid|mov|dsc[nf]?|mvi|mah|gopr|g[hx]|dji|hdv|wp|fb[ _-]?(?:vid|img)|" +
            "snapchat|screen[ _-]?record(?:er|ing)?s?|screencast|xrecorder|azrecorder|inshot|capcut|vn|youcut|" +
            "kinemaster|received|tiktok|snaptik(?:[.]app)?|ssstik(?:[.]io)?|instagram|igtv|facebook|" +
            "whatsapp(?:[ _-]video)?"
    private val COUNTER = Regex("^(?:$COUNTER_WORDS)[ _-]*[0-9]{3}", IGNORE_CASE)

    /** Plain words that can start a real title too, so only a date or a long number proves a stamp. */
    private const val DATED_WORDS = "video|movie|screen|record|recording|rec|clip|camera|cam|capture"
    private val DATED = Regex("^(?:$DATED_WORDS)[ _-]*(?:[0-9]{6}|[0-9]{4}[-_. ][0-9]{2}[-_. ][0-9]{2})", IGNORE_CASE)

    private val SIGNAL = Regex("^signal-[0-9]{4}", IGNORE_CASE)
    private val SONY = Regex("^C[0-9]{4}$", IGNORE_CASE)
    private val MESSENGER = Regex("^messenger[ _-]creation", IGNORE_CASE)

    private const val MONTH = "(?:0[1-9]|1[0-2])"
    private const val DAY = "(?:0[1-9]|[12][0-9]|3[01])"
    private const val YEAR = "(?:19|20)[0-9]{2}"
    private const val SEP = "[-_. ]"

    // The separators must agree, or 1408.2007, a film and its year, would read as 14 August 2007.
    private val DATE = Regex(
        "^(?:$YEAR$MONTH$DAY|$YEAR$SEP$MONTH$SEP$DAY|$DAY$MONTH$YEAR|$DAY$SEP$MONTH$SEP$YEAR)" +
            "(?:[-_. ]+(?:at[ _]+)?[0-9][-_. 0-9]*)?$",
        IGNORE_CASE,
    )
    private val NUMBER = Regex("^[0-9]+(?:[ _-]+[0-9]+)*(?:_n)?$")
    private val HEX_HASH = Regex("^[0-9a-f-]{16,}$", IGNORE_CASE)

    private const val MIN_HASH_LENGTH = 16
    private const val MIN_HASH_SWITCHES = 4
    private const val MIN_LONG_NUMBER = 5
    private const val YEAR_DIGITS = 4

    /** What phones and social apps write. Short numbers in anything else are titles such as 300.mkv. */
    private val PHONE_EXTENSIONS = setOf("mp4", "mov", "3gp", "m4v", "webm")

    /**
     * A season and an episode in the name itself always win. A device stamp comes next: on IMG_0123 the
     * number is a counter, not an episode. A year and a title, or a number the folders read as an episode,
     * then beat the weaker signs: a bare number, a hash, a generic word, a clip folder.
     */
    fun matches(
        fileName: String,
        stem: String,
        file: Scan,
        parsed: ParsedName,
        hasTitleFolder: Boolean,
        folders: List<String>,
    ): Boolean {
        if (file.season != null && file.episode != null) return false
        val plain = withoutCopyMark(stem)
        if (isStamped(plain) || DATE.matches(plain)) return true
        if (parsed.season != null && parsed.episode != null) return false
        if (parsed.year != null && parsed.title.isNotEmpty()) return false
        if (parsed.episode == null && (isNumberName(plain, fileName) || isHash(plain))) return true
        if (!hasTitleFolder && ArabicNormalizer.key(plain) in Vocabulary.CLIP_STEMS) return true
        return folders.any(::isClipFolder)
    }

    private fun isStamped(stem: String): Boolean {
        if (COUNTER.containsMatchIn(stem) || DATED.containsMatchIn(stem)) return true
        if (SIGNAL.containsMatchIn(stem) || SONY.matches(stem) || MESSENGER.containsMatchIn(stem)) return true
        if (stem.none { it in '0'..'9' }) return false
        val key = ArabicNormalizer.key(stem)
        return Vocabulary.CLIP_STEM_STARTS.any(key::startsWith)
    }

    /** Removes the copy counter of `name (1)`, which says nothing about the name. */
    private fun withoutCopyMark(stem: String): String {
        if (!stem.endsWith(')')) return stem
        val open = stem.lastIndexOf('(')
        if (open <= 0 || stem.length - open > 5) return stem
        if ((open + 1 until stem.length - 1).any { stem[it] !in '0'..'9' }) return stem
        return stem.substring(0, open).trimEnd(' ', '_', '-')
    }

    private fun isNumberName(stem: String, fileName: String): Boolean {
        if (!NUMBER.matches(stem)) return false
        val digits = stem.count { it in '0'..'9' }
        if (digits >= MIN_LONG_NUMBER) return true
        val alone = digits == stem.length
        if (alone && digits == YEAR_DIGITS && isPlausibleYear(stem.toInt())) return false
        return fileName.substringAfterLast('.', "").lowercase() in PHONE_EXTENSIONS
    }

    private fun isHash(stem: String): Boolean {
        if (HEX_HASH.matches(stem)) return stem.any { it in '0'..'9' } && stem.any { it.isLetter() }
        if (stem.length < MIN_HASH_LENGTH || !stem.all(::isAsciiLetterOrDigit)) return false
        var switches = 0
        for (i in 1 until stem.length) {
            if ((stem[i] in '0'..'9') != (stem[i - 1] in '0'..'9')) switches++
        }
        return switches >= MIN_HASH_SWITCHES
    }

    private fun isAsciiLetterOrDigit(ch: Char): Boolean = ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z'

    private fun isClipFolder(name: String): Boolean {
        val key = ArabicNormalizer.key(NameText.normalizeSeparators(name))
        return key in Vocabulary.CLIP_FOLDERS || key.startsWith(Vocabulary.WHATSAPP_WORD)
    }
}
