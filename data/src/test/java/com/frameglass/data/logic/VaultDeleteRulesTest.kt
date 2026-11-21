package com.frameglass.data.logic

import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.VaultItem
import com.frameglass.data.db.VaultRowState
import com.frameglass.data.db.VaultSidecar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The delete inside the private folder with no disk: what a plan counts, and what goes with a deleted video. */
class VaultDeleteRulesTest {
    private fun stored(id: Long, media: MediaFile) = VaultItem(
        id = id, storedName = "stored$id.bin", sha256 = "hash", state = VaultRowState.STORED, movedAt = 0, file = media,
    )

    private fun subtitle(id: Long, itemId: Long, size: Long) = VaultSidecar(
        id = id, itemId = itemId, storedName = "sub$id.bin", originalRel = "name.ar.srt", sizeBytes = size, sha256 = "hash",
        language = "ar", forced = false, signs = false, keptOriginal = false,
    )

    // Two episodes of title 5, a film of title 8, and a clip with no title, which is a card of its own.
    private val first = stored(1, file(11, "t:5:s1:e1", season = 1, episode = 1, titleId = 5, size = 1_000))
    private val second = stored(2, file(12, "t:5:s1:e2", season = 1, episode = 2, titleId = 5, size = 2_000))
    private val film = stored(3, file(13, "t:8", titleId = 8, size = 5_000))
    private val clip = stored(4, file(14, "f:abc", size = 300).copy(titleId = null))
    private val all = listOf(first, second, film, clip)
    private val subtitles = mapOf(1L to listOf(subtitle(1, 1, 40), subtitle(2, 1, 60)), 3L to listOf(subtitle(3, 3, 10)))
    private val names = mapOf(5L to "Night Harbour", 8L to "Paper Lanterns", -4L to "Beach day")

    @Test
    fun aClipWithNoTitleIsACardOfItsOwn() {
        assertEquals(-4L, clip.groupId)
        assertEquals(5L, first.groupId)
    }

    @Test
    fun aPlanCountsEveryVideoOfTheCardAndTheSubtitlesWithThem() {
        val plan = VaultDeleteRules.plan(listOf(5L), all, subtitles, names)
        assertEquals(listOf(5L), plan.titleIds)
        assertEquals(listOf(1L, 2L), plan.itemIds)
        assertEquals(2, plan.fileCount)
        assertEquals(2, plan.subtitleCount)
        assertEquals(3_100L, plan.totalBytes)
        assertEquals(listOf("Night Harbour"), plan.names)
        assertFalse(plan.isEmpty)
    }

    @Test
    fun severalCardsAddUpInTheOrderAsked() {
        val plan = VaultDeleteRules.plan(listOf(-4L, 8L), all, subtitles, names)
        assertEquals(listOf(-4L, 8L), plan.titleIds)
        assertEquals(listOf(4L, 3L), plan.itemIds)
        assertEquals(2, plan.fileCount)
        assertEquals(1, plan.subtitleCount)
        assertEquals(5_310L, plan.totalBytes)
        assertEquals(listOf("Beach day", "Paper Lanterns"), plan.names)
    }

    @Test
    fun aCardAskedTwiceCountsOnceAndOneThatIsNotInTheFolderAddsNothing() {
        val plan = VaultDeleteRules.plan(listOf(8L, 8L, 77L), all, subtitles, names)
        assertEquals(listOf(8L), plan.titleIds)
        assertEquals(1, plan.fileCount)
        assertTrue(VaultDeleteRules.plan(listOf(77L), all, subtitles, names).isEmpty)
        assertTrue(VaultDeleteRules.plan(emptyList(), all, subtitles, names).isEmpty)
    }

    @Test
    fun nothingOutsideTheAskedCardsIsEverInAPlan() {
        val plan = VaultDeleteRules.plan(listOf(8L), all, subtitles, names)
        assertEquals(listOf(film), VaultDeleteRules.targets(plan, all))
    }

    @Test
    fun aVideoMovedInAfterThePlanStays() {
        val plan = VaultDeleteRules.plan(listOf(5L), all, subtitles, names)
        val late = stored(9, file(19, "t:5:s1:e3", season = 1, episode = 3, titleId = 5))
        assertEquals(listOf(first, second), VaultDeleteRules.targets(plan, all + late))
    }

    @Test
    fun aVideoThatLeftTheFolderMeanwhileIsNotATarget() {
        val plan = VaultDeleteRules.plan(listOf(5L), all, subtitles, names)
        assertEquals(listOf(second), VaultDeleteRules.targets(plan, listOf(second, film, clip)))
        assertTrue(VaultDeleteRules.targets(plan, emptyList()).isEmpty())
    }

    @Test
    fun aRowTakesItsVideoFirstAndThenItsSubtitles() {
        assertEquals(
            listOf("stored1.bin", "sub1.bin", "sub2.bin"),
            VaultDeleteRules.storedFiles(first, subtitles.getValue(1)),
        )
        assertEquals(listOf("stored4.bin"), VaultDeleteRules.storedFiles(clip, emptyList()))
    }

    private val empty = TitleTraces(
        privateLeft = 0, libraryFiles = 0, otherProgress = 0,
        favorite = false, onWatchlist = false, manualMatch = false, manualCover = false,
    )

