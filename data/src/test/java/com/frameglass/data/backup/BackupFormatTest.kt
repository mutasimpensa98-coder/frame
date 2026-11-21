package com.frameglass.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import com.frameglass.data.api.ViewSize
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

    private val phone = com.frameglass.data.api.AppSettings()

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
        assertEquals(null, saved.toAppSettings(fontExists = false, phone).subtitleFontPath)
        assertEquals("/gone.ttf", saved.toAppSettings(fontExists = true, phone).subtitleFontPath)
    }

    @Test
    fun wholePhoneSearchSurvivesABackup() {
        val off = com.frameglass.data.api.AppSettings(scanWholePhone = false)
        assertEquals(false, BackupSettings.from(off).toAppSettings(fontExists = false, phone).scanWholePhone)
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
    fun theWatchlistSurvivesABackupApartFromFavourites() {
        val saved = doc.copy(titles = listOf(title.copy(watchlistAt = 77)))
        val back = read(zip(BackupCodec.encode(saved), cover)).doc.titles.single()
        assertEquals(77L, back.watchlistAt)
        assertFalse(back.favorite)
    }

    @Test
    fun aBackupWrittenBeforeTheWatchlistStillRestores() {
        val full = Json.parseToJsonElement(BackupCodec.encode(doc)).jsonObject
        val titles = full.getValue("titles").jsonArray.map { JsonObject(it.jsonObject - "watchlistAt") }
        val older = JsonObject(full + ("titles" to JsonArray(titles))).toString()
        assertFalse(older.contains("watchlistAt"))
        assertEquals(null, BackupCodec.decode(older).titles.single().watchlistAt)
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
            theme = com.frameglass.data.api.ThemeId.LIGHT,
            accent = com.frameglass.data.api.AccentColor.VIOLET,
            coverTint = true,
            cinemaMode = true,
            sendReleaseName = false,
            sendFileHash = false,
            vaultLockSeconds = 30,
        )
        val back = BackupSettings.from(chosen).toAppSettings(fontExists = false, phone)
        assertEquals(chosen.theme, back.theme)
        assertEquals(chosen.accent, back.accent)
        assertEquals(chosen.coverTint, back.coverTint)
        assertEquals(chosen.cinemaMode, back.cinemaMode)
        assertEquals(chosen.sendReleaseName, back.sendReleaseName)
        assertEquals(chosen.sendFileHash, back.sendFileHash)
        assertEquals(chosen.vaultLockSeconds, back.vaultLockSeconds)
    }

    @Test
    fun theServiceSwitchesRoundTrip() {
        val chosen = com.frameglass.data.api.AppSettings(tmdbEnabled = false, imdbEnabled = true)
        val back = BackupSettings.from(chosen).toAppSettings(fontExists = false, phone)
        assertEquals(false, back.tmdbEnabled)
        assertEquals(true, back.imdbEnabled)
    }

    @Test
    fun aBackupWrittenBeforeTheSwitchesKeepsThePhonesSwitches() {
        val full = BackupCodec.encode(doc.copy(settings = BackupSettings.from(phone)))
        val json = full.replace(Regex(""",?\s*"(tmdbEnabled|imdbEnabled)": (true|false|null)"""), "")
        assertTrue("Enabled" !in json)
        val saved = BackupCodec.decode(json).settings
        assertEquals(null, saved.tmdbEnabled)
        val turnedOff = phone.copy(tmdbEnabled = false, imdbEnabled = false)
        val back = saved.toAppSettings(fontExists = false, turnedOff)
        assertFalse(back.tmdbEnabled)
        assertFalse(back.imdbEnabled)
        assertTrue(saved.toAppSettings(fontExists = false, phone).let { it.tmdbEnabled && it.imdbEnabled })
    }

    @Test
    fun aBackupWithTheSwitchesSetsThemWhateverThePhoneHolds() {
        val saved = BackupSettings.from(phone.copy(tmdbEnabled = false, imdbEnabled = true))
        val back = BackupCodec.decode(BackupCodec.encode(doc.copy(settings = saved))).settings
            .toAppSettings(fontExists = false, phone.copy(imdbEnabled = false))
        assertFalse(back.tmdbEnabled)
        assertTrue(back.imdbEnabled)
    }

    @Test
    fun theAutomaticSubtitleChoiceRoundTrips() {
        val chosen = phone.copy(autoSubtitles = com.frameglass.data.api.AutoSubtitleMode.ARABIC_THEN_ENGLISH)
        val back = BackupCodec.decode(BackupCodec.encode(doc.copy(settings = BackupSettings.from(chosen)))).settings
            .toAppSettings(fontExists = false, phone)
        assertEquals(com.frameglass.data.api.AutoSubtitleMode.ARABIC_THEN_ENGLISH, back.autoSubtitles)
    }

    @Test
    fun aBackupWrittenBeforeAutomaticSubtitlesKeepsThePhonesChoice() {
        val json = BackupCodec.encode(doc.copy(settings = BackupSettings.from(phone)))
            .replace(Regex(""",?\s*"autoSubtitles": ("[A-Z_]+"|null)"""), "")
        assertTrue("autoSubtitles" !in json)
        val saved = BackupCodec.decode(json).settings
        assertEquals(null, saved.autoSubtitles)
        val off = phone.copy(autoSubtitles = com.frameglass.data.api.AutoSubtitleMode.OFF)
        assertEquals(com.frameglass.data.api.AutoSubtitleMode.OFF, saved.toAppSettings(fontExists = false, off).autoSubtitles)
    }

    @Test
    fun theViewSizesRoundTripPlaceByPlace() {
        val chosen = phone.copy(
            viewLibrary = ViewSize.SMALL_GRID,
            viewFolders = ViewSize.LIST,
            viewPrivate = ViewSize.LARGE_GRID,
        )
        val back = BackupCodec.decode(BackupCodec.encode(doc.copy(settings = BackupSettings.from(chosen)))).settings
            .toAppSettings(fontExists = false, phone)
        assertEquals(ViewSize.SMALL_GRID, back.viewLibrary)
        assertEquals(ViewSize.LIST, back.viewFolders)
        assertEquals(ViewSize.LARGE_GRID, back.viewPrivate)
        assertEquals(null, back.viewLists)
        // Nothing else moved on the way.
        assertEquals(chosen, back)
    }

    @Test
    fun aBackupWrittenBeforeViewSizesKeepsThePhonesSizes() {
        val json = BackupCodec.encode(doc.copy(settings = BackupSettings.from(phone)))
            .replace(Regex(""",?\s*"view(Library|Folders|Private|Lists)": ("[A-Z_]+"|null)"""), "")
        assertTrue("viewLibrary" !in json && "viewLists" !in json)
        val saved = BackupCodec.decode(json).settings
        assertEquals(null, saved.viewLibrary)
        val onPhone = phone.copy(viewLibrary = ViewSize.COMPACT_LIST, viewLists = ViewSize.LARGE_GRID)
        val back = saved.toAppSettings(fontExists = false, onPhone)
        assertEquals(ViewSize.COMPACT_LIST, back.viewLibrary)
        assertEquals(ViewSize.LARGE_GRID, back.viewLists)
        assertEquals(null, back.viewFolders)
    }

    @Test
    fun aPlaceTheBackupHasNoSizeForKeepsThePhones() {
        val saved = BackupSettings.from(phone.copy(viewLibrary = ViewSize.LIST))
        val onPhone = phone.copy(viewLibrary = ViewSize.LARGE_GRID, viewFolders = ViewSize.MEDIUM_GRID)
        val back = saved.toAppSettings(fontExists = false, onPhone)
        assertEquals(ViewSize.LIST, back.viewLibrary)
        assertEquals(ViewSize.MEDIUM_GRID, back.viewFolders)
    }

    @Test
    fun aViewSizeThisBuildDoesNotKnowKeepsThePhones() {
        val json = BackupCodec.encode(doc.copy(settings = BackupSettings.from(phone.copy(viewPrivate = ViewSize.SMALL_GRID))))
            .replace("SMALL_GRID", "HUGE_GRID")
        val onPhone = phone.copy(viewPrivate = ViewSize.LIST)
        assertEquals(ViewSize.LIST, BackupCodec.decode(json).settings.toAppSettings(fontExists = false, onPhone).viewPrivate)
    }

    @Test
    fun aBackupWrittenBeforeCinemaModeLeavesItOff() {
        val json = BackupCodec.encode(doc).replace(Regex(",?\\s*\"cinemaMode\": false"), "")
        assertTrue("cinemaMode" !in json)
        assertEquals(false, BackupCodec.decode(json).settings.cinemaMode)
    }

    @Test
    fun theThemeAndTheCustomColoursRoundTrip() {
        val chosen = phone.copy(
            theme = com.frameglass.data.api.ThemeId.CUSTOM,
            followSystem = true,
            accent = com.frameglass.data.api.AccentColor.CUSTOM,
            customHue = 301.5f,
            customShade = 0.87f,
            customSurface = 0.12f,
            customAccent = 0xFF8A4B1F.toInt(),
        )
        val text = BackupCodec.encode(doc.copy(settings = BackupSettings.from(chosen)))
        val decoded = BackupCodec.decode(text)
        BackupCodec.validate(decoded, setOf("covers/t5.jpg"))
        // Whatever the phone holds, the backup's look wins.
        val other = phone.copy(theme = com.frameglass.data.api.ThemeId.PAPER, customHue = 5f, customAccent = -1)
        assertEquals(chosen, decoded.settings.toAppSettings(fontExists = false, other))
    }

    @Test
    fun everyThemeAndAccentRoundTrips() {
        for (theme in com.frameglass.data.api.ThemeId.entries) {
            for (accent in com.frameglass.data.api.AccentColor.entries) {
                val chosen = phone.copy(theme = theme, accent = accent)
                val text = BackupCodec.encode(doc.copy(settings = BackupSettings.from(chosen)))
                assertEquals(chosen, BackupCodec.decode(text).settings.toAppSettings(fontExists = false, phone))
            }
        }
    }

    /** A backup as a version before the ready themes wrote it: the old two fields and none of the new ones. */
    private fun olderBackup(themeMode: String, accent: String): String {
        val old = settings.copy(themeMode = themeMode, accent = accent)
        val json = BackupCodec.encode(doc.copy(settings = old))
            .replace(Regex(""",?\s*"(theme|followSystem|customHue|customShade|customSurface|customAccent)": null"""), "")
        for (name in listOf("\"theme\"", "followSystem", "customHue", "customShade", "customSurface", "customAccent")) {
            assertTrue(name, name !in json)
        }
        return json
    }

    @Test
    fun aBackupWrittenBeforeTheReadyThemesRestoresTheLookItHad() {
        val custom = phone.copy(customHue = 77f, customShade = 0.9f, customSurface = 0.1f, customAccent = 0xFF010203.toInt())
        fun restore(themeMode: String, accent: String) =
            BackupCodec.decode(olderBackup(themeMode, accent)).settings.toAppSettings(fontExists = false, custom)

        val dark = restore("DARK", "WHITE")
        assertEquals(com.frameglass.data.api.ThemeId.BLACK, dark.theme)
        assertFalse(dark.followSystem)
        assertEquals(com.frameglass.data.api.AccentColor.THEME, dark.accent)

        val light = restore("LIGHT", "GREEN")
        assertEquals(com.frameglass.data.api.ThemeId.LIGHT, light.theme)
        assertFalse(light.followSystem)
        assertEquals(com.frameglass.data.api.AccentColor.GREEN, light.accent)

        val follow = restore("SYSTEM", "RED")
        assertEquals(com.frameglass.data.api.ThemeId.BLACK, follow.theme)
        assertTrue(follow.followSystem)
        assertEquals(com.frameglass.data.api.AccentColor.RED, follow.accent)

        // The custom colours were never in such a backup, so the phone keeps the ones it has.
        for (back in listOf(dark, light, follow)) {
            assertEquals(77f, back.customHue)
            assertEquals(0.9f, back.customShade)
            assertEquals(0.1f, back.customSurface)
            assertEquals(0xFF010203.toInt(), back.customAccent)
        }
    }

    @Test
    fun aNewBackupStillCarriesTheOldFieldForTheNearestOldLook() {
        fun old(theme: com.frameglass.data.api.ThemeId, follow: Boolean = false) =
            BackupSettings.from(phone.copy(theme = theme, followSystem = follow)).themeMode
        assertEquals("DARK", old(com.frameglass.data.api.ThemeId.COFFEE))
        assertEquals("LIGHT", old(com.frameglass.data.api.ThemeId.PAPER))
        assertEquals("SYSTEM", old(com.frameglass.data.api.ThemeId.MIDNIGHT, follow = true))
    }

    @Test
    fun customColoursFromABackupAreBroughtIntoRange() {
        val wild = BackupSettings.from(phone).copy(customHue = 999f, customShade = -3f, customSurface = 8f, customAccent = 0x00ABCDEF)
        val back = wild.toAppSettings(fontExists = false, phone)
        assertEquals(360f, back.customHue)
        assertEquals(0f, back.customShade)
        assertEquals(1f, back.customSurface)
        assertEquals(0xFFABCDEF.toInt(), back.customAccent)
    }

    @Test
    fun rejectsABadThemeOrLockTime() {
        invalid(doc.copy(settings = settings.copy(themeMode = "NEON")))
        invalid(doc.copy(settings = settings.copy(accent = "NEON")))
        invalid(doc.copy(settings = settings.copy(theme = "NEON")))
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
