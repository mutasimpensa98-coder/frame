package com.frameglass.player.ui

import com.frameglass.data.api.ContinueItem
import com.frameglass.data.api.FileVersion
import com.frameglass.data.api.HeroItem
import com.frameglass.data.api.MediaEntryKind
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which rows the long-press menu shows for a film, a series episode, a clip and a title that is not on storage. */
class CardMenuRulesTest {
    private val resume = PlayRef(playableKey = "tv:7:s2e4")
    private val first = PlayRef(playableKey = "movie:3")
    private val episode = CardEpisode("tv:7:s2e4", "S2 E4")

    private fun version(row: Long, available: Boolean = true) = FileVersion(
        fileRowId = row,
        name = "file-$row.mkv",
        width = 1920,
        height = 1080,
        sizeBytes = 1_000L * row,
        videoCodec = null,
        hdrType = null,
        audioSummary = null,
        available = available,
        isDefault = row == 1L,
    )

    private fun continueItem(play: PlayRef, label: String?, title: String? = null) = ContinueItem(
        titleId = 7,
        play = play,
        name = "Night Harbour",
        episodeLabel = label,
        episodeTitle = title,
        imagePath = null,
        progress = 0.4f,
        timeLeftMs = 60_000,
        newEpisode = false,
    )

    private fun hero(play: PlayRef?, card: ContinueItem? = null) = HeroItem(
        titleId = 7,
        kind = TitleKind.SERIES,
        name = "Night Harbour",
        logoPath = null,
        backdropPath = null,
        coverPath = null,
        genre = null,
        year = null,
        dominantColor = null,
        resumable = true,
        play = play,
        continueItem = card,
    )

    @Test
    fun aFilmPosterPlaysWhatDetailsWouldAndHasOneDelete() {
        val rows = CardMenuRules.titleRows(TitleKind.MOVIE, availableFiles = 1, titlePlay = first)
        assertEquals(TitleMenuRows(popUp = first, episode = null, delete = DeleteWording.PLAIN), rows)
    }

    @Test
    fun aFilmOnAContinueCardStillHasOneDelete() {
        val card = continueItem(first, label = null).cardTarget()
        val rows = CardMenuRules.titleRows(TitleKind.MOVIE, 2, titlePlay = first, cardPlay = card.play, cardEpisode = card.episode)
        assertEquals(TitleMenuRows(popUp = first, episode = null, delete = DeleteWording.PLAIN), rows)
    }

    @Test
    fun aSeriesEpisodeOnAContinueCardCanGoAloneOrWithTheWholeSeries() {
        val card = continueItem(resume, label = "S2 E4").cardTarget()
        val rows = CardMenuRules.titleRows(TitleKind.SERIES, 12, titlePlay = resume, cardPlay = card.play, cardEpisode = card.episode)
        assertEquals(TitleMenuRows(popUp = resume, episode = episode, delete = DeleteWording.WHOLE_SERIES), rows)
    }

    @Test
    fun anAnimeIsASeriesToo() {
        val rows = CardMenuRules.titleRows(TitleKind.ANIME, 3, titlePlay = resume, cardPlay = resume, cardEpisode = episode)
        assertEquals(episode, rows.episode)
        assertEquals(DeleteWording.WHOLE_SERIES, rows.delete)
    }

    @Test
    fun aSeriesPosterOffersOnlyTheWholeSeries() {
        val rows = CardMenuRules.titleRows(TitleKind.SERIES, 12, titlePlay = resume)
        assertEquals(TitleMenuRows(popUp = resume, episode = null, delete = DeleteWording.WHOLE_SERIES), rows)
    }

    @Test
    fun aClipIsAVideoOfItsOwnWithOneDelete() {
        val clip = PlayRef(playableKey = "clip:91")
        val rows = CardMenuRules.titleRows(TitleKind.MOVIE, 1, titlePlay = clip)
        assertEquals(TitleMenuRows(popUp = clip, episode = null, delete = DeleteWording.PLAIN), rows)
    }

