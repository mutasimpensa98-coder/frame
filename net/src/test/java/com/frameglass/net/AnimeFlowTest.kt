package com.frameglass.net

import com.frameglass.parser.NameKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeFlowTest {
    private val rig = Rig()
    private val release = "github.com/anibridge/anibridge-mappings/releases/download/v3/mappings.min.json"

    @After
    fun clean() = rig.clean()

    private fun media(
        id: Long,
        romaji: String,
        english: String? = null,
        mal: Long? = id + 1000,
        year: Int = 2023,
        format: String = "TV",
        synonyms: String = "",
    ): String {
        val englishJson = english?.let { "\"$it\"" } ?: "null"
        val malJson = mal?.toString() ?: "null"
        return """{"id":$id,"idMal":$malJson,"format":"$format","startDate":{"year":$year},""" +
            """"title":{"romaji":"$romaji","english":$englishJson,"native":null},"synonyms":[$synonyms]}"""
    }

    private fun aniList(vararg rows: String) = """{"data":{"Page":{"media":[${rows.joinToString(",")}]}}}"""

    private fun serveMappings(body: String) = rig.server.on(release, Reply(body = body))

    private val bridge = """{"anilist:50":{"tmdb_show:700:s1":{"1-12":"1-12"}}}"""
    private val showDetails = """{"id":700,"name":"Sousou no Frieren","original_name":"葬送のフリーレン",
        "first_air_date":"2023-09-29","poster_path":"/f.jpg","overview":"Plot"}"""

    @Test
    fun aHintRunsAniListFirstAndAnibridgeGivesTheShow() {
        rig.server.json(ANILIST, aniList(media(50, "Sousou no Frieren", "Frieren: Beyond Journey's End")))
        serveMappings(bridge)
        rig.server.json("$TMDB/tv/700", showDetails)

        val outcome = blocking { rig.client.match(parsed("Sousou no Frieren", episode = 5, animeHint = true)) }.value()

        assertEquals(ANILIST, rig.server.requests.first().url.run { host + encodedPath })
        val auto = outcome.auto!!
        assertEquals(700L, auto.tmdbId)
        assertEquals(50L, outcome.anilistId)
        assertEquals(1050L, outcome.malId)
        assertTrue(auto.isAnime)
        assertEquals(0.90, auto.score, 1e-9)
        assertTrue(rig.server.hits("$TMDB/search/tv").isEmpty())
    }

    @Test
    fun aniListReceivesTheTitleAndNothingElse() {
        rig.server.json(ANILIST, aniList())
        rig.server.json("$TMDB/search/tv", page())
        rig.server.json("$TMDB/search/movie", page())
        val name = parsed("Sample", episode = 5, animeHint = true, tags = listOf("1080p", "SECRETGROUP"))
        blocking { rig.client.match(name) }.value()

        val body = rig.server.bodyOf(rig.server.hits(ANILIST).single())
        val json = NetJson.parseToJsonElement(body).toString()
        assertTrue("\"variables\":{\"search\":\"Sample\"}" in json)
        assertFalse("SECRETGROUP" in body)
        assertFalse("1080p" in body)
        assertEquals("POST", rig.server.hits(ANILIST).single().method)
    }

    @Test
    fun withNoMappingTmdbIsSearchedUnderAniListNames() {
        rig.wifi = false
        rig.server.json(ANILIST, aniList(media(50, "Sousou no Frieren", "Frieren: Beyond Journey's End")))
        rig.server.json("$TMDB/search/tv", page(tvRow(700, "Frieren: Beyond Journey's End", 2023, genres = "16", country = "JP")))

        val outcome = blocking { rig.client.match(parsed("Sousou no Frieren", episode = 5, animeHint = true)) }.value()

        assertEquals("Frieren: Beyond Journey's End", rig.server.hits("$TMDB/search/tv").first().url.queryParameter("query"))
        val auto = outcome.auto!!
        assertEquals(700L, auto.tmdbId)
        assertEquals(50L, outcome.anilistId)
        // Exact through AniList's English name, plus AniList's own year.
        assertEquals(1.0, auto.score, 1e-9)
    }

    @Test
    fun aniListsYearSettlesAnAnimeAndItsLiveRemake() {
        rig.wifi = false
        rig.server.json(ANILIST, aniList(media(21, "One Piece", year = 1999)))
        rig.server.json(
            "$TMDB/search/tv",
            page(tvRow(1, "One Piece", 2023), tvRow(2, "One Piece", 1999, genres = "16", country = "JP")),
        )
        val outcome = blocking { rig.client.match(parsed("One Piece", animeHint = true)) }.value()
        assertEquals(2L, outcome.auto!!.tmdbId)
    }

    @Test
    fun anAnimeMovieSearchesTmdbMovies() {
        rig.server.json(ANILIST, aniList(media(60, "Sample Movie", format = "MOVIE", year = 2021)))
        rig.server.json("$TMDB/search/movie", page(movieRow(800, "Sample Movie", 2021, genres = "16")))
        val outcome = blocking { rig.client.match(parsed("Sample Movie", kind = NameKind.MOVIE, animeHint = true)) }.value()
        assertEquals(MetaKind.MOVIE, outcome.auto!!.kind)
        assertEquals(60L, outcome.anilistId)
        assertTrue(rig.server.requests.none { "mappings.min.json" in it.url.encodedPath })
    }

    @Test
    fun aNameAniListCannotConfirmFallsBackToTmdb() {
        rig.server.json(ANILIST, aniList(media(50, "Sousou no Frieren", "Frieren: Beyond Journey's End")))
        rig.server.json("$TMDB/search/tv", page(tvRow(1, "Sample Western Show")))
        val outcome = blocking { rig.client.match(parsed("Sample Western Show", animeHint = true)) }.value()
        assertEquals(1L, outcome.auto!!.tmdbId)
        assertNull(outcome.anilistId)
    }

    @Test
    fun anAniListOutageDoesNotStopTheMatch() {
        rig.server.on(ANILIST, Reply(500))
        rig.server.json("$TMDB/search/tv", page(tvRow(1, "Sample Show")))
        val outcome = blocking { rig.client.match(parsed("Sample Show", animeHint = true)) }.value()
        assertEquals(1L, outcome.auto!!.tmdbId)
    }

    @Test
    fun aTmdbOutageAfterAnibridgeQueuesTheTitle() {
        rig.server.json(ANILIST, aniList(media(50, "Sousou no Frieren")))
        serveMappings(bridge)
        rig.server.on("$TMDB/tv/700", Reply(503))
        val result = blocking { rig.client.match(parsed("Sousou no Frieren", animeHint = true)) }
        assertEquals(NetResult.Reason.NETWORK, (result as NetResult.Failure).reason)
    }

    @Test
    fun aJapaneseAnimationFoundOnTmdbThenRunsAniList() {
        rig.server.json("$TMDB/search/tv", page(tvRow(700, "Frieren", 2023, genres = "16,10759", country = "JP", language = "ja")))
        rig.server.json(ANILIST, aniList(media(50, "Sousou no Frieren", synonyms = "\"Frieren\"")))

        val outcome = blocking { rig.client.match(parsed("Frieren", season = 1, episode = 5)) }.value()

        val auto = outcome.auto!!
        assertEquals(700L, auto.tmdbId)
        assertTrue(auto.isAnime)
        assertEquals(50L, outcome.anilistId)
        assertEquals(1050L, outcome.malId)
    }

    @Test
    fun aWesternCartoonDoesNotRunAniList() {
        rig.server.json("$TMDB/search/tv", page(tvRow(1, "Sample Toon", genres = "16", country = "US")))
        val outcome = blocking { rig.client.match(parsed("Sample Toon")) }.value()
        assertFalse(outcome.auto!!.isAnime)
        assertTrue(rig.server.hits(ANILIST).isEmpty())
    }

    @Test
    fun aMissingAniListHitStillKeepsTheTmdbMatch() {
        rig.server.json("$TMDB/search/tv", page(tvRow(700, "Frieren", genres = "16", country = "JP")))
        rig.server.json(ANILIST, aniList())
        val outcome = blocking { rig.client.match(parsed("Frieren")) }.value()
        assertEquals(700L, outcome.auto!!.tmdbId)
        assertNull(outcome.anilistId)
    }

    @Test
    fun anilistIdsComeFromTheMappingWhenAniListIsDown() {
        serveMappings("""{"anilist:50":{"tmdb_show:700:s1":{"1-12":"1-12"}},"mal:150":{"tmdb_show:700:s1":{"1-12":"1-12"}}}""")
        blocking { rig.mappings.refreshIfDue() }
        rig.server.json("$TMDB/search/tv", page(tvRow(700, "Frieren", genres = "16", country = "JP")))
        rig.server.on(ANILIST, Reply(500))
        val outcome = blocking { rig.client.match(parsed("Frieren")) }.value()
        assertEquals(50L, outcome.anilistId)
        assertEquals(150L, outcome.malId)
    }

    @Test
    fun anAbsoluteNumberUsesAnibridgeRangesFirst() {
        serveMappings("""{"anilist:21":{"tmdb_show:37854:s21":{"892-1088":"892-1088"}}}""")
        val mapped = blocking { rig.client.animeEpisode(21, null, 1071) }.value()
        assertEquals(AnimeEpisodeMap(37854, 21, 1071), mapped)
        assertTrue(rig.server.requests.none { it.url.host == "api.themoviedb.org" })
    }

    @Test
    fun aNumberPastTheRangesFallsBackToTheLargestAbsoluteEpisodeGroup() {
        serveMappings("""{"anilist:16":{"tmdb_show:1429:s1":{"1-25":"1-25"}}}""")
        rig.server.json(
            "$TMDB/tv/1429/episode_groups",
            """{"results":[{"id":"aired","type":1,"episode_count":90},{"id":"small","type":2,"episode_count":1},
                {"id":"abs","type":2,"episode_count":27}]}""",
        )
        val seasonOne = (1..25).joinToString(",") { """{"order":${it - 1},"season_number":1,"episode_number":$it}""" }
        val seasonTwo = (1..2).joinToString(",") { """{"order":${it - 1},"season_number":2,"episode_number":$it}""" }
        rig.server.json(
            "$TMDB/tv/episode_group/abs",
            """{"groups":[{"order":0,"episodes":[$seasonOne]},{"order":1,"episodes":[$seasonTwo]}]}""",
        )
        assertEquals(AnimeEpisodeMap(1429, 1, 3), blocking { rig.client.animeEpisode(16, null, 3) }.value())
        assertTrue(rig.server.hits("$TMDB/tv/1429/episode_groups").isEmpty())

        assertEquals(AnimeEpisodeMap(1429, 2, 1), blocking { rig.client.animeEpisode(16, null, 26) }.value())
        val past = blocking { rig.client.animeEpisode(16, null, 28) }
        assertEquals(NetResult.Reason.NOT_FOUND, (past as NetResult.Failure).reason)
    }

    @Test
    fun aShowWithNoAbsoluteGroupIsNotFound() {
        serveMappings("""{"anilist:16":{"tmdb_show:1429:s1":{"1-25":"1-25"}}}""")
        rig.server.json("$TMDB/tv/1429/episode_groups", """{"results":[{"id":"aired","type":1,"episode_count":90}]}""")
        val result = blocking { rig.client.animeEpisode(16, null, 26) }
        assertEquals(NetResult.Reason.NOT_FOUND, (result as NetResult.Failure).reason)
    }

    @Test
    fun theAbsoluteGroupFollowsOrderFieldsAndIsCached() {
        serveMappings("""{"anilist:16":{"tmdb_show:1429:s1":{"1-1":"1"}}}""")
        rig.server.json("$TMDB/tv/1429/episode_groups", """{"results":[{"id":"abs","type":2,"episode_count":3}]}""")
        rig.server.json(
            "$TMDB/tv/episode_group/abs",
            """{"groups":[
                {"order":1,"episodes":[{"order":1,"season_number":2,"episode_number":2},{"order":0,"season_number":2,"episode_number":1}]},
                {"order":0,"episodes":[{"order":0,"season_number":1,"episode_number":1}]}]}""",
        )
        assertEquals(AnimeEpisodeMap(1429, 2, 1), blocking { rig.client.animeEpisode(16, null, 2) }.value())
        assertEquals(AnimeEpisodeMap(1429, 2, 2), blocking { rig.client.animeEpisode(16, null, 3) }.value())
        assertEquals(1, rig.server.hits("$TMDB/tv/episode_group/abs").size)
    }

    @Test
    fun anEntryWithNoMappingIsNotFound() {
        rig.wifi = false
        val result = blocking { rig.client.animeEpisode(777, null, 1) }
        assertEquals(NetResult.Reason.NOT_FOUND, (result as NetResult.Failure).reason)
    }
}
