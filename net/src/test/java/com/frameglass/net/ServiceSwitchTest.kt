package com.frameglass.net

import java.io.File
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The switches are enforced in the one HTTP client, so every caller is covered, Coil included. */
class ServiceSwitchTest {
    private val rig = Rig(FakeConfig(omdb = OMDB_KEY))
    private val target = File(rig.dir, "image.bin")

    @After
    fun clean() = rig.clean()

    private fun assertOff(result: NetResult<*>) {
        val failure = result as NetResult.Failure
        assertEquals(NetResult.Reason.OFFLINE_SETTING, failure.reason)
    }

    @Test
    fun tmdbOffReachesNoTmdbHostThroughAnyCall() {
        rig.config.tmdbEnabled = false
        assertOff(blocking { rig.client.match(parsed("Sample Journey")) })
        assertOff(blocking { rig.client.match(parsed("Sample Journey", animeHint = true)) })
        assertOff(blocking { rig.client.searchManual("Sample", "en-US") })
        assertOff(blocking { rig.client.details(MetaKind.TV, 10, "en-US") })
        assertOff(blocking { rig.client.season(10, 1, "en-US") })
        assertTrue(blocking { rig.client.animeEpisode(50, null, 3) } is NetResult.Failure)
        assertOff(blocking { rig.client.tmdbForImdb("tt0000001", parsed("Sample")) })
        assertOff(blocking { rig.client.download("https://image.tmdb.org/t/p/w500/a.jpg", target) })
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun tmdbOffClosesAniListAndAnibridgeToo() {
        rig.config.tmdbEnabled = false
        assertOff(blocking { rig.client.download("https://github.com/anibridge/anibridge-mappings/releases/download/v3/x", target) })
        val call = rig.okHttp.newCall(Request.Builder().url("https://graphql.anilist.co/".toHttpUrl()).build())
        assertTrue(runCatching { call.execute() }.exceptionOrNull() is ServiceOffException)
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun tmdbOffClosesTheCustomBaseUrl() {
        rig.config.tmdbBaseUrl = "https://worker.example.dev/"
        rig.config.tmdbEnabled = false
        assertOff(blocking { rig.client.match(parsed("Sample")) })
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun tmdbOffLeavesOmdbAndSkipTimesOpen() {
        rig.config.tmdbEnabled = false
        rig.server.json("www.omdbapi.com/", """{"imdbRating":"7.5","Response":"True"}""")
        rig.server.json("api.aniskip.com/v2/skip-times/40748/1", """{"found":false,"results":[]}""")
        assertEquals(7.5, blocking { rig.client.imdbRating("tt0000001") }.value(), 1e-9)
        blocking { rig.client.skipSegments(40748, null, 1, 1, 1_420_000) }
        assertEquals(setOf("www.omdbapi.com", "api.aniskip.com"), rig.server.requests.map { it.url.host }.toSet())
    }

    @Test
    fun imdbOffReachesNoOmdbHostThroughAnyCall() {
        rig.config.imdbEnabled = false
        assertOff(blocking { rig.client.imdbRating("tt0000001") })
        assertOff(blocking { rig.client.omdbMatch(parsed("Sample", year = 2020)) })
        assertOff(blocking { rig.client.omdbSearch("Sample") })
        assertOff(blocking { rig.client.omdbTitle("tt0000001") })
        assertOff(blocking { rig.client.omdbSeason("tt0000001", 1) })
        assertOff(blocking { rig.client.download("https://$OMDB_POSTER_HOST/images/M/a._V1_SX300.jpg", target) })
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun imdbOffLeavesTmdbOpen() {
        rig.config.imdbEnabled = false
        rig.server.json("$TMDB/search/tv", page(tvRow(1, "Sample Journey")))
        assertTrue(blocking { rig.client.match(parsed("Sample Journey")) } is NetResult.Ok)
        assertFalse(rig.server.requests.any { it.url.host == "www.omdbapi.com" })
    }

    @Test
    fun aConfigWithoutSwitchesHasBothOn() {
        val plain = object : NetConfig {
            override fun key(kind: ApiKey): String? = null
            override val tmdbBaseUrl = DEFAULT_TMDB_BASE
            override val onlineEnabled = true
        }
        NetPolicy { plain }.check("https://image.tmdb.org/a.jpg".toHttpUrl())
        NetPolicy { plain }.check("https://www.omdbapi.com/".toHttpUrl())
    }

    @Test
    fun theOmdbPosterHostIsListed() = assertTrue(OMDB_POSTER_HOST in ALLOWED_HOSTS)
}