    @Test
    fun aTitleThatIsNotOnStorageCanNeitherPlayNorBeDeleted() {
        val film = CardMenuRules.titleRows(TitleKind.MOVIE, availableFiles = 0, titlePlay = null)
        assertEquals(TitleMenuRows(popUp = null, episode = null, delete = null), film)
        val series = CardMenuRules.titleRows(TitleKind.SERIES, 0, titlePlay = resume, cardPlay = resume, cardEpisode = episode)
        assertEquals(TitleMenuRows(popUp = null, episode = null, delete = null), series)
    }

    @Test
    fun aTitleWithFilesButNothingToPlayHasNoPopUpRow() {
        val rows = CardMenuRules.titleRows(TitleKind.MOVIE, availableFiles = 1, titlePlay = null)
        assertNull(rows.popUp)
        assertEquals(DeleteWording.PLAIN, rows.delete)
    }

    @Test
    fun theCardsOwnPlayWinsOverTheTitles() {
        val other = PlayRef(playableKey = "tv:7:s2e5")
        val rows = CardMenuRules.titleRows(TitleKind.SERIES, 12, titlePlay = other, cardPlay = resume)
        assertEquals(resume, rows.popUp)
    }

    @Test
    fun anEpisodeDeleteNamesOnlyThatEpisodesFilesOnStorage() {
        val versions = listOf(version(1), version(2, available = false), version(3))
        val entries = CardMenuRules.episodeEntries(versions, "Night Harbour", "S2 E4")
        assertEquals(listOf(1L, 3L), entries.map { it.fileRowId })
        assertEquals(listOf("file|1", "file|3"), entries.map { it.id })
        assertEquals(setOf(MediaEntryKind.FILE), entries.map { it.kind }.toSet())
        assertEquals(setOf("Night Harbour S2 E4"), entries.map { it.name }.toSet())
        // Never the title itself, which would take every episode with it.
        assertEquals(listOf(null, null), entries.map { it.titleId })
    }

    @Test
    fun anEpisodeWithNoFileOnStorageAsksForNothing() {
        assertEquals(emptyList<Any>(), CardMenuRules.episodeEntries(listOf(version(1, available = false)), "Night Harbour", "S2 E4"))
        assertEquals(emptyList<Any>(), CardMenuRules.episodeEntries(emptyList(), "Night Harbour", null))
    }

    @Test
    fun anEpisodeWithNoNumberIsNamedByTheTitleAlone() {
        assertEquals("Night Harbour", CardMenuRules.episodeEntries(listOf(version(1)), "Night Harbour", null).single().name)
        assertEquals("Night Harbour", CardMenuRules.episodeEntries(listOf(version(1)), "Night Harbour", " ").single().name)
    }

    @Test
    fun theWholeTitleDeleteNamesTheTitle() {
        val entry = CardMenuRules.titleEntry(7, "Night Harbour")
        assertEquals(MediaEntryKind.TITLE, entry.kind)
        assertEquals(7L, entry.titleId)
        assertNull(entry.fileRowId)
    }

    @Test
    fun aContinueCardCarriesItsEpisodeAndWhatItPlays() {
        val numbered = continueItem(resume, label = "S2 E4", title = "The Tide").cardTarget()
        assertEquals(CardTarget.Title(7, "Night Harbour", resume, CardEpisode("tv:7:s2e4", "S2 E4")), numbered)
        val unnumbered = continueItem(resume, label = null, title = "The Tide").cardTarget()
        assertEquals("The Tide", unnumbered.episode?.label)
    }

    @Test
    fun aContinueCardThatPlaysOneFileHasNoEpisodeToDelete() {
        val card = continueItem(PlayRef(fileRowId = 5), label = null).cardTarget()
        assertNull(card.episode)
        assertEquals(PlayRef(fileRowId = 5), card.play)
    }

    @Test
    fun theHeroOffersWhatItsContinueCardWould() {
        val card = continueItem(resume, label = "S2 E4")
        assertEquals(card.cardTarget(), hero(resume, card).cardTarget())
    }

    @Test
    fun aHeroWithNoContinueCardPlaysItsOwnPick() {
        assertEquals(CardTarget.Title(7, "Night Harbour", first), hero(first).cardTarget())
        assertNull(hero(null).cardTarget().play)
    }
}
