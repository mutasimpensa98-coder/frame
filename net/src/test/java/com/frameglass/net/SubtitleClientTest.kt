package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Reason
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleClientTest {
    private val config = FakeConfig(tmdb = null, openSubtitles = OS_KEY, subDl = SUBDL_KEY, subSource = SUBSOURCE_KEY)
    private val rig = Rig(config)
    private val server = rig.server
    private val folder = File(rig.dir, "subs")
    private val query = subtitleQuery(season = 1, episode = 2, tmdbId = 77, imdbId = "tt0133093", languages = listOf("ar"))

    @After
    fun clean() = rig.clean()

    private fun search(asked: SubtitleQuery = query) = blocking { rig.subtitles.search(asked) }

    private fun hosts() = server.requests.map { it.url.host }.toSet()

    /** Any of the three providers with one good row. */
    private fun offerEverything() {
        server.json(OS_SEARCH, osSearch(osItem(1)))
        server.json(SUBDL_SEARCH, subDlReply(subDlItem()))
        server.json(SUBSOURCE_MOVIES, ssMovies(ssMovie(11)))
        server.json(SUBSOURCE_LIST, ssList(ssSubtitle(501)))
    }

    @Test
    fun configuredProvidersFollowTheKeysInTheDefaultOrder() {
        assertEquals(listOf(ApiKey.OPENSUBTITLES, ApiKey.SUBDL, ApiKey.SUBSOURCE), rig.subtitles.configuredProviders)
        config.subDl = "   "
        config.openSubtitles = null
        assertEquals(listOf(ApiKey.SUBSOURCE), rig.subtitles.configuredProviders)
    }

    @Test
    fun noKeyAtAllAsksNothing() {
        config.openSubtitles = null
        config.subDl = null
        config.subSource = null
        assertEquals(Reason.NO_KEY, (search() as Failure).reason)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun onlyProvidersWithAKeyAreAsked() {
        config.subSource = null
        offerEverything()
        search()
        assertEquals(setOf("api.opensubtitles.com", "api.subdl.com"), hosts())
    }

    @Test
    fun theOnlineSwitchStopsSearchAndDownload() {
        config.onlineEnabled = false
        offerEverything()
        assertEquals(Reason.OFFLINE_SETTING, (search() as Failure).reason)
        val saved = blocking { rig.subtitles.download(subtitleHit(), null, null, folder) } as Failure
        assertEquals(Reason.OFFLINE_SETTING, saved.reason)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun providersRunAtTheSameTime() {
        offerEverything()
        val allThree = CountDownLatch(3)
        val met = CopyOnWriteArrayList<Boolean>()
        server.onRequest = { request ->
            if (!request.url.encodedPath.endsWith("/subtitles") || request.url.host != "api.subsource.net") {
                allThree.countDown()
                met += allThree.await(10, TimeUnit.SECONDS)
            }
        }
        search()
        assertEquals(listOf(true, true, true), met)
    }

    @Test
    fun oneProviderFailingKeepsTheOthers() {
        offerEverything()
        server.on(SUBDL_SEARCH, Reply(500, "down"))
        assertEquals(setOf(PROVIDER_OPENSUBTITLES, PROVIDER_SUBSOURCE), search().value().map { it.provider }.toSet())
    }

    @Test
    fun aRefusedKeyOnOneProviderKeepsTheOthers() {
        offerEverything()
        server.on(OS_SEARCH, Reply(403, "{}"))
        assertEquals(setOf(PROVIDER_SUBDL, PROVIDER_SUBSOURCE), search().value().map { it.provider }.toSet())
    }

    @Test
    fun aBrokenReplyFromOneProviderKeepsTheOthers() {
        offerEverything()
        server.json(SUBSOURCE_MOVIES, "<html>maintenance</html>")
        assertEquals(setOf(PROVIDER_OPENSUBTITLES, PROVIDER_SUBDL), search().value().map { it.provider }.toSet())
    }

    @Test
    fun oneFailedLanguageKeepsTheOther() {
        server.json(SUBDL_SEARCH, subDlReply(subDlItem()))
        config.openSubtitles = null
        config.subSource = null
        server.on(SUBDL_SEARCH, Reply(500, "down"), Reply(body = subDlReply(subDlItem())))
        assertEquals(1, search(query.copy(languages = listOf("ar", "en"))).value().size)
    }

    @Test
    fun anEmptyAnswerFromAllIsAnEmptyListAndNotAFailure() {
        server.json(OS_SEARCH, osSearch())
        server.json(SUBDL_SEARCH, subDlReply())
        server.json(SUBSOURCE_MOVIES, ssMovies())
        assertEquals(emptyList<SubtitleHit>(), search().value())
    }

    @Test
    fun aFailureComesBackOnlyWhenEveryProviderFailed() {
        server.on(OS_SEARCH, Reply(500, "down"))
        server.on(SUBDL_SEARCH, Reply(500, "down"))
        server.on(SUBSOURCE_MOVIES, Reply(500, "down"))
        assertEquals(Reason.NETWORK, (search() as Failure).reason)
    }

    @Test
    fun aStalledProviderDoesNotHoldBackTheOthers() {
        val impatient = FrameSubtitleClient(config, rig.http, File(rig.dir, "scratch"), deadlineMs = 2_000)
        offerEverything()
        val release = CountDownLatch(1)
        server.onRequest = { request ->
            if (request.url.host == "api.subdl.com") release.await(10, TimeUnit.SECONDS)
        }
        val hits = blocking { impatient.search(query) }.value()
        release.countDown()
        assertEquals(setOf(PROVIDER_OPENSUBTITLES, PROVIDER_SUBSOURCE), hits.map { it.provider }.toSet())
    }

    @Test
    fun acrossProvidersAHashMatchComesFirstThenArabicThenEnglish() {
        server.json(SUBSOURCE_MOVIES, ssMovies(ssMovie(11)))
        server.answer = { request ->
            val language = request.url.queryParameter("languages") ?: request.url.queryParameter("language")
            when (request.url.host to language) {
                "api.opensubtitles.com" to "en" -> Reply(body = osSearch(osItem(1, hashMatch = true, downloads = 1)))
                "api.opensubtitles.com" to "ar" -> Reply(body = osSearch(osItem(2, downloads = 5)))
                "api.subdl.com" to "AR" -> Reply(body = subDlReply(subDlItem(url = "/subtitle/ar.zip")))
                "api.subdl.com" to "EN" -> Reply(body = subDlReply(subDlItem(url = "/subtitle/en.zip")))
                "api.subsource.net" to "arabic" -> Reply(body = ssList(ssSubtitle(501, downloads = 900)))
                "api.subsource.net" to "english" -> Reply(body = ssList(ssSubtitle(502, downloads = 900_000)))
                else -> null
            }
        }
        val ranked = search(query.copy(languages = listOf("en", "ar"))).value()
        assertEquals(
            listOf("1", "501", "2", "/subtitle/ar.zip", "502", "/subtitle/en.zip"),
            ranked.map { it.id },
        )
        assertEquals(listOf("en", "ar", "ar", "ar", "en", "en"), ranked.map { it.language })
    }

    @Test
    fun aRepeatedRowFromOneProviderIsShownOnce() {
        server.json(OS_SEARCH, osSearch(osItem(1), osItem(1)))
        config.subDl = null
        config.subSource = null
        assertEquals(1, search().value().size)
    }

    @Test
    fun anUnknownProviderCannotBeDownloaded() {
        val hit = subtitleHit(provider = "Other")
        assertEquals(Reason.BAD_RESPONSE, (blocking { rig.subtitles.download(hit, null, null, folder) } as Failure).reason)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun aProviderThatLostItsKeyCannotDownload() {
        config.openSubtitles = null
        assertEquals(Reason.NO_KEY, (blocking { rig.subtitles.download(subtitleHit(), null, null, folder) } as Failure).reason)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun aDownloadLeavesOneFileAndNoPartsBehind() {
        server.on("dl.subdl.com/subtitle/1.zip", Reply(bytes = zipOf("a.S01E02.srt" to SRT_TEXT, "a.S01E03.srt" to SRT_TEXT)))
        val hit = subtitleHit(PROVIDER_SUBDL, id = "/subtitle/1.zip", seasonPack = true)
        val saved = blocking { rig.subtitles.download(hit, 1, 2, folder) }.value()
        assertEquals(listOf(saved.name), folder.list().orEmpty().toList())
        assertEquals(emptyList<String>(), File(rig.dir, "scratch").list().orEmpty().toList())
    }

    @Test
    fun aPackWithoutTheEpisodeReportsNotFoundAndKeepsNothing() {
        server.on("dl.subdl.com/subtitle/1.zip", Reply(bytes = zipOf("a.S01E03.srt" to SRT_TEXT, "a.S01E04.srt" to SRT_TEXT)))
        val hit = subtitleHit(PROVIDER_SUBDL, id = "/subtitle/1.zip", seasonPack = true)
        assertEquals(Reason.NOT_FOUND, (blocking { rig.subtitles.download(hit, 1, 2, folder) } as Failure).reason)
        assertFalse(folder.list().orEmpty().any())
        assertEquals(emptyList<String>(), File(rig.dir, "scratch").list().orEmpty().toList())
    }
}

class SubtitleRankingTest {
    private fun rank(vararg hits: SubtitleHit, releaseName: String? = null): List<SubtitleHit> =
        SubtitleRanking.rank(
            hits.toList(),
            subtitleQuery(releaseName = releaseName),
            listOf("ar", "en"),
            listOf(PROVIDER_OPENSUBTITLES, PROVIDER_SUBDL, PROVIDER_SUBSOURCE),
        )

    private fun ids(ranked: List<SubtitleHit>) = ranked.map { it.id }

    @Test
    fun anEmptyListAsksForArabicAndEnglish() = assertEquals(listOf("ar", "en"), SubtitleRanking.languages(emptyList()))

    @Test
    fun arabicIsFirstAndEnglishSecondWhateverTheCallerSent() {
        assertEquals(listOf("ar", "en", "fr"), SubtitleRanking.languages(listOf("fr", "en", "ar")))
    }

    @Test
    fun onlyAskedLanguagesAreSearched() = assertEquals(listOf("ar"), SubtitleRanking.languages(listOf("ar")))

    @Test
    fun languageNamesAndRegionsCollapseToOneTag() {
        assertEquals(listOf("ar", "en"), SubtitleRanking.languages(listOf("ENG", "Arabic", "ar-LY", "ara", "en_US", " ")))
    }

    @Test
    fun aHashMatchBeatsLanguage() {
        val ranked = rank(subtitleHit(id = "ar", language = "ar"), subtitleHit(id = "hash", language = "en", hashMatch = true))
        assertEquals(listOf("hash", "ar"), ids(ranked))
    }

    @Test
    fun amongHashMatchesArabicIsFirst() {
        val ranked = rank(
            subtitleHit(id = "en", language = "en", hashMatch = true),
            subtitleHit(id = "ar", language = "ar", hashMatch = true),
        )
        assertEquals(listOf("ar", "en"), ids(ranked))
    }

    @Test
    fun arabicBeatsEnglishWhateverTheDownloads() {
        val ranked = rank(subtitleHit(id = "en", language = "en", downloads = 90_000), subtitleHit(id = "ar", downloads = 1))
        assertEquals(listOf("ar", "en"), ids(ranked))
    }

    @Test
    fun aReleaseCloseToTheFileBeatsDownloads() {
        val ranked = rank(
            subtitleHit(id = "popular", release = "Show.S01E02.720p.HDTV-OTHER", downloads = 900),
            subtitleHit(id = "close", release = "Show.S01E02.1080p.WEB-DL-GROUP", downloads = 1),
            releaseName = "Show.S01E02.1080p.WEB-DL-GROUP",
        )
        assertEquals(listOf("close", "popular"), ids(ranked))
    }

    @Test
    fun aSingleFileBeatsAPack() {
        val ranked = rank(subtitleHit(id = "pack", seasonPack = true, downloads = 900), subtitleHit(id = "single", downloads = 1))
        assertEquals(listOf("single", "pack"), ids(ranked))
    }

    @Test
    fun moreDownloadsRankHigherAndUnknownCountsLast() {
        val ranked = rank(subtitleHit(id = "none"), subtitleHit(id = "few", downloads = 2), subtitleHit(id = "many", downloads = 50))
        assertEquals(listOf("many", "few", "none"), ids(ranked))
    }

    @Test
    fun theDefaultProviderOrderSettlesATie() {
        val ranked = rank(
            subtitleHit(PROVIDER_SUBSOURCE, id = "c"),
            subtitleHit(PROVIDER_SUBDL, id = "b"),
            subtitleHit(PROVIDER_OPENSUBTITLES, id = "a"),
        )
        assertEquals(listOf("a", "b", "c"), ids(ranked))
    }

    @Test
    fun theSameHitIsKeptOnce() {
        assertEquals(1, rank(subtitleHit(), subtitleHit()).size)
    }
}
