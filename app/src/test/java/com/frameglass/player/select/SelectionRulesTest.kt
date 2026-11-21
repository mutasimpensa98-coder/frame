package com.frameglass.player.select

import com.frameglass.data.api.EpisodeRow
import com.frameglass.data.api.FileVersion
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.MediaEntry
import com.frameglass.data.api.MediaEntryKind
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.TitleKind
import com.frameglass.data.api.WatchTarget
import com.frameglass.player.select.SelectionAction.DELETE
import com.frameglass.player.select.SelectionAction.FAVORITE_ADD
import com.frameglass.player.select.SelectionAction.FAVORITE_REMOVE
import com.frameglass.player.select.SelectionAction.HIDE
import com.frameglass.player.select.SelectionAction.MARK_UNWATCHED
import com.frameglass.player.select.SelectionAction.MARK_WATCHED
import com.frameglass.player.select.SelectionAction.MOVE_OUT
import com.frameglass.player.select.SelectionAction.MOVE_TO_PRIVATE
import com.frameglass.player.select.SelectionAction.WATCHLIST_ADD
import com.frameglass.player.select.SelectionAction.WATCHLIST_REMOVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Multi-select with no screen: picking, which actions the bar offers for films, series, clips, folders, episodes and
 * things that are not on storage, and what a delete or a move is handed.
 */
class SelectionRulesTest {
    private val film = SelectItem.Title(3, "Paper Lanterns", onStorage = true, watched = false)
    private val series = SelectItem.Title(7, "Night Harbour", onStorage = true, watched = true)
    private val clip = SelectItem.Title(11, "Beach day", onStorage = true, watched = false)
    private val gone = SelectItem.Title(13, "Old Tape", onStorage = false, watched = false)
    private val folder = SelectItem.Folder("/storage/films/drama", "drama", onStorage = true)
    private val emptyFolder = SelectItem.Folder("/storage/films/gone", "gone", onStorage = false)
    private val file = SelectItem.File(41, "trip.mp4", onStorage = true)
    private val lostFile = SelectItem.File(42, "lost.mp4", onStorage = false)
    private val episode = SelectItem.Episode("tv:7:s1e2", "Night Harbour", "S1 E2", onStorage = true, watched = false)
    private val seenEpisode = SelectItem.Episode("tv:7:s1e1", "Night Harbour", "S1 E1", onStorage = true, watched = true)
    private val secret = SelectItem.Private(5, "Hidden cut")

    private fun card(id: Long, unavailable: Boolean = false, watched: Boolean = false) = TitleCard(
        id = id,
        kind = TitleKind.MOVIE,
        name = "Title $id",
        year = 2020,
        coverPath = null,
        rating = null,
        progress = null,
        unavailable = unavailable,
        watched = watched,
    )

    private fun folderEntry(directory: Boolean, row: Long? = null, unavailable: Boolean = false) = FolderEntry(
        path = if (directory) "/storage/films/drama" else "/storage/films/drama/trip.mp4",
        name = if (directory) "drama" else "trip.mp4",
        isDirectory = directory,
        videoCount = 2,
        sizeBytes = 10,
        isNew = false,
        hasSubtitle = false,
        coverPath = null,
        progress = null,
        unavailable = unavailable,
        play = row?.let { PlayRef(fileRowId = it) },
    )

    private fun episodeRow(
        key: String?,
        label: String = "S1 E2",
        available: Boolean = true,
        missing: Boolean = false,
        watched: Boolean = false,
    ) = EpisodeRow(
        season = 1,
        number = 2,
        label = label,
        title = "The Tide",
        imagePath = null,
        durationMs = 0,
        progress = null,
        watched = watched,
        available = available,
        missing = missing,
        play = key?.let { PlayRef(playableKey = it) },
    )

    private fun mediaFolder(path: String, kind: MediaEntryKind = MediaEntryKind.FOLDER) = MediaEntry(
        id = "$kind|$path",
        kind = kind,
        name = path.substringAfterLast('/'),
        detail = null,
        sizeBytes = 500,
        fileCount = 2,
        durationMs = null,
        width = null,
        height = null,
        coverPath = null,
        titleId = null,
        folderPath = path,
        fileRowId = null,
    )

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