    @Test
    fun aTitleThatWasOnlyEverPrivateIsForgottenOnceItIsEmpty() {
        assertTrue(VaultDeleteRules.forgettable(empty))
        assertEquals(setOf(5L, 8L), VaultDeleteRules.orphanTitles(listOf(5L, 8L), mapOf(5L to empty, 8L to empty)))
    }

    @Test
    fun aTitleWithAnotherPrivateVideoOrALibraryFileIsKept() {
        // One episode deleted, another still private.
        assertFalse(VaultDeleteRules.forgettable(empty.copy(privateLeft = 1)))
        // The private copy deleted, a copy still in the library, on storage or not.
        assertFalse(VaultDeleteRules.forgettable(empty.copy(libraryFiles = 2)))
    }

    @Test
    fun aSeriesWhoseLibraryFolderWasRemovedKeepsItsProgress() {
        // The folder was removed or excluded, which deletes its file rows on purpose and keeps the title, so
        // "no file row" alone must never forget it: the other episodes' progress is the promise.
        val removedFolder = empty.copy(otherProgress = 7)
        assertFalse(VaultDeleteRules.forgettable(removedFolder))
        assertTrue(VaultDeleteRules.orphanTitles(listOf(5L), mapOf(5L to removedFolder)).isEmpty())
    }

    @Test
    fun anythingTheOwnerChoseForTheTitleKeepsIt() {
        assertFalse(VaultDeleteRules.forgettable(empty.copy(favorite = true)))
        assertFalse(VaultDeleteRules.forgettable(empty.copy(onWatchlist = true)))
        assertFalse(VaultDeleteRules.forgettable(empty.copy(manualMatch = true)))
        assertFalse(VaultDeleteRules.forgettable(empty.copy(manualCover = true)))
    }

    @Test
    fun onlyTheEmptyTitlesOfAMixedDeleteAreForgotten() {
        val traces = mapOf(5L to empty.copy(otherProgress = 1), 8L to empty)
        assertEquals(setOf(8L), VaultDeleteRules.orphanTitles(listOf(5L, 8L), traces))
    }

    @Test
    fun aTitleThatIsAlreadyGoneIsLeftAlone() {
        assertTrue(VaultDeleteRules.orphanTitles(listOf(5L), emptyMap()).isEmpty())
        assertTrue(VaultDeleteRules.orphanTitles(emptyList(), mapOf(5L to empty)).isEmpty())
    }

    @Test
    fun theLeftoversOfAFileGoUnlessACopyOfItStays() {
        assertEquals(setOf("id13", "id14"), VaultDeleteRules.freeFileIds(listOf("id13", "id14"), setOf("id11")))
        // The same file is still in the folder or in the library under another row.
        assertEquals(setOf("id14"), VaultDeleteRules.freeFileIds(listOf("id13", "id14"), setOf("id13")))
        assertTrue(VaultDeleteRules.freeFileIds(listOf("id13"), setOf("id13")).isEmpty())
        assertTrue(VaultDeleteRules.freeFileIds(emptyList(), emptySet()).isEmpty())
    }

    // The journal that lets a cleanup cut off by a kill finish on the next start.

    private val filmLeft = VaultLeftover("id13", 8)
    private val clipLeft = VaultLeftover("id14", null)

    @Test
    fun aJournalLineReadsBackWithAndWithoutATitle() {
        val text = VaultCleanupJournal.line(filmLeft) + "\n" + VaultCleanupJournal.line(clipLeft) + "\n"
        assertEquals(listOf(filmLeft, clipLeft), VaultCleanupJournal.parse(text))
    }

    @Test
    fun theJournalNamesNoFileAndNoTitle() {
        assertEquals("8\tid13", VaultCleanupJournal.line(filmLeft))
        assertEquals("\tid14", VaultCleanupJournal.line(clipLeft))
    }

    @Test
    fun aLineCutShortByAKillIsSkippedAndTheRestStillRead() {
        val text = "8\tid13\nnot a line\nx\tid9\n7\t\n\tid14\n8"
        assertEquals(listOf(filmLeft, clipLeft), VaultCleanupJournal.parse(text))
        assertTrue(VaultCleanupJournal.parse("").isEmpty())
    }

    @Test
    fun theSameVideoNotedTwiceIsCleanedOnce() {
        val line = VaultCleanupJournal.line(filmLeft) + "\n"
        assertEquals(listOf(filmLeft), VaultCleanupJournal.parse(line + line))
    }

    @Test
    fun aFinishedCleanupLeavesOnlyWhatWasAddedMeanwhile() {
        val late = VaultLeftover("id20", 5)
        val text = listOf(filmLeft, clipLeft, late).joinToString("") { VaultCleanupJournal.line(it) + "\n" }
        val left = VaultCleanupJournal.without(text, listOf(filmLeft, clipLeft))
        assertEquals(listOf(late), VaultCleanupJournal.parse(left))
        assertEquals("", VaultCleanupJournal.without(text, listOf(filmLeft, clipLeft, late)))
    }

    @Test
    fun runningTheCleanupAgainFindsNothingMoreToDo() {
        // After a first run the file is no longer free to clean and the title is gone: the rules name nothing.
        assertTrue(VaultDeleteRules.freeFileIds(listOf("id13"), setOf("id13")).isEmpty())
        assertTrue(VaultDeleteRules.orphanTitles(listOf(8L), emptyMap()).isEmpty())
    }
}
