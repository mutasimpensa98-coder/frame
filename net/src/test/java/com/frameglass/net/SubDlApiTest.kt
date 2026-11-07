package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Reason
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubDlApiTest {
    private val rig = Rig(FakeConfig(tmdb = null, subDl = SUBDL_KEY))
    private val server = rig.server
    private val folder = File(rig.dir, "subs")
    private val episode = subtitleQuery(season = 1, episode = 2, tmdbId = 77, languages = listOf("ar"))

    @After
    fun clean() = rig.clean()

    private fun search(query: SubtitleQuery = episode) = blocking { rig.subtitles.search(query) }

    private fun reply(vararg items: String) = server.json(SUBDL_SEARCH, subDlReply(*items))

    private fun parameter(name: String) = server.requests.last().url.queryParameter(name)

    @Test
    fun aShowIsSearchedByItsTmdbId() {
        reply()
        search()
        assertEquals("77", parameter("tmdb_id"))
        assertEquals("tv", parameter("type"))
        assertEquals("1", parameter("season_number"))
        assertEquals("2", parameter("episode_number"))
        assertEquals("AR", parameter("languages"))
        assertEquals("30", parameter("subs_per_page"))
        assertEquals(SUBDL_KEY, parameter("api_key"))
        assertNull(parameter("film_name"))
    }

    @Test
    fun aMovieIsSearchedAsAMovie() {
        reply()
        search(subtitleQuery(tmdbId = 603, languages = listOf("en")))
        assertEquals("603", parameter("tmdb_id"))
        assertEquals("movie", parameter("type"))
        assertEquals("EN", parameter("languages"))
        assertNull(parameter("season_number"))
    }

    @Test
    fun eachLanguageIsItsOwnRequest() {
        reply()
        search(subtitleQuery(tmdbId = 77))
        assertEquals(setOf("AR", "EN"), server.requests.map { it.url.queryParameter("languages") }.toSet())
    }

    @Test
    fun theImdbIdStandsInWhenThereIsNoTmdbId() {
        reply()
        search(subtitleQuery(imdbId = "tt0133093", languages = listOf("ar")))
        assertEquals("tt0133093", parameter("imdb_id"))
        assertNull(parameter("tmdb_id"))
    }

    @Test
    fun aTitleSearchReplacesWhatTheApiRefusesWithASpace() {
        reply()
        search(subtitleQuery(title = "Grey's Anatomy", languages = listOf("ar")))
        assertEquals("Grey s Anatomy", parameter("film_name"))
        assertEquals("2024", parameter("year"))
    }

    @Test
    fun aQueryThatNamesNothingAsksNothing() {
        assertEquals(emptyList<SubtitleHit>(), search(subtitleQuery(title = "")).value())
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun neitherTheHashNorTheSizeNorTheReleaseNameLeavesThePhoneToThisProvider() {
        reply()
        search(
            subtitleQuery(
                title = "Quiet",
                languages = listOf("ar"),
                osHash = "8e245d9679d31e12",
                fileSize = 734003200,
                releaseName = "Private.Name-GROUP",
            ),
        )
        val sent = server.requests.single().url.toString().lowercase()
        assertFalse("8e245d9679d31e12" in sent)
        assertFalse("734003200" in sent)
        assertFalse("private" in sent)
    }

    @Test
    fun aReplyBecomesHits() {
        reply(subDlItem())
        val hit = search().value().single()
        assertEquals(PROVIDER_SUBDL, hit.provider)
        assertEquals("/subtitle/3197651-3213944.zip", hit.id)
        assertEquals("ar", hit.language)
        assertEquals("Quiet.Coast.S01E02.1080p.WEB-DL.x265-GROUP", hit.release)
        assertFalse(hit.hashMatch)
        assertNull(hit.downloads)
        assertFalse(hit.seasonPack)
    }

    @Test
    fun aMissingReleaseFieldFallsBackToTheReleasesList() {
        reply(subDlItem(release = "", releases = listOf("From.The.List.S01E02")))
        assertEquals("From.The.List.S01E02", search().value().single().release)
    }

    @Test
    fun anotherEpisodeOrSeasonIsDropped() {
        reply(subDlItem(episode = "3"), subDlItem(url = "/subtitle/2.zip", season = "2"), subDlItem(url = "/subtitle/3.zip"))
        assertEquals(listOf("/subtitle/3.zip"), search().value().map { it.id })
    }

    @Test
    fun aFullSeasonIsAPack() {
        reply(subDlItem(release = "Quiet.Coast.S01.Complete", episode = "null", fullSeason = true))
        assertTrue(search().value().single().seasonPack)
    }

    @Test
    fun aRangeIsAPackOnlyWhenItHoldsTheEpisode() {
        reply(
            subDlItem(url = "/subtitle/in.zip", episode = "null", from = "1", end = "6"),
            subDlItem(url = "/subtitle/out.zip", episode = "null", from = "7", end = "12"),
        )
        val hit = search().value().single()
        assertEquals("/subtitle/in.zip", hit.id)
        assertTrue(hit.seasonPack)
    }

    @Test
    fun whenTheFieldsAreEmptyTheReleaseNameDecides() {
        reply(
            subDlItem(url = "/subtitle/right.zip", season = "0", episode = "0"),
            subDlItem(url = "/subtitle/wrong.zip", release = "Quiet.Coast.S01E09.WEB", season = "0", episode = "null"),
            subDlItem(url = "/subtitle/pack.zip", release = "Quiet.Coast.S01.WEB", season = "0", episode = "null"),
        )
        val hits = search().value().associateBy { it.id }
        assertEquals(setOf("/subtitle/right.zip", "/subtitle/pack.zip"), hits.keys)
        assertFalse(hits.getValue("/subtitle/right.zip").seasonPack)
        assertTrue(hits.getValue("/subtitle/pack.zip").seasonPack)
    }

    @Test
    fun aMovieKeepsEveryRow() {
        reply(subDlItem(season = "0", episode = "null"))
        assertEquals(1, search(subtitleQuery(tmdbId = 603, languages = listOf("ar"))).value().size)
    }

    @Test
    fun aReplyCannotChooseTheDownloadHost() {
        reply(
            subDlItem(url = "https://files.example.com/a.zip"),
            subDlItem(url = "//files.example.com/a.zip"),
            subDlItem(url = "/subtitle/../../a.zip"),
            subDlItem(url = "/subtitle/kept.zip"),
        )
        assertEquals(listOf("/subtitle/kept.zip"), search().value().map { it.id })
    }

    @Test
    fun nothingFoundIsAnEmptyResultAndNotAFailure() {
        server.json(SUBDL_SEARCH, """{"status":false,"error":"can't find movie or tv"}""")
        assertEquals(emptyList<SubtitleHit>(), search().value())
    }

    @Test
    fun aFalseStatusThatRefusesTheKeyReportsNoKey() {
        server.json(SUBDL_SEARCH, """{"status":false,"error":"Not Authorized"}""")
        assertEquals(Reason.NO_KEY, (search() as Failure).reason)
    }

    @Test
    fun theProviders403ReportsNoKey() {
        server.on(SUBDL_SEARCH, Reply(403, """{"status":false,"statusCode":403,"error":"not_authorized","message":"Not Authorized"}"""))
        assertEquals(Reason.NO_KEY, (search() as Failure).reason)
    }

    @Test
    fun anUnknownFalseStatusIsABadReply() {
        server.json(SUBDL_SEARCH, """{"status":false,"error":"something else"}""")
        assertEquals(Reason.BAD_RESPONSE, (search() as Failure).reason)
    }

    @Test
    fun aDownloadUsesTheFixedHostAndSendsNoKey() {
        server.on("dl.subdl.com/subtitle/1-2.zip", Reply(bytes = zipOf("Quiet.Coast.S01E02.srt" to SRT_TEXT)))
        val hit = subtitleHit(PROVIDER_SUBDL, id = "/subtitle/1-2.zip")
        val saved = blocking { rig.subtitles.download(hit, 1, 2, folder) }.value()
        assertEquals(SRT_TEXT, saved.readText())
        val sent = server.requests.single()
        assertEquals("dl.subdl.com", sent.url.host)
        assertNull(sent.url.queryParameter("api_key"))
    }

    @Test
    fun aHitThatIsNotAPathIsRefusedBeforeAnyCall() {
        val hit = subtitleHit(PROVIDER_SUBDL, id = "https://files.example.com/a.zip")
        assertEquals(Reason.BAD_RESPONSE, (blocking { rig.subtitles.download(hit, 1, 2, folder) } as Failure).reason)
        assertTrue(server.requests.isEmpty())
    }
}
