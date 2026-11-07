package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.db.RootFolder
import com.frameglass.data.scan.ScanProgress
import com.frameglass.parser.NameParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScanRootsTest {
    private val mounted = listOf("primary", "usb")
    private val movies = RootFolder(5, "primary", "Movies", 1)

    @Test
    fun everyMountedVolumeIsSearchedWithNoFolderChoice() {
        val plan = ScanRoots.plan(emptyList(), mounted, wholePhone = true)
        assertEquals(mounted, plan.map { it.volumeUuid })
        assertTrue(plan.all { it.relPath.isEmpty() })
    }

    @Test
    fun aFolderAddedByHandKeepsItsOwnRun() {
        val plan = ScanRoots.plan(listOf(movies), mounted, wholePhone = true)
        assertEquals(listOf("", "", "Movies"), plan.map { it.relPath })
        assertEquals(5L, plan.last().id)
    }

    @Test
    fun automaticRootsCannotBeMistakenForAddedOnes() {
        val plan = ScanRoots.plan(listOf(movies), mounted, wholePhone = true)
        assertTrue(plan.filter { it.relPath.isEmpty() }.none { it.id == movies.id })
    }

    @Test
    fun aVolumeAddedByHandIsNotWalkedTwice() {
        val whole = RootFolder(6, "primary", "", 1)
        val plan = ScanRoots.plan(listOf(whole), mounted, wholePhone = true)
        assertEquals(listOf("primary", "usb"), plan.map { it.volumeUuid }.sorted())
    }

    @Test
    fun withWholePhoneOffOnlyAddedFoldersRun() {
        assertEquals(listOf(movies), ScanRoots.plan(listOf(movies), mounted, wholePhone = false))
    }

    @Test
    fun coversOnlyWhatLiesInsideAnAddedFolder() {
        assertTrue(ScanRoots.covers(listOf(movies), "primary", "Movies/a.mkv"))
        assertFalse(ScanRoots.covers(listOf(movies), "primary", "Movies2/a.mkv"))
        assertFalse(ScanRoots.covers(listOf(movies), "usb", "Movies/a.mkv"))
        assertTrue(ScanRoots.covers(listOf(RootFolder(6, "usb", "", 1)), "usb", "x/a.mkv"))
    }
}

class ScanRulesTest {
    @Test
    fun theWalkSkipsAndroidHiddenFoldersAndJunk() {
        assertFalse(ScanRules.walks("Android", "Android", hidden = false))
        assertFalse(ScanRules.walks(".hidden", ".hidden", hidden = false))
        assertFalse(ScanRules.walks("Movies/cache", "cache", hidden = false))
        assertFalse(ScanRules.walks("DCIM/.thumbnails", ".thumbnails", hidden = false))
        assertFalse(ScanRules.walks("Pictures/Thumbnails", "Thumbnails", hidden = false))
        assertFalse(ScanRules.walks("LOST.DIR", "LOST.DIR", hidden = false))
        assertTrue(ScanRules.walks("Movies", "Movies", hidden = false))
        assertTrue(ScanRules.walks("DCIM/Camera", "Camera", hidden = false))
    }

    @Test
    fun onlyTheAndroidFolderAtTheVolumeRootIsSkipped() {
        assertTrue(ScanRules.walks("Movies/Android", "Android", hidden = false))
    }

    @Test
    fun theHiddenSettingOpensAndroidAndHiddenFoldersButNotJunk() {
        assertTrue(ScanRules.walks("Android", "Android", hidden = true))
        assertTrue(ScanRules.walks(".hidden", ".hidden", hidden = true))
        assertFalse(ScanRules.walks("Movies/.cache", ".cache", hidden = true))
        assertFalse(ScanRules.walks("Android/data/x/cache", "cache", hidden = true))
        assertFalse(ScanRules.walks("DCIM/.thumbnails", ".thumbnails", hidden = true))
    }

