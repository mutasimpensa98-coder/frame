package com.frameglass.playerui

import com.frameglass.data.api.AutoSubtitleMode
import com.frameglass.data.api.PlaybackItem

/** The small note after an automatic download: which site and which language. */
internal data class AutoNotice(val provider: String, val language: String)

/**
 * When the player asks for a subtitle by itself (waiver of 2026-11-08). The data layer then checks the rest: clips,
 * the once-per-file memory and which sites may be asked.
 */
internal object AutoSubtitleRule {
    private const val ARABIC = "ar"
    private const val ENGLISH = "en"
    private val ENGLISH_TAGS = setOf("en", "eng", "english")

    /**
     * The languages to fetch, Arabic first, or empty for no search. A forced or Signs track alone does not count as a
     * subtitle, because it leaves most of the speech without one. English is asked for only when the setting allows it
     * and the video has no English subtitle of its own. A video whose subtitles the owner turned off stays off, and so
     * does one with a subtitle file beside it in no stated language.
     */
    fun languages(
        mode: AutoSubtitleMode,
        onlineAvailable: Boolean,
        item: PlaybackItem,
        subtitlesOff: Boolean,
        rows: List<TrackRow>,
    ): List<String> {
        if (mode == AutoSubtitleMode.OFF || !onlineAvailable || item.isPrivate || subtitlesOff) return emptyList()
        val full = rows.filter { it.supported && !it.forced && !it.signs }
        if (full.any { TrackPicker.isArabic(it.language) }) return emptyList()
        // A file beside the video in no stated language is what the picker shows, because the owner put it there.
        if (full.any { it.sidecarPath != null && it.language == null }) return emptyList()
        val english = mode == AutoSubtitleMode.ARABIC_THEN_ENGLISH && full.none { isEnglish(it.language) }
        return if (english) listOf(ARABIC, ENGLISH) else listOf(ARABIC)
    }

    private fun isEnglish(language: String?): Boolean =
        language != null && language.substringBefore('-').substringBefore('_').lowercase() in ENGLISH_TAGS
}
