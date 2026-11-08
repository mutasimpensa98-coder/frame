package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Reason
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestdownApiTest {
    private val config = FakeConfig(tmdb = null)
    private val rig = Rig(config, keyless = true)
    private val server = rig.server
    private val folder = File(rig.dir, "subs")
    private val episode = subtitleQuery(season = 1, episode = 2, tmdbId = 77, languages = listOf("ar"))

    @After
    fun clean() = rig.clean()

    private fun search(query: SubtitleQuery = episode) = blocking { rig.subtitles.search(query) }

    private fun listings() = server.requests.filter { "/subtitles/get/" in it.url.encodedPath }

    @Test
    fun anEpisodeNeedsNoKey() {
        server.json(GD_SHOWS, gdShows(gdShow()))
        server.json(gdEpisodePath(1, 2, "ar"), gdEpisode(gdSubtitle()))
        val hit = search().value().single()
        assertEquals(PROVIDER_GESTDOWN, hit.provider)
        assertEquals("ar", hit.language)
        assertEquals("WEB-DL GROUP", hit.release)
        assertEquals(40, hit.downloads)
        assertEquals("srt", hit.format)
        assertFalse(hit.hashMatch)
        assertFalse(hit.seasonPack)
    }

    @Test
    fun theShowsTmdbIdBeatsItsName() {
        server.json(GD_SHOWS, gdShows(gdShow(id = "otherShow1", tmdb = "88"), gdShow(id = "rightShow1", name = "Quiet Coast (US)")))
        server.json(gdEpisodePath(1, 2, "ar", show = "rightShow1"), gdEpisode(gdSubtitle()))
        assertEquals(1, search().value().size)
        assertEquals(listOf(gdEpisodePath(1, 2, "ar", show = "rightShow1")), listings().map { it.url.host + it.url.encodedPath })
    }

    @Test
    fun aShowLinkedToAnotherTmdbIdIsNeverUsed() {
        server.json(GD_SHOWS, gdShows(gdShow(tmdb = "88")))
        assertEquals(emptyList<SubtitleHit>(), search().value())
        assertTrue(listings().isEmpty())
    }

    @Test
    fun withNoTmdbIdTheNameMustPointAtOneShow() {
        server.json(GD_SHOWS, gdShows(gdShow(id = "usShow1", name = "Quiet Coast (US)"), gdShow(id = "ukShow1", name = "Quiet Coast (UK)")))
        assertEquals(emptyList<SubtitleHit>(), search(episode.copy(tmdbId = null)).value())
        assertTrue(listings().isEmpty())

        server.json(GD_SHOWS, gdShows(gdShow(tmdb = "null"), gdShow(id = "minis1", name = "Quiet Coast Minisodes", tmdb = "null")))
        server.json(gdEpisodePath(1, 2, "ar"), gdEpisode(gdSubtitle()))
        assertEquals(1, search(episode.copy(tmdbId = null)).value().size)
    }

    @Test
    fun aShowWithoutThatSeasonIsNotUsed() {
        server.json(GD_SHOWS, gdShows(gdShow(tmdb = "null", seasons = listOf(1))))
        assertEquals(emptyList<SubtitleHit>(), search(episode.copy(season = 3, tmdbId = null)).value())
        assertTrue(listings().isEmpty())
    }

    @Test
    fun aFileWithNoSeasonAsksForTheFirst() {
        server.json(GD_SHOWS, gdShows(gdShow()))
        server.json(gdEpisodePath(1, 5, "ar"), gdEpisode(gdSubtitle()))
        assertEquals(1, search(episode.copy(season = null, episode = 5)).value().size)
    }

    @Test
    fun eachLanguageIsItsOwnRequest() {
        server.json(GD_SHOWS, gdShows(gdShow()))
        server.json(gdEpisodePath(1, 2, "ar"), gdEpisode(gdSubtitle(id = "arabicSub1")))
        server.json(gdEpisodePath(1, 2, "en"), gdEpisode(gdSubtitle(id = "englishSub1")))
        val hits = search(episode.copy(languages = listOf("en", "ar"))).value()
        assertEquals(listOf("arabicSub1" to "ar", "englishSub1" to "en"), hits.map { it.id to it.language })
    }

    @Test
    fun aFilmOrAFileWithNoEpisodeNeverReachesIt() {
        assertEquals(Reason.NO_KEY, (search(subtitleQuery(tmdbId = 603)) as Failure).reason)
        assertEquals(Reason.NO_KEY, (search(episode.copy(episode = null)) as Failure).reason)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun noReleaseNameAndNoHashGoOut() {
        server.json(GD_SHOWS, gdShows(gdShow()))
        server.json(gdEpisodePath(1, 2, "ar"), gdEpisode(gdSubtitle()))
        val hash = "0123456789abcdef"
        search(episode.copy(releaseName = "Quiet.Coast.S01E02.1080p.WEB-DL.x265-GROUP", osHash = hash, fileSize = 123_456_789))
        val sent = server.requests.joinToString(" ") { it.url.toString() + it.headers.toString() }.lowercase()
        for (secret in listOf("1080p", "x265", hash, "123456789")) assertFalse(secret, secret in sent)
        assertEquals(setOf("User-Agent", "Accept"), server.requests.flatMap { it.headers.names() }.toSet())
    }

    @Test
    fun anUnfinishedTranslationIsLeftOut() {
        server.json(GD_SHOWS, gdShows(gdShow()))
        server.json(gdEpisodePath(1, 2, "ar"), gdEpisode(gdSubtitle(completed = false), gdSubtitle(id = "doneSub1")))
        assertEquals(listOf("doneSub1"), search().value().map { it.id })
    }

    @Test
    fun anIdThatIsNotPlainTextNeverReachesAPath() {
        server.json(GD_SHOWS, gdShows(gdShow(id = "../admin")))
        assertEquals(emptyList<SubtitleHit>(), search().value())
        assertTrue(listings().isEmpty())

        server.json(GD_SHOWS, gdShows(gdShow()))
        server.json(gdEpisodePath(1, 2, "ar"), gdEpisode(gdSubtitle(id = "a/../b")))
        assertEquals(emptyList<SubtitleHit>(), search().value())
    }

    @Test
    fun anUnknownLanguageOrShowIsEmptyAndNotAFailure() {
        server.json(GD_SHOWS, gdShows(gdShow()))
        server.on(gdEpisodePath(1, 2, "ar"), Reply(423, ""))
        assertEquals(emptyList<SubtitleHit>(), search().value())

        server.on(GD_SHOWS, Reply(404, ""))
        assertEquals(emptyList<SubtitleHit>(), search().value())
    }

    @Test
    fun aServerErrorIsAFailureWhenNoOtherSiteAnswers() {
        server.on(GD_SHOWS, Reply(500, "down"))
        assertEquals(Reason.NETWORK, (search() as Failure).reason)
    }

    @Test
    fun aDownloadIsTheSrtFromTheApiHost() {
        server.on("$GD_HOST/subtitles/download/7SubOne0000000000000a", Reply(body = SRT_TEXT))
        val hit = subtitleHit(PROVIDER_GESTDOWN, id = "7SubOne0000000000000a", format = "srt")
        val saved = blocking { rig.subtitles.download(hit, 1, 2, folder) }.value()
        assertEquals("srt", saved.extension)
        assertEquals(SRT_TEXT, saved.readText())
        val request = server.requests.single()
        assertEquals(setOf("User-Agent", "Accept"), request.headers.names())
        assertEquals("*/*", request.header("Accept"))
    }

    @Test
    fun aDownloadWithABadIdAsksNothing() {
        val hit = subtitleHit(PROVIDER_GESTDOWN, id = "a/b")
        assertEquals(Reason.BAD_RESPONSE, (blocking { rig.subtitles.download(hit, 1, 2, folder) } as Failure).reason)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun onlineOffAsksNothing() {
        config.onlineEnabled = false
        assertEquals(Reason.OFFLINE_SETTING, (search() as Failure).reason)
        val hit = subtitleHit(PROVIDER_GESTDOWN, id = "7SubOne0000000000000a")
        assertEquals(Reason.OFFLINE_SETTING, (blocking { rig.subtitles.download(hit, 1, 2, folder) } as Failure).reason)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun theSiteListNamesEverySiteAndItsKey() {
        assertEquals(
            listOf(
                SubtitleSite(PROVIDER_OPENSUBTITLES, ApiKey.OPENSUBTITLES, episodesOnly = false, tapOnly = true),
                SubtitleSite(PROVIDER_SUBDL, ApiKey.SUBDL, episodesOnly = false),
                SubtitleSite(PROVIDER_SUBSOURCE, ApiKey.SUBSOURCE, episodesOnly = false),
                SubtitleSite(PROVIDER_GESTDOWN, null, episodesOnly = true),
            ),
            rig.subtitles.sites,
        )
        assertEquals(emptyList<ApiKey>(), rig.subtitles.configuredProviders)
    }
}
