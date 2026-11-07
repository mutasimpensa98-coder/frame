package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.LibraryFilter
import com.frameglass.data.api.LibrarySort
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.MediaEntry
import com.frameglass.data.api.MediaEntryKind
import com.frameglass.data.api.MediaGroup
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.TitleRow
import com.frameglass.data.db.Volume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

private const val INTERNAL = "/storage/emulated/0"

private fun row(id: Long, name: String = "t$id", kind: TitleKind = TitleKind.MOVIE, state: MatchState = MatchState.AUTO, favorite: Boolean = false) = TitleRow(
    id = id, kind = kind, name = name, year = null, coverPath = "/c$id", framePath = null, backdropPath = null, logoPath = null,
    dominantColor = null, collectionId = null, matchState = state, sortKey = name, createdAt = 0, favorite = favorite,
)

private fun video(rowId: Long, titleId: Long, relPath: String, size: Long, available: Boolean = true, nameKind: String = NameKinds.MOVIE): MediaFile =
    file(rowId, "t:$titleId", titleId = titleId, relPath = relPath, size = size, available = available, nameKind = nameKind)

private fun snapshot(titles: List<TitleRow>, files: List<MediaFile>, hidden: List<TitleRow> = emptyList()) = Snapshot(
    titleRows = titles,
    files = files,
    episodes = emptyList(),
    watch = emptyMap(),
    ratings = emptyList(),
    genreLinks = emptyList(),
    collections = emptyMap(),
    volumes = listOf(Volume("primary", "Internal", false, 0, INTERNAL), Volume("usb", "SSD", true, 0, "/storage/AAAA")),
    roots = emptyList(),
    settings = AppSettings(),
    now = TimeUnit.DAYS.toMillis(100),
    mounted = setOf("primary"),
    hiddenRows = hidden,
)

private fun List<MediaEntry>.names() = map { it.name }

class StorageSummaryTest {
    @Test
    fun countsOnlyWhatIsOnStorage() {
        val s = snapshot(
            listOf(row(1), row(2)),
            listOf(video(1, 1, "Movies/a.mkv", 100), video(2, 1, "Movies/b.mkv", 50), video(3, 2, "Other/c.mkv", 7, available = false)),
        )
        val summary = MediaViews.summary(s, deviceTotal = 1000, deviceFree = 400)
        assertEquals(150, summary.videoBytes)
        assertEquals(2, summary.videoCount)
        assertEquals(1, summary.folderCount)
        assertEquals(1000, summary.deviceTotalBytes)
        assertEquals(400, summary.deviceFreeBytes)
    }

    @Test
    fun aHiddenTitleStillHoldsItsSpace() {
        val s = snapshot(listOf(row(1)), listOf(video(1, 1, "a.mkv", 100), video(2, 2, "b.mkv", 40)), hidden = listOf(row(2)))
        assertEquals(140, MediaViews.summary(s, 0, 0).videoBytes)
    }

    @Test
    fun sameFolderNameOnTwoDrivesIsTwoFolders() {
        val files = listOf(video(1, 1, "Movies/a.mkv", 1), video(2, 1, "Movies/b.mkv", 1).copy(volumeUuid = "usb"))
        assertEquals(2, MediaViews.summary(snapshot(listOf(row(1)), files), 0, 0).folderCount)
    }
}

class MediaEntriesTest {
    private val titles = listOf(row(1, "Big"), row(2, "Small"))

    @Test
    fun foldersComeBiggestFirstWithTheirOwnFilesOnly() {
        val s = snapshot(
            titles,
            listOf(
                video(1, 1, "Movies/a.mkv", 100),
                video(2, 1, "Movies/Sub/b.mkv", 500),
                video(3, 2, "c.mkv", 1),
            ),
        )
        val folders = MediaViews.entries(s, MediaGroup.FOLDERS, "Clips")
        assertEquals(listOf("Sub", "Movies", "Internal"), folders.names())
        assertEquals(listOf(500L, 100L, 1L), folders.map { it.sizeBytes })
        assertEquals("$INTERNAL/Movies/Sub", folders[0].folderPath)
        assertEquals(INTERNAL, folders[2].folderPath)
        assertEquals(setOf("a.mkv"), MediaViews.children(s, folders[1]).names().toSet())
    }