    @Test
    fun appCachesAreJunkHoweverTheyAreSpelled() {
        listOf("cache", "Cache", ".cache", "caches", "video_cache", "image-cache", "MediaCache", "exoplayercache", "WhatsApp Animated Gifs").forEach {
            assertTrue(it, ScanRules.isJunkFolder(it))
        }
        listOf("Movies", "Cached Stories", "Thumbs Up", "Trash Humpers").forEach { assertFalse(it, ScanRules.isJunkFolder(it)) }
    }

    @Test
    fun mediaStoreMayBringInAppMediaOnly() {
        assertTrue(ScanRules.indexAdmits("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video", hidden = false))
        assertTrue(ScanRules.indexAdmits("Android/media/org.telegram.messenger/Telegram/Telegram Video", hidden = false))
        assertFalse(ScanRules.indexAdmits("Android/data/com.example/files", hidden = false))
        assertFalse(ScanRules.indexAdmits("Android/obb/com.example", hidden = false))
        assertFalse(ScanRules.indexAdmits("Android", hidden = false))
    }

    @Test
    fun mediaStoreCannotBringInWhatTheWalkSkips() {
        assertFalse(ScanRules.indexAdmits("Movies/.private", hidden = false))
        assertFalse(ScanRules.indexAdmits("Android/media/com.whatsapp/WhatsApp/Media/.Statuses", hidden = false))
        assertFalse(ScanRules.indexAdmits("Movies/.thumbnails", hidden = true))
        assertFalse(ScanRules.indexAdmits("Android/media/com.app/cache", hidden = false))
        assertTrue(ScanRules.indexAdmits("Movies/.private", hidden = true))
        assertTrue(ScanRules.indexAdmits("DCIM/Camera", hidden = false))
    }

    @Test
    fun filesAtTheVolumeRootAreAdmitted() = assertTrue(ScanRules.indexAdmits("", hidden = false))

    @Test
    fun appMediaIsOnlyWhatLiesUnderIt() {
        assertTrue(ScanRules.isAppMedia("Android/media/com.whatsapp/x.mp4"))
        assertFalse(ScanRules.isAppMedia("Android/data/com.whatsapp/x.mp4"))
        assertFalse(ScanRules.isAppMedia("Movies/Android/media/x.mp4"))
    }

    @Test
    fun aPhotoNeedsNoFolderCheckButATorrentFolderDoes() {
        assertTrue(ScanRules.isPlainFile("IMG_0001.JPG"))
        assertFalse(ScanRules.isPlainFile("Show.S01.1080p.WEB-DL.AAC"))
        assertFalse(ScanRules.isPlainFile("Movies"))
    }

    @Test
    fun aFileJoinsTheLibraryWhenItLandedOnThePhone() {
        assertEquals(500L, ScanRules.firstSeen(500, 1_000))
        assertEquals(1_000L, ScanRules.firstSeen(1_000, 1_000))
        assertEquals(1_000L, ScanRules.firstSeen(2_000, 1_000))
        assertEquals(1_000L, ScanRules.firstSeen(0, 1_000))
    }
}

class AutoReachTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun mkdir(rel: String) = File(folder.root, rel).apply { mkdirs() }

    private fun reach(opts: AppSettings = AppSettings()) = AutoReach(folder.root.path, opts)

    @Test
    fun aPlainFolderIsReached() {
        mkdir("Movies/Show")
        assertTrue(reach().reaches("Movies/Show"))
        assertTrue(reach().reaches(""))
    }

    @Test
    fun nomediaBlocksTheFolderAndEverythingBelow() {
        mkdir("Private/Sub")
        File(mkdir("Private"), ".nomedia").createNewFile()
        assertFalse(reach().reaches("Private"))
        assertFalse(reach().reaches("Private/Sub"))
    }

    @Test
    fun theNomediaSettingOpensIt() {
        File(mkdir("Private"), ".nomedia").createNewFile()
        assertTrue(reach(AppSettings(scanNomediaFolders = true)).reaches("Private"))
    }

    @Test
    fun aNomediaAboveAppMediaDoesNotHideWhatAppsPublish() {
        mkdir("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video")
        File(mkdir("Android"), ".nomedia").createNewFile()
        File(mkdir("Android/media"), ".nomedia").createNewFile()
        assertTrue(reach().reaches("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"))
    }

    @Test
    fun aNomediaInsideAnAppFolderStillCounts() {
        File(mkdir("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Sent"), ".nomedia").createNewFile()
        assertFalse(reach().reaches("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Sent"))
    }

    @Test
    fun aNomediaFileAtTheVolumeRootDoesNotHideTheVolume() {
        mkdir("Movies")
        File(folder.root, ".nomedia").createNewFile()
        assertTrue(reach().reaches("Movies"))
    }

    @Test
    fun theSettingStillLeavesCachesOut() {
        mkdir("Movies/cache")
        assertFalse(reach(AppSettings(scanNomediaFolders = true, scanHiddenFolders = true)).reaches("Movies/cache"))
    }
}

