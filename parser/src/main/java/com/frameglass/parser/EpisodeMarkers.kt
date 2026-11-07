package com.frameglass.parser

import java.time.Year

private const val MIN_YEAR = 1900

/** The reach of all markers found: the title stops at [start], the tags begin at [end]. */
internal class MarkerSpan(textLength: Int) {
    var start = textLength
        private set
    var end = 0
        private set

    fun add(from: Int, toExclusive: Int) {
        if (from < start) start = from
        if (toExclusive > end) end = toExclusive
    }

    fun add(range: IntRange) = add(range.first, range.last + 1)
}

internal val MAX_YEAR: Int = Year.now().value + 1

internal fun isPlausibleYear(value: Int): Boolean = value in MIN_YEAR..MAX_YEAR

internal object EpisodeMarkers {
    private val IGNORE_CASE = RegexOption.IGNORE_CASE

    private val SEASON_EPISODE = Regex(
        "${WORD_START}S([0-9]{1,4})[ -]?EP?[ ]?([0-9]{1,4})" +
            "((?:[ +&-]*EP?[ ]?[0-9]{1,4}|-[0-9]{1,4})*)(?:[ ]?V([0-9]{1,2}))?$WORD_END",
        IGNORE_CASE,
    )
    private val CROSS_FORM = Regex(
        "$WORD_START([0-9]{1,2})X([0-9]{2,3})((?:[-X][0-9]{2,3})*)(?:V([0-9]{1,2}))?$WORD_END",
        IGNORE_CASE,
    )
    private val EPISODE_WORD = Regex(
        "${WORD_START}(?:Episodes?|Episodio|Eps?)[ #-]*([0-9]{1,4})" +
            "((?: ?(?:-|&|to) ?(?:EP?)?[0-9]{1,4})?)(?: ?V([0-9]{1,2}))?$WORD_END",
        IGNORE_CASE,
    )
    private val EPISODE_SHORT = Regex(
        "${WORD_START}E([0-9]{1,4})((?:-?E[0-9]{1,4}|-[0-9]{1,4})*)(?:V([0-9]{1,2}))?$WORD_END",
        IGNORE_CASE,
    )
    private val EPISODE_CJK = Regex("第([0-9]{1,4})[話话集回]")
    private val DASH_NUMBER = Regex(
        " - ([0-9]{1,4})(?:[.]([0-9]))?(?: ?- ?([0-9]{1,4}))?(?: ?V([0-9]{1,2}))?$WORD_END",
        IGNORE_CASE,
    )

    private val SEASON_WORD =
        Regex("$WORD_START(?:Season|Saison|Temporada)[ -]?([0-9]{1,4})$WORD_END", IGNORE_CASE)
    private val SEASON_ORDINAL =
        Regex("$WORD_START([0-9]{1,2})(?:st|nd|rd|th)[ -]?Season$WORD_END", IGNORE_CASE)
    private val SEASON_SHORT = Regex("${WORD_START}S([0-9]{1,2})$WORD_END", IGNORE_CASE)
    private val SEASON_ORDINAL_WORD = Regex(
        "$WORD_START(First|Second|Third|Fourth|Fifth|Sixth|Seventh|Eighth|Ninth|Tenth)[ -]Season$WORD_END",
        IGNORE_CASE,
    )
    private val SEASON_CARDINAL_WORD = Regex(
        "${WORD_START}Season[ -](One|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten)$WORD_END",
        IGNORE_CASE,
    )
    private val ORDINAL_WORDS = listOf(
        "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
    )
    private val CARDINAL_WORDS =
        listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

    private val OVA = Regex(
        "$WORD_START(?:OVA|OAV|OAD|ONA)[ -]?([0-9]{1,3})?(?:V([0-9]{1,2}))?$WORD_END",
        IGNORE_CASE,
    )
    private val EXTRA_NAMED = Regex(
        "$WORD_START(?:NC[ -]?OP|NC[ -]?ED|NCBD|Creditless(?:[ -](?:OP|ED|Opening|Ending))?)" +
            "[ -]?([0-9]{1,2})?[A-Za-z]?(?:V([0-9]{1,2}))?$WORD_END",
        IGNORE_CASE,
    )

    private const val EXTRA_WORDS =
        "OP|ED|Opening|Ending|PV|CM|Trailer|Teaser|Preview|Menu|Sample|Promo|Featurette|Bonus|Extras?|Interview"
    private val EXTRA_AFTER_DASH = Regex(
        " - (?:$EXTRA_WORDS)(?:[ -]?[0-9]{1,2}[A-Za-z]?)?(?:V[0-9]{1,2})?$WORD_END",
        IGNORE_CASE,
    )
    private val EXTRA_WHOLE = Regex(
        "(?:NC[ -]?OP|NC[ -]?ED|$EXTRA_WORDS)(?:[ -]?[0-9]{1,2}[A-Za-z]?)?(?:V[0-9]{1,2})?",
        IGNORE_CASE,
    )
    private val SPECIAL_WHOLE = Regex(
        "(?:OVA|OAV|OAD|ONA|SP|Specials?)[ -]?([0-9]{1,3})?(?:V([0-9]{1,2}))?",
        IGNORE_CASE,
    )

