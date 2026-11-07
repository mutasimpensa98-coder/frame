package com.frameglass.player.model

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.frameglass.data.api.HeroItem
import com.frameglass.data.api.HomeFeed
import com.frameglass.data.api.HomeRow
import com.frameglass.data.api.HomeRowKind
import java.io.File

// A small decode still reads the whole file, which is all the check needs.
private const val CheckSizePx = 96

/**
 * :data only knows the art is on disk, and a file can be there and still not decode. A hero keeps its backdrop
 * when that decodes, falls back to its cover, and leaves the list when neither does, so no page of the pager is
 * one it cannot draw.
 */
internal suspend fun List<HeroItem>.withDrawableArt(decodes: suspend (String) -> Boolean): List<HeroItem> =
    mapNotNull { hero ->
        when {
            hero.backdropPath?.let { decodes(it) } == true -> hero
            hero.coverPath?.let { decodes(it) } == true -> hero.copy(backdropPath = null)
            else -> null
        }
    }

/**
 * The feed with only heroes that can be drawn. A dropped hero that stood for a Continue watching card gives the
 * card back at the head of the row, so a title in progress never leaves Home. A row that appears this way takes
 * the place of the last title row, because F-U5 counts it among the automatic rows.
 */
internal suspend fun HomeFeed.withDrawableHeroes(decodes: suspend (String) -> Boolean): HomeFeed {
    val kept = heroes.withDrawableArt(decodes)
    val returned = heroes.filter { hero -> kept.none { it.titleId == hero.titleId } }.mapNotNull { it.continueItem }
    if (returned.isEmpty()) return copy(heroes = kept)
    return copy(
        heroes = kept,
        continueWatching = returned + continueWatching.filterNot { card -> returned.any { it.play == card.play } },
        rows = if (continueWatching.isEmpty()) withoutLastTitleRow(rows) else rows,
    )
}

private fun withoutLastTitleRow(rows: List<HomeRow>): List<HomeRow> {
    if (rows.count { it.kind != HomeRowKind.VIDEOS } < HomeFeed.MAX_AUTOMATIC_ROWS) return rows
    val last = rows.indexOfLast { it.kind != HomeRowKind.VIDEOS }
    return rows.filterIndexed { index, _ -> index != last }
}

/** Decodes through the loader that draws the hero, so the check and the page cannot disagree. */
internal suspend fun Context.decodesArt(path: String): Boolean {
    val request = ImageRequest.Builder(this)
        .data(File(path))
        .size(CheckSizePx)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .build()
    return SingletonImageLoader.get(this).execute(request) is SuccessResult
}
