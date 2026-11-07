package com.frameglass.parser

enum class NameKind { MOVIE, EPISODE, SPECIAL, EXTRA }

data class ParsedName(
    val title: String,
    val year: Int?,
    val kind: NameKind,
    val season: Int?,
    val episode: Int?,
    /** Last episode of a multi-episode file such as S02E07E08. */
    val episodeEnd: Int?,
    val absolute: Int?,
    val revision: Int?,
    /**
     * A release sign: a [Group] bracket, a hash tag, the word anime, or a running number on a file that is not a
     * bare phone video. A title with no such sign counts as anime only once a real match says so.
     */
    val animeHint: Boolean,
    val subtitled: Boolean,
    val dubbed: Boolean,
    val releaseTags: List<String>,
    /** A personal clip or a social app video, such as VID_20240101_1200.mp4, not a film or an episode. */
    val clipLike: Boolean = false,
)
