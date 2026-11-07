package com.frameglass.net

import com.frameglass.parser.NameKind
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmdbMatchTest {
    private val rig = Rig(FakeConfig(omdb = OMDB_KEY))
    private val omdbCalls get() = rig.server.requests.filter { it.url.host == "www.omdbapi.com" }

    @After
    fun clean() = rig.clean()

    private fun record(
        id: String,
        title: String,
        year: String,
        type: String = "movie",
        poster: String = "https://$OMDB_POSTER_HOST/images/M/$id._V1_SX300.jpg",
    ) = """{"Title":"$title","Year":"$year","Runtime":"148 min","Genre":"Action, Sci-Fi","Director":"Sample Director",
        "Writer":"First Writer (screenplay), Second Writer (story)","Actors":"Actor One, Actor Two, Actor Three",
        "Plot":"A full plot.","Poster":"$poster","imdbRating":"8.8","imdbVotes":"2,345,678","imdbID":"$id",
        "Type":"$type","Response":"True"}"""

    private fun hit(id: String, title: String, year: String, type: String = "movie") =
        """{"Title":"$title","Year":"$year","imdbID":"$id","Type":"$type","Poster":"N/A"}"""

    private fun search(vararg hits: String) = """{"Search":[${hits.joinToString(",")}],"totalResults":"${hits.size}","Response":"True"}"""

    private val miss = Reply(body = """{"Response":"False","Error":"Movie not found!"}""")

    /** Routes OMDb by its query, since every call shares one path. */
    private fun omdb(route: (Request) -> Reply?) {
        rig.server.answer = { request -> if (request.url.host == "www.omdbapi.com") route(request) ?: miss else null }
    }

    private fun Request.q(name: String) = url.queryParameter(name)

    @Test
    fun aYearMakesOneExactCallEnough() {
        omdb { if (it.q("t") == "Sample Journey" && it.q("y") == "2010" && it.q("type") == "movie") Reply(body = record("tt0000010", "Sample Journey", "2010")) else null }
        val outcome = blocking { rig.client.omdbMatch(parsed("Sample Journey", year = 2010, kind = NameKind.MOVIE)) }.value()
        val title = requireNotNull(outcome.auto)
        assertEquals(1, omdbCalls.size)
        assertEquals("tt0000010", title.imdbId)
        assertEquals(MetaKind.MOVIE, title.kind)
        assertEquals(2010, title.year)
        assertEquals(148, title.runtimeMinutes)
        assertEquals(listOf("Action", "Science Fiction"), title.genres)
        assertEquals(listOf("Actor One", "Actor Two", "Actor Three"), title.cast)
        assertEquals(
            listOf(CrewMeta("Director", "Sample Director"), CrewMeta("Writer", "First Writer"), CrewMeta("Writer", "Second Writer")),
            title.crew,
        )
        assertEquals(8.8, title.imdbRating!!, 1e-9)
        assertEquals(2_345_678, title.imdbVotes)
        assertEquals("A full plot.", title.overview)
        assertEquals("full", omdbCalls.single().q("plot"))
    }

    @Test
    fun thePosterIsAskedLargerFirst() {
        val urls = OmdbImages.posters("https://$OMDB_POSTER_HOST/images/M/abc._V1_SX300.jpg")
        assertEquals(
            listOf("https://$OMDB_POSTER_HOST/images/M/abc._V1_SX500.jpg", "https://$OMDB_POSTER_HOST/images/M/abc._V1_SX300.jpg"),
            urls,
        )
        assertEquals(emptyList<String>(), OmdbImages.posters("N/A"))
        assertEquals(listOf("https://$OMDB_POSTER_HOST/p.jpg"), OmdbImages.posters("http://$OMDB_POSTER_HOST/p.jpg"))
    }

    @Test
    fun withoutAYearTheSearchDecidesAndTwoSameNamedWorksNeedAChoice() {
        omdb { if (it.q("s") != null) Reply(body = search(hit("tt1", "Sample", "1998"), hit("tt2", "Sample", "2016"))) else null }
        val outcome = blocking { rig.client.omdbMatch(parsed("Sample", kind = NameKind.MOVIE)) }.value()
        assertNull(outcome.auto)
        assertEquals(setOf("tt1", "tt2"), outcome.choices.map { it.imdbId }.toSet())
        assertTrue(omdbCalls.none { it.q("t") != null || it.q("i") != null })
    }

    @Test
    fun aMissedExactTitleFallsBackToTheSearchAndOneDetailCall() {
        omdb {
            when {
                it.q("s") == "Sample Journey" && it.q("y") == "2010" ->
                    Reply(body = search(hit("tt5", "The Sample Journey", "2010"), hit("tt6", "Unrelated", "2010")))
                it.q("s") == "Sample Journey" -> Reply(body = search(hit("tt5", "Sample Journey", "2010")))
                it.q("i") == "tt5" -> Reply(body = record("tt5", "Sample Journey", "2010"))
                else -> null
            }
        }
        val outcome = blocking { rig.client.omdbMatch(parsed("Sample Journey", year = 2010, kind = NameKind.MOVIE)) }.value()
        assertEquals("tt5", outcome.auto?.imdbId)
        assertEquals(listOf("t", "s", "s", "i"), omdbCalls.map { request -> listOf("t", "s", "i").first { request.q(it) != null } })
    }

    @Test
    fun aMovieThatIsReallyASeriesIsFoundLast() {
        omdb {
            when {
                it.q("s") != null && it.q("type") == "series" -> Reply(body = search(hit("tt7", "Sample Show", "2019–2022", "series")))
                it.q("i") == "tt7" -> Reply(body = record("tt7", "Sample Show", "2019–2022", type = "series"))
                else -> null
            }
        }
        val outcome = blocking { rig.client.omdbMatch(parsed("Sample Show", kind = NameKind.MOVIE)) }.value()
        assertEquals(MetaKind.TV, outcome.auto?.kind)
        assertEquals(2019, outcome.auto?.year)
    }

    @Test
    fun episodesAndGamesNeverBecomeChoices() {
        omdb {
            if (it.q("s") != null) {
                Reply(body = search(hit("tt8", "Sample", "2001", "episode"), hit("tt9", "Sample", "2001", "game"), hit("tt3", "Sample", "2001")))
            } else {
                null
            }
        }
        val found = blocking { rig.client.omdbSearch("Sample") }.value()
        assertEquals(listOf("tt3"), found.map { it.imdbId })
        assertNull(omdbCalls.single().q("type"))
    }

    @Test
    fun aSpentDayIsADailyLimit() {
        omdb { Reply(401, """{"Response":"False","Error":"Request limit reached!"}""") }
        val failure = blocking { rig.client.omdbMatch(parsed("Sample", year = 2020)) } as NetResult.Failure
        assertTrue(failure.isDailyLimit())
        assertEquals(1, omdbCalls.size)
        val rating = blocking { rig.client.imdbRating("tt1") } as NetResult.Failure
        assertTrue(rating.isDailyLimit())
    }

    @Test
    fun aShortRateLimitIsNotADailyLimit() {
        assertFalse(NetResult.Failure(NetResult.Reason.RATE_LIMIT, "omdb").isDailyLimit())
    }

    @Test
    fun aMissingKeySendsNothing() {
        rig.config.omdb = null
        val failure = blocking { rig.client.omdbMatch(parsed("Sample", year = 2020)) } as NetResult.Failure
        assertEquals(NetResult.Reason.NO_KEY, failure.reason)
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun aSeasonGivesTitlesDatesAndRatings() {
        omdb {
            if (it.q("i") == "tt7" && it.q("Season") == "2") {
                Reply(
                    body = """{"Title":"Sample Show","Season":"2","Episodes":[
                        {"Title":"First Light","Released":"2020-01-05","Episode":"1","imdbRating":"8.1","imdbID":"tt71"},
                        {"Title":"Episode #2.2","Released":"N/A","Episode":"2","imdbRating":"N/A","imdbID":"tt72"},
                        {"Title":"Bad","Released":"2020","Episode":"N/A","imdbRating":"7.0","imdbID":"tt73"}],"Response":"True"}""",
                )
            } else {
                null
            }
        }
        val episodes = blocking { rig.client.omdbSeason("tt7", 2) }.value()
        assertEquals(listOf(1, 2), episodes.map { it.number })
        assertEquals(OmdbEpisode(2, 1, "First Light", "2020-01-05", 8.1), episodes[0])
        assertEquals(OmdbEpisode(2, 2, null, null, null), episodes[1])
    }

    @Test
    fun omdbHasNoSeasonZero() {
        val failure = blocking { rig.client.omdbSeason("tt7", 0) } as NetResult.Failure
        assertEquals(NetResult.Reason.NOT_FOUND, failure.reason)
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun anImdbIdFindsItsTmdbRecord() {
        rig.server.json("$TMDB/find/tt0000020", """{"movie_results":[],"tv_results":[${tvRow(321, "Sample Show")}]}""")
        val outcome = blocking { rig.client.tmdbForImdb("tt0000020", parsed("Sample Show")) }.value()
        assertEquals(321L, outcome.auto?.tmdbId)
        assertEquals(MetaKind.TV, outcome.auto?.kind)
        assertEquals(1.0, outcome.auto!!.score, 1e-9)
        val request = rig.server.hits("$TMDB/find/tt0000020").single()
        assertEquals("imdb_id", request.url.queryParameter("external_source"))
    }

    @Test
    fun anImdbIdTmdbDoesNotKnowIsNotFound() {
        rig.server.json("$TMDB/find/tt0000021", """{"movie_results":[],"tv_results":[]}""")
        val failure = blocking { rig.client.tmdbForImdb("tt0000021", parsed("Sample")) } as NetResult.Failure
        assertEquals(NetResult.Reason.NOT_FOUND, failure.reason)
    }
}
