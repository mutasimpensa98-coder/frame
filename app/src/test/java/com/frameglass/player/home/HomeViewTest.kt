package com.frameglass.player.home

import com.frameglass.data.api.ContinueItem
import com.frameglass.data.api.HeroItem
import com.frameglass.data.api.HomeFeed
import com.frameglass.data.api.HomeRow
import com.frameglass.data.api.HomeRowKind
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewTest {
    private val hero = HeroItem(
        titleId = 1,
        kind = TitleKind.MOVIE,
        name = "Paper Harbour",
        logoPath = null,
        backdropPath = "/art/wide-1.jpg",
        coverPath = null,
        genre = null,
        year = null,
        dominantColor = null,
        resumable = false,
        play = PlayRef(playableKey = "p1"),
    )
    private val started = ContinueItem(
        titleId = 2,
        play = PlayRef(playableKey = "p2"),
        name = "Quiet Orchard",
        episodeLabel = null,
        episodeTitle = null,
        imagePath = null,
        progress = 0.4f,
        timeLeftMs = 60_000,
        newEpisode = false,
    )
    private val row = HomeRow(HomeRowKind.RECENT, null, emptyList(), 0)

    private fun feed(
        heroes: List<HeroItem> = emptyList(),
        continuing: List<ContinueItem> = emptyList(),
        rows: List<HomeRow> = emptyList(),
        empty: Boolean = false,
        cinemaEmpty: Boolean = false,
    ) = HomeFeed(heroes, continuing, rows, empty, cinemaEmpty)

    @Test
    fun noFeedYetIsLoading() {
        assertEquals(HomeView.Loading, homeView(null))
    }

    @Test
    fun noVideosComesFirst() {
        assertEquals(HomeView.NoVideos, homeView(feed(heroes = listOf(hero), empty = true)))
    }

    @Test
    fun anEmptyCinemaFeedIsToldApartFromAnEmptyList() {
        assertEquals(HomeView.NoCinemaTitles, homeView(feed(cinemaEmpty = true)))
    }

    @Test
    fun anyPartOfTheFeedIsEnoughWithoutAHero() {
        assertEquals(HomeView.Feed, homeView(feed(rows = listOf(row))))
        assertEquals(HomeView.Feed, homeView(feed(continuing = listOf(started))))
        assertEquals(HomeView.Feed, homeView(feed(heroes = listOf(hero))))
    }

    @Test
    fun videosWithNothingForHomePointToLibrary() {
        assertEquals(HomeView.LibraryOnly, homeView(feed()))
    }
}
