package com.frameglass.player.model

import com.frameglass.data.api.ContinueItem
import com.frameglass.data.api.HeroItem
import com.frameglass.data.api.HomeFeed
import com.frameglass.data.api.HomeRow
import com.frameglass.data.api.HomeRowKind
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.TitleKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HeroArtTest {
    private val decoding = setOf("/art/wide-1.jpg", "/art/cover-2.jpg", "/art/cover-4.jpg")
    private val checked = ArrayList<String>()
    private val decodes: suspend (String) -> Boolean = { path ->
        checked += path
        path in decoding
    }

    private fun hero(id: Long, backdrop: String?, cover: String?) = HeroItem(
        titleId = id,
        kind = TitleKind.MOVIE,
        name = "Title $id",
        logoPath = null,
        backdropPath = backdrop,
        coverPath = cover,
        genre = null,
        year = null,
        dominantColor = null,
        resumable = false,
        play = PlayRef(playableKey = "p$id"),
    )

    private fun keep(vararg heroes: HeroItem) = runBlocking { heroes.toList().withDrawableArt(decodes) }

    private fun card(id: Long) = ContinueItem(
        titleId = id,
        play = PlayRef(playableKey = "p$id"),
        name = "Title $id",
        episodeLabel = null,
        episodeTitle = null,
        imagePath = null,
        progress = 0.4f,
        timeLeftMs = 60_000,
        newEpisode = false,
    )

    private fun row(kind: HomeRowKind) = HomeRow(kind, null, emptyList(), 0)

    private val fullRows = listOf(row(HomeRowKind.TOP_RATED), row(HomeRowKind.GENRE), row(HomeRowKind.COLLECTION), row(HomeRowKind.VIDEOS))

    private fun feed(heroes: List<HeroItem>, continuing: List<ContinueItem>, rows: List<HomeRow> = fullRows) = runBlocking {
        HomeFeed(heroes, continuing, rows, empty = false).withDrawableHeroes(decodes)
    }

    @Test
    fun aBackdropThatDecodesKeepsTheHeroAsItIs() {
        val hero = hero(1, "/art/wide-1.jpg", "/art/cover-1.jpg")
        assertEquals(listOf(hero), keep(hero))
        assertEquals(listOf("/art/wide-1.jpg"), checked)
    }

    @Test
    fun aBrokenBackdropFallsBackToTheCover() {
        val hero = hero(2, "/art/wide-2.jpg", "/art/cover-2.jpg")
        assertEquals(listOf(hero.copy(backdropPath = null)), keep(hero))
    }

    @Test
    fun aHeroWithNoArtThatDecodesLeavesThePager() {
        assertEquals(emptyList<HeroItem>(), keep(hero(3, "/art/wide-3.jpg", "/art/cover-3.jpg"), hero(5, null, null)))
    }

    @Test
    fun theOrderOfTheRestStays() {
        val kept = keep(
            hero(1, "/art/wide-1.jpg", null),
            hero(3, "/art/wide-3.jpg", null),
            hero(4, null, "/art/cover-4.jpg"),
        )
        assertEquals(listOf(1L, 4L), kept.map { it.titleId })
    }

    @Test
    fun aDroppedHeroGivesItsCardBackAtTheHead() {
        val broken = hero(3, "/art/wide-3.jpg", "/art/cover-3.jpg").copy(continueItem = card(3))
        val out = feed(listOf(broken, hero(1, "/art/wide-1.jpg", null)), listOf(card(7)))
        assertEquals(listOf(1L), out.heroes.map { it.titleId })
        assertEquals(listOf(3L, 7L), out.continueWatching.map { it.titleId })
        assertEquals(fullRows, out.rows)
    }

    @Test
    fun aRowThatComesBackTakesTheLastTitleRowsPlace() {
        val out = feed(listOf(hero(3, null, null).copy(continueItem = card(3))), emptyList())
        assertEquals(listOf(3L), out.continueWatching.map { it.titleId })
        assertEquals(listOf(HomeRowKind.TOP_RATED, HomeRowKind.GENRE, HomeRowKind.VIDEOS), out.rows.map { it.kind })
    }

    @Test
    fun theWatchlistRowIsTheOwnersAndNeverGivesWay() {
        val rows = listOf(row(HomeRowKind.WATCHLIST)) + fullRows
        val out = feed(listOf(hero(3, null, null).copy(continueItem = card(3))), emptyList(), rows)
        assertEquals(
            listOf(HomeRowKind.WATCHLIST, HomeRowKind.TOP_RATED, HomeRowKind.GENRE, HomeRowKind.VIDEOS),
            out.rows.map { it.kind },
        )
        val fewer = listOf(row(HomeRowKind.WATCHLIST), row(HomeRowKind.TOP_RATED), row(HomeRowKind.GENRE))
        assertEquals(fewer, feed(listOf(hero(3, null, null).copy(continueItem = card(3))), emptyList(), fewer).rows)
    }

    @Test
    fun fewerTitleRowsLeaveRoomForIt() {
        val rows = listOf(row(HomeRowKind.RECENT), row(HomeRowKind.VIDEOS))
        val out = feed(listOf(hero(3, null, null).copy(continueItem = card(3))), emptyList(), rows)
        assertEquals(rows, out.rows)
    }

    @Test
    fun aHeroThatDrawsKeepsItsCardOutOfTheRow() {
        val hero = hero(1, "/art/wide-1.jpg", null).copy(continueItem = card(1))
        val out = feed(listOf(hero), listOf(card(7)))
        assertEquals(listOf(hero), out.heroes)
        assertEquals(listOf(7L), out.continueWatching.map { it.titleId })
    }
}
