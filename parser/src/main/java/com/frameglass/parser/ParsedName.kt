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
    /** Bracket group, hash tag or absolute number was seen. */
    val animeHint: Boolean,
    val subtitled: Boolean,
    val dubbed: Boolean,
    val releaseTags: List<String>,
    /** A personal clip or a social app video, such as VID_20240101_1200.mp4, not a film or an episode. */
    val clipLike: Boolean = false,
)
