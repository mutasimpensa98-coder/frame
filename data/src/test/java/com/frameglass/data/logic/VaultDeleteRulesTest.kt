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

    @Test
    fun aTitleWithNothingLeftAnywhereIsForgotten() {
        val orphans = VaultDeleteRules.orphanTitles(listOf(first, second, film), mapOf(5L to 0, 8L to 0), mapOf(5L to 0, 8L to 0))
        assertEquals(setOf(5L, 8L), orphans)
    }

    @Test
    fun aTitleWithAnotherPrivateVideoOrALibraryFileIsKept() {
        // One episode deleted, another still private.
        assertTrue(VaultDeleteRules.orphanTitles(listOf(first), mapOf(5L to 1), mapOf(5L to 0)).isEmpty())
        // The private copy deleted, a copy still in the library.
        assertTrue(VaultDeleteRules.orphanTitles(listOf(film), mapOf(8L to 0), mapOf(8L to 2)).isEmpty())
    }

    @Test
    fun aClipWithNoTitleLeavesNoTitleToForget() {
        assertTrue(VaultDeleteRules.orphanTitles(listOf(clip), emptyMap(), emptyMap()).isEmpty())
    }

    @Test
    fun theLeftoversOfAFileGoUnlessACopyOfItStays() {
        val twin = stored(7, file(17, "t:8", titleId = 8).copy(fileId = film.file.fileId))
        assertEquals(setOf("id13", "id14"), VaultDeleteRules.freeFileIds(listOf(film, clip), listOf(first), emptySet()))
        // The same file is still in the folder under another row.
        assertEquals(setOf("id14"), VaultDeleteRules.freeFileIds(listOf(film, clip), listOf(twin), emptySet()))
        // The same file is still in the library.
        assertEquals(setOf("id13"), VaultDeleteRules.freeFileIds(listOf(film, clip), emptyList(), setOf("id14")))
    }
}