    private fun offered(place: SelectionPlace, vararg items: SelectItem) =
        SelectionRules.actions(place, items.toList()).map { it.action }

    private fun enabled(place: SelectionPlace, vararg items: SelectItem) =
        SelectionRules.actions(place, items.toList()).filter { it.enabled }.map { it.action }

    // Picking.

    @Test
    fun aTapPicksAndTheNextTapLetsGo() {
        val one = SelectionRules.toggle(emptyMap(), film.key, film)
        assertEquals(listOf(film), one.values.toList())
        assertTrue(SelectionRules.toggle(one, film.key, film).isEmpty())
    }

    @Test
    fun picksKeepTheOrderTheyWereMadeIn() {
        var picked = emptyMap<String, SelectItem>()
        for (item in listOf(clip, film, series)) picked = SelectionRules.toggle(picked, item.key, item)
        picked = SelectionRules.toggle(picked, film.key, film)
        assertEquals(listOf(clip, series), picked.values.toList())
    }

    @Test
    fun selectAllAddsTheRestInListOrderAndPicksNothingTwice() {
        val picked = mapOf(series.key to series)
        val all = SelectionRules.selectAll(picked, listOf(film, series, clip)) { it.key }
        assertEquals(listOf(series, film, clip), all.values.toList())
    }

    @Test
    fun retainDropsWhatLeftTheListAndTakesTheListedValue() {
        val picked = mapOf(film.key to film, gone.key to gone, series.key to series)
        val watchedFilm = film.copy(watched = true)
        val kept = SelectionRules.retain(picked, listOf(series, watchedFilm, clip)) { it.key }
        assertEquals(listOf(series, watchedFilm), kept.values.toList())
    }

    @Test
    fun allPickedNeedsAListAndEveryItemOfIt() {
        assertFalse(SelectionRules.allPicked(setOf(film.key), emptyList()))
        assertFalse(SelectionRules.allPicked(setOf(film.key), listOf(film.key, clip.key)))
        assertTrue(SelectionRules.allPicked(setOf(film.key, clip.key), listOf(film.key, clip.key)))
        // A pick that has scrolled out of a narrowed list does not stand in the way.
        assertTrue(SelectionRules.allPicked(setOf(film.key, clip.key, series.key), listOf(film.key, clip.key)))
    }

    // What a card is as a pick.

    @Test
    fun aTitleCardCarriesItsStorageAndWatchedState() {
        assertEquals(SelectItem.Title(4, "Title 4", onStorage = true, watched = true), card(4, watched = true).selectItem())
        assertFalse(card(4, unavailable = true).selectItem().onStorage)
        assertEquals(SelectItem.Private(4, "Title 4"), card(4).privateSelectItem())
    }

    @Test
    fun aFolderRowIsAFolderOrAFileAndAFileTheLibraryLostCannotBePicked() {
        assertEquals(folder, folderEntry(directory = true).selectItem())
        assertEquals(file, folderEntry(directory = false, row = 41).selectItem())
        assertNull(folderEntry(directory = false, row = null).selectItem())
        assertFalse(folderEntry(directory = true, unavailable = true).selectItem()!!.onStorage)
    }

    @Test
    fun anEpisodeIsNamedWithItsSeriesAndOneWithNoFileCannotBePicked() {
        val item = episodeRow("tv:7:s1e2").selectItem("Night Harbour") as SelectItem.Episode
        assertEquals("Night Harbour S1 E2", item.name)
        assertEquals("episode|tv:7:s1e2", item.key)
        assertNull(episodeRow(null).selectItem("Night Harbour"))
        // With no number, the episode's own title tells it apart.
        assertEquals("Night Harbour The Tide", episodeRow("tv:7:x", label = "").selectItem("Night Harbour")!!.name)
        assertFalse(episodeRow("tv:7:s1e2", available = false).selectItem("Night Harbour")!!.onStorage)
        assertFalse(episodeRow("tv:7:s1e2", missing = true).selectItem("Night Harbour")!!.onStorage)
    }

