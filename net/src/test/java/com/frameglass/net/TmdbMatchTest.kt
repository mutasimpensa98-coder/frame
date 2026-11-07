package com.frameglass.net

import com.frameglass.parser.NameKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMatchTest {
    private val rig = Rig()

    @After
    fun clean() = rig.clean()

    @Test
    fun aV3KeyGoesOutAsApiKey() {
        rig.server.json("$TMDB/search/tv", page(tvRow(1, "Sample Journey")))
        blocking { rig.client.match(parsed("Sample Journey")) }.value()
        val request = rig.server.hits("$TMDB/search/tv").single()
        assertEquals(V3_KEY, request.url.queryParameter("api_key"))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun aV4TokenGoesOutAsABearerHeader() {
        rig.config.tmdb = V4_TOKEN
        rig.server.json("$TMDB/search/tv", page(tvRow(1, "Sample Journey")))
        blocking { rig.client.match(parsed("Sample Journey")) }.value()
        val request = rig.server.hits("$TMDB/search/tv").single()
        assertEquals("Bearer $V4_TOKEN", request.header("Authorization"))
        assertNull(request.url.queryParameter("api_key"))
    }

    @Test
    fun shapeDetectionTrimsAPastedKey() {
        assertTrue(TmdbCredential.detect("  Bearer $V4_TOKEN\n")!!.isBearer)
        assertFalse(TmdbCredential.detect(" $V3_KEY ")!!.isBearer)
        assertNull(TmdbCredential.detect("   "))
    }

    @Test
    fun theCredentialCannotPrintItself() {
        assertFalse(V3_KEY in TmdbCredential.detect(V3_KEY).toString())
    }

    @Test
    fun theBaseUrlAcceptsEveryFormTheOwnerMightType() {
        val expected = "https://api.themoviedb.org/3"
        for (form in listOf("", "   ", DEFAULT_TMDB_BASE, "$DEFAULT_TMDB_BASE/", "$DEFAULT_TMDB_BASE/3", "api.themoviedb.org")) {
            assertEquals(form, expected, tmdbApiRoot(form).toString().trimEnd('/'))
        }
        assertEquals("https://w.example.dev/proxy/3", tmdbApiRoot("https://w.example.dev/proxy/").toString())
    }

    @Test
    fun theEditableBaseUrlIsUsed() {
        rig.config.tmdbBaseUrl = "https://worker.example.dev/"
        rig.server.json("worker.example.dev/3/search/tv", page(tvRow(1, "Sample")))
        blocking { rig.client.match(parsed("Sample")) }.value()
        assertEquals(1, rig.server.hits("worker.example.dev/3/search/tv").size)
    }

    @Test
    fun aMissingKeyFailsBeforeAnyRequest() {
        rig.config.tmdb = null
        val result = blocking { rig.client.match(parsed("Sample")) }
        assertEquals(NetResult.Reason.NO_KEY, (result as NetResult.Failure).reason)
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun aRejectedKeyIsReportedAsAKeyProblem() {
        rig.server.on("$TMDB/search/tv", Reply(401, """{"status_code":7}"""))
        val result = blocking { rig.client.match(parsed("Sample")) }
        assertEquals(NetResult.Reason.NO_KEY, (result as NetResult.Failure).reason)
    }

    @Test
    fun anEpisodeSearchesTvInEnglish() {
        rig.server.json("$TMDB/search/tv", page(tvRow(1, "Sample Journey")))
        val outcome = blocking { rig.client.match(parsed("Sample Journey", season = 1, episode = 2)) }.value()
        val request = rig.server.hits("$TMDB/search/tv").single()
        assertEquals("en-US", request.url.queryParameter("language"))
        assertEquals("Sample Journey", request.url.queryParameter("query"))
        assertEquals(MetaKind.TV, outcome.auto!!.kind)
        assertTrue(rig.server.hits("$TMDB/search/movie").isEmpty())
    }

    @Test
    fun aMovieWithAYearFiltersThenScoresTheYearBonus() {
        rig.server.json("$TMDB/search/movie", page(movieRow(5, "Quiet Coast", 2026), movieRow(6, "Quiet Coast", 2019)))
        val outcome = blocking { rig.client.match(parsed("Quiet Coast", 2026, NameKind.MOVIE)) }.value()
        assertEquals("2026", rig.server.hits("$TMDB/search/movie").first().url.queryParameter("year"))
        val auto = outcome.auto!!
        assertEquals(5L, auto.tmdbId)
        assertEquals(1.0, auto.score, 1e-9)
        assertTrue(outcome.choices.isEmpty())
    }

    @Test
    fun anExactTitleInTheWrongYearIsOfferedNotApplied() {
        rig.server.json("$TMDB/search/movie", page(movieRow(6, "Quiet Coast", 2019)))
        val outcome = blocking { rig.client.match(parsed("Quiet Coast", 2026, NameKind.MOVIE)) }.value()
        assertNull(outcome.auto)
        assertEquals(listOf(6L), outcome.choices.map(MatchCandidate::tmdbId))
        assertEquals(0.65, outcome.choices.single().score, 1e-9)
    }

    @Test
    fun twoIdenticalTitlesWithNoYearGoToTheOwnerAsChoices() {
        rig.server.json("$TMDB/search/tv", page(tvRow(1, "The Office", 2001), tvRow(2, "The Office", 2005)))
        val outcome = blocking { rig.client.match(parsed("The Office")) }.value()
        assertNull(outcome.auto)
        assertEquals(setOf(1L, 2L), outcome.choices.map(MatchCandidate::tmdbId).toSet())
    }

    @Test
    fun choicesStopAtThreeWithCovers() {
        val rows = (1L..6L).map { tvRow(it, "Sample Show $it") }
        rig.server.json("$TMDB/search/tv", page(*rows.toTypedArray()))
        val outcome = blocking { rig.client.match(parsed("Sample Show")) }.value()
        assertEquals(3, outcome.choices.size)
        assertTrue(outcome.choices.all { it.posterUrl!!.startsWith("https://image.tmdb.org/t/p/w500/") })
    }

    @Test
    fun alternativeTitlesAreFetchedForTheTopThreeOnly() {
        val rows = (1L..5L).map { tvRow(it, "Sample Show $it") }
        rig.server.json("$TMDB/search/tv", page(*rows.toTypedArray()))
        blocking { rig.client.match(parsed("Sample Show")) }.value()
        val fetched = rig.server.requests.filter { "alternative_titles" in it.url.encodedPath && "/tv/" in it.url.encodedPath }
        assertEquals(3, fetched.size)
    }

    @Test
    fun anAlternativeTitleCanWinAnArabicName() {
        rig.server.json("$TMDB/search/tv", page(tvRow(1, "The Choice", original = "The Choice")))
        rig.server.json("$TMDB/tv/1/alternative_titles", """{"results":[{"title":"الاختيار","iso_3166_1":"EG"}]}""")
        val outcome = blocking { rig.client.match(parsed("الاختيار")) }.value()
        val auto = outcome.auto!!
        assertEquals(1L, auto.tmdbId)
        assertEquals(0.90, auto.score, 1e-9)
    }

    @Test
    fun movieAlternativeTitlesUseTheirOwnShape() {
        rig.server.json("$TMDB/search/movie", page(movieRow(5, "The Choice")))
        rig.server.json("$TMDB/movie/5/alternative_titles", """{"id":5,"titles":[{"title":"الاختيار"}]}""")
        val outcome = blocking { rig.client.match(parsed("الاختيار", kind = NameKind.MOVIE)) }.value()
        assertEquals(5L, outcome.auto!!.tmdbId)
    }

    @Test
    fun aFailedAlternativeTitleLookupOnlyLosesTheBonus() {
        rig.server.json("$TMDB/search/tv", page(tvRow(1, "Sample Journey")))
        rig.server.on("$TMDB/tv/1/alternative_titles", Reply(500))
        val outcome = blocking { rig.client.match(parsed("Sample Journey")) }.value()
        assertNotNull(outcome.auto)
    }

    @Test
    fun aWrongKindGuessStillMatchesThroughTheOtherSearch() {
        rig.server.json("$TMDB/search/movie", page())
        rig.server.json("$TMDB/search/tv", page(tvRow(1, "Sample Journey")))
        val outcome = blocking { rig.client.match(parsed("Sample Journey", kind = NameKind.MOVIE)) }.value()
        assertEquals(MetaKind.TV, outcome.auto!!.kind)
    }

    @Test
    fun aMisDatedMovieIsFoundByThePlainSearch() {
        rig.server.on("$TMDB/search/movie", Reply(body = page()), Reply(body = page(movieRow(6, "Quiet Coast", 2025))))
        val outcome = blocking { rig.client.match(parsed("Quiet Coast", 2026, NameKind.MOVIE)) }.value()
        assertEquals(listOf(6L), outcome.choices.map(MatchCandidate::tmdbId))
    }

    @Test
    fun noResultsIsASuccessfulEmptyAnswer() {
        rig.server.json("$TMDB/search/tv", page())
        rig.server.json("$TMDB/search/movie", page())
        val outcome = blocking { rig.client.match(parsed("Nothing Like This")) }.value()
        assertNull(outcome.auto)
        assertTrue(outcome.choices.isEmpty())
    }

    @Test
    fun aBlankTitleSendsNothing() {
        val outcome = blocking { rig.client.match(parsed("  ")) }.value()
        assertNull(outcome.auto)
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun aServerErrorQueuesRatherThanDecides() {
        rig.server.on("$TMDB/search/tv", Reply(503))
        val result = blocking { rig.client.match(parsed("Sample")) }
        assertEquals(NetResult.Reason.NETWORK, (result as NetResult.Failure).reason)
    }

    @Test
    fun brokenJsonFailsSoftly() {
        rig.server.json("$TMDB/search/tv", "<html>not json</html>")
        val result = blocking { rig.client.match(parsed("Sample")) }
        assertEquals(NetResult.Reason.BAD_RESPONSE, (result as NetResult.Failure).reason)
    }

    @Test
    fun aKeyNeverAppearsInAFailureMessage() {
        rig.server.on("$TMDB/search/tv", Reply(500))
        val failures = listOf(
            blocking { rig.client.match(parsed("Sample")) },
            blocking { rig.client.details(MetaKind.TV, 1, "en-US") },
        )
        for (failure in failures) {
            assertFalse(V3_KEY in failure.toString())
            assertFalse("api_key" in failure.toString())
        }
    }

    @Test
    fun onlyAllowedFieldsLeaveThePhone() {
        rig.server.json("$TMDB/search/movie", page(movieRow(5, "Quiet Coast", 2026)))
        val name = parsed("Quiet Coast", 2026, NameKind.MOVIE, tags = listOf("1080p", "BluRay", "x265", "SECRETGROUP"))
        blocking { rig.client.match(name) }.value()

        val allowed = setOf("query", "language", "include_adult", "year", "api_key")
        for (request in rig.server.requests) {
            assertEquals(emptySet<String>(), request.url.queryParameterNames - allowed)
            for (tag in name.releaseTags) assertFalse(tag in request.url.toString())
        }
    }

    @Test
    fun manualSearchKeepsMoviesAndShowsAndDropsPeople() {
        val person = """{"id":9,"media_type":"person","name":"Someone"}"""
        val show = multiRow(tvRow(1, "Sample"), "tv")
        val movie = multiRow(movieRow(2, "Sample"), "movie")
        rig.server.json("$TMDB/search/multi", page(person, show, movie))
        val results = blocking { rig.client.searchManual("Sample", "ar") }.value()
        assertEquals(listOf(MetaKind.TV, MetaKind.MOVIE), results.map(MatchCandidate::kind))
        assertEquals("ar", rig.server.hits("$TMDB/search/multi").single().url.queryParameter("language"))
    }
}