    @Test
    fun largestVideosAreCappedAndSorted() {
        val files = (1L..(MediaViews.LARGEST + 5L)).map { video(it, 1, "v$it.mkv", it) }
        val largest = MediaViews.entries(snapshot(titles, files), MediaGroup.LARGEST_VIDEOS, "Clips")
        assertEquals(MediaViews.LARGEST, largest.size)
        assertEquals(MediaViews.LARGEST + 5L, largest.first().sizeBytes)
        assertEquals(MediaEntryKind.FILE, largest.first().kind)
    }

    @Test
    fun titlesAddUpEveryFileAndLeaveClipsOut() {
        val s = snapshot(
            listOf(row(1, "Show", TitleKind.SERIES), row(2, "Film"), row(3, "Clip", state = MatchState.UNMATCHED)),
            listOf(
                video(1, 1, "S/e1.mkv", 10),
                video(2, 1, "S/e2.mkv", 20),
                video(3, 1, "S/NCOP.mkv", 5, nameKind = NameKinds.EXTRA),
                video(4, 2, "F/film.mkv", 30),
                video(5, 3, "DCIM/c.mp4", 1000, nameKind = NameKinds.CLIP),
            ),
        )
        val entries = MediaViews.entries(s, MediaGroup.TITLES, "Clips")
        assertEquals(listOf("Show", "Film"), entries.names())
        assertEquals(35, entries[0].sizeBytes)
        assertEquals(3, entries[0].fileCount)
        assertEquals("$INTERNAL/S", entries[0].folderPath)
    }

    @Test
    fun aTitleAcrossFoldersNamesNoFolder() {
        val s = snapshot(listOf(row(1)), listOf(video(1, 1, "A/a.mkv", 1), video(2, 1, "B/b.mkv", 1)))
        assertNull(MediaViews.entries(s, MediaGroup.TITLES, "Clips").single().folderPath)
    }

    @Test
    fun clipsAreOneGroupWithChildren() {
        val s = snapshot(
            listOf(row(1, "Film"), row(2, "c2", state = MatchState.UNMATCHED), row(3, "c3", state = MatchState.UNMATCHED)),
            listOf(
                video(1, 1, "F/film.mkv", 9000),
                video(2, 2, "DCIM/c2.mp4", 2, nameKind = NameKinds.CLIP),
                video(3, 3, "DCIM/c3.mp4", 3, nameKind = NameKinds.CLIP),
            ),
        )
        val group = MediaViews.entries(s, MediaGroup.CLIPS, "مقاطع").single()
        assertEquals(MediaEntryKind.CLIP_GROUP, group.kind)
        assertEquals("مقاطع", group.name)
        assertEquals(5, group.sizeBytes)
        assertEquals(2, group.fileCount)
        assertEquals(listOf("c3.mp4", "c2.mp4"), MediaViews.children(s, group).names())
    }

    @Test
    fun noClipsMeansNoGroup() {
        assertTrue(MediaViews.entries(snapshot(titles, listOf(video(1, 1, "a.mkv", 1))), MediaGroup.CLIPS, "Clips").isEmpty())
    }

    @Test
    fun aMissingFileIsNotListed() {
        val s = snapshot(titles, listOf(video(1, 1, "Movies/a.mkv", 100, available = false)))
        MediaGroup.entries.forEach { assertTrue(MediaViews.entries(s, it, "Clips").isEmpty()) }
    }

    @Test
    fun aFileHasNoChildren() {
        val s = snapshot(titles, listOf(video(1, 1, "a.mkv", 1)))
        val entry = MediaViews.entries(s, MediaGroup.LARGEST_VIDEOS, "Clips").single()
        assertTrue(MediaViews.children(s, entry).isEmpty())
    }
}

class DeleteExpansionTest {
    private val s = snapshot(
        listOf(row(1)),
        listOf(
            video(1, 1, "Movies/a.mkv", 100),
            video(2, 1, "Movies/b.mkv", 50),
            video(3, 1, "Movies/Sub/c.mkv", 7),
            video(4, 1, "Movies/gone.mkv", 9, available = false),
        ),
    )

    private fun folder(name: String) = MediaViews.entries(s, MediaGroup.FOLDERS, "Clips").first { it.name == name }

    @Test
    fun aFolderTakesOnlyItsOwnFilesNeverASubfolder() {
        assertEquals(listOf(1L, 2L), MediaViews.expand(s, listOf(folder("Movies"))).map { it.rowId })
    }