    @Test
    fun keysOfDifferentKindsNeverCollide() {
        val keys = listOf(SelectItem.Title(5, "a", true, false), SelectItem.File(5, "a", true), secret).map { it.key }
        assertEquals(3, keys.toSet().size)
    }

    // Which actions the bar offers.

    @Test
    fun titlesGetEveryActionInBarOrderWithDeleteLast() {
        val all = listOf(WATCHLIST_ADD, FAVORITE_ADD, MARK_WATCHED, HIDE, MOVE_TO_PRIVATE, DELETE)
        assertEquals(all, offered(SelectionPlace.TITLES, film))
        assertEquals(all, enabled(SelectionPlace.TITLES, film, clip))
    }

    @Test
    fun theFavouritesAndWatchlistFiltersTakeTitlesOut() {
        assertEquals(FAVORITE_REMOVE, offered(SelectionPlace.FAVORITES, film).first())
        assertTrue(WATCHLIST_ADD in offered(SelectionPlace.FAVORITES, film))
        assertFalse(FAVORITE_ADD in offered(SelectionPlace.FAVORITES, film))
        assertEquals(WATCHLIST_REMOVE, offered(SelectionPlace.WATCHLIST, film).first())
        assertTrue(FAVORITE_ADD in offered(SelectionPlace.WATCHLIST, film))
        assertFalse(WATCHLIST_ADD in offered(SelectionPlace.WATCHLIST, film))
    }

    @Test
    fun theWatchedButtonTurnsOnlyWhenEverythingPickedIsWatched() {
        assertTrue(MARK_WATCHED in offered(SelectionPlace.TITLES, film))
        assertTrue(MARK_UNWATCHED in offered(SelectionPlace.TITLES, series))
        assertFalse(MARK_WATCHED in offered(SelectionPlace.TITLES, series))
        // A film not seen beside a series that is: the rest get marked.
        assertTrue(MARK_WATCHED in offered(SelectionPlace.TITLES, film, series))
        assertTrue(MARK_UNWATCHED in offered(SelectionPlace.EPISODES, seenEpisode))
        assertTrue(MARK_WATCHED in offered(SelectionPlace.EPISODES, seenEpisode, episode))
    }

    @Test
    fun aTitleWithNothingOnStorageCanBeListedOrHiddenButNotMovedOrDeleted() {
        assertEquals(listOf(WATCHLIST_ADD, FAVORITE_ADD, MARK_WATCHED, HIDE), enabled(SelectionPlace.TITLES, gone))
    }

    @Test
    fun oneItemOnStorageIsEnoughForMoveAndDelete() {
        val on = enabled(SelectionPlace.TITLES, gone, film)
        assertTrue(MOVE_TO_PRIVATE in on)
        assertTrue(DELETE in on)
    }

    @Test
    fun foldersOnlyMoveOrDelete() {
        assertEquals(listOf(MOVE_TO_PRIVATE, DELETE), offered(SelectionPlace.FOLDERS, folder, file))
        assertEquals(listOf(MOVE_TO_PRIVATE, DELETE), enabled(SelectionPlace.FOLDERS, folder, file))
        assertTrue(enabled(SelectionPlace.FOLDERS, emptyFolder, lostFile).isEmpty())
    }

    @Test
    fun aMixOfTitlesAndFoldersDimsNothingThatOneOfThemCanTake() {
        assertEquals(offered(SelectionPlace.TITLES, film, folder), enabled(SelectionPlace.TITLES, film, folder))
    }

    @Test
    fun foldersAloneDimTheTitleActions() {
        assertEquals(listOf(MOVE_TO_PRIVATE, DELETE), enabled(SelectionPlace.TITLES, folder, file))
    }

    @Test
    fun episodesCanBeMarkedMovedOrDeleted() {
        assertEquals(listOf(MARK_WATCHED, MOVE_TO_PRIVATE, DELETE), offered(SelectionPlace.EPISODES, episode))
        assertEquals(listOf(MARK_WATCHED, MOVE_TO_PRIVATE, DELETE), enabled(SelectionPlace.EPISODES, episode))
        val lost = episode.copy(onStorage = false)
        assertEquals(listOf(MARK_WATCHED), enabled(SelectionPlace.EPISODES, lost))
    }

