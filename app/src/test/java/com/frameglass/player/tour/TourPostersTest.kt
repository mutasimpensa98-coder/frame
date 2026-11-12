package com.frameglass.player.tour

import com.frameglass.data.api.RatingInfo
import com.frameglass.data.api.RatingSource
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TourPostersTest {
    private fun card(id: Long, cover: String? = "/covers/$id.jpg", rating: Double? = null, source: RatingSource = RatingSource.TMDB) =
        TitleCard(
            id = id,
            kind = TitleKind.MOVIE,
            name = "Quiet Title $id",
            year = 2020,
            coverPath = cover,
            rating = rating?.let { RatingInfo(source, it) },
            progress = null,
            unavailable = false,
            watched = false,
        )

    private val everyFile: (String) -> Boolean = { true }

    private fun coverIds(posters: List<TourPoster>) = posters.filterIsInstance<TourPoster.Cover>().map { it.titleId }

    @Test
    fun aFreshInstallGetsSixDrawnPostersInOrder() {
        val posters = TourPosters.pick(emptyList(), emptySet(), emptyList(), everyFile)
        assertEquals((0 until 6).map { TourPoster.Placeholder(it) }, posters)
    }

    @Test
    fun neverMoreThanSixEvenFromABigLibrary() {
        val titles = (1L..40L).map { card(it, rating = 7.0) }
        val posters = TourPosters.pick(titles, emptySet(), emptyList(), everyFile)
        assertEquals(6, posters.size)
        assertEquals((1L..6L).toList(), coverIds(posters))
    }

    @Test
    fun ratedTitlesComeFirstAndKeepTheirOrder() {
        val titles = listOf(card(1), card(2, rating = 8.1), card(3), card(4, rating = 6.5, source = RatingSource.IMDB))
        val posters = TourPosters.pick(titles, emptySet(), emptyList(), everyFile)
        assertEquals(listOf(2L, 4L, 1L, 3L), coverIds(posters))
        val imdb = posters.filterIsInstance<TourPoster.Cover>().first { it.titleId == 4L }
        assertEquals(RatingInfo(RatingSource.IMDB, 6.5), imdb.rating)
    }

    @Test
    fun clipsAndPrivateFolderItemsNeverShow() {
        val titles = listOf(card(1, rating = 9.0), card(2, rating = 8.0), card(3, rating = 7.0), card(4))
        val privateById = card(2)
        val privateByCover = card(99, cover = "/covers/3.jpg")
        val posters = TourPosters.pick(titles, clips = setOf(1L), private = listOf(privateById, privateByCover), onDisk = everyFile)
        assertEquals(listOf(4L), coverIds(posters))
    }

    @Test
    fun onlyCoversThatAreFilesOnThePhoneCount() {
        val titles = listOf(card(1, rating = 9.0), card(2, cover = null, rating = 8.0), card(3))
        val posters = TourPosters.pick(titles, emptySet(), emptyList(), onDisk = { it != "/covers/1.jpg" })
        assertEquals(listOf(3L), coverIds(posters))
    }

    @Test
    fun drawnPostersFillTheSlotsLeftWithTheirOwnDesigns() {
        val titles = listOf(card(1, rating = 8.0), card(2))
        val posters = TourPosters.pick(titles, emptySet(), emptyList(), everyFile)
        assertEquals(6, posters.size)
        assertEquals(listOf(1L, 2L), coverIds(posters))
        assertEquals((2 until 6).map { TourPoster.Placeholder(it) }, posters.drop(2))
    }

    @Test
    fun aCoverSharedByTwoTitlesShowsOnce() {
        val titles = listOf(card(1, cover = "/covers/same.jpg"), card(2, cover = "/covers/same.jpg"), card(3))
        val posters = TourPosters.pick(titles, emptySet(), emptyList(), everyFile)
        assertEquals(listOf(1L, 3L), coverIds(posters))
    }

    @Test
    fun theDiskIsAskedOnlyUntilTheSlotsAreFull() {
        val asked = mutableListOf<String>()
        val titles = (1L..20L).map { card(it) }
        TourPosters.pick(titles, emptySet(), emptyList(), onDisk = { asked += it; true })
        assertEquals(6, asked.size)
        assertTrue(asked.all { it.startsWith("/covers/") })
    }
}
