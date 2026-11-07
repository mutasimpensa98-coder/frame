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

class OpenSubtitlesApiTest {
    private val rig = Rig(FakeConfig(tmdb = null, openSubtitles = OS_KEY))
    private val server = rig.server
    private val folder = File(rig.dir, "subs")

    @After
    fun clean() = rig.clean()

    private fun search(query: SubtitleQuery) = blocking { rig.subtitles.search(query) }

    private fun queries() = server.requests.map { it.url.encodedQuery.orEmpty() }

    private fun fileLink(name: String = "sample.srt") = "https://$OS_FILE_HOST/download/abc123/subfile/$name"

    private fun offerFile(body: String = SRT_TEXT) {
        server.json(OS_DOWNLOAD, osTicket(fileLink()))
        server.on("$OS_FILE_HOST/download/abc123/subfile/sample.srt", Reply(body = body))
    }

    @Test
    fun anEpisodeSearchesByParentIdInCanonicalForm() {
        server.json(OS_SEARCH, osSearch())
        search(subtitleQuery(season = 1, episode = 2, imdbId = "tt0133093", tmdbId = 77, languages = listOf("ar")))
        assertEquals(
            listOf("episode_number=2&languages=ar&parent_imdb_id=133093&season_number=1&type=episode"),
            queries(),
        )
    }

    @Test
    fun aMovieSearchesByItsOwnId() {
        search(subtitleQuery(imdbId = "tt0133093", languages = listOf("en")))
        assertEquals(listOf("imdb_id=133093&languages=en"), queries())
    }

    @Test
    fun theTmdbIdIsUsedWhenThereIsNoImdbId() {
        search(subtitleQuery(season = 1, episode = 2, tmdbId = 77, languages = listOf("ar")))
        assertEquals(
            listOf("episode_number=2&languages=ar&parent_tmdb_id=77&season_number=1&type=episode"),
            queries(),
        )
    }

    @Test
    fun seasonZeroIsARealSeason() {
        search(subtitleQuery(season = 0, episode = 3, tmdbId = 77, languages = listOf("ar")))
        assertTrue(queries().single().contains("season_number=0"))
    }

    @Test
    fun eachLanguageIsItsOwnRequestSoEnglishCannotCrowdOutArabic() {
        server.json(OS_SEARCH, osSearch())
        search(subtitleQuery(tmdbId = 77))
        val asked = queries().map { query -> query.split('&').first { it.startsWith("languages=") } }.toSet()
        assertEquals(setOf("languages=ar", "languages=en"), asked)
    }

    @Test
    fun theKeyTravelsInAHeaderAndTheClientNamesItself() {
        search(subtitleQuery(tmdbId = 77))
        val request = server.requests.first()
        assertEquals(OS_KEY, request.header("Api-Key"))
        assertEquals(SUBTITLE_USER_AGENT, request.header("User-Agent"))
        assertEquals("application/json", request.header("Accept"))
        assertFalse(OS_KEY in request.url.toString())
    }

    @Test
    fun aReplyBecomesHits() {
        server.json(OS_SEARCH, osSearch(osItem(8000001, downloads = 1200, hashMatch = true)))
        val hit = search(subtitleQuery(tmdbId = 77, languages = listOf("ar"))).value().single()
        assertEquals(PROVIDER_OPENSUBTITLES, hit.provider)
        assertEquals("8000001", hit.id)
        assertEquals("ar", hit.language)
        assertEquals("Quiet.Coast.S01E02.1080p.WEB-DL.x265-GROUP", hit.release)
        assertTrue(hit.hashMatch)
        assertEquals(1200, hit.downloads)
        assertEquals("srt", hit.format)
        assertFalse(hit.seasonPack)
    }

    @Test
    fun anItemWithNoFileCannotBeDownloadedSoItIsLeftOut() {
        val noFiles = osItem(1).replace(Regex(""""files":\[.*?]"""), """"files":[]""")
        server.json(OS_SEARCH, osSearch(noFiles, osItem(2)))
        assertEquals(listOf("2"), search(subtitleQuery(tmdbId = 77, languages = listOf("ar"))).value().map { it.id })
    }

