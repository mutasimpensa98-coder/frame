package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.FolderSort
import com.frameglass.data.api.MatchState
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

/** Folders as one flat list (owner request of 2026-11-22), with invented names only. */
class FlatFoldersTest {
    private val internal = "/storage/emulated/0"
    private val shown = TitleRow(
        id = 1, kind = TitleKind.MOVIE, name = "shown", year = null, coverPath = null, framePath = null,
        backdropPath = null, logoPath = null, dominantColor = null, collectionId = null,
        matchState = MatchState.UNMATCHED, sortKey = "shown", createdAt = 0,
    )

    private var nextRow = 0L

    private fun video(
        rel: String,
        size: Long = 10,
        written: Long = 0,
        volume: String = "primary",
        titleId: Long = 1,
        available: Boolean = true,
    ): MediaFile {
        val id = ++nextRow
        return file(
            id, "f:$id", relPath = rel, size = size, volume = volume, titleId = titleId, available = available,
            nameKind = NameKinds.MOVIE,
        ).copy(mtime = written)
    }

    private fun snapshot(
        files: List<MediaFile>,
        volumes: List<Volume> = listOf(
            Volume("primary", "Internal", false, 0, internal),
            Volume("usb", "SSD", true, 0, "/storage/AAAA-BBBB"),
        ),
    ) = Snapshot(
        titleRows = listOf(shown),
        files = files,
        episodes = emptyList(),
        watch = emptyMap(),
        ratings = emptyList(),
        genreLinks = emptyList(),
        collections = emptyMap(),
        volumes = volumes,
        roots = emptyList(),
        settings = AppSettings(),
        now = TimeUnit.DAYS.toMillis(100),
        mounted = setOf("primary"),
    )

    private fun flat(files: List<MediaFile>, sort: FolderSort = FolderSort.NAME): List<FolderEntry> =
        FlatFolders.build(snapshot(files), sort).entries

    private fun key(name: String): String = SearchText.clean(name.lowercase())

    private fun find(files: List<MediaFile>, query: String, under: String? = null, deep: Boolean = true) =
        FlatFolders.search(snapshot(files), key(query).split(' ').filter { it.isNotEmpty() }, under, deep, ::key).entries

    @Test
    fun onlyFoldersThatHoldVideosDirectlyAreListed() {
        val entries = flat(
            listOf(
                video("DCIM/Camera/a.mp4"),
                video("DCIM/Camera/b.mp4"),
                video("Movies/Show/Season 1/e1.mkv"),
                video("Movies/film.mkv"),
                video("top.mp4"),
            ),
        )
        // DCIM and Show hold only folders, so they have no row. The top of the volume holds a video, so it has one.
        assertEquals(listOf("Camera", "Internal", "Movies", "Season 1"), entries.map { it.name })
        assertEquals(
            listOf("$internal/DCIM/Camera", internal, "$internal/Movies", "$internal/Movies/Show/Season 1"),
            entries.map { it.path },
        )
        assertTrue(entries.all { it.isDirectory && it.direct && it.play == null })
    }

    @Test
    fun aRowCountsAndWeighsItsOwnVideosOnly() {
        val byName = flat(
            listOf(video("Movies/film.mkv", size = 5), video("Movies/Sub/a.mkv", size = 7), video("Movies/Sub/b.mkv", size = 8)),
        ).associateBy { it.name }
        assertEquals(1, byName.getValue("Movies").videoCount)
        assertEquals(5, byName.getValue("Movies").sizeBytes)
        assertEquals(2, byName.getValue("Sub").videoCount)
        assertEquals(15, byName.getValue("Sub").sizeBytes)
    }

    @Test
    fun aNameOfItsOwnHasNoSecondLine() {
        assertTrue(flat(listOf(video("Download/a.mp4"), video("Movies/b.mp4"))).all { it.hint == null })
    }

    @Test
    fun twoFoldersOfOneNameShowWhereTheyAre() {
        val entries = flat(listOf(video("Pictures/Reels/a.mp4"), video("Movies/Reels/b.mp4"), video("Download/c.mp4")))
        assertEquals(listOf("Download", "Reels", "Reels"), entries.map { it.name })
        // The same name is ordered by its path, so the list does not shuffle between two builds.
        assertEquals(listOf(null, "Movies", "Pictures"), entries.map { it.hint })
    }

