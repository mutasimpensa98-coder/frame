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

class SubSourceApiTest {
    private val rig = Rig(FakeConfig(tmdb = null, subSource = SUBSOURCE_KEY))
    private val server = rig.server
    private val folder = File(rig.dir, "subs")
    private val episode = subtitleQuery(season = 2, episode = 2, imdbId = "tt0133093", languages = listOf("ar"))

    @After
    fun clean() = rig.clean()

    private fun search(query: SubtitleQuery = episode) = blocking { rig.subtitles.search(query) }

    private fun listing() = server.requests.filter { it.url.encodedPath.endsWith("/subtitles") }

    private fun offerSeason(vararg subtitles: String) {
        server.json(SUBSOURCE_MOVIES, ssMovies(ssMovie(11, season = "1"), ssMovie(12, season = "2")))
        server.json(SUBSOURCE_LIST, ssList(*subtitles))
    }

    @Test
    fun aShowFindsItsSeasonByImdbThenListsSubtitles() {
        offerSeason()
        search()
        val lookup = server.hits(SUBSOURCE_MOVIES).single().url
        assertEquals("imdb", lookup.queryParameter("searchType"))
        assertEquals("tt0133093", lookup.queryParameter("imdb"))

        val list = listing().single().url
        assertEquals("12", list.queryParameter("movieId"))
        assertEquals("arabic", list.queryParameter("language"))
        assertEquals("100", list.queryParameter("limit"))
        assertEquals("2", list.queryParameter("seasonNumber"))
        assertEquals("2", list.queryParameter("episodeNumber"))
    }

    @Test
    fun oneLookupServesEveryLanguage() {
        offerSeason()
        search(episode.copy(languages = listOf("ar", "en")))
        assertEquals(1, server.hits(SUBSOURCE_MOVIES).size)
        assertEquals(setOf("arabic", "english"), listing().map { it.url.queryParameter("language") }.toSet())
    }

    @Test
    fun theKeyStaysInTheHeader() {
        offerSeason(ssSubtitle(1))
        search()
        for (request in server.requests) {
            assertEquals(SUBSOURCE_KEY, request.header("X-API-Key"))
            assertEquals(SUBTITLE_USER_AGENT, request.header("User-Agent"))
            assertFalse(SUBSOURCE_KEY in request.url.toString())
        }
    }

    @Test
    fun neitherTheHashNorTheSizeNorTheReleaseNameLeavesThePhoneToThisProvider() {
        offerSeason()
        search(episode.copy(osHash = "8e245d9679d31e12", fileSize = 734003200, releaseName = "Private.Name-GROUP"))
        val sent = server.requests.joinToString(" ") { it.url.toString() }.lowercase()
        assertFalse("8e245d9679d31e12" in sent)
        assertFalse("734003200" in sent)
        assertFalse("private" in sent)
    }

    @Test
    fun withNoImdbIdATextLookupFindsTheTitleByTmdbId() {
        server.json(
            SUBSOURCE_MOVIES,
            ssMovies(ssMovie(21, tmdb = "5", title = "Quiet Coast"), ssMovie(22, tmdb = "603", title = "Other Name", season = "null")),
        )
        server.json(SUBSOURCE_LIST, ssList())
        search(subtitleQuery(tmdbId = 603, languages = listOf("ar")))
        val lookup = server.hits(SUBSOURCE_MOVIES).single().url
        assertEquals("text", lookup.queryParameter("searchType"))
        assertEquals("Quiet Coast", lookup.queryParameter("q"))
        assertEquals("2024", lookup.queryParameter("year"))
        assertEquals("22", listing().single().url.queryParameter("movieId"))
    }

    @Test
    fun anEpisodeTextLookupSendsTheSeasonAndNotTheYear() {
        server.json(SUBSOURCE_MOVIES, ssMovies(ssMovie(21, tmdb = "null")))
        server.json(SUBSOURCE_LIST, ssList())
        search(subtitleQuery(season = 1, episode = 2, languages = listOf("ar")))
        val lookup = server.hits(SUBSOURCE_MOVIES).single().url
        assertEquals("1", lookup.queryParameter("season"))
        assertNull(lookup.queryParameter("year"))
        assertEquals("21", listing().single().url.queryParameter("movieId"))
    }

