package com.frameglass.player.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibrarySort
import com.frameglass.data.api.RatingInfo
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.frameServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private const val LoadTimeoutMillis = 3_000L

/** One poster slot of the tour. No film or series art ships in the APK (R8), so a slot is the owner's own or drawn. */
internal sealed interface TourPoster {
    /** A cover already stored on the phone for one of the owner's titles. */
    data class Cover(
        val titleId: Long,
        val path: String,
        val rating: RatingInfo?,
        val year: Int?,
        val progress: Float?,
    ) : TourPoster

    /** A quiet geometric poster drawn in code. [design] picks one of [TourArt]'s designs. */
    data class Placeholder(val design: Int) : TourPoster
}

/** Picks the posters of the library page from the owner's own covers. */
internal object TourPosters {
    /** Slots on the library page. */
    const val COUNT = 6

    /** What a fresh install shows, and what shows while the library loads. */
    fun placeholders(count: Int = COUNT): List<TourPoster> = List(count) { TourPoster.Placeholder(it) }

    /**
     * Up to [count] of [titles] whose cover is a file on the phone, rated ones first and otherwise in the order
     * given, then placeholders for the slots left. Clips ([clips] holds their ids) and anything in the private folder
     * never show: [private] is matched by id and by cover path. [onDisk] runs only until the slots are full.
     */
    fun pick(
        titles: List<TitleCard>,
        clips: Set<Long>,
        private: List<TitleCard>,
        onDisk: (String) -> Boolean,
        count: Int = COUNT,
    ): List<TourPoster> {
        val privateIds = private.mapTo(HashSet()) { it.id }
        val privateCovers = private.mapNotNullTo(HashSet()) { it.coverPath }
        val seenIds = HashSet<Long>()
        val seenPaths = HashSet<String>()
        val covers = titles
            .sortedBy { it.rating == null }
            .asSequence()
            .filter { it.id !in clips && it.id !in privateIds }
            .filter { card -> card.coverPath.let { it != null && it !in privateCovers } }
            .filter { seenIds.add(it.id) && seenPaths.add(requireNotNull(it.coverPath)) }
            .filter { onDisk(requireNotNull(it.coverPath)) }
            .take(count)
            .map { TourPoster.Cover(it.id, requireNotNull(it.coverPath), it.rating, it.year, it.progress) }
            .toList()
        return covers + (covers.size until count).map { TourPoster.Placeholder(it) }
    }
}

/**
 * The tour's posters, from the library on the phone. Placeholders show until it answers, and stay when it holds
 * no cover yet, as on a fresh install.
 */
@Composable
internal fun rememberTourPosters(): List<TourPoster> {
    val services = LocalContext.current.frameServices
    val start = remember { TourPosters.placeholders() }
    val posters by produceState(start, services) {
        val picked = withTimeoutOrNull(LoadTimeoutMillis) {
            val library = services.library
            val titles = library.titles(LibraryFilter.ALL, LibrarySort.RATING).first()
            val clips = library.titles(LibraryFilter.VIDEOS, LibrarySort.RECENT).first().mapTo(HashSet()) { it.id }
            val private = services.vault.items.first()
            withContext(Dispatchers.IO) { TourPosters.pick(titles, clips, private, { File(it).isFile }) }
        }
        if (picked != null) value = picked
    }
    return posters
}