    @Test
    fun thePrivateFolderOnlyMovesOut() {
        assertEquals(listOf(MOVE_OUT), offered(SelectionPlace.PRIVATE, secret))
        assertEquals(listOf(MOVE_OUT), enabled(SelectionPlace.PRIVATE, secret))
    }

    @Test
    fun aPrivateItemIsNeverOfferedToTheLibraryActions() {
        assertTrue(enabled(SelectionPlace.TITLES, secret).isEmpty())
        assertTrue(enabled(SelectionPlace.FOLDERS, secret).isEmpty())
        assertTrue(enabled(SelectionPlace.PRIVATE, film).isEmpty())
    }

    @Test
    fun withNothingPickedEveryButtonIsDimmed() {
        for (place in SelectionPlace.entries) {
            val actions = SelectionRules.actions(place, emptyList())
            assertTrue(actions.isNotEmpty())
            assertTrue(actions.none { it.enabled })
        }
        assertTrue(MARK_WATCHED in offered(SelectionPlace.TITLES))
    }

    // How the bar is laid out.

    @Test
    fun sixActionsKeepDeleteAtTheEndAndPutTheRestBehindMore() {
        val layout = SelectionRules.bar(SelectionRules.actions(SelectionPlace.TITLES, listOf(film)))
        assertEquals(listOf(WATCHLIST_ADD, FAVORITE_ADD, MARK_WATCHED), layout.shown.map { it.action })
        assertEquals(listOf(HIDE, MOVE_TO_PRIVATE), layout.overflow.map { it.action })
        assertEquals(listOf(DELETE), layout.pinned.map { it.action })
        // Three in the row, More and Delete: five places.
        assertEquals(SelectionRules.BAR_SLOTS, layout.shown.size + 1 + layout.pinned.size)
    }

    @Test
    fun aShortBarShowsEverything() {
        val episodes = SelectionRules.bar(SelectionRules.actions(SelectionPlace.EPISODES, listOf(episode)))
        assertEquals(listOf(MARK_WATCHED, MOVE_TO_PRIVATE), episodes.shown.map { it.action })
        assertTrue(episodes.overflow.isEmpty())
        assertEquals(listOf(DELETE), episodes.pinned.map { it.action })

        val private = SelectionRules.bar(SelectionRules.actions(SelectionPlace.PRIVATE, listOf(secret)))
        assertEquals(listOf(MOVE_OUT), private.shown.map { it.action })
        assertTrue(private.overflow.isEmpty())
        assertTrue(private.pinned.isEmpty())
    }

    @Test
    fun aBarThatJustFitsNeedsNoMore() {
        val actions = SelectionRules.actions(SelectionPlace.TITLES, listOf(film))
        val wide = SelectionRules.bar(actions, slots = 6)
        assertEquals(5, wide.shown.size)
        assertTrue(wide.overflow.isEmpty())
        val narrow = SelectionRules.bar(actions, slots = 3)
        assertEquals(listOf(WATCHLIST_ADD), narrow.shown.map { it.action })
        assertEquals(4, narrow.overflow.size)
        assertEquals(listOf(DELETE), narrow.pinned.map { it.action })
    }

    @Test
    fun aDimmedActionKeepsItsPlaceInTheBar() {
        val layout = SelectionRules.bar(SelectionRules.actions(SelectionPlace.TITLES, listOf(gone)))
        assertEquals(listOf(BarAction(DELETE, enabled = false)), layout.pinned)
        assertEquals(BarAction(MOVE_TO_PRIVATE, enabled = false), layout.overflow.last())
    }

    // What an action is handed.

    @Test
    fun titleActionsTakeTitlesOnlyAndEachOnce() {
        assertEquals(listOf(3L, 7L), SelectionRules.titleIds(listOf(film, folder, series, film, episode, secret)))
        assertEquals(listOf(5L), SelectionRules.privateIds(listOf(film, secret, secret)))
    }

