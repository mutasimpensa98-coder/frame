package com.frameglass.parser

/** What surrounds a name. Folders are scanned with [isFolder] set and no other context. */
internal class NameContext(
    val isFolder: Boolean = false,
    val inSeasonFolder: Boolean = false,
    val parentKey: String? = null,
    /** A container phones write, so the name alone says nothing about a release. */
    val phoneFile: Boolean = false,
)

/** What one file or folder name says on its own. */
internal class Scan {
    var title = ""

    /** A weaker title, used only when no folder gives one. */
    var fallbackTitle = ""
    var year: Int? = null
    var season: Int? = null
    var episode: Int? = null
    var episodeEnd: Int? = null
    var absolute: Int? = null
    var revision: Int? = null
    var animeHint = false
    var subtitled = false
    var dubbed = false
    var special = false
    var extra = false
    var seriesHint = false
    var movieHint = false

    /** The number follows the fansub `Title - 05` form, where it counts from the start. */
    var runningNumber = false

    /** The name itself states an episode, so it is more than an episode title. */
    var hasMarker = false
    var group: String? = null
    var bracketYear: Int? = null
    var bracketEpisode: Int? = null
    var bracketRevision: Int? = null
    val otherBrackets = ArrayList<String>()
    val tags = LinkedHashSet<String>()
}

internal object NameScanner {
    private val IGNORE_CASE = RegexOption.IGNORE_CASE
    private val CRC = Regex("[0-9A-Fa-f]{8}")
    private val BRACKET_NUMBER = Regex("([0-9]{1,4})(?:V([0-9]{1,2}))?(?: ?(?:END|FINAL))?", IGNORE_CASE)
    private val LOOSE_NUMBER = Regex("([0-9]{1,4})(?:[vV]([0-9]{1,2}))?")
    private val SPECIAL_WORD = Regex("(SP|Specials?)([0-9]{1,3})?(?:V([0-9]{1,2}))?", IGNORE_CASE)
    private val ANIME_EXTRA_WORD = Regex("(?:OP|ED|PV|CM)[0-9]{0,2}[A-Za-z]?", IGNORE_CASE)
    private const val EDGE_PUNCTUATION = "-:,;|~."
    private val LEADING_JUNK = charArrayOf(' ', '-', GAP[0])

    fun scan(name: String, context: NameContext): Scan {
        val out = Scan()
        val (plain, brackets) = NameText.splitBrackets(NameText.stripSites(name))
        readBrackets(brackets, out)
        val dotted = plain.trim().let { ' ' !in it && '.' in it }
        val normalized = NameText.normalizeSeparators(plain)
        val text = dropLeadingWords(normalized.trimStart(*LEADING_JUNK), out)
        val span = EpisodeMarkers.read(text, out)
        val after = wordsOf(text.substring(maxOf(span.start, span.end)))

        val words = cutTitle(wordsOf(text.substring(0, span.start)), out)
        after.forEach { readTag(it, out) }
        readLastTailWord(after, out)
        readWordsAfterMarker(after, out)
        readLooseNumber(words, out, context)
        readTitleEnd(words, out)
        if (out.episode == null && out.bracketEpisode != null) {
            out.episode = out.bracketEpisode
            out.runningNumber = true
            out.hasMarker = true
        }
        out.revision = out.revision ?: out.bracketRevision
        out.title = finishTitle(words, dotted)
        if (out.title.isEmpty()) findOtherTitle(after, out)
        settleNumbering(out, context)
        return out
    }

    private fun readBrackets(brackets: List<Bracket>, out: Scan) {
        for (bracket in brackets) {
            val text = NameText.normalizeSeparators(bracket.text)
            when {
                text.isEmpty() -> Unit
                CRC.matches(bracket.text) -> {
                    if (bracket.text.any { it !in '0'..'9' }) out.animeHint = true
                }
                text.length == 4 && yearOf(text) != null -> out.bracketYear = yearOf(text)
                bracket.square && readBracketNumber(text, out) -> Unit
                EpisodeMarkers.readWholeKind(text, out) -> Unit
                readBracketSeason(text, out) -> Unit
                wordsOf(text).map { readTag(it, out) }.any { it } -> Unit
                text.lowercase() in Vocabulary.SITE_NAMES || NameText.isSite(bracket.text) -> Unit
                bracket.atStart && out.group == null -> {
                    out.group = text
                    // Square brackets are how a release names its group. (Nour) can be anyone, so it needs a release sign.
                    if (bracket.square) {
                        out.tags += text
                        out.animeHint = true
                    }
                }
                else -> out.otherBrackets += text
            }
        }
    }

