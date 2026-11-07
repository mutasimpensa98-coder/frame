package com.frameglass.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupFormatTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val settings = BackupSettings(
        onlineFeatures = true, doubleTapSeconds = 10, backgroundAudio = false, autoPlayNext = true, nightAudio = false,
        subtitleScale = 1.5f, subtitleColor = -1, subtitleBackground = false, subtitleFontPath = null, subtitleBottomOffset = 0f,
        scanHiddenFolders = false, scanNomediaFolders = false, showUnavailable = false,
        tmdbBaseUrl = "https://api.themoviedb.org", neverSleepingHintDone = true, onboardingDone = true,
    )

    private val title = BackupTitle(
        id = 5, kind = "SERIES", groupKey = "SERIES|show|-", groupName = "show", localName = "Show", localYear = null,
        tmdbId = 42, tmdbIsMovie = false, anilistId = null, malId = null, imdbId = null, matchState = "MANUAL",
        matchConfidence = 1.0, manualMatch = true, coverFile = "covers/t5.jpg", removed = false, createdAt = 1,
    )

    private val file = BackupFile(
        fileId = "abc", volumeUuid = "primary", relPath = "Show/الحلقة 1.mkv", size = 9, mtime = 2, durationMs = 3, width = 4,
        height = 5, videoCodec = "hevc", hdrType = null, audioSummary = "AAC 2.0", osHash = "00", firstSeenAt = 6, titleId = 5,
        releaseTags = null, hasSidecarSub = true, hasEmbeddedSub = false, playableKey = "t:5:s1:e1", parsedSeason = 1,
        parsedEpisode = 1, parsedEpisodeEnd = null, parsedAbsolute = null, nameKind = "EPISODE",
    )

    private val doc = BackupDoc(
        exportedAt = 123,
        settings = settings,
        volumes = listOf(BackupVolume("primary", "Internal", false, "/storage/emulated/0")),
        roots = listOf(BackupRoot("primary", "Show", 7)),
        titles = listOf(title),
        aliases = listOf(BackupAlias("ANIME|show|-", 5)),
        files = listOf(file),
        watch = listOf(BackupWatch("t:5:s1:e1", 10, 20, false, 30, 5)),
        seriesPrefs = listOf(BackupSeriesPrefs(5, "ar", "ar", false, 1.25f, "Fit")),
        filePrefs = listOf(BackupFilePrefs("abc", "ar", "ar", null, "windows-1256", 150, false, 1f, null)),
        audioDelays = listOf(BackupDelay("Buds", 120)),
        intros = listOf(BackupIntro(5, null, "OP", 0, 90_000)),
    )

    private fun zip(json: String?, vararg extra: Pair<String, ByteArray>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            if (json != null) {
                out.putNextEntry(ZipEntry(BackupCodec.JSON_ENTRY))
                out.write(json.toByteArray())
                out.closeEntry()
            }
            extra.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name))
                out.write(body)
                out.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private fun read(stream: ByteArrayInputStream) = BackupArchive.read(stream, folder.newFolder())

    private fun rejected(stream: ByteArrayInputStream) {
        assertThrows(BackupException::class.java) { read(stream) }
    }

    private val cover = "covers/t5.jpg" to byteArrayOf(1, 2, 3)

    @Test
    fun aRoundTripKeepsEveryField() {
        val archive = read(zip(BackupCodec.encode(doc), cover))
        assertEquals(doc, archive.doc)
        assertEquals(3L, archive.covers.getValue("covers/t5.jpg").length())
    }

    @Test
    fun theFileNamesItsFormatAndVersion() {
        val text = BackupCodec.encode(doc)
        assertTrue(text.contains("\"format\": \"frame-glass-backup\""))
        assertTrue(text.contains("\"version\": 2"))
    }

    @Test
    fun theFileHoldsNoKeyField() {
        val text = BackupCodec.encode(doc).lowercase()
        listOf("tmdbkey", "apikey", "api_key", "omdb", "opensubtitles", "subdl", "subsource").forEach {
            assertFalse(it, text.contains(it))
        }
    }

    @Test
    fun rejectsSomethingThatIsNotAZip() {
        rejected(ByteArrayInputStream("hello".toByteArray()))
    }

    @Test
    fun rejectsAZipWithNoJson() {
        rejected(zip(null, cover))
    }

    @Test
    fun rejectsAnotherFormat() {
        rejected(zip(BackupCodec.encode(doc).replace("frame-glass-backup", "other"), cover))
    }

    @Test
    fun rejectsAnotherVersion() {
        rejected(zip(BackupCodec.encode(doc).replace("\"version\": 2", "\"version\": 3"), cover))
    }

    @Test
    fun rejectsDamagedJson() {
        rejected(zip("{\"format\":\"frame-glass-backup\",\"version\":2", cover))
        rejected(zip("{\"format\":\"frame-glass-backup\",\"version\":2}", cover))
    }

    @Test
    fun rejectsAMissingCover() {
        rejected(zip(BackupCodec.encode(doc)))
    }

    @Test
    fun rejectsAnEntryOutsideTheLayout() {
        rejected(zip(BackupCodec.encode(doc), cover, "../evil.txt" to byteArrayOf(1)))
        rejected(zip(BackupCodec.encode(doc), cover, "covers/../evil.txt" to byteArrayOf(1)))
        rejected(zip(BackupCodec.encode(doc), cover, "covers/.." to byteArrayOf(1)))
    }

    private fun invalid(change: BackupDoc) {
        assertThrows(BackupException::class.java) { BackupCodec.validate(change, setOf("covers/t5.jpg")) }
    }

    @Test
    fun validDocPasses() {
        BackupCodec.validate(doc, setOf("covers/t5.jpg"))
    }

    @Test
    fun rejectsBadReferences() {
        invalid(doc.copy(files = listOf(file.copy(titleId = 99))))
        invalid(doc.copy(watch = listOf(BackupWatch("k", 1, 1, false, 1, 99))))
        invalid(doc.copy(aliases = listOf(BackupAlias("x", 99))))
        invalid(doc.copy(seriesPrefs = listOf(BackupSeriesPrefs(99, null, null, false, 1f, null))))
        invalid(doc.copy(intros = listOf(BackupIntro(99, null, "OP", 0, 1))))
    }

    @Test
    fun rejectsBadValues() {
        invalid(doc.copy(titles = listOf(title.copy(kind = "PODCAST"))))
        invalid(doc.copy(titles = listOf(title.copy(matchState = "MAYBE"))))
        invalid(doc.copy(titles = listOf(title, title)))
        invalid(doc.copy(files = listOf(file.copy(relPath = "../../etc/passwd"))))
        invalid(doc.copy(files = listOf(file.copy(relPath = "/etc/passwd"))))
        invalid(doc.copy(files = listOf(file, file)))
        invalid(doc.copy(watch = listOf(BackupWatch("k", -1, 1, false, 1, 5))))
        invalid(doc.copy(intros = listOf(BackupIntro(5, null, "OP", 9, 1))))
    }

    @Test
    fun aMissingFontDoesNotComeBack() {
        val saved = settings.copy(subtitleFontPath = "/gone.ttf")
        assertEquals(null, saved.toAppSettings(fontExists = false).subtitleFontPath)
        assertEquals("/gone.ttf", saved.toAppSettings(fontExists = true).subtitleFontPath)
    }

    @Test
    fun wholePhoneSearchSurvivesABackup() {
        val off = com.frameglass.data.api.AppSettings(scanWholePhone = false)
        assertEquals(false, BackupSettings.from(off).toAppSettings(fontExists = false).scanWholePhone)
    }

    @Test
    fun aBackupWrittenBeforeWholePhoneSearchTurnsItOn() {
        // Plain Json leaves out a value equal to its default, which is what a file from before this setting looks like.
        val older = Json.encodeToString(BackupSettings.serializer(), settings)
        assertFalse(older.contains("scanWholePhone"))
        assertTrue(Json.decodeFromString(BackupSettings.serializer(), older).scanWholePhone)
    }

    @Test
    fun favoritesAndExcludedFoldersSurviveABackup() {
        val saved = doc.copy(
            titles = listOf(title.copy(favorite = true)),
            excluded = listOf(BackupExcluded("primary", "Movies/Junk", 9)),
        )
        val back = read(zip(BackupCodec.encode(saved), cover)).doc
        assertTrue(back.titles.single().favorite)
        assertEquals(listOf(BackupExcluded("primary", "Movies/Junk", 9)), back.excluded)
    }

    @Test
    fun aBackupWrittenBeforeFavoritesStillRestores() {
        val full = Json.parseToJsonElement(BackupCodec.encode(doc)).jsonObject
        val titles = full.getValue("titles").jsonArray.map { JsonObject(it.jsonObject - "favorite") }
        val older = JsonObject(full - "excluded" + ("titles" to JsonArray(titles))).toString()
        assertFalse(older.contains("\"favorite\""))
        assertFalse(older.contains("\"excluded\""))
        val back = BackupCodec.decode(older)
        assertFalse(back.titles.single().favorite)
        assertTrue(back.excluded.isEmpty())
    }

    @Test
    fun theNewPreferencesSurviveABackup() {
        val chosen = com.frameglass.data.api.AppSettings(
            themeMode = com.frameglass.data.api.ThemeMode.LIGHT,
            accent = com.frameglass.data.api.AccentColor.VIOLET,
            coverTint = true,
            cinemaMode = true,
            sendReleaseName = false,
            sendFileHash = false,
            vaultLockSeconds = 30,
        )
        val back = BackupSettings.from(chosen).toAppSettings(fontExists = false)
        assertEquals(chosen.themeMode, back.themeMode)
        assertEquals(chosen.accent, back.accent)
        assertEquals(chosen.coverTint, back.coverTint)
        assertEquals(chosen.cinemaMode, back.cinemaMode)
        assertEquals(chosen.sendReleaseName, back.sendReleaseName)
        assertEquals(chosen.sendFileHash, back.sendFileHash)
        assertEquals(chosen.vaultLockSeconds, back.vaultLockSeconds)
    }

    @Test
    fun aBackupWrittenBeforeCinemaModeLeavesItOff() {
        val json = BackupCodec.encode(doc).replace(Regex(",?\\s*\"cinemaMode\": false"), "")
        assertTrue("cinemaMode" !in json)
        assertEquals(false, BackupCodec.decode(json).settings.cinemaMode)
    }

    @Test
    fun rejectsABadThemeOrLockTime() {
        invalid(doc.copy(settings = settings.copy(themeMode = "NEON")))
        invalid(doc.copy(settings = settings.copy(accent = "NEON")))
        invalid(doc.copy(settings = settings.copy(vaultLockSeconds = -1)))
    }

    @Test
    fun rejectsABadExcludedFolder() {
        invalid(doc.copy(excluded = listOf(BackupExcluded("primary", "../..", 1))))
    }

    private val publicTitle = title.copy(id = 6, groupKey = "SERIES|other|-", coverFile = null)

    private val withPrivate = doc.copy(
        titles = listOf(title, publicTitle),
        aliases = listOf(BackupAlias("ANIME|show|-", 5), BackupAlias("ANIME|other|-", 6)),
        files = listOf(file.copy(titleId = 6, playableKey = "t:6:s1:e1")),
        watch = listOf(
            BackupWatch("t:5:s1:e1", 10, 20, false, 30, 5),
            BackupWatch("t:5:s1:e2", 10, 20, false, 30, null),
            BackupWatch("t:6:s1:e1", 10, 20, false, 30, 6),
        ),
        seriesPrefs = listOf(BackupSeriesPrefs(5, "ar", "ar", false, 1f, null), BackupSeriesPrefs(6, "ar", "ar", false, 1f, null)),
        filePrefs = listOf(
            BackupFilePrefs("private", "ar", "ar", "/subs/Secret.ar.srt", null, 0, false, 1f, null),
            BackupFilePrefs("abc", "ar", "ar", null, null, 0, false, 1f, null),
        ),
        intros = listOf(BackupIntro(5, null, "OP", 0, 1), BackupIntro(6, null, "OP", 0, 1)),
    )

    @Test
    fun nothingOfAPrivateTitleStaysInABackup() {
        val clean = BackupPrivacy.strip(withPrivate, titles = setOf(5), files = setOf("private"))
        assertEquals(listOf(6L), clean.titles.map { it.id })
        assertEquals(listOf(6L), clean.aliases.map { it.titleId })
        assertEquals(listOf("t:6:s1:e1"), clean.watch.map { it.playableKey })
        assertEquals(listOf(6L), clean.seriesPrefs.map { it.titleId })
        assertEquals(listOf("abc"), clean.filePrefs.map { it.fileId })
        assertEquals(listOf(6L), clean.intros.map { it.titleId })
        assertFalse(BackupCodec.encode(clean).contains("Secret"))
    }

    @Test
    fun aBackupWithPrivateItemsTakenOutStillValidates() {
        BackupCodec.validate(BackupPrivacy.strip(withPrivate, titles = setOf(5), files = setOf("private")), emptySet())
    }

    @Test
    fun aBackupWithNothingPrivateIsUnchanged() {
        assertEquals(doc, BackupPrivacy.strip(doc, emptySet(), emptySet()))
    }
}
