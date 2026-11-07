package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Reason
import com.frameglass.net.SubtitleArchive.Ask
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleArchiveTest {
    private val root = File(System.getProperty("java.io.tmpdir"), "fg-archive-${System.nanoTime()}")
    private val folder = File(root, "file/subs")
    private val payload = File(root, "payload.bin")
    private val episodeTwo = Ask(season = 1, episode = 2, language = "ar", seasonPack = true)
    private val movie = Ask(season = null, episode = null, language = "ar", seasonPack = false)

    private fun place(bytes: ByteArray, ask: Ask = movie): NetResult<File> {
        root.mkdirs()
        payload.writeBytes(bytes)
        return SubtitleArchive.place(payload, ask, folder, "stem.ar")
    }

    private fun zipped(ask: Ask, vararg files: Pair<String, String>) = place(zipOf(*files), ask)

    /** Each entry holds its own name, so the test can read which one came out. */
    private fun episodePack(vararg names: String) = names.map { it to "$SRT_TEXT$it" }.toTypedArray()

    /** A real subtitle start, so only the size can refuse it and the sniffing cannot. */
    private fun oversize(): ByteArray = SRT_TEXT.toByteArray().copyOf((MAX_SUBTITLE_BYTES + 1).toInt())

    private fun assertFails(reason: Reason, result: NetResult<File>, message: String? = null) {
        val failure = result as Failure
        assertEquals(reason, failure.reason)
        if (message != null) assertEquals(message, failure.message)
        assertEquals(emptyList<String>(), folder.list().orEmpty().toList())
    }

    @After
    fun clean() {
        root.deleteRecursively()
    }

    @Test
    fun aSeasonPackGivesTheAskedEpisode() {
        val pack = episodePack("Show.S01E01.srt", "Show.S01E02.srt", "Show.S01E03.srt")
        assertTrue(zipped(episodeTwo, *pack).value().readText().endsWith("Show.S01E02.srt"))
    }

    @Test
    fun aDoubleEpisodeFileCoversBothEpisodes() {
        val pack = episodePack("Show.S01E01.srt", "Show.S01E02E03.srt", "Show.S01E04.srt")
        assertTrue(zipped(episodeTwo, *pack).value().readText().endsWith("Show.S01E02E03.srt"))
    }

    @Test
    fun aFolderCanCarryTheSeason() {
        val pack = episodePack("Show/Season 1/Show - 02.srt", "Show/Season 2/Show - 02.srt")
        assertTrue(zipped(episodeTwo, *pack).value().readText().endsWith("Season 1/Show - 02.srt"))
    }

    @Test
    fun theRightSeasonWinsInAPackOfSeveral() {
        val pack = episodePack("Show.S02E02.srt", "Show.S01E02.srt")
        assertTrue(zipped(episodeTwo, *pack).value().readText().endsWith("Show.S01E02.srt"))
    }

    @Test
    fun anExactSeasonBeatsAFileWithNoSeason() {
        val pack = episodePack("Show - 02.srt", "Show.S01E02.srt")
        assertTrue(zipped(episodeTwo, *pack).value().readText().endsWith("Show.S01E02.srt"))
    }

    @Test
    fun arabicEpisodeNamesAreRead() {
        val pack = episodePack("الحلقة 01.srt", "الحلقة 02.srt", "الحلقة 03.srt")
        assertTrue(zipped(episodeTwo, *pack).value().readText().endsWith("الحلقة 02.srt"))
    }

    @Test
    fun aNameWrittenInTheWindowsArabicCodePageIsStillRead() {
        val legacy = charset("windows-1256")
        val pack = listOf("الحلقة 01.srt" to SRT_TEXT + "one", "الحلقة 02.srt" to SRT_TEXT + "two")
        val zip = zipBytesOf(pack.map { (name, text) -> name to text.toByteArray() }, legacy)
        assertTrue(place(zip, episodeTwo).value().readText().endsWith("two"))
    }

    @Test
    fun aPackWithoutTheEpisodeIsNotFound() {
        assertFails(Reason.NOT_FOUND, zipped(episodeTwo, *episodePack("Show.S01E01.srt", "Show.S01E03.srt")))
    }

    @Test
    fun aPackOfUnnamedFilesIsNotGuessedAt() {
        assertFails(Reason.NOT_FOUND, zipped(episodeTwo, *episodePack("first subtitle.srt", "second subtitle.srt")))
    }

    @Test
    fun aPackHoldingOneFileOfAnotherEpisodeIsNotFound() {
        assertFails(Reason.NOT_FOUND, zipped(episodeTwo, "Show.S01E09.srt" to SRT_TEXT))
    }

    @Test
    fun aPackHoldingOneUnnamedFileIsThatFile() {
        assertEquals(SRT_TEXT, zipped(episodeTwo, "subtitle.srt" to SRT_TEXT).value().readText())
    }

    @Test
    fun aSingleEpisodeHitTrustsItsProviderOverAFileName() {
        val ask = Ask(season = 3, episode = 5, language = "ar", seasonPack = false)
        assertEquals(SRT_TEXT, zipped(ask, "Show - 125.srt" to SRT_TEXT).value().readText())
    }

    @Test
    fun oneEpisodeInTwoFormatsNeedsNoEpisodeInTheName() {
        val ask = Ask(season = 1, episode = 2, language = "ar", seasonPack = false)
        val result = zipped(ask, "subtitle.srt" to SRT_TEXT, "subtitle.ass" to ASS_TEXT + "padding padding padding")
        assertEquals("ass", result.value().extension)
    }

    @Test
    fun theLanguageInAFileNameDecidesFirst() {
        val pack = arrayOf("Movie.en.srt" to SRT_TEXT + "english text that is longer", "Movie.ar.srt" to SRT_TEXT + "arabic")
        assertTrue(zipped(movie, *pack).value().readText().endsWith("arabic"))
    }

    @Test
    fun theLargestFileWinsOverAForcedTrack() {
        val pack = arrayOf("Movie.forced.srt" to SRT_TEXT, "Movie.srt" to SRT_TEXT + "full dialogue")
        assertTrue(zipped(movie, *pack).value().readText().endsWith("full dialogue"))
    }

    @Test
    fun onlyTheFourFormatsAreAccepted() {
        val others = arrayOf("a.sub" to SRT_TEXT, "a.idx" to SRT_TEXT, "a.txt" to SRT_TEXT, "a.exe" to SRT_TEXT, "a.srt.exe" to SRT_TEXT)
        assertFails(Reason.NOT_FOUND, zipped(movie, *others))
    }

    @Test
    fun eachAcceptedFormatComesOutWithItsOwnExtension() {
        assertEquals("srt", zipped(movie, "a.SRT" to SRT_TEXT).value().extension)
        assertEquals("ass", zipped(movie, "a.ass" to ASS_TEXT).value().extension)
        assertEquals("ssa", zipped(movie, "a.ssa" to SSA_TEXT).value().extension)
        assertEquals("vtt", zipped(movie, "a.vtt" to VTT_TEXT).value().extension)
    }

    @Test
    fun macMetadataIsIgnored() {
        val pack = arrayOf("__MACOSX/._a.srt" to "binary junk that is long enough to look bigger", "._b.srt" to "more junk", "a.srt" to SRT_TEXT)
        assertEquals(SRT_TEXT, zipped(movie, *pack).value().readText())
    }

    @Test
    fun anEmptyArchiveIsNotFound() {
        assertFails(Reason.NOT_FOUND, place(zipOf()))
    }

    @Test
    fun aDirectoryNamedLikeASubtitleIsNotOne() {
        assertFails(Reason.NOT_FOUND, zipped(movie, "folder.srt/" to ""))
    }

    @Test
    fun anyEscapingEntryRefusesTheWholeArchive() {
        val names = listOf(
            "../evil.srt",
            "a/../../evil.srt",
            "..\\evil.srt",
            "a\\..\\..\\evil.srt",
            "/etc/evil.srt",
            "\\evil.srt",
            "C:\\evil.srt",
            "c:/evil.srt",
        )
        for (name in names) {
            val result = zipped(movie, "good.srt" to SRT_TEXT, name to SRT_TEXT)
            assertEquals(name, Reason.BAD_RESPONSE, (result as Failure).reason)
            assertEquals(name, "archive path", result.message)
            assertEquals(name, emptyList<String>(), folder.list().orEmpty().toList())
        }
        assertFalse(File(root, "evil.srt").exists())
    }

    @Test
    fun twoDotsInsideANameAreNotAnEscape() {
        assertEquals(SRT_TEXT, zipped(movie, "Show..2024...srt" to SRT_TEXT).value().readText())
    }

    @Test
    fun aFileExactlyAtTheCapIsAccepted() {
        val full = SRT_TEXT.toByteArray().copyOf(MAX_SUBTITLE_BYTES.toInt())
        assertEquals(MAX_SUBTITLE_BYTES, place(zipBytesOf(listOf("full.srt" to full))).value().length())
    }

    @Test
    fun anEntryOverTheCapIsRefusedAndLeavesNothing() {
        assertFails(Reason.BAD_RESPONSE, place(zipBytesOf(listOf("bomb.srt" to oversize()))), "subtitle too large")
    }

    @Test
    fun aHeaderThatUnderstatesItsSizeDoesNotPassTheCap() {
        val zip = zipBytesOf(listOf("bomb.srt" to oversize()))
        val central = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN)
        val start = zip.indices.first { it + 4 < zip.size && central.getInt(it) == CENTRAL_ENTRY_SIGNATURE }
        central.putInt(start + CENTRAL_SIZE_OFFSET, 16)
        assertFails(Reason.BAD_RESPONSE, place(zip), "subtitle too large")
    }

    @Test
    fun tooManyEntriesAreRefused() {
        val crowd = (0..2_000).map { "file$it.srt" to SRT_TEXT.toByteArray() }
        assertFails(Reason.BAD_RESPONSE, place(zipBytesOf(crowd)), "archive entries")
    }

    @Test
    fun aFileRenamedToSrtIsNotASubtitle() {
        assertFails(Reason.BAD_RESPONSE, zipped(movie, "page.srt" to "<html><body>Sign in</body></html>"), "not a subtitle")
    }

    @Test
    fun aBrokenZipFailsSoftly() {
        val broken = "PK\u0003\u0004".toByteArray() + ByteArray(64)
        assertFails(Reason.BAD_RESPONSE, place(broken), "archive")
    }

    @Test
    fun aBareSubtitleIsSavedByItsContent() {
        assertEquals("srt", place(SRT_TEXT.toByteArray()).value().extension)
        assertEquals("vtt", place(VTT_TEXT.toByteArray()).value().extension)
        assertEquals("ass", place(ASS_TEXT.toByteArray()).value().extension)
        assertEquals("ssa", place(SSA_TEXT.toByteArray()).value().extension)
    }

    @Test
    fun aBareSubtitleWithAByteOrderMarkIsRecognised() {
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + VTT_TEXT.toByteArray()
        assertEquals("vtt", place(utf8).value().extension)
    }

    @Test
    fun aBareUtf16SubtitleIsKeptByteForByte() {
        val bytes = SRT_TEXT.toByteArray(Charsets.UTF_16)
        val saved = place(bytes).value()
        assertEquals("srt", saved.extension)
        assertTrue(bytes.contentEquals(saved.readBytes()))
    }

    @Test
    fun anArabicWindowsSubtitleIsKeptByteForByte() {
        val bytes = (SRT_TEXT + "مرحبا").toByteArray(charset("windows-1256"))
        assertTrue(bytes.contentEquals(place(bytes).value().readBytes()))
    }

    @Test
    fun aWebPageIsNotASubtitleEvenWithACommentInIt() {
        val page = "<!doctype html><html><!-- a comment --></html>".toByteArray()
        assertFails(Reason.BAD_RESPONSE, place(page), "not a subtitle")
    }

    @Test
    fun aRarIsRefusedBecauseNoListedDependencyReadsIt() {
        val rar = "Rar!\u001A\u0007\u0000 rest of the archive".toByteArray()
        assertFails(Reason.BAD_RESPONSE, place(rar), "not a subtitle")
    }

    @Test
    fun aBareFileOverTheCapIsRefused() {
        assertFails(Reason.BAD_RESPONSE, place(oversize()), "subtitle too large")
    }

    @Test
    fun savedFindsOnlyAFileWithContent() {
        folder.mkdirs()
        File(folder, "stem.ar.srt").writeText("")
        assertNull(SubtitleArchive.saved(folder, "stem.ar"))
        File(folder, "stem.ar.ass").writeText(ASS_TEXT)
        assertEquals("ass", SubtitleArchive.saved(folder, "stem.ar")?.extension)
    }

    @Test
    fun aSavedNameIsBuiltFromSafeCharactersOnly() {
        val hit = subtitleHit(PROVIDER_SUBDL, id = "/subtitle/../3197651-3213944.zip", language = "ar/../x")
        val stem = stemOf(hit, season = 1, episode = 2)
        assertEquals("subdl-subtitle-3197651-3213944-zip.s1e2.ar-x", stem)
        assertEquals(emptyList<Char>(), stem.filter { it == '/' || it == '\\' }.toList())
    }

    @Test
    fun aHostileIdCannotMakeAnOverlongName() {
        assertTrue(stemOf(subtitleHit(id = "9".repeat(5000)), null, null).length < 120)
    }

    @Test
    fun aDownloadOverTheCapStopsEarlyAndLeavesNothing() {
        val rig = Rig()
        val target = File(rig.dir, "payload")
        rig.server.on("api.subsource.net/huge", Reply(bytes = ByteArray(1024)))
        val request = Request.Builder().url("https://api.subsource.net/huge".toHttpUrl()).build()
        val failure = blocking { rig.http.download(request, target, "probe", maxBytes = 512) } as Failure
        assertEquals(Reason.BAD_RESPONSE, failure.reason)
        assertFalse(target.exists())
        assertFalse(File(target.path + ".part").exists())
        rig.clean()
    }

    private companion object {
        const val CENTRAL_ENTRY_SIGNATURE = 0x02014b50
        const val CENTRAL_SIZE_OFFSET = 24
    }
}