    @Test
    fun aSameNamedTitleFromAnotherYearIsNotAccepted() {
        server.json(SUBSOURCE_MOVIES, ssMovies(ssMovie(21, year = 1999, season = "null", imdb = "tt1", tmdb = "1")))
        assertEquals(emptyList<SubtitleHit>(), search(subtitleQuery(languages = listOf("ar"))).value())
        assertTrue(listing().isEmpty())
    }

    @Test
    fun anImdbReplyThatDoesNotEchoTheIdIsStillTrusted() {
        server.json(SUBSOURCE_MOVIES, """{"data":[{"movieId":31,"title":"Different Spelling"}]}""")
        server.json(SUBSOURCE_LIST, ssList())
        search(subtitleQuery(title = "Quiet", imdbId = "tt0133093", languages = listOf("ar")))
        assertEquals("31", listing().single().url.queryParameter("movieId"))
    }

    @Test
    fun anEmptyImdbLookupFallsBackToText() {
        server.on(
            SUBSOURCE_MOVIES,
            Reply(body = ssMovies()),
            Reply(body = ssMovies(ssMovie(41, imdb = "tt7", tmdb = "null", season = "2"))),
        )
        server.json(SUBSOURCE_LIST, ssList())
        search()
        assertEquals(listOf("imdb", "text"), server.hits(SUBSOURCE_MOVIES).map { it.url.queryParameter("searchType") })
        assertEquals("41", listing().single().url.queryParameter("movieId"))
    }

    @Test
    fun aTitleNobodyHasListsNothing() {
        server.json(SUBSOURCE_MOVIES, ssMovies())
        assertEquals(emptyList<SubtitleHit>(), search().value())
        assertTrue(listing().isEmpty())
    }

    @Test
    fun aRefusedKeyReportsNoKey() {
        server.on(SUBSOURCE_MOVIES, Reply(401, """{"error":"API key required","message":"Please provide an API key"}"""))
        assertEquals(Reason.NO_KEY, (search() as Failure).reason)
    }

    @Test
    fun aReplyBecomesHits() {
        offerSeason(ssSubtitle(501, listOf("Quiet.Coast.S02E02.WEB-DL-GROUP"), downloads = 40))
        val hit = search().value().single()
        assertEquals(PROVIDER_SUBSOURCE, hit.provider)
        assertEquals("501", hit.id)
        assertEquals("ar", hit.language)
        assertEquals("Quiet.Coast.S02E02.WEB-DL-GROUP", hit.release)
        assertEquals(40, hit.downloads)
        assertFalse(hit.hashMatch)
        assertFalse(hit.seasonPack)
    }

    @Test
    fun anotherEpisodeOrSeasonIsDropped() {
        offerSeason(
            ssSubtitle(1, listOf("Quiet.Coast.S02E09.WEB")),
            ssSubtitle(2, listOf("Quiet.Coast.S01E02.WEB")),
            ssSubtitle(3, listOf("Quiet.Coast.S02E02.WEB")),
        )
        assertEquals(listOf("3"), search().value().map { it.id })
    }

    @Test
    fun aReleaseWithNoEpisodeIsAPack() {
        offerSeason(ssSubtitle(1, listOf("Quiet.Coast.S02.Complete.WEB")))
        assertTrue(search().value().single().seasonPack)
    }

    @Test
    fun aSubtitleWithSeveralFilesIsAPackEvenIfOneReleaseNamesTheEpisode() {
        offerSeason(ssSubtitle(1, listOf("Quiet.Coast.S02E02.WEB"), files = 8))
        assertTrue(search().value().single().seasonPack)
    }

