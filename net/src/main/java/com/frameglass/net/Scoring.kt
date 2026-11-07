package com.frameglass.net

import com.frameglass.parser.ArabicNormalizer
import java.text.Normalizer

/** Brief F-M2. These numbers are tuned at Gate 3 and nowhere else. */
internal object Scoring {
    const val EXACT = 0.90
    const val JACCARD_WEIGHT = 0.78
    const val YEAR_MATCH = 0.10
    const val YEAR_MISMATCH = -0.25
    const val AUTO_MIN = 0.75
    const val AUTO_MARGIN = 0.12

    /** 0.90 - 0.78 lands a hair under 0.12 in binary, and that margin has to pass. */
    private const val EPSILON = 1e-9

    private val MARKS = Regex("\\p{M}+")
    private val APOSTROPHES = Regex("['’ʼ`´]")
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    /** Comparison key: ArabicNormalizer first, then Latin accents and punctuation go. */
    fun tokens(text: String): List<String> {
        val folded = Normalizer.normalize(ArabicNormalizer.key(text), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(APOSTROPHES, "")
            .replace("&", " and ")
        return folded.split(SEPARATORS).filter(String::isNotEmpty)
    }

    /** Exact wins over Jaccard. [names] holds title, original title and alternative titles. */
    fun nameScore(queries: List<String>, names: List<String>): Double {
        val queryTokens = queries.map(::tokens).filter(List<String>::isNotEmpty)
        val nameTokens = names.map(::tokens).filter(List<String>::isNotEmpty)
        var best = 0.0
        for (query in queryTokens) {
            for (name in nameTokens) {
                if (query == name) return EXACT
                best = maxOf(best, jaccard(query.toSet(), name.toSet()) * JACCARD_WEIGHT)
            }
        }
        return best
    }

    fun yearBonus(queryYear: Int?, candidateYear: Int?): Double = when {
        queryYear == null || candidateYear == null -> 0.0
        queryYear == candidateYear -> YEAR_MATCH
        else -> YEAR_MISMATCH
    }

    fun score(queries: List<String>, names: List<String>, queryYear: Int?, candidateYear: Int?): Double =
        nameScore(queries, names) + yearBonus(queryYear, candidateYear)

    /** The item that may be applied with no manual step, or null when the owner has to choose. */
    fun <T> pick(items: List<T>, scoreOf: (T) -> Double): T? {
        val ranked = items.sortedByDescending(scoreOf)
        val best = ranked.firstOrNull() ?: return null
        if (scoreOf(best) < AUTO_MIN - EPSILON) return null
        val runnerUp = ranked.getOrNull(1) ?: return best
        return best.takeIf { scoreOf(best) - scoreOf(runnerUp) >= AUTO_MARGIN - EPSILON }
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double =
        (a intersect b).size.toDouble() / (a union b).size
}