    @Test
    fun aTopFolderIsToldApartByItsVolume() {
        val entries = flat(listOf(video("Clips/a.mp4"), video("Clips/b.mp4", volume = "usb")))
        assertEquals(listOf("SSD", "Internal"), entries.map { it.hint })
    }

    @Test
    fun aLongParentPathIsCutAtItsStart() {
        val byPath = flat(listOf(video("Android/media/app.invented/Media/Videos/a.mp4"), video("Videos/b.mp4")))
            .associateBy { it.path }
        assertEquals("…/app.invented/Media", byPath.getValue("$internal/Android/media/app.invented/Media/Videos").hint)
        assertEquals("Internal", byPath.getValue("$internal/Videos").hint)
    }

    @Test
    fun twoPathsThatEndTheSameGetTheWholePath() {
        val entries = flat(listOf(video("A/X/Y/Clips/a.mp4"), video("B/X/Y/Clips/b.mp4")))
        assertEquals(listOf("Internal/A/X/Y", "Internal/B/X/Y"), entries.map { it.hint })
    }

    @Test
    fun namesThatDifferOnlyInCaseAreTheSameName() {
        val entries = flat(listOf(video("One/Clips/a.mp4"), video("Two/clips/b.mp4")))
        assertEquals(listOf("One", "Two"), entries.map { it.hint })
    }

    @Test
    fun whatTheLibraryDoesNotShowHasNoRow() {
        val files = listOf(
            video("Kept/a.mp4"),
            // The file of a title the owner hid, a file of a volume the database lost, and one with no path to open.
            video("Hidden/b.mp4", titleId = 2),
            video("Lost/c.mp4", volume = "gone"),
            video("NoPath/d.mp4", volume = "blank"),
        )
        val volumes = listOf(Volume("primary", "Internal", false, 0, internal), Volume("blank", "Card", true, 0, null))
        assertEquals(listOf("Kept"), FlatFolders.build(snapshot(files, volumes)).entries.map { it.name })
    }

    @Test
    fun aFolderOfADriveThatIsOutIsDimmed() {
        val entries = flat(
            listOf(
                video("Gone/a.mp4", volume = "usb", available = false),
                video("Half/b.mp4", volume = "usb"),
                video("Half/c.mp4", volume = "usb", available = false),
            ),
        )
        assertTrue(entries.first { it.name == "Gone" }.unavailable)
        assertFalse(entries.first { it.name == "Half" }.unavailable)
    }

    @Test
    fun videosGoneFromThePhoneAreNotListed() {
        val files = listOf(video("Gone/old.mp4", available = false), video("Half/b.mp4"), video("Half/c.mp4", available = false))
        val half = flat(files).single()
        assertEquals("Half", half.name)
        assertEquals(1, half.videoCount)
        assertFalse(half.unavailable)
        assertTrue(find(files, "gone").isEmpty())
        assertTrue(find(files, "old").isEmpty())
        assertEquals(listOf("b.mp4"), FlatFolders.videos("$internal/Half", snapshot(files)).entries.map { it.name })
    }

    @Test
    fun theDefaultOrderIsByNameInNaturalOrder() {
        val entries = flat(listOf(video("Part 10/a.mp4"), video("part 2/b.mp4"), video("Alpha/c.mp4")))
        assertEquals(listOf("Alpha", "part 2", "Part 10"), entries.map { it.name })
    }

    @Test
    fun theOtherOrdersPutTheMostFirstAndBreakTiesByName() {
        val files = listOf(
            video("Big/a.mp4", size = 900, written = 10),
            video("Many/b.mp4", size = 10, written = 30),
            video("Many/c.mp4", size = 10, written = 20),
            video("Many/d.mp4", size = 10, written = 5),
            video("Fresh/e.mp4", size = 30, written = 99),
            video("Also/f.mp4", size = 30, written = 10),
        )
        assertEquals(listOf("Big", "Also", "Fresh", "Many"), flat(files, FolderSort.SIZE).map { it.name })
        assertEquals(listOf("Many", "Also", "Big", "Fresh"), flat(files, FolderSort.COUNT).map { it.name })
        // A folder is as new as its newest video.
        assertEquals(listOf("Fresh", "Many", "Also", "Big"), flat(files, FolderSort.NEWEST).map { it.name })
    }

