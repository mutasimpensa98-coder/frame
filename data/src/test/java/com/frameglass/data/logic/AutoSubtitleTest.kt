package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.AutoSubtitleMode
import com.frameglass.data.api.SubtitleResult
import com.frameglass.data.repo.AutoTried
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The automatic subtitle download of the waiver of 2026-11-08: what goes out, what is picked, and once per file. */
class AutoSubtitleTest {
    private val facts = SubtitleFacts(
        title = "Quiet Coast", year = 2024, season = 2, episode = 3, tmdbId = 77, imdbId = "tt7",
        osHash = "0123456789abcdef", fileSize = 4_000, releaseName = "Quiet.Coast.S02E03.1080p.WEB.GRP",
    )

    private fun result(
        provider: String = "SubDL",
        token: String = "1",
        language: String = "ar",
        release: String = "Quiet.Coast.S02E03.WEB",
        seasonPack: Boolean = false,
        downloads: Int? = null,
    ) = SubtitleResult(token, provider, language, release, hashMatch = false, downloads, "srt", seasonPack)

    @Test
    fun theAutomaticQueryNeverCarriesTheReleaseNameOrTheHash() {
        val q = SubtitleQueries.automatic(facts, listOf("ar"))
        assertNull(q.releaseName)
        assertNull(q.osHash)
        assertNull(q.fileSize)
        assertTrue(q.automatic)
        assertEquals("Quiet Coast", q.title)
        assertEquals(2024, q.year)
        assertEquals(2, q.season)
        assertEquals(3, q.episode)
        assertEquals(77L, q.tmdbId)
        assertEquals("tt7", q.imdbId)
        assertEquals(listOf("ar"), q.languages)
    }

    @Test
    fun aTappedQueryIsNotAutomatic() {
        assertFalse(SubtitleQueries.build(facts, AppSettings()).automatic)
    }

    @Test
    fun aTapOnlySiteIsNeverPicked() {
        val picked = AutoSubtitlePick.ranked(
            listOf(result(provider = "OpenSubtitles", token = "9", downloads = 999), result(provider = "SubDL")),
            localName = null,
            languages = listOf("ar"),
            tapOnly = setOf("OpenSubtitles"),
        )
        assertEquals(listOf("SubDL"), picked.map { it.provider })
    }

    @Test
    fun arabicComesBeforeEnglishAndOtherLanguagesAreDropped() {
        val picked = AutoSubtitlePick.ranked(
            listOf(result(token = "en", language = "en"), result(token = "fr", language = "fr"), result(token = "ar", language = "ara")),
            localName = null,
            languages = listOf("ar", "en"),
            tapOnly = emptySet(),
        )
        assertEquals(listOf("ar", "en"), picked.map { it.token })
    }

    @Test
    fun englishIsNotPickedWhenOnlyArabicIsWanted() {
        val picked = AutoSubtitlePick.ranked(listOf(result(language = "en")), null, listOf("ar"), emptySet())
        assertTrue(picked.isEmpty())
    }

    @Test
    fun aFullTrackBeatsForcedAndPlainBeatsSdh() {
        val picked = AutoSubtitlePick.ranked(
            listOf(
                result(token = "forced", release = "Quiet.Coast.S02E03.FORCED"),
                result(token = "sdh", release = "Quiet.Coast.S02E03.SDH"),
                result(token = "plain", release = "Quiet.Coast.S02E03"),
            ),
            localName = null,
            languages = listOf("ar"),
            tapOnly = emptySet(),
        )
        assertEquals(listOf("plain", "sdh", "forced"), picked.map { it.token })
    }

    @Test
    fun aReleaseCloseToTheLocalNameRanksFirst() {
        val picked = AutoSubtitlePick.ranked(
            listOf(
                result(token = "other", release = "Quiet.Coast.S02E03.720p.HDTV.XYZ", downloads = 500),
                result(token = "close", release = "Quiet.Coast.S02E03.1080p.WEB.GRP", downloads = 10),
            ),
            localName = facts.releaseName,
            languages = listOf("ar"),
            tapOnly = emptySet(),
        )
        assertEquals("close", picked.first().token)
    }

    @Test
    fun aSingleFileBeatsAPackThenDownloadsDecide() {
        val picked = AutoSubtitlePick.ranked(
            listOf(
                result(token = "pack", release = "x", seasonPack = true, downloads = 900),
                result(token = "few", release = "x", downloads = 5),
                result(token = "many", release = "x", downloads = 50),
            ),
            localName = null,
            languages = listOf("ar"),
            tapOnly = emptySet(),
        )
        assertEquals(listOf("many", "few", "pack"), picked.map { it.token })
    }

    @Test
    fun theSettingParsesWithADefault() {
        assertEquals(AutoSubtitleMode.ARABIC, AppSettings().autoSubtitles)
        assertEquals(AutoSubtitleMode.OFF, AutoSubtitleMode.parse("OFF"))
        assertEquals(AutoSubtitleMode.ARABIC_THEN_ENGLISH, AutoSubtitleMode.parse("ARABIC_THEN_ENGLISH"))
        assertEquals(AutoSubtitleMode.ARABIC, AutoSubtitleMode.parse(null))
        assertEquals(AutoSubtitleMode.ARABIC, AutoSubtitleMode.parse("SOMETHING_NEWER"))
    }

    @Test
    fun aFileIsRememberedOnceTried() {
        val dir = Files.createTempDirectory("auto-tried").toFile()
        try {
            val tried = AutoTried(dir)
            assertFalse(tried.wasTried("file-a"))
            tried.markTried("file-a")
            assertTrue(tried.wasTried("file-a"))
            assertFalse(tried.wasTried("file-b"))
            // A new instance reads the same folder, as after the app restarts.
            assertTrue(AutoTried(dir).wasTried("file-a"))
            tried.markTried("file-a")
            assertEquals(1, dir.listFiles()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aKeyWithSlashesStaysInsideTheFolder() {
        val dir = Files.createTempDirectory("auto-tried").toFile()
        try {
            AutoTried(dir).markTried("../../outside/key")
            assertEquals(1, dir.listFiles()!!.size)
            assertTrue(dir.listFiles()!!.single().isFile)
        } finally {
            dir.deleteRecursively()
        }
    }
}
