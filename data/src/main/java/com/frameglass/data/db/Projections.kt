package com.frameglass.data.db

import com.frameglass.data.api.MatchState
import com.frameglass.data.api.RatingSource
import com.frameglass.data.api.TitleKind

/** A title without its long text fields, for the in-memory library snapshot. */
data class TitleRow(
    val id: Long,
    val kind: TitleKind,
    val name: String,
    val year: Int?,
    val coverPath: String?,
    val framePath: String?,
    val backdropPath: String?,
    val logoPath: String?,
    val dominantColor: Int?,
    val collectionId: Long?,
    val matchState: MatchState,
    val sortKey: String,
    val createdAt: Long,
    val favorite: Boolean = false,
    val seriesStatus: String? = null,
    val nextAirDate: String? = null,
    val nextSeason: Int? = null,
    val nextEpisode: Int? = null,
    val watchlistAt: Long? = null,
)

data class EpisodeLite(
    val id: Long,
    val titleId: Long,
    val season: Int,
    val number: Int,
    val name: String?,
    val stillPath: String?,
    val runtime: Int?,
    val airDate: String?,
    val fetchedAt: Long?,
    val imdbRating: Double? = null,
)

data class RatingRow(val titleId: Long, val source: RatingSource, val value: Double)

data class UnsettledTitle(val id: Long, val matchState: MatchState, val omdbTriedAt: Long?)

data class GenreLink(val titleId: Long, val name: String)

data class CachedFile(
    val rowId: Long,
    val fileId: String,
    val playableKey: String,
    val relPath: String,
    val size: Long,
    val mtime: Long,
    val available: Boolean,
    val hasSidecarSub: Boolean,
)

data class CastRow(
    val personId: Long,
    val name: String,
    val character: String?,
    val photoPath: String?,
)

data class ExcludedPath(val volumeUuid: String, val relPath: String, val base: String?)

data class VaultTotals(val count: Int, val bytes: Long)
