package com.frameglass.data.logic

import com.frameglass.data.api.MatchState
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.TitleKind
import com.frameglass.data.api.privatePlayRef
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.Title
import com.frameglass.data.db.VaultItem
import com.frameglass.data.db.VaultRowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun item(
    id: Long,
    media: MediaFile,
    position: Long = 0,
    duration: Long = 0,
    watched: Boolean = false,
    at: Long = 0,
) = VaultItem(
    id = id, storedName = "stored$id", sha256 = "hash", state = VaultRowState.STORED, movedAt = 0,
    watchPositionMs = position, watchDurationMs = duration, watched = watched, lastPlayedAt = at, file = media,
)

private fun episode(rowId: Long, number: Int, titleId: Long = 5) =
    file(rowId, "t:$titleId:s1:e$number", season = 1, episode = number, titleId = titleId, relPath = "Show/e$number.mkv")

private fun title(name: String = "Show", kind: TitleKind = TitleKind.SERIES) = Title(
    id = 5, kind = kind, groupKey = "k", groupName = "n", localName = name, localYear = null, name = name, year = 2020,
    matchState = MatchState.AUTO, coverPath = "/poster", sortKey = "n", createdAt = 0,
)

class VaultRefsTest {
    @Test
    fun aCardOpensByItsGroup() {
        val key = VaultRefs.group(7)
        assertEquals(7L, VaultRefs.groupOf(key))
        assertNull(VaultRefs.itemOf(key))
        assertTrue(VaultRefs.isVault(key))
    }

    @Test
    fun anItemOpensByItsId() {
        val key = VaultRefs.item(9)
        assertEquals(9L, VaultRefs.itemOf(key))
        assertNull(VaultRefs.groupOf(key))
        assertTrue(VaultRefs.isVault(key))
    }

    @Test
    fun aLibraryKeyIsNeverAVaultKey() {
        listOf(PlayableKeys.movie(3), PlayableKeys.episode(3, 1, 2), PlayableKeys.file("abc"), PlayableKeys.uri("abc")).forEach {
            assertFalse(VaultRefs.isVault(it))
        }
    }

    @Test
    fun aFileWithNoTitleIsAGroupOfItsOwnAndCannotMeetATitle() {
        val row = item(4, episode(1, 1).copy(titleId = null))
        assertEquals(-4, row.groupId)
        val card = TitleCard(row.groupId, TitleKind.MOVIE, "x", null, null, null, null, false, false)
        assertEquals(-4L, VaultRefs.groupOf(card.privatePlayRef().playableKey!!))
    }
}

class VaultRecoveryTest {
    @Test
    fun theOriginalGoesOnlyWhenBothSidesHashRight() {
        assertEquals(MoveInRecovery.REMOVE_ORIGINAL, VaultRecovery.moveIn(copyWhole = true, originalWhole = true))
    }

    @Test
    fun aDamagedCopyPutsTheFileBack() {
        assertEquals(MoveInRecovery.UNDO, VaultRecovery.moveIn(copyWhole = false, originalWhole = true))
    }

    @Test
    fun anotherFileAtTheSamePathStays() {
        assertEquals(MoveInRecovery.KEEP_BOTH, VaultRecovery.moveIn(copyWhole = true, originalWhole = false))
    }

    @Test
    fun withNeitherSideWholeNothingIsRemoved() {
        assertEquals(MoveInRecovery.KEEP_BOTH, VaultRecovery.moveIn(copyWhole = false, originalWhole = false))
    }
}

class VaultCardsTest {
    @Test
    fun aSeriesIsOneCard() {
        val rows = listOf(item(1, episode(1, 1)), item(2, episode(2, 2)))
        val card = VaultCards.card(5, title(), rows) { true }
        assertEquals(5, card.id)
        assertEquals("Show", card.name)
        assertEquals(TitleKind.SERIES, card.kind)
        assertEquals("/poster", card.coverPath)
        assertEquals(2020, card.year)
        assertNull(card.progress)
        assertFalse(card.watched)
    }

    @Test
    fun withNoTitleRowTheCardComesFromTheFile() {
        val media = file(1, "t:5", titleId = 5, relPath = "Movies/Home.Video.mkv", nameKind = NameKinds.MOVIE).copy(framePath = "/frame")
        val card = VaultCards.card(5, null, listOf(item(1, media))) { true }
        assertEquals("Home Video", card.name)
        assertEquals("/frame", card.coverPath)
    }