    @Test
    fun watchedIsWrittenToAWholeTitleOrToOneEpisode() {
        assertEquals(
            listOf(WatchTarget.Title(7), WatchTarget.Playable("tv:7:s1e2")),
            SelectionRules.watchTargets(listOf(series, folder, episode, file, secret)),
        )
    }

    @Test
    fun onlyFoldersAndEpisodesOnStorageAreLookedUp() {
        assertEquals(listOf(folder.path), SelectionRules.foldersOnStorage(listOf(folder, emptyFolder, film)))
        assertEquals(
            listOf("tv:7:s1e2"),
            SelectionRules.episodesOnStorage(listOf(episode, episode.copy(playableKey = "tv:7:s1e9", onStorage = false))),
        )
    }

    @Test
    fun deleteAsksAboutEveryPickOnStorageInPickOrder() {
        val entries = SelectionRules.deleteEntries(
            items = listOf(clip, file, folder, episode),
            folders = listOf(mediaFolder("/storage/films/other"), mediaFolder(folder.path)),
            versions = mapOf(episode.playableKey to listOf(version(1), version(2))),
        )
        assertEquals(listOf("title|11", "file|41", "FOLDER|${folder.path}", "file|1", "file|2"), entries.map { it.id })
        assertEquals(MediaEntryKind.TITLE, entries[0].kind)
        assertEquals(11L, entries[0].titleId)
        assertEquals(41L, entries[1].fileRowId)
        // A failed delete reports the episode by the name the owner knows it by.
        assertEquals("Night Harbour S1 E2", entries[3].name)
    }

    @Test
    fun deleteLeavesOutWhatIsNotOnStorage() {
        val entries = SelectionRules.deleteEntries(
            items = listOf(gone, lostFile, emptyFolder, episode.copy(onStorage = false), film),
            folders = listOf(mediaFolder(emptyFolder.path)),
            versions = mapOf(episode.playableKey to listOf(version(1))),
        )
        assertEquals(listOf("title|3"), entries.map { it.id })
    }

    @Test
    fun deleteNeverReachesIntoThePrivateFolder() {
        assertTrue(SelectionRules.deleteEntries(listOf(secret), emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun aFolderMediaManagerDoesNotListAddsNothing() {
        // The same path under another kind, such as a title filed there, is not the folder.
        val others = listOf(mediaFolder(folder.path, MediaEntryKind.TITLE), mediaFolder("/storage/films"))
        assertTrue(SelectionRules.deleteEntries(listOf(folder), others, emptyMap()).isEmpty())
    }

    @Test
    fun anEpisodeGivesOnlyItsVersionsOnStorageAndNoFileTwice() {
        val versions = mapOf(episode.playableKey to listOf(version(1), version(2, available = false)))
        val entries = SelectionRules.deleteEntries(listOf(SelectItem.File(1, "file-1.mkv", true), episode), emptyList(), versions)
        assertEquals(listOf("file|1"), entries.map { it.id })
        assertTrue(SelectionRules.deleteEntries(listOf(episode), emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun aMoveTakesWholeTitlesAndTheFilesOfEverythingElse() {
        val move = SelectionRules.moveIn(
            items = listOf(film, file, folder, episode, series),
            folderFiles = mapOf(folder.path to listOf(60L, 61L, 41L)),
            versions = mapOf(episode.playableKey to listOf(version(1), version(2, available = false))),
        )
        assertEquals(listOf(3L, 7L), move.titleIds)
        assertEquals(listOf(41L, 60L, 61L, 1L), move.fileRowIds)
        assertFalse(move.isEmpty)
    }

    @Test
    fun aMoveLeavesOutWhatIsNotOnStorageAndWhatIsAlreadyPrivate() {
        val move = SelectionRules.moveIn(
            items = listOf(gone, lostFile, emptyFolder, secret, episode.copy(onStorage = false)),
            folderFiles = mapOf(emptyFolder.path to listOf(60L)),
            versions = mapOf(episode.playableKey to listOf(version(1))),
        )
        assertTrue(move.isEmpty)
    }
}