    private fun readBracketNumber(text: String, out: Scan): Boolean {
        val match = BRACKET_NUMBER.matchEntire(text) ?: return false
        out.bracketEpisode = match.groupValues[1].toInt()
        out.bracketRevision = match.groupValues[2].toIntOrNull()
        return true
    }

    private fun readBracketSeason(text: String, out: Scan): Boolean {
        val season = EpisodeMarkers.readSeason(text)?.first ?: ArabicMarkers.seasonIn(text) ?: return false
        out.season = out.season ?: season
        return true
    }

    /** Removes the words that come before a title: type words, site names and flags. */
    private fun dropLeadingWords(text: String, out: Scan): String {
        var rest = text
        while (true) {
            val space = rest.indexOf(' ')
            if (space < 0) return rest
            val word = rest.substring(0, space)
            val key = ArabicNormalizer.key(word)
            when {
                key == Vocabulary.SITE_WORD -> rest = afterSiteName(rest.substring(space + 1))
                key in Vocabulary.SERIES_PREFIX -> out.seriesHint = true
                key in Vocabulary.MOVIE_PREFIX -> out.movieHint = true
                key in Vocabulary.ANIME_PREFIX -> out.animeHint = true
                key in Vocabulary.NOISE_PREFIX -> Unit
                key in Vocabulary.SUB_ARABIC -> out.subtitled = true
                key in Vocabulary.DUB_ARABIC -> out.dubbed = true
                word.lowercase() in Vocabulary.SITE_NAMES || word.startsWith("@") -> Unit
                else -> return rest
            }
            if (key != Vocabulary.SITE_WORD) rest = rest.substring(space + 1)
            rest = rest.trimStart(*LEADING_JUNK)
        }
    }

    /** Skips a site's name after the word `موقع`: up to a dash, or up to a type word. */
    private fun afterSiteName(text: String): String {
        val dash = text.indexOf(" - ")
        if (dash >= 0) return text.substring(dash + 3)
        val words = text.split(' ')
        val start = words.indexOfFirst {
            val key = ArabicNormalizer.key(it)
            key in Vocabulary.SERIES_PREFIX || key in Vocabulary.MOVIE_PREFIX || key in Vocabulary.ANIME_PREFIX
        }
        return if (start > 0) words.drop(start).joinToString(" ") else text
    }

    /** Ends the title at the first strong tag, the last plausible year or a bracket. */
    private fun cutTitle(words: List<String>, out: Scan): MutableList<String> {
        val size = words.size
        val tagAt = (1 until size).firstOrNull { isStrong(words[it]) } ?: size
        val yearAt = if (out.bracketYear != null) null else findYear(words, tagAt)
        val gapAt = words.indexOf(GAP).takeIf { it >= 0 } ?: size
        var end = minOf(tagAt, gapAt, yearAt ?: size)
        if (end == tagAt && end < size) {
            while (end > 1 && ReleaseTags.weak(words[end - 1]) != null) end--
        }
        out.year = out.year ?: out.bracketYear ?: yearAt?.let { yearOf(words[it]) }
        val rest = words.subList(end, size)
        rest.forEach { readTag(it, out) }
        readLastTailWord(rest, out)
        return words.subList(0, end).toMutableList()
    }

    private fun isStrong(word: String): Boolean {
        val key = ArabicNormalizer.key(word)
        return ReleaseTags.strong(word) != null || key in Vocabulary.SUB_ARABIC || key in Vocabulary.DUB_ARABIC
    }

    /** Prefers a year before the release tags. The first word is always the title. */
    private fun findYear(words: List<String>, tagAt: Int): Int? {
        fun usable(i: Int) =
            yearOf(words[i]) != null && words[i - 1].lowercase() !in Vocabulary.YEAR_LEAD_WORDS
        return (tagAt - 1 downTo 1).firstOrNull(::usable)
            ?: (tagAt until words.size).firstOrNull(::usable)
    }