    fun read(text: String, out: Scan): MarkerSpan {
        val span = MarkerSpan(text.length)
        readSeasonEpisode(text, out, span)
        readExtras(text, out, span)
        if (out.season == null) readSeasonOnly(text, out, span)
        if (out.episode == null) readEpisodeOnly(text, out, span)
        ArabicMarkers.read(text, out, span)
        readOva(text, out, span)
        if (out.episode == null) readDashNumber(text, out, span)
        return span
    }

    /** Reads a bracket that names a kind, such as `[OVA 2]` or `(NCOP1)`. */
    fun readWholeKind(text: String, out: Scan): Boolean {
        if (EXTRA_WHOLE.matches(text)) {
            out.extra = true
            return true
        }
        val special = SPECIAL_WHOLE.matchEntire(text) ?: return false
        out.special = true
        out.episode = out.episode ?: special.groupValues[1].toIntOrNull()
        out.revision = out.revision ?: special.groupValues[2].toIntOrNull()
        return true
    }

    fun readSeason(text: String): Pair<Int, IntRange>? {
        // Ordinal forms come first: in "4th Season 07" the 07 is an episode.
        SEASON_ORDINAL.find(text)?.let { return it.groupValues[1].toInt() to it.range }
        SEASON_ORDINAL_WORD.find(text)?.let {
            return ORDINAL_WORDS.indexOf(it.groupValues[1].lowercase()) + 1 to it.range
        }
        SEASON_WORD.find(text)?.let { return it.groupValues[1].toInt() to it.range }
        SEASON_CARDINAL_WORD.find(text)?.let {
            return CARDINAL_WORDS.indexOf(it.groupValues[1].lowercase()) + 1 to it.range
        }
        SEASON_SHORT.find(text)?.let { return it.groupValues[1].toInt() to it.range }
        return null
    }

    private fun readSeasonEpisode(text: String, out: Scan, span: MarkerSpan) {
        val match = SEASON_EPISODE.find(text) ?: CROSS_FORM.find(text) ?: return
        val (season, episode, more, revision) = match.destructured
        out.season = season.toInt()
        applyEpisode(episode.toInt(), more, revision, out)
        span.add(match.range)
    }

    private fun readSeasonOnly(text: String, out: Scan, span: MarkerSpan) {
        val (season, range) = readSeason(text) ?: return
        out.season = season
        span.add(range)
    }

    private fun readEpisodeOnly(text: String, out: Scan, span: MarkerSpan) {
        val match = EPISODE_WORD.find(text) ?: EPISODE_SHORT.find(text)
        if (match != null) {
            val (episode, more, revision) = match.destructured
            applyEpisode(episode.toInt(), more, revision, out)
            span.add(match.range)
            return
        }
        EPISODE_CJK.find(text)?.let {
            out.episode = it.groupValues[1].toInt()
            out.hasMarker = true
            span.add(it.range)
        }
    }

    private fun applyEpisode(episode: Int, more: String, revision: String, out: Scan) {
        out.episode = episode
        out.episodeEnd = rangeEnd(episode, lastNumber(more))
        out.revision = out.revision ?: revision.toIntOrNull()
        out.hasMarker = true
    }

    private fun readExtras(text: String, out: Scan, span: MarkerSpan) {
        val named = EXTRA_NAMED.find(text)
        if (named != null) {
            out.extra = true
            out.tags += named.value.trim().uppercase()
            out.revision = out.revision ?: named.groupValues[2].toIntOrNull()
            span.add(named.range)
            return
        }
        EXTRA_AFTER_DASH.find(text)?.let {
            out.extra = true
            span.add(it.range)
        }
    }

    private fun readOva(text: String, out: Scan, span: MarkerSpan) {
        val match = OVA.find(text) ?: return
        out.special = true
        out.episode = out.episode ?: match.groupValues[1].toIntOrNull()
        out.revision = out.revision ?: match.groupValues[2].toIntOrNull()
        span.add(match.range)
    }

    private fun readDashNumber(text: String, out: Scan, span: MarkerSpan) {
        for (match in DASH_NUMBER.findAll(text)) {
            val (number, half, end, revision) = match.destructured
            val value = number.toInt()
            if (number.length == 4 && isPlausibleYear(value)) continue
            out.episode = value
            out.episodeEnd = rangeEnd(value, end.toIntOrNull())
            out.revision = out.revision ?: revision.toIntOrNull()
            out.runningNumber = true
            out.hasMarker = true
            // 13.5 is a recap between episodes. The field holds whole numbers only.
            if (half.isNotEmpty()) out.special = true
            span.add(match.range)
            return
        }
    }

