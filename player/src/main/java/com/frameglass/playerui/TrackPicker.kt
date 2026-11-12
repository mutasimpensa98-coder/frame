package com.frameglass.playerui

import com.frameglass.data.api.TrackPrefs

/** One audio or subtitle track as the sheets and the picker see it. */
internal data class TrackRow(
    /** Index in Tracks.groups, or negative for a subtitle playing beside Media3. */
    val group: Int,
    /** Index inside that group. */
    val track: Int,
    val language: String?,
    val label: String?,
    /** Short name, such as "AC-3" or "ASS". */
    val codec: String?,
    val channels: Int,
    val forced: Boolean,
    val signs: Boolean,
    /** Set for a subtitle file beside the video. */
    val sidecarPath: String?,
    val isDefault: Boolean,
    val selected: Boolean,
    val supported: Boolean,
    /** For a text track inside the video: its place among the container's text tracks, so a second reader finds it. */
    val embeddedOrdinal: Int? = null,
)

/**
 * Remembered tracks are language first, so series memory (F-S6) survives files whose track order differs.
 * The stored text also carries the position among tracks of that language: two Arabic tracks in one
 * file must restore the same one after a kill (F-P15), and in another file a position that does not exist
 * just falls back to the first track of the language.
 */
internal object TrackMemory {
    private const val SEPARATOR = '#'
    private const val UNDETERMINED = "und"

    fun encode(rows: List<TrackRow>, row: TrackRow): String {
        val language = row.language ?: UNDETERMINED
        val same = rows.filter { (it.language ?: UNDETERMINED) == language }
        return "$language$SEPARATOR${same.indexOf(row).coerceAtLeast(0)}"
    }

    fun find(rows: List<TrackRow>, stored: String?): TrackRow? {
        if (stored.isNullOrEmpty()) return null
        val language = stored.substringBefore(SEPARATOR)
        val index = stored.substringAfter(SEPARATOR, "").toIntOrNull() ?: 0
        val same = rows.filter { sameLanguage(it.language ?: UNDETERMINED, language) }
        return same.getOrNull(index) ?: same.firstOrNull()
    }

    private fun sameLanguage(a: String, b: String): Boolean =
        a.equals(b, ignoreCase = true) || base(a).equals(base(b), ignoreCase = true)

    private fun base(tag: String): String = tag.substringBefore('-').substringBefore('_')
}

/** The answer to "which subtitle now": a track, off, or leave what Media3 chose. */
internal sealed interface SubtitleChoice {
    data class Track(val row: TrackRow) : SubtitleChoice
    data object Off : SubtitleChoice
    data object Keep : SubtitleChoice
}

internal object TrackPicker {
    private val ARABIC = setOf("ar", "ara", "arabic")
    private val SIGNS_WORDS = listOf("sign", "song", "forced", "lyrics", "karaoke", "لافتات", "أغاني")

    fun isArabic(language: String?): Boolean =
        language != null && language.substringBefore('-').substringBefore('_').lowercase() in ARABIC

    fun looksLikeSigns(label: String?): Boolean =
        label != null && SIGNS_WORDS.any { label.contains(it, ignoreCase = true) }

    fun audio(rows: List<TrackRow>, prefs: TrackPrefs): TrackRow? =
        TrackMemory.find(rows.filter { it.supported }, prefs.audioLanguage)

    /**
     * F-S6, F-S7 and F-A7, in order: a remembered choice, then a full Arabic track, with sidecars before
     * embedded ones, then a sidecar in no stated language, because the owner put it there for this file.
     * Forced and Signs tracks come after every full one of the same language.
     */
    fun subtitle(rows: List<TrackRow>, prefs: TrackPrefs): SubtitleChoice {
        if (prefs.subtitlesOff) return SubtitleChoice.Off
        val usable = rows.filter { it.supported }
        prefs.subtitleUri?.let { path -> usable.firstOrNull { it.sidecarPath == path } }?.let { return SubtitleChoice.Track(it) }
        TrackMemory.find(usable, prefs.subtitleLanguage)?.let { return SubtitleChoice.Track(it) }
        val arabic = usable.filter { isArabic(it.language) }
        val full = arabic.filterNot { it.forced || it.signs }
        val best = full.firstOrNull { it.sidecarPath != null }
            ?: full.firstOrNull()
            ?: usable.firstOrNull { it.sidecarPath != null && it.language == null && !it.forced && !it.signs }
            ?: arabic.firstOrNull()
        return if (best != null) SubtitleChoice.Track(best) else SubtitleChoice.Keep
    }
}

/** Subtitle files a reopen lists beyond the video's own sidecars. */
internal object CopiedSubtitles {
    /**
     * The file chosen last, then every other file copied in for this video by name, each once and none already
     * [listed]. A frame rate fix becomes the chosen file, so its original only stays in the list this way.
     */
    fun extra(listed: Collection<String>, chosen: String?, copied: List<String>): List<String> =
        (listOfNotNull(chosen) + copied.sorted()).distinct().filterNot { it in listed }
}