    private fun yearOf(word: String): Int? {
        if (word.length < 4 || (word.length > 4 && word[4] != '-')) return null
        if ((0 until 4).any { word[it] !in '0'..'9' }) return null
        return word.substring(0, 4).toInt().takeIf(::isPlausibleYear)
    }

    private fun readTag(raw: String, out: Scan): Boolean {
        val token = raw.trim(',', ';', ':', '!', '"', '\'')
        if (token.isEmpty() || token == GAP || token == "-") return false
        val key = ArabicNormalizer.key(token)
        val lower = token.lowercase()
        if (key in Vocabulary.SUB_ARABIC || lower in Vocabulary.SUB_LATIN) {
            out.subtitled = true
            return true
        }
        if (key in Vocabulary.DUB_ARABIC || lower in Vocabulary.DUB_LATIN) {
            out.dubbed = true
            return true
        }
        ReleaseTags.exact(token)?.let {
            out.tags += it
            return true
        }
        val dash = token.lastIndexOf('-')
        if (dash <= 0) return false
        out.tags += ReleaseTags.exact(token.substring(0, dash)) ?: return false
        val suffix = token.substring(dash + 1)
        when (suffix.lowercase()) {
            in Vocabulary.TAIL_EXTRAS -> out.extra = true
            in Vocabulary.SITE_NAMES, "" -> Unit
            else -> out.tags += suffix
        }
        return true
    }

    private fun readLastTailWord(rest: List<String>, out: Scan) {
        val last = rest.lastOrNull { it != GAP } ?: return
        if (last.lowercase() in Vocabulary.TAIL_EXTRAS) out.extra = true
    }

    /** Covers `Title S2 05` and a year right after the marker, as in `Show S01E02 2019`. */
    private fun readWordsAfterMarker(after: List<String>, out: Scan) {
        val first = after.firstOrNull { it != GAP && it != "-" } ?: return
        if (out.year == null) out.year = yearOf(first)
        if (out.episode != null || out.season == null) return
        val number = LOOSE_NUMBER.matchEntire(first) ?: return
        val value = number.groupValues[1].toInt()
        if (isPlausibleYear(value)) return
        out.episode = value
        out.revision = out.revision ?: number.groupValues[2].toIntOrNull()
        out.hasMarker = true
    }

    /** Reads a number with no marker, when the context shows it cannot be part of the title. */
    private fun readLooseNumber(words: MutableList<String>, out: Scan, context: NameContext) {
        if (out.episode != null || out.movieHint || context.isFolder || words.isEmpty()) return
        val leading = LOOSE_NUMBER.matchEntire(words.first())
        if (leading != null && (context.inSeasonFolder || context.parentKey != null)) {
            val alone = words.size == 1 || words[1] == "-"
            val value = leading.groupValues[1].toInt()
            if (context.inSeasonFolder || (alone && !isPlausibleYear(value))) {
                takeNumber(leading, out)
                words.clear()
                return
            }
        }
        val trailing = LOOSE_NUMBER.matchEntire(words.last()) ?: return
        val digits = trailing.groupValues[1]
        val padded = digits.length >= 2 && digits[0] == '0'
        if (words.size == 1) {
            if (padded && out.group != null) {
                takeNumber(trailing, out)
                out.runningNumber = true
                words.clear()
            }
            return
        }
        if (isPlausibleYear(digits.toInt())) return
        val titleKey = ArabicNormalizer.key(words.dropLast(1).joinToString(" "))
        val known = out.group != null || context.inSeasonFolder || out.seriesHint || context.parentKey == titleKey
        if (!padded && !known) return
        takeNumber(trailing, out)
        out.runningNumber = out.group != null
        words.removeAt(words.lastIndex)
    }

    private fun takeNumber(match: MatchResult, out: Scan) {
        out.episode = match.groupValues[1].toInt()
        out.revision = out.revision ?: match.groupValues[2].toIntOrNull()
        out.hasMarker = true
    }