    @Test
    fun theReleaseShownIsTheOneForThisEpisode() {
        offerSeason(ssSubtitle(1, listOf("Quiet.Coast.S02E01.WEB", "Quiet.Coast.S02E02.WEB")))
        assertEquals("Quiet.Coast.S02E02.WEB", search().value().single().release)
    }

    @Test
    fun aMovieKeepsEveryRow() {
        server.json(SUBSOURCE_MOVIES, ssMovies(ssMovie(61, season = "null")))
        server.json(SUBSOURCE_LIST, ssList(ssSubtitle(1, listOf("Quiet.Coast.2024.1080p"), files = 3)))
        val hit = search(subtitleQuery(imdbId = "tt0133093", languages = listOf("ar"))).value().single()
        assertFalse(hit.seasonPack)
    }

    @Test
    fun anUnsuccessfulListIsEmpty() {
        offerSeason()
        server.json(SUBSOURCE_LIST, """{"success":false,"data":[]}""")
        assertEquals(emptyList<SubtitleHit>(), search().value())
    }

    @Test
    fun aMissingTitleIsEmpty() {
        server.json(SUBSOURCE_MOVIES, ssMovies(ssMovie(12, season = "2")))
        server.on(SUBSOURCE_LIST, Reply(404, """{"error":"not found"}"""))
        assertEquals(emptyList<SubtitleHit>(), search().value())
    }

    @Test
    fun aDownloadSendsTheKeyInTheHeaderToTheApiHost() {
        val zip = zipOf("Quiet.Coast.S02E02.srt" to SRT_TEXT)
        server.on("api.subsource.net/api/v1/subtitles/501/download", Reply(bytes = zip))
        val hit = subtitleHit(PROVIDER_SUBSOURCE, id = "501")
        val saved = blocking { rig.subtitles.download(hit, 2, 2, folder) }.value()
        assertEquals(SRT_TEXT, saved.readText())
        val sent = server.requests.single()
        assertEquals(SUBSOURCE_KEY, sent.header("X-API-Key"))
        assertFalse(SUBSOURCE_KEY in sent.url.toString())
    }

    @Test
    fun aDownloadRefusedForTheKeyReportsNoKey() {
        server.on("api.subsource.net/api/v1/subtitles/501/download", Reply(401, "{}"))
        val hit = subtitleHit(PROVIDER_SUBSOURCE, id = "501")
        assertEquals(Reason.NO_KEY, (blocking { rig.subtitles.download(hit, 2, 2, folder) } as Failure).reason)
    }

    @Test
    fun aHitIdThatIsNotANumberIsRefusedBeforeAnyCall() {
        val hit = subtitleHit(PROVIDER_SUBSOURCE, id = "../../movies")
        assertEquals(Reason.BAD_RESPONSE, (blocking { rig.subtitles.download(hit, 2, 2, folder) } as Failure).reason)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun aRedirectToAnotherAllowedHostLosesTheKey() {
        server.on(
            "api.subsource.net/api/v1/subtitles/501/download",
            Reply(302, headers = mapOf("Location" to "https://dl.subdl.com/cdn/a.zip")),
        )
        server.on("dl.subdl.com/cdn/a.zip", Reply(bytes = zipOf("a.srt" to SRT_TEXT)))
        blocking { rig.subtitles.download(subtitleHit(PROVIDER_SUBSOURCE, id = "501"), null, null, folder) }.value()
        assertNull(server.hits("dl.subdl.com/cdn/a.zip").single().header("X-API-Key"))
    }

    @Test
    fun aRedirectOutsideTheListStopsBeforeTheHop() {
        server.on(
            "api.subsource.net/api/v1/subtitles/501/download",
            Reply(302, headers = mapOf("Location" to "https://cdn.example.com/a.zip")),
        )
        val hit = subtitleHit(PROVIDER_SUBSOURCE, id = "501")
        val failure = blocking { rig.subtitles.download(hit, null, null, folder) } as Failure
        assertEquals("blocked cdn.example.com", failure.message)
        assertEquals(1, server.requests.size)
    }
}
