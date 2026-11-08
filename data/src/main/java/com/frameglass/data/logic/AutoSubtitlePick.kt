package com.frameglass.data.logic

import com.frameglass.data.api.SubtitleResult

/**
 * Picks what the automatic download (waiver of 2026-11-08) takes, on the phone. The local file name only orders the
 * results here; it never went out with the search.
 */
internal object AutoSubtitlePick {
    private val WORD = Regex("[a-z0-9]+")
    private val FORCED_WORDS = setOf("forced", "signs", "songs")
    private val SDH_WORDS = setOf("sdh", "hi", "cc")

    /**
     * Only [languages] in their order, never a [tapOnly] site. Within a language a full track beats a forced or Signs
     * one, plain beats SDH, a release close to [localName] keeps its timing, a single file beats a pack, and downloads
     * settle the rest.
     */
    fun ranked(
        results: List<SubtitleResult>,
        localName: String?,
        languages: List<String>,
        tapOnly: Set<String>,
    ): List<SubtitleResult> {
        val wanted = languages.map(::tagOf).distinct()
        val local = localName?.let(::wordsOf).orEmpty()
        return results
            .filter { it.provider !in tapOnly && tagOf(it.language) in wanted }
            .distinctBy { it.provider to it.token }
            .sortedWith(
                compareBy<SubtitleResult> { wanted.indexOf(tagOf(it.language)) }
                    .thenBy { isForced(it.release) }
                    .thenBy { isSdh(it.release) }
                    .thenByDescending { result -> wordsOf(result.release).count { it in local } }
                    .thenBy { it.seasonPack }
                    .thenByDescending { it.downloads ?: -1 },
            )
    }

    fun tagOf(raw: String): String {
        val text = raw.trim().lowercase().replace('_', '-').substringBefore('-')
        return when (text) {
            "ara", "arabic" -> "ar"
            "eng", "english" -> "en"
            else -> text
        }
    }

    private fun isForced(release: String): Boolean = wordsOf(release).any { it in FORCED_WORDS }

    private fun isSdh(release: String): Boolean = wordsOf(release).any { it in SDH_WORDS }

    private fun wordsOf(text: String): Set<String> = WORD.findAll(text.lowercase()).map { it.value }.toSet()
}