    @Test
    fun aFileChosenTwiceCountsOnce() {
        val fileEntry = MediaViews.children(s, folder("Movies")).first { it.name == "a.mkv" }
        val title = MediaViews.entries(s, MediaGroup.TITLES, "Clips").single()
        val rows = MediaViews.expand(s, listOf(folder("Movies"), fileEntry, title)).map { it.rowId }
        assertEquals(listOf(1L, 2L, 3L), rows)
    }

    @Test
    fun aRowThatChangedSincePlanningIsNotExpandedFromAStaleEntry() {
        val entry = MediaViews.children(s, folder("Movies")).first { it.name == "a.mkv" }
        val later = snapshot(listOf(row(1)), listOf(video(2, 1, "Movies/b.mkv", 50)))
        assertTrue(MediaViews.expand(later, listOf(entry)).isEmpty())
    }
}

class DeletePlannerTest {
    private fun video(id: Long, size: Long, recoverable: Boolean = true) =
        DeleteTarget("/s/M/v$id.mkv", "v$id.mkv", "/s/M", size, id, listOf(id), recoverable)

    private fun sidecar(name: String, size: Long, recoverable: Boolean = false, owners: List<Long> = listOf(1)) =
        DeleteTarget("/s/M/$name", name, "/s/M", size, null, owners, recoverable)

    @Test
    fun videosAndSidecarsAreTotalledApart() {
        val plan = DeletePlanner.plan(listOf(video(1, 100), video(2, 50)), listOf(sidecar("v1.srt", 3), sidecar("v2.ass", 4)), true)
        assertEquals(2, plan.fileCount)
        assertEquals(150, plan.totalBytes)
        assertEquals(2, plan.sidecarCount)
        assertEquals(7, plan.sidecarBytes)
        assertTrue(plan.includeSidecars)
    }

    @Test
    fun theDialogCanOfferSidecarsThatAreNotTaken() {
        val plan = DeletePlanner.plan(listOf(video(1, 100)), listOf(sidecar("v1.srt", 3)), false)
        assertEquals(1, plan.sidecarCount)
        assertEquals(3, plan.sidecarBytes)
        assertEquals(listOf("v1.mkv"), plan.entries.map { it.name })
        assertFalse(plan.includeSidecars)
    }

    @Test
    fun everyFileThatWouldGoIsEitherRecoverableOrPermanent() {
        val videos = listOf(video(1, 1, recoverable = true), video(2, 1, recoverable = false))
        val sidecars = listOf(sidecar("a.srt", 1, recoverable = false), sidecar("b.srt", 1, recoverable = true))
        val taken = DeletePlanner.plan(videos, sidecars, true)
        assertEquals(2, taken.recoverableCount)
        assertEquals(2, taken.permanentCount)
        val left = DeletePlanner.plan(videos, sidecars, false)
        assertEquals(1, left.recoverableCount)
        assertEquals(1, left.permanentCount)
    }

    @Test
    fun anEmptyPlanIsAllZero() {
        val plan = DeletePlanner.plan(emptyList(), emptyList(), true)
        assertEquals(0, plan.fileCount)
        assertEquals(0, plan.totalBytes)
        assertEquals(0, plan.recoverableCount)
        assertEquals(0, plan.permanentCount)
        assertTrue(plan.entries.isEmpty())
    }

    @Test
    fun sampleNamesStopAtFiveAndAreVideos() {
        val plan = DeletePlanner.plan((1L..9L).map { video(it, 1) }, listOf(sidecar("x.srt", 1)), true)
        assertEquals(DeletePlanner.SAMPLE_NAMES, plan.sampleNames.size)
        assertEquals("v1.mkv", plan.sampleNames.first())
    }

    @Test
    fun aPlanEntryReadsBackExactly() {
        val targets = listOf(video(7, 100, recoverable = false), sidecar("v7.ar.srt", 3, recoverable = true, owners = listOf(7, 8)))
        val plan = DeletePlanner.plan(targets.take(1), targets.drop(1), true)
        assertEquals(targets, plan.entries.map { DeletePlanner.decode(it) })
    }

    @Test
    fun onlySidecarEntriesCountAsSidecars() {
        val plan = DeletePlanner.plan(listOf(video(1, 1)), listOf(sidecar("x.srt", 1)), true)
        assertEquals(listOf(false, true), plan.entries.map { DeletePlanner.isSidecar(it) })
    }