class ScanProgressTest {
    @Test
    fun foundCountsUpAndStartsOverOnEachScan() {
        val progress = ScanProgress()
        progress.begin()
        progress.found()
        progress.found(4)
        assertEquals(5, progress.state.value.found)
        progress.begin()
        assertEquals(0, progress.state.value.found)
    }

    @Test
    fun theFolderShowsAtOnceThenThrottles() {
        val progress = ScanProgress()
        progress.begin()
        progress.inFolder("a", 1_000)
        progress.inFolder("b", 1_050)
        assertEquals("a", progress.state.value.currentFolder)
        progress.inFolder("c", 1_200)
        assertEquals("c", progress.state.value.currentFolder)
    }

    @Test
    fun theFolderClearsWhenTheScanEnds() {
        val progress = ScanProgress()
        progress.begin()
        assertTrue(progress.running)
        progress.inFolder("a", 1_000)
        progress.end()
        assertFalse(progress.running)
        assertNull(progress.state.value.currentFolder)
    }
}

/** The parser sees a whole-volume path through ParsePaths, so these hold the two together. */
class WholePhonePathsTest {
    private fun parse(rel: String) = NameParser.parse(ParsePaths.forParser("", rel))

    @Test
    fun aVolumePathKeepsTheThreeNearestFolders() {
        assertEquals("c/d/e/f.mkv", ParsePaths.forParser("", "a/b/c/d/e/f.mkv"))
        assertEquals("a/f.mkv", ParsePaths.forParser("", "a/f.mkv"))
        assertEquals("f.mkv", ParsePaths.forParser("", "f.mkv"))
    }

    @Test
    fun aFolderFarAboveCannotHideAVideoAsAnExtra() {
        val p = parse("Extras/Old/Keep/Movies/The.Matrix.1999.mkv")
        assertEquals("The Matrix", p.title)
        assertEquals("MOVIE", p.kind.name)
    }

    @Test
    fun seriesStillReadThroughTheirFolders() {
        val p = parse("Download/Complete/Show/Season 02/07.mkv")
        assertEquals("Show", p.title)
        assertEquals(2, p.season)
        assertEquals(7, p.episode)
    }

    @Test
    fun whatsAppKeepsItsFolderUnderAndroidMedia() {
        assertTrue(parse("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Funny cat.mp4").clipLike)
    }

    @Test
    fun aTelegramDownloadUnderAndroidMediaIsARealTitle() {
        val p = parse("Android/media/org.telegram.messenger/Telegram/Telegram Video/Quiet.Coast.2026.mkv")
        assertFalse(p.clipLike)
        assertEquals("Quiet Coast", p.title)
    }

    @Test
    fun aCameraRollSplitByDateStaysClips() {
        assertTrue(parse("DCIM/Camera/2024/01/VID_20240101_120000.mp4").clipLike)
    }
}

class ClipKeysTest {
    @Test
    fun clipsGroupByContentNotByName() {
        assertEquals("CLIP|abc", GroupKeys.clip("abc"))
        assertFalse(GroupKeys.clip("abc") == GroupKeys.clip("def"))
    }

    @Test
    fun aClipKeyCannotCollideWithATitle() {
        val titles = com.frameglass.data.api.TitleKind.entries.map { GroupKeys.key(it, "clip", null) }
        assertFalse(GroupKeys.clip("abc") in titles)
    }
}