    fun rangeEnd(first: Int, last: Int?): Int? = last?.takeIf { it > first && it - first < 100 }

    private fun lastNumber(text: String): Int? {
        var end = text.length
        while (end > 0 && text[end - 1] !in '0'..'9') end--
        var start = end
        while (start > 0 && text[start - 1] in '0'..'9') start--
        return if (start < end) text.substring(start, end).toInt() else null
    }
}

/** Arabic is read word by word on normalised keys, so no Arabic text goes through a regex. */
internal object ArabicMarkers {
    private class Word(val text: String, val start: Int) {
        val key = ArabicNormalizer.key(text)
        val end get() = start + text.length
    }

    private class NumberRead(val value: Int, val rangeEnd: Int?, val revision: Int?, val next: Int)

    private val NUMBER = Regex("([0-9]{1,4})(?:-([0-9]{1,4}))?(?:[vV]([0-9]{1,2}))?")
    private val LAST_WORDS =
        listOf("الأخير", "الأخيرة", "النهائي", "النهائية").map(ArabicNormalizer::key).toSet()

    fun read(text: String, out: Scan, span: MarkerSpan) {
        val words = wordsOf(text)
        val keys = words.map { it.key }
        var part: NumberRead? = null
        var partAt = -1
        for (i in words.indices) {
            val key = keys[i]
            when (key) {
                in Vocabulary.EPISODE_WORDS -> if (out.episode == null && !out.movieHint) {
                    readEpisode(words, keys, i, out, span)
                }
                in Vocabulary.SEASON_WORDS -> if (out.season == null) {
                    readSeason(words, keys, i, out, span)
                }
                in Vocabulary.PART_WORDS -> if (part == null) {
                    part = readNumber(words, keys, i + 1)
                    partAt = i
                }
                in Vocabulary.OVA_WORDS -> {
                    out.special = true
                    span.add(words[i].start, words[i].end)
                }
            }
        }
        // A part is a season of a series, but it belongs to the title of a film.
        val found = part ?: return
        if (out.movieHint || (out.episode == null && !out.seriesHint)) return
        out.season = out.season ?: found.value
        span.add(words[partAt].start, words[found.next - 1].end)
    }

    /** The season of a bracket or folder written in Arabic, such as `الموسم الثاني`. */
    fun seasonIn(text: String): Int? {
        val probe = Scan()
        read(text, probe, MarkerSpan(text.length))
        return probe.season
    }

    private fun readEpisode(words: List<Word>, keys: List<String>, at: Int, out: Scan, span: MarkerSpan) {
        val number = readNumber(words, keys, at + 1)
        if (number != null) {
            out.episode = number.value
            out.episodeEnd = EpisodeMarkers.rangeEnd(number.value, number.rangeEnd)
            out.revision = out.revision ?: number.revision
            out.hasMarker = true
            span.add(words[at].start, words[number.next - 1].end)
            return
        }
        val next = keys.getOrNull(at + 1) ?: return
        if (next in Vocabulary.SPECIAL_WORDS) out.special = true else if (next !in LAST_WORDS) return
        out.seriesHint = true
        span.add(words[at].start, words[at + 1].end)
    }

    private fun readSeason(words: List<Word>, keys: List<String>, at: Int, out: Scan, span: MarkerSpan) {
        val number = readNumber(words, keys, at + 1)
        if (number != null) {
            out.season = number.value
            span.add(words[at].start, words[number.next - 1].end)
        } else if (keys.getOrNull(at + 1) in LAST_WORDS) {
            span.add(words[at].start, words[at + 1].end)
        }
    }

    private fun readNumber(words: List<Word>, keys: List<String>, from: Int): NumberRead? {
        var i = from
        if (keys.getOrNull(i) == Vocabulary.NUMBER_WORD) i++
        val word = words.getOrNull(i) ?: return null
        NUMBER.matchEntire(word.text)?.let { match ->
            val (value, end, revision) = match.destructured
            return NumberRead(value.toInt(), end.toIntOrNull(), revision.toIntOrNull(), i + 1)
        }
        val (value, used) = Vocabulary.ordinal(keys, i) ?: return null
        return NumberRead(value, null, null, i + used)
    }

    private fun wordsOf(text: String): List<Word> {
        val words = ArrayList<Word>()
        var i = 0
        while (i < text.length) {
            if (text[i] == ' ') {
                i++
                continue
            }
            var end = text.indexOf(' ', i)
            if (end < 0) end = text.length
            words += Word(text.substring(i, end), i)
            i = end
        }
        return words
    }
}