    @Test
    fun aRowOpensItsOwnVideosAndNothingOfTheFoldersInside() {
        val s = snapshot(listOf(video("Movies/b 10.mkv"), video("Movies/b 2.mkv"), video("Movies/Sub/c.mkv"), video("Other/d.mkv")))
        val view = FlatFolders.videos("$internal/Movies", s)
        assertEquals("Movies", view.name)
        assertEquals(listOf("b 2.mkv", "b 10.mkv"), view.entries.map { it.name })
        assertEquals(listOf("$internal/Movies/b 2.mkv", "$internal/Movies/b 10.mkv"), view.entries.map { it.path })
        assertTrue(view.entries.all { !it.isDirectory && !it.direct && it.play?.fileRowId != null })
    }

    @Test
    fun theTopOfAVolumeOpensUnderTheVolumesName() {
        val view = FlatFolders.videos(internal, snapshot(listOf(video("top.mp4"), video("Movies/a.mkv"))))
        assertEquals("Internal", view.name)
        assertEquals(listOf("top.mp4"), view.entries.map { it.name })
    }

    @Test
    fun aPathNoVolumeHoldsOpensEmpty() {
        val view = FlatFolders.videos("/elsewhere/Clips", snapshot(listOf(video("Clips/a.mp4"))))
        assertEquals("Clips", view.name)
        assertTrue(view.entries.isEmpty())
    }

    @Test
    fun aSearchListsFoldersThenVideosThatHoldEveryWord() {
        val files = listOf(
            video("Trips/Sea Trip/day one.mp4"),
            video("Trips/Sea Trip/day two.mp4"),
            video("Trips/Hill/sea view.mp4"),
            video("Work/notes.mp4"),
        )
        val found = find(files, "sea")
        assertEquals(listOf("Sea Trip", "sea view.mp4"), found.map { it.name })
        assertTrue(found.first().isDirectory && found.first().direct)
        assertEquals(2, found.first().videoCount)
        // Every word must be there, in any order and anywhere in the name.
        assertEquals(listOf("day two.mp4"), find(files, "two DAY").map { it.name })
        assertEquals(listOf("sea view.mp4"), find(files, "ie se").map { it.name })
        assertTrue(find(files, "sea notes").isEmpty())
    }

    @Test
    fun aBlankSearchFindsNothing() {
        val files = listOf(video("Trips/a.mp4"))
        assertTrue(find(files, "").isEmpty())
        assertTrue(find(files, "  .. ").isEmpty())
    }

    @Test
    fun aSearchInsideAFolderStaysInsideIt() {
        val files = listOf(
            video("Trips/Sea/clip sea.mp4"),
            video("Trips/Sea/Old/sea again.mp4"),
            video("Trips/sea top.mp4"),
            video("Other/Sea/sea elsewhere.mp4"),
        )
        // Below Trips: its folders that match, never Trips itself, and nothing of Other.
        assertEquals(
            listOf("$internal/Trips/Sea", "$internal/Trips/Sea/clip sea.mp4", "$internal/Trips/Sea/Old/sea again.mp4", "$internal/Trips/sea top.mp4"),
            find(files, "sea", under = "$internal/Trips").map { it.path },
        )
        assertTrue(find(files, "trips", under = "$internal/Trips").isEmpty())
        // A page of one folder's own videos searches those alone, and lists no folder.
        assertEquals(listOf("clip sea.mp4"), find(files, "sea", under = "$internal/Trips/Sea", deep = false).map { it.name })
        assertTrue(find(files, "sea", under = "/elsewhere").isEmpty())
    }

    @Test
    fun aFoundFolderKeepsTheSecondLineItHasInTheList() {
        val files = listOf(video("Pictures/Reels/a.mp4"), video("Movies/Reels/b.mp4"))
        assertEquals(listOf("Movies", "Pictures"), find(files, "reels").map { it.hint })
    }

    @Test
    fun aSearchIsCutAtItsLimit() {
        val files = (1..FlatFolders.MAX_FOUND + 20).map { video("Clips/clip $it.mp4") }
        val found = find(files, "clip")
        assertEquals(1 + FlatFolders.MAX_FOUND, found.size)
        assertEquals("Clips", found.first().name)
        assertEquals("clip 1.mp4", found[1].name)
    }

    @Test
    fun theTreeStillListsFoldersInsideFoldersAndIsNotMarkedDirect() {
        val s = snapshot(listOf(video("DCIM/Camera/a.mp4"), video("Movies/b.mkv")))
        val tree = FolderBuilder.build(null, s).entries
        assertEquals(listOf("DCIM", "Movies"), tree.map { it.name })
        assertTrue(tree.none { it.direct })
        assertNull(tree.first().hint)
    }
}
