package com.frameglass.data.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FileIdentityTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun write(name: String, bytes: ByteArray): File = folder.newFile(name).apply { writeBytes(bytes) }

    private fun pattern(size: Int, seed: Int = 7) = ByteArray(size) { ((it * 31 + seed) and 0xFF).toByte() }

    @Test
    fun theSameBytesGiveTheSameIdUnderAnyName() {
        val a = FileIdentity.compute(write("a.mkv", pattern(1_000_000)))
        val b = FileIdentity.compute(write("renamed.mkv", pattern(1_000_000)))
        assertEquals(a, b)
        assertEquals(64, a.fileId.length)
    }

    @Test
    fun aChangeInTheHeadChangesTheId() {
        val bytes = pattern(1_000_000)
        val a = FileIdentity.compute(write("a.mkv", bytes))
        bytes[10] = (bytes[10] + 1).toByte()
        assertNotEquals(a.fileId, FileIdentity.compute(write("b.mkv", bytes)).fileId)
    }

    @Test
    fun aChangeInTheTailChangesTheId() {
        val bytes = pattern(1_000_000)
        val a = FileIdentity.compute(write("a.mkv", bytes))
        bytes[999_990] = (bytes[999_990] + 1).toByte()
        assertNotEquals(a.fileId, FileIdentity.compute(write("b.mkv", bytes)).fileId)
    }

    @Test
    fun aChangeInTheMiddleIsNotRead() {
        val bytes = pattern(1_000_000)
        val a = FileIdentity.compute(write("a.mkv", bytes))
        bytes[500_000] = (bytes[500_000] + 1).toByte()
        assertEquals(a.fileId, FileIdentity.compute(write("b.mkv", bytes)).fileId)
    }

    @Test
    fun smallFilesWork() {
        assertEquals(64, FileIdentity.compute(write("small.mkv", pattern(100))).fileId.length)
    }

    @Test
    fun osHashOfZeroesIsTheSize() {
        val size = 200_000
        assertEquals("%016x".format(size), FileIdentity.compute(write("z.mkv", ByteArray(size))).osHash)
    }

    @Test
    fun osHashAddsLittleEndianWordsOfBothEnds() {
        val size = 200_000
        val bytes = ByteArray(size)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putLong(0, 5L)
        buffer.putLong(size - 8, 9L)
        buffer.putLong(100_000, 77L)
        assertEquals("%016x".format(size + 5L + 9L), FileIdentity.compute(write("w.mkv", bytes)).osHash)
    }
}

class NaturalOrderTest {
    @Test
    fun numbersSortByValue() {
        val sorted = listOf("E10", "E2", "e1").sortedWith(NaturalOrder)
        assertEquals(listOf("e1", "E2", "E10"), sorted)
    }

    @Test
    fun leadingZeroesDoNotMatter() {
        assertTrue(NaturalOrder.compare("E02", "E10") < 0)
    }

    @Test
    fun arabicIndicDigitsCompareByValue() {
        assertTrue(NaturalOrder.compare("الحلقة ٢", "الحلقة ١٠") < 0)
    }

    @Test
    fun hugeNumbersDoNotOverflow() {
        assertTrue(NaturalOrder.compare("x99999999999999999999", "x100000000000000000000") < 0)
    }
}

class ScanOrderTest {
    @Test
    fun comparesSegmentBySegment() {
        assertTrue(ScanOrder.compare("a", "a b") < 0)
        assertTrue(ScanOrder.compare("a/z", "a b") < 0)
        assertTrue(ScanOrder.compare("a", "a/b") < 0)
        assertTrue(ScanOrder.compare("", "a") < 0)
        assertEquals(0, ScanOrder.compare("a/b", "a/b"))
    }

    /** Walks a tree in visit order and resumes from each checkpoint, so nothing can be skipped or repeated. */
    @Test
    fun aResumeFromAnyCheckpointVisitsExactlyTheRest() {
        val tree = mapOf(
            "" to listOf("a", "a b", "c"),
            "a" to listOf("x", "y"),
            "a/x" to emptyList(),
            "a/y" to listOf("z"),
            "a/y/z" to emptyList(),
            "a b" to emptyList(),
            "c" to listOf("d"),
            "c/d" to emptyList(),
        )

        fun walk(dir: String, checkpoint: String?, out: MutableList<String>) {
            if (!ScanOrder.isDone(dir, checkpoint)) out += dir
            for (child in tree.getValue(dir).sorted()) {
                val path = if (dir.isEmpty()) child else "$dir/$child"
                if (ScanOrder.canPrune(path, checkpoint)) continue
                walk(path, checkpoint, out)
            }
        }

        val full = ArrayList<String>().also { walk("", null, it) }
        assertEquals(tree.size, full.size)
        full.forEachIndexed { index, checkpoint ->
            val rest = ArrayList<String>().also { walk("", checkpoint, it) }
            assertEquals("resume from '$checkpoint'", full.drop(index + 1), rest)
        }
    }