    /** Reads kind words that sit at the very end of the title, such as `SP1` or `OP2`. */
    private fun readTitleEnd(words: MutableList<String>, out: Scan) {
        if (words.size < 2) return
        val last = words.last()
        val beforeLast = words[words.size - 2]
        val fansubForm = out.animeHint || out.group != null || out.runningNumber || beforeLast == "-"
        if (last.lowercase() in Vocabulary.TITLE_END_EXTRAS || (fansubForm && ANIME_EXTRA_WORD.matches(last))) {
            out.extra = true
            words.removeAt(words.lastIndex)
            return
        }
        val spaced = LOOSE_NUMBER.matches(last) && words.size >= 3
        val match = SPECIAL_WORD.matchEntire(if (spaced) beforeLast else last) ?: return
        val number = if (spaced) last else match.groupValues[2]
        if (number.isEmpty() && !fansubForm) return
        out.special = true
        out.episode = out.episode ?: LOOSE_NUMBER.matchEntire(number)?.groupValues?.get(1)?.toInt()
        out.revision = out.revision ?: match.groupValues[3].toIntOrNull()
        repeat(if (spaced) 2 else 1) { words.removeAt(words.lastIndex) }
    }

    /** With an empty title, looks after the marker and in the brackets. */
    private fun findOtherTitle(after: List<String>, out: Scan) {
        var words = after.dropWhile { it == GAP || it == "-" }
        val namedAfter = ArabicNormalizer.key(words.firstOrNull().orEmpty()) == Vocabulary.OF_WORD
        if (namedAfter) words = words.drop(1)
        val text = dropLeadingWords(words.joinToString(" "), out)
        val tailTitle = finishTitle(cutTitle(wordsOf(text), out), dotted = false)
        when {
            namedAfter && tailTitle.isNotEmpty() -> out.title = tailTitle
            out.otherBrackets.isNotEmpty() -> out.title = out.otherBrackets.first()
            tailTitle.isNotEmpty() -> out.fallbackTitle = tailTitle
            else -> out.fallbackTitle = out.group.orEmpty()
        }
    }

    private fun finishTitle(words: List<String>, dotted: Boolean): String {
        val kept = words.filterTo(ArrayList()) {
            it != GAP && it.lowercase() !in Vocabulary.SITE_NAMES && !it.startsWith("@")
        }
        while (kept.size > 1 && isTrailingNoise(kept.last())) kept.removeAt(kept.lastIndex)
        while (kept.isNotEmpty() && kept.first().all { it in EDGE_PUNCTUATION }) kept.removeAt(0)
        if (kept.size == 1 && kept[0].all { it in EDGE_PUNCTUATION }) return ""
        val shaped = if (dotted) rejoinInitials(kept) else kept
        return shaped.joinToString(" ").trim(' ', '-', ':', ',', ';', '|', '~', '،', '؛')
    }

    private fun isTrailingNoise(word: String): Boolean =
        word.all { it in EDGE_PUNCTUATION } ||
            ArabicNormalizer.key(word) in Vocabulary.TRAILING_NOISE ||
            ReleaseTags.droppableAtEnd(word)

    /** Turns the words of a dotted name back into `L.A.` and `S.H.I.E.L.D.`. */
    private fun rejoinInitials(words: List<String>): List<String> {
        val out = ArrayList<String>(words.size)
        var i = 0
        while (i < words.size) {
            var end = i
            while (end < words.size && words[end].length == 1 && words[end][0] in 'A'..'Z') end++
            if (end - i >= 2) {
                out += words.subList(i, end).joinToString(".") + "."
                i = end
            } else {
                out += words[i++]
            }
        }
        return out
    }

    /**
     * Anime names with no season count from the first episode, whatever marker they use. The dash
     * form alone is not read as anime on an Arabic title: AniList cannot search those. It is not
     * read as anime on a bare phone file either, because Beach Day - 3.mp4 is how people number
     * their own clips. A real anime named that way still becomes one through its match.
     */
    private fun settleNumbering(out: Scan, context: NameContext) {
        val fansubForm = out.group != null || (out.runningNumber && out.title.none { isArabicLetter(it) })
        if (fansubForm && hasReleaseSign(out, context)) out.animeHint = true
        if (out.animeHint && out.season == null && !out.special && !out.extra) out.absolute = out.episode
        if (out.extra) {
            out.episode = null
            out.episodeEnd = null
            out.absolute = null
        }
    }

    private fun hasReleaseSign(out: Scan, context: NameContext): Boolean =
        !(context.isFolder || context.phoneFile) || out.tags.any { ReleaseTags.strong(it) != null }

    private fun wordsOf(text: String): List<String> = text.split(' ').filter { it.isNotEmpty() }
}
