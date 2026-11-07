package com.frameglass.data.logic

import com.frameglass.data.db.ExcludedFolder
import com.frameglass.data.db.RootFolder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun excluded(vararg rel: String, volume: String = "primary") = Exclusions(rel.map { ExcludedFolder(volume, it, 0) })

private fun root(rel: String, volume: String = "primary") = RootFolder(id = rel.hashCode().toLong(), volumeUuid = volume, relPath = rel, addedAt = 0)

class PathScopeTest {
    @Test
    fun aFolderHoldsItselfAndWhatIsUnderIt() {
        assertTrue(PathScope.within("Movies", "Movies"))
        assertTrue(PathScope.within("Movies/Anime", "Movies"))
    }

    @Test
    fun aNameThatOnlyStartsTheSameIsAnotherFolder() {
        assertFalse(PathScope.within("Movies2", "Movies"))
        assertFalse(PathScope.within("Movies2/a", "Movies"))
    }

    @Test
    fun caseDoesNotMatter() {
        assertTrue(PathScope.within("movies/anime", "Movies"))
    }

    @Test
    fun anEmptyFolderIsTheWholeVolume() {
        assertTrue(PathScope.within("anything/at/all", ""))
        assertTrue(PathScope.within("", ""))
    }
}

class ExclusionsTest {
    @Test
    fun theWalkStaysOutOfAnExcludedFolderAndBelow() {
        val e = excluded("Movies/Junk")
        assertTrue(e.blocks("primary", "", "Movies/Junk"))
        assertTrue(e.blocks("primary", "", "Movies/Junk/Deep"))
        assertFalse(e.blocks("primary", "", "Movies/Keep"))
    }

    @Test
    fun anExclusionOnOneDriveSaysNothingAboutAnother() {
        assertFalse(excluded("Movies").blocks("usb", "", "Movies"))
    }

    @Test
    fun aFolderAddedInsideAnExclusionWinsForThatFolderOnly() {
        val e = excluded("Movies", "Movies/Anime/Junk")
        assertFalse(e.blocks("primary", "Movies/Anime", "Movies/Anime/Show"))
        assertTrue(e.blocks("primary", "Movies/Anime", "Movies/Anime/Junk"))
        assertTrue(e.blocks("primary", "", "Movies/Anime"))
    }

    @Test
    fun aWholeVolumeCanBeExcluded() {
        val e = excluded("")
        assertTrue(e.wholeVolume("primary"))
        assertFalse(excluded("Movies").wholeVolume("primary"))
    }

    @Test
    fun rowsUnderAnExclusionGo() {
        assertTrue(excluded("Movies/Junk").drops("primary", "Movies/Junk/a.mkv", emptyList()))
        assertTrue(excluded("Movies/Junk").drops("primary", "Movies/Junk/Deep/a.mkv", emptyList()))
    }

    @Test
    fun rowsOutsideStay() {
        val e = excluded("Movies/Junk")
        assertFalse(e.drops("primary", "Movies/a.mkv", emptyList()))
        assertFalse(e.drops("primary", "Movies/Junk2/a.mkv", emptyList()))
        assertFalse(e.drops("usb", "Movies/Junk/a.mkv", emptyList()))
    }

    @Test
    fun anAddedFolderInsideAnExclusionKeepsItsRows() {
        val e = excluded("Movies")
        val roots = listOf(root("Movies/Anime"))
        assertFalse(e.drops("primary", "Movies/Anime/a.mkv", roots))
        assertTrue(e.drops("primary", "Movies/Other/a.mkv", roots))
    }

    @Test
    fun anExclusionInsideAnAddedFolderStillDropsItsRows() {
        assertTrue(excluded("Movies/Anime/Junk").drops("primary", "Movies/Anime/Junk/a.mkv", listOf(root("Movies/Anime"))))
    }

    @Test
    fun nothingExcludedIsEmpty() {
        assertTrue(excluded().isEmpty)
        assertFalse(excluded("A").isEmpty)
    }
}

class StagedNamesTest {
    @Test
    fun trashedAndPendingFilesAreNeverVideos() {
        assertTrue(ScanRules.isStaged(".trashed-1789000000-Movie.mkv"))
        assertTrue(ScanRules.isStaged(".pending-1789000000-Movie.mkv"))
        assertFalse(ScanRules.isStaged("Movie.mkv"))
        assertFalse(ScanRules.isStaged(".hidden.mkv"))
    }
}

class SidecarClaimsTest {
    private val movie = SidecarHit(SidecarFile("/m", "Movie.ar.srt"), "ar", forced = false, signs = false)
    private val shared = SidecarHit(SidecarFile("/m", "S01E01.srt"), null, forced = false, signs = false)

    @Test
    fun aSubtitleOnlyThisVideoReadsGoesWithIt() {
        val claims = SidecarClaims.of(listOf("Movie.mkv"), listOf("Other.mkv")) { if (it == "Movie.mkv") listOf(movie) else emptyList() }
        assertFalse(claims.single().shared)
    }

    @Test
    fun aSubtitleAVideoStayingBehindAlsoReadsIsShared() {
        val claims = SidecarClaims.of(listOf("S01E01.720p.mkv"), listOf("S01E01.1080p.mkv")) { listOf(shared) }
        assertTrue(claims.single().shared)
    }

    @Test
    fun twoMovingVideosOwnASubtitleTogether() {
        val claims = SidecarClaims.of(listOf("a.mkv", "b.mkv"), emptyList()) { listOf(shared) }
        assertFalse(claims.single().shared)
        assertTrue(claims.single().owners.containsAll(listOf("a.mkv", "b.mkv")))
    }

    @Test
    fun noMatchIsNoClaim() {
        assertTrue(SidecarClaims.of(listOf("a.mkv"), listOf("b.mkv")) { emptyList() }.isEmpty())
    }
}