    @Test
    fun noCheckpointMeansNothingIsDone() {
        assertFalse(ScanOrder.isDone("a", null))
        assertFalse(ScanOrder.canPrune("a", null))
    }
}

class SidecarMatcherTest {
    private val video = "Show.S01E02.mkv"
    private fun same(vararg names: String) = names.map { SidecarFile("", it) }
    private val noParse: (String) -> EpisodeTag? = { null }

    private fun names(hits: List<SidecarHit>) = hits.map { it.file.name }

    @Test
    fun matchesEveryNamedForm() {
        val hits = SidecarMatcher.match(
            video,
            same("Show.S01E02.srt", "Show.S01E02.ar.srt", "Show.S01E02.ara.srt", "Show.S01E02.arabic.srt", "Show.S01E02.ar.forced.ass"),
            emptyList(),
            noParse,
        )
        assertEquals(5, hits.size)
        assertEquals("ar", hits.first().language)
    }

    @Test
    fun fullArabicOutranksForcedAndSigns() {
        val hits = SidecarMatcher.match(
            video,
            same("Show.S01E02.ar.forced.ass", "Show.S01E02.ara.signs.ass", "Show.S01E02.en.srt", "Show.S01E02.ar.srt"),
            emptyList(),
            noParse,
        )
        assertEquals("Show.S01E02.ar.srt", hits.first().file.name)
        assertTrue(hits[1].language == "ar" && (hits[1].forced || hits[1].signs))
        assertTrue(hits[2].language == "ar")
        assertEquals("en", hits[3].language)
    }

    @Test
    fun findsSubsAndSubtitlesFolders() {
        val hits = SidecarMatcher.match(video, emptyList(), listOf(SidecarFile("Subs", "Show.S01E02.ar.srt")), noParse)
        assertEquals("Subs", hits.single().file.folder)
    }

    @Test
    fun ignoresOtherEpisodes() {
        assertTrue(SidecarMatcher.match(video, same("Show.S01E03.ar.srt"), emptyList(), noParse).isEmpty())
    }

    @Test
    fun aLongerBaseNameIsNotAMatch() {
        assertTrue(SidecarMatcher.match("Show.mkv", same("Show2.srt"), emptyList(), noParse).isEmpty())
    }

    @Test
    fun caseDoesNotMatter() {
        assertEquals(1, SidecarMatcher.match(video, same("show.s01e02.AR.SRT"), emptyList(), noParse).size)
    }

    private val tags = mapOf(
        "Show.S01E02.mkv" to EpisodeTag(1, 2),
        "other name 1x02 ar.srt" to EpisodeTag(1, 2),
        "second 1x02.srt" to EpisodeTag(1, 2),
        "other 1x03.srt" to EpisodeTag(1, 3),
    )

    @Test
    fun episodeFallbackNeedsExactlyOneCandidate() {
        val one = SidecarMatcher.match(video, same("other name 1x02 ar.srt", "other 1x03.srt"), emptyList()) { tags[it] }
        assertEquals(listOf("other name 1x02 ar.srt"), names(one))
        assertEquals("ar", one.single().language)
        val two = SidecarMatcher.match(video, same("other name 1x02 ar.srt", "second 1x02.srt"), emptyList()) { tags[it] }
        assertTrue(two.isEmpty())
    }

    @Test
    fun aBaseNameMatchTurnsTheFallbackOff() {
        val hits = SidecarMatcher.match(video, same("Show.S01E02.srt", "other name 1x02 ar.srt"), emptyList()) { tags[it] }
        assertEquals(listOf("Show.S01E02.srt"), names(hits))
    }

    @Test
    fun fallbackLooksInTheSameFolderOnly() {
        val hits = SidecarMatcher.match(video, emptyList(), listOf(SidecarFile("Subs", "other name 1x02 ar.srt"))) { tags[it] }
        assertTrue(hits.isEmpty())
    }

    @Test
    fun mimeTypesFollowTheExtension() {
        assertEquals("application/x-subrip", SidecarMatcher.mimeType("a.SRT"))
        assertEquals("text/x-ssa", SidecarMatcher.mimeType("a.ass"))
        assertEquals("text/x-ssa", SidecarMatcher.mimeType("a.ssa"))
        assertEquals("text/vtt", SidecarMatcher.mimeType("a.vtt"))
    }
}