    @Test
    fun aBlankReleaseFallsBackToTheFileName() {
        server.json(OS_SEARCH, osSearch(osItem(1, release = "").replace("\"file_name\":\".srt\"", "\"file_name\":\"Named.srt\"")))
        assertEquals("Named.srt", search(subtitleQuery(tmdbId = 77, languages = listOf("ar"))).value().single().release)
    }

    @Test
    fun searchSpendsNoQuota() {
        server.json(OS_SEARCH, osSearch(osItem(1, hashMatch = true)))
        search(subtitleQuery(tmdbId = 77))
        assertTrue(server.hits(OS_DOWNLOAD).isEmpty())
    }

    @Test
    fun theHashIsSentOnlyWhenTheCallerGaveIt() {
        search(subtitleQuery(tmdbId = 77, languages = listOf("ar")))
        assertFalse(queries().single().contains("moviehash"))
        server.requests.clear()

        search(subtitleQuery(tmdbId = 77, languages = listOf("ar"), osHash = "8E245D9679D31E12"))
        assertTrue(queries().single().contains("moviehash=8e245d9679d31e12"))
    }

    @Test
    fun aMalformedHashIsNotSent() {
        search(subtitleQuery(tmdbId = 77, languages = listOf("ar"), osHash = "not-a-hash"))
        assertFalse(queries().single().contains("moviehash"))
    }

    @Test
    fun aHashAloneIsEnoughToSearch() {
        search(subtitleQuery(title = "", year = null, languages = listOf("ar"), osHash = "8e245d9679d31e12"))
        assertEquals(listOf("languages=ar&moviehash=8e245d9679d31e12"), queries())
    }

    @Test
    fun aFileNobodyParsedIsNotLimitedToMovies() {
        search(subtitleQuery(languages = listOf("ar"), osHash = "8e245d9679d31e12"))
        assertFalse(queries().single().contains("type="))
    }

    @Test
    fun theSizeNeverLeavesThePhoneBecauseTheApiHasNoSuchField() {
        search(subtitleQuery(tmdbId = 77, languages = listOf("ar"), osHash = "8e245d9679d31e12", fileSize = 734003200))
        assertFalse(queries().single().contains("734003200"))
    }

    @Test
    fun withIdsTheReleaseNameStaysOnThePhone() {
        search(subtitleQuery(tmdbId = 77, languages = listOf("ar"), releaseName = "Private.Name.1080p-GROUP"))
        assertFalse(queries().single().lowercase().contains("private"))
    }

    @Test
    fun withNoIdsTheReleaseNameIsTheSearchTextInCanonicalForm() {
        search(subtitleQuery(season = 1, episode = 2, languages = listOf("ar"), releaseName = "Quiet Coast S01E02 1080p"))
        assertEquals(
            listOf("episode_number=2&languages=ar&query=quiet+coast+s01e02+1080p&season_number=1&type=episode"),
            queries(),
        )
    }

    @Test
    fun withNoIdsAndNoReleaseNameTheTitleIsTheSearchTextAndAMovieAddsItsYear() {
        search(subtitleQuery(languages = listOf("ar")))
        assertEquals(listOf("languages=ar&query=quiet+coast&year=2024"), queries())
    }