    @Test
    fun progressStaysPrivateButShowsOnTheCard() {
        val rows = listOf(
            item(1, episode(1, 1), watched = true, at = 5),
            item(2, episode(2, 2), position = 500_000, duration = 1_000_000, at = 6),
        )
        assertEquals(0.75f, VaultCards.card(5, title(), rows) { true }.progress!!, 0.001f)
    }

    @Test
    fun allWatchedHasNoProgress() {
        val card = VaultCards.card(5, title(), listOf(item(1, episode(1, 1), watched = true, at = 5))) { true }
        assertTrue(card.watched)
        assertNull(card.progress)
    }

    @Test
    fun aMissingStoredFileDimsTheCard() {
        val rows = listOf(item(1, episode(1, 1)))
        assertTrue(VaultCards.card(5, title(), rows) { false }.unavailable)
    }

    @Test
    fun noWatchStateMeansNone() {
        assertNull(VaultCards.watchOf(item(1, episode(1, 1))))
    }

    @Test
    fun watchStateIsKeptOnTheItem() {
        val state = VaultCards.watchOf(item(1, episode(1, 1), position = 40, duration = 100, at = 9))
        assertNotNull(state)
        assertEquals(40, state!!.positionMs)
        assertEquals(100, state.durationMs)
        assertEquals(9, state.lastPlayedAt)
    }
}

class VaultPlayablesTest {
    @Test
    fun aRowIdReadsAsTheVaultItem() {
        val playables = VaultCards.playables(5, listOf(item(70, episode(1, 1))), emptyList())
        assertEquals(70L, playables.single().best.rowId)
    }

    @Test
    fun episodesPlayInOrderWhateverOrderTheyMovedIn() {
        val playables = VaultCards.playables(5, listOf(item(1, episode(1, 3)), item(2, episode(2, 1)), item(3, episode(3, 2))), emptyList())
        assertEquals(listOf(1, 2, 3), playables.map { it.number })
    }

    @Test
    fun resumeLandsWhereTheOwnerStopped() {
        val rows = listOf(
            item(1, episode(1, 1), watched = true, at = 10),
            item(2, episode(2, 2), position = 50, duration = 100, at = 20),
            item(3, episode(3, 3)),
        )
        val playables = VaultCards.playables(5, rows, emptyList())
        assertEquals(2, ResumeLogic.pick(playables)!!.number)
        assertEquals(3, NextUp.after(playables, playables[1].key)!!.number)
    }

    @Test
    fun anExtraStillPlaysInHere() {
        val extra = file(1, "f:x", titleId = 5, relPath = "Show/NCOP1.mkv", nameKind = NameKinds.EXTRA)
        assertEquals(1, VaultCards.playables(5, listOf(item(1, extra)), emptyList()).size)
    }

    @Test
    fun twoVersionsOfAnEpisodeShareProgress() {
        val rows = listOf(
            item(1, episode(1, 1), position = 10, duration = 100, at = 1),
            item(2, episode(2, 1), position = 60, duration = 100, at = 2),
        )
        val playable = VaultCards.playables(5, rows, emptyList()).single()
        assertEquals(60, playable.watch!!.positionMs)
    }
}

class VaultNamesTest {
    @Test
    fun aTakenNameGetsANumberBeforeTheExtension() {
        assertEquals("Movie (1).mkv", VaultNames.numbered("Movie.mkv", 1))
        assertEquals("فيلم (2).mp4", VaultNames.numbered("فيلم.mp4", 2))
        assertEquals("no-extension (3)", VaultNames.numbered("no-extension", 3))
        assertEquals(".hidden (1)", VaultNames.numbered(".hidden", 1))
    }

    @Test
    fun aSubtitleFollowsItsVideosNewName() {
        assertEquals("Movie (1).ar.srt", VaultNames.sidecarFor("Movie.ar.srt", "Movie.mkv", "Movie (1).mkv"))
    }

    @Test
    fun aSubtitleWithAnotherNameKeepsIt() {
        assertEquals("S01E01.srt", VaultNames.sidecarFor("S01E01.srt", "Show.mkv", "Show (1).mkv"))
    }

    @Test
    fun anUnchangedVideoChangesNothing() {
        assertEquals("Movie.ar.srt", VaultNames.sidecarFor("Movie.ar.srt", "Movie.mkv", "Movie.mkv"))
    }
}