    @Test
    fun whatPlanDeleteDidNotWriteIsNeverATarget() {
        val s = snapshot(listOf(row(1)), listOf(video(1, 1, "Movies/a.mkv", 100)))
        val folder = MediaViews.entries(s, MediaGroup.FOLDERS, "Clips").single()
        val file = MediaViews.children(s, folder).single()
        assertNull(DeletePlanner.decode(folder))
        assertNull(DeletePlanner.decode(file))
    }

    @Test
    fun aPlannedVideoCanBePlannedAgainWhenTheOwnerChangesTheSidecarChoice() {
        val s = snapshot(listOf(row(1)), listOf(video(1, 1, "Movies/a.mkv", 100)))
        val plan = DeletePlanner.plan(listOf(DeleteTarget("$INTERNAL/Movies/a.mkv", "a.mkv", "$INTERNAL/Movies", 100, 1, listOf(1), true)), listOf(sidecar("a.srt", 1)), true)
        assertEquals(listOf(1L), MediaViews.expand(s, plan.entries).map { it.rowId })
    }
}

class FavoritesFilterTest {
    @Test
    fun theFilterHoldsFavoritesOnly() {
        val s = snapshot(
            listOf(row(1, favorite = true), row(2)),
            listOf(video(1, 1, "a.mkv", 1), video(2, 2, "b.mkv", 1)),
        )
        assertEquals(listOf(1L), TitleLists.library(s, LibraryFilter.FAVORITES, LibrarySort.NAME).map { it.id })
    }

    @Test
    fun aFavoriteWithNothingToShowFollowsTheLibraryRule() {
        val s = snapshot(listOf(row(1, favorite = true)), listOf(video(1, 1, "a.mkv", 1, available = false)))
        assertTrue(TitleLists.library(s, LibraryFilter.FAVORITES, LibrarySort.NAME).isEmpty())
    }
}

class HiddenTitlesTest {
    @Test
    fun aHiddenTitleStaysOutOfTheLibrary() {
        val s = snapshot(listOf(row(1)), listOf(video(1, 1, "a.mkv", 1), video(2, 2, "b.mkv", 1)), hidden = listOf(row(2)))
        assertEquals(listOf(1L), TitleLists.library(s, LibraryFilter.ALL, LibrarySort.NAME).map { it.id })
        assertEquals(setOf(2L), s.hiddenInfos.keys)
    }

    @Test
    fun aHiddenTitleWithNoFileLeftHasNothingToBringBack() {
        val s = snapshot(emptyList(), emptyList(), hidden = listOf(row(2)))
        assertTrue(s.hiddenInfos.isEmpty())
    }
}

class FileFactsTest {
    @Test
    fun everyFileOfTheTitleWithItsRealFacts() {
        val first = video(1, 1, "Show/e2.mkv", 100).copy(
            width = 1920, height = 1080, videoCodec = "hevc", hdrType = "HDR10", audioSummary = "AAC 2.0", mtime = 55, hasSidecarSub = true,
        )
        val extra = video(2, 1, "Show/NCOP.mkv", 5, available = false, nameKind = NameKinds.EXTRA)
        val other = video(3, 2, "Other/x.mkv", 1)
        val facts = FileFactsBuilder.of(snapshot(listOf(row(1), row(2)), listOf(first, extra, other)), 1)
        assertEquals(listOf("e2.mkv", "NCOP.mkv"), facts.map { it.name })
        val e2 = facts[0]
        assertEquals("$INTERNAL/Show/e2.mkv", e2.path)
        assertEquals("$INTERNAL/Show", e2.folder)
        assertEquals(1920, e2.width)
        assertEquals("hevc", e2.videoCodec)
        assertEquals("HDR10", e2.hdrType)
        assertEquals("AAC 2.0", e2.audioSummary)
        assertEquals(55, e2.modifiedAtMs)
        assertTrue(e2.hasSubtitle)
        assertTrue(e2.available)
        assertFalse(facts[1].available)
    }
}

class TrashIndexTest {
    @Test
    fun aPathFindsItsRowWhateverItsCase() {
        assertEquals(mapOf("/s/a.mp4" to 1), TrashIndex.byPath(listOf("/s/A.mp4" to 1)))
    }

    @Test
    fun twoFilesThatDifferOnlyByCaseGetNoRow() {
        val index = TrashIndex.byPath(listOf("/usb/A.mp4" to 1, "/usb/a.mp4" to 2, "/usb/b.mp4" to 3))
        assertEquals(mapOf("/usb/b.mp4" to 3), index)
    }
}