    @Test
    fun aQueryThatNamesNothingAsksNothing() {
        assertEquals(emptyList<SubtitleHit>(), search(subtitleQuery(title = " ", year = null)).value())
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun aRefusedKeyReportsNoKey() {
        server.on(OS_SEARCH, Reply(403, """{"message":"You cannot consume this service"}"""))
        assertEquals(Reason.NO_KEY, (search(subtitleQuery(tmdbId = 77)) as Failure).reason)
    }

    @Test
    fun aDownloadAsksForAFileThenFetchesItWithNoCredentials() {
        offerFile()
        val saved = blocking { rig.subtitles.download(subtitleHit(id = "8000001"), 1, 2, folder) }.value()

        val ticket = server.hits(OS_DOWNLOAD).single()
        assertEquals("POST", ticket.method)
        assertEquals("""{"file_id":8000001,"sub_format":"srt"}""", server.bodyOf(ticket))
        assertEquals(OS_KEY, ticket.header("Api-Key"))

        val file = server.requests.last()
        assertEquals(OS_FILE_HOST, file.url.host)
        assertNull(file.header("Api-Key"))
        assertNull(file.header("Authorization"))
        assertEquals(SUBTITLE_USER_AGENT, file.header("User-Agent"))
        assertEquals(SRT_TEXT, saved.readText())
    }

    @Test
    fun theLinkMayStartOnTheDlHostAndLandOnWww() {
        server.json(OS_DOWNLOAD, osTicket("https://dl.opensubtitles.com/download/abc123/subfile/sample.srt"))
        server.on(
            "dl.opensubtitles.com/download/abc123/subfile/sample.srt",
            Reply(301, headers = mapOf("Location" to fileLink())),
        )
        server.on("$OS_FILE_HOST/download/abc123/subfile/sample.srt", Reply(body = SRT_TEXT))
        assertEquals(SRT_TEXT, blocking { rig.subtitles.download(subtitleHit(), null, null, folder) }.value().readText())
    }

    @Test
    fun aSpentQuotaIsARateLimitAndNoFileIsAskedFor() {
        server.on(
            OS_DOWNLOAD,
            Reply(406, """{"requests":5,"remaining":0,"message":"You have downloaded your allowed 5 subtitles"}"""),
        )
        val failure = blocking { rig.subtitles.download(subtitleHit(), null, null, folder) } as Failure
        assertEquals(Reason.RATE_LIMIT, failure.reason)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun aTicketWithNoLinkAndNoQuotaIsARateLimit() {
        server.json(OS_DOWNLOAD, """{"requests":5,"remaining":0,"message":"limit"}""")
        val failure = blocking { rig.subtitles.download(subtitleHit(), null, null, folder) } as Failure
        assertEquals(Reason.RATE_LIMIT, failure.reason)
    }

    @Test
    fun aTicketWithNoLinkIsABadReply() {
        server.json(OS_DOWNLOAD, """{"requests":1,"remaining":4}""")
        val failure = blocking { rig.subtitles.download(subtitleHit(), null, null, folder) } as Failure
        assertEquals(Reason.BAD_RESPONSE, failure.reason)
    }

    @Test
    fun aLinkOutsideTheAllowListIsBlockedBeforeAnyHop() {
        server.json(OS_DOWNLOAD, osTicket("https://files.example.com/a.srt"))
        val failure = blocking { rig.subtitles.download(subtitleHit(), null, null, folder) } as Failure
        assertEquals(Reason.BAD_RESPONSE, failure.reason)
        assertEquals("blocked files.example.com", failure.message)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun aCleartextLinkIsBlocked() {
        server.json(OS_DOWNLOAD, osTicket("http://$OS_FILE_HOST/download/a.srt"))
        val failure = blocking { rig.subtitles.download(subtitleHit(), null, null, folder) } as Failure
        assertEquals(Reason.BAD_RESPONSE, failure.reason)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun anExpiredLinkFailsSoftly() {
        server.json(OS_DOWNLOAD, osTicket(fileLink()))
        server.on("$OS_FILE_HOST/download/abc123/subfile/sample.srt", Reply(410, "gone"))
        val failure = blocking { rig.subtitles.download(subtitleHit(), null, null, folder) } as Failure
        assertEquals(Reason.BAD_RESPONSE, failure.reason)
        assertFalse(folder.listFiles().orEmpty().any())
    }

    @Test
    fun aHitIdThatIsNotANumberSpendsNothing() {
        val failure = blocking { rig.subtitles.download(subtitleHit(id = "../1"), null, null, folder) } as Failure
        assertEquals(Reason.BAD_RESPONSE, failure.reason)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun aHitAlreadySavedSpendsNoQuota() {
        offerFile()
        val hit = subtitleHit(id = "8000001")
        val first = blocking { rig.subtitles.download(hit, 1, 2, folder) }.value()
        server.requests.clear()
        val second = blocking { rig.subtitles.download(hit, 1, 2, folder) }.value()
        assertEquals(first, second)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun anotherEpisodeOfTheSameHitIsNotMistakenForTheSavedOne() {
        offerFile()
        val hit = subtitleHit(id = "8000001")
        blocking { rig.subtitles.download(hit, 1, 2, folder) }.value()
        blocking { rig.subtitles.download(hit, 1, 3, folder) }.value()
        assertEquals(2, server.hits(OS_DOWNLOAD).size)
    }
}
