package com.frameglass.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentsTest {
    private val rig = Rig()
    private val lengthMs = 1_420_000L
    private val introDb = "api.theintrodb.org/v4/media"

    @After
    fun clean() = rig.clean()

    // Shape read from the live API on 2026-11-07.
    private val aniSkipBody = """{"found":true,"results":[
        {"interval":{"startTime":54.711,"endTime":145.111},"skipType":"op","skipId":"a","episodeLength":1435.122},
        {"interval":{"startTime":1338.255,"endTime":1428.255},"skipType":"ed","skipId":"b","episodeLength":1435.028},
        {"interval":{"startTime":1.0,"endTime":2.0},"skipType":"mixed-op","skipId":"c","episodeLength":1435.0}],
        "message":"Successfully found skip times","statusCode":200}"""

    private val notFound = """{"found":false,"results":[],"message":"No skip times found","statusCode":404}"""

    private fun skip(mal: Long? = 40748, tmdb: Long? = null, season: Int? = 1, episode: Int? = 1, length: Long = lengthMs) =
        blocking { rig.client.skipSegments(mal, tmdb, season, episode, length) }

    @Test
    fun aniSkipIsAskedAsTheBriefSpells() {
        rig.server.json("api.aniskip.com/v2/skip-times/40748/1", aniSkipBody)
        skip().value()
        val url = rig.server.requests.single().url
        val query = url.encodedQuery.orEmpty()
        assertEquals("types[]=op&types[]=ed&types[]=recap&episodeLength=1420", query)
    }

    @Test
    fun aniSkipSegmentsBecomeMilliseconds() {
        rig.server.json("api.aniskip.com/v2/skip-times/40748/1", aniSkipBody)
        val segments = skip().value()
        assertEquals(
            listOf(
                NetSegment("OP", 54_711, 145_111, "aniskip"),
                NetSegment("ED", 1_338_255, 1_428_255, "aniskip"),
            ),
            segments,
        )
    }

    @Test
    fun anUnknownLengthStillSendsTheRequiredParameter() {
        rig.server.json("api.aniskip.com/v2/skip-times/40748/1", aniSkipBody)
        skip(length = 0).value()
        assertEquals("0", rig.server.requests.single().url.queryParameter("episodeLength"))
    }

    @Test
    fun aHitOnAniSkipDoesNotSpendTheIntroDbQuota() {
        rig.server.json("api.aniskip.com/v2/skip-times/40748/1", aniSkipBody)
        skip(tmdb = 95479).value()
        assertTrue(rig.server.hits(introDb).isEmpty())
    }

    @Test
    fun a404OnAniSkipFallsThroughToTheIntroDb() {
        rig.server.on("api.aniskip.com/v2/skip-times/40748/1", Reply(404, notFound))
        rig.server.json(introDb, """{"tvdb_id":1,"type":"tv","intro":[{"start_ms":30000,"end_ms":90000}]}""")
        val segments = skip(tmdb = 95479).value()
        assertEquals(listOf(NetSegment("OP", 30_000, 90_000, "theintrodb")), segments)
    }

    @Test
    fun theIntroDbIsAskedByTmdbIdOnly() {
        rig.server.json(introDb, """{"tvdb_id":1,"type":"tv"}""")
        skip(mal = null, tmdb = 1396, season = 1, episode = 2).value()
        val url = rig.server.requests.single().url
        assertEquals(setOf("tmdb_id", "season", "episode", "details"), url.queryParameterNames)
        assertEquals("1396", url.queryParameter("tmdb_id"))
        assertEquals("2", url.queryParameter("episode"))
    }

    @Test
    fun theFileLengthDoesNotLeaveThePhoneForTheIntroDb() {
        rig.server.json(introDb, """{"tvdb_id":1,"type":"tv"}""")
        skip(mal = null, tmdb = 1396).value()
        assertFalse("duration" in rig.server.requests.single().url.toString())
    }

    @Test
    fun aMovieIsAskedWithoutSeasonOrEpisode() {
        rig.server.json(introDb, """{"tvdb_id":169,"type":"movie","intro":[{"start_ms":null,"end_ms":40000}]}""")
        val segments = skip(mal = null, tmdb = 603, season = null, episode = null).value()
        assertEquals(setOf("tmdb_id", "details"), rig.server.requests.single().url.queryParameterNames)
        assertEquals(listOf(NetSegment("OP", 0, 40_000, "theintrodb")), segments)
    }

    @Test
    fun aSeasonWithoutAnEpisodeIsNeverSent() {
        assertTrue(skip(mal = null, tmdb = 1, season = 1, episode = null).value().isEmpty())
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun nullsAndZerosMeanWhatTheIntroDbSpecSays() {
        val body = """{"tvdb_id":1,"type":"tv",
            "intro":[{"start_ms":null,"end_ms":23000},{"start_ms":100,"end_ms":0}],
            "recap":[{"start_ms":25000,"end_ms":134000},{"start_ms":10,"end_ms":null}],
            "credits":[{"start_ms":1300000,"end_ms":null},{"start_ms":0,"end_ms":null},{"start_ms":200,"end_ms":0}],
            "preview":[{"start_ms":1000,"end_ms":2000}]}"""
        rig.server.json(introDb, body)
        assertEquals(
            listOf(
                NetSegment("OP", 0, 23_000, "theintrodb"),
                NetSegment("RECAP", 25_000, 134_000, "theintrodb"),
                NetSegment("ED", 1_300_000, lengthMs, "theintrodb"),
            ),
            skip(mal = null, tmdb = 1).value(),
        )
    }

    @Test
    fun openCreditsNeedAKnownLength() {
        rig.server.json(introDb, """{"tvdb_id":1,"credits":[{"start_ms":1300000,"end_ms":null}]}""")
        assertTrue(skip(mal = null, tmdb = 1, length = 0).value().isEmpty())
    }

    @Test
    fun aSegmentPastTheEndOfTheFileIsCutToFit() {
        rig.server.json(introDb, """{"tvdb_id":1,"credits":[{"start_ms":1300000,"end_ms":1500000}]}""")
        assertEquals(lengthMs, skip(mal = null, tmdb = 1).value().single().endMs)
    }

    @Test
    fun timesFromADifferentCutAreRejected() {
        val farCut = """{"tvdb_id":1,"group_duration_ms":3501000,"intro":[{"start_ms":228647,"end_ms":246339}]}"""
        rig.server.json(introDb, farCut)
        assertTrue(skip(mal = null, tmdb = 1).value().isEmpty())
    }

    @Test
    fun timesFromACloseCutAreKept() {
        val closeCut = """{"tvdb_id":1,"group_duration_ms":1500000,"intro":[{"start_ms":1000,"end_ms":2000}]}"""
        rig.server.json(introDb, closeCut)
        assertEquals(1, skip(mal = null, tmdb = 1).value().size)
    }

    @Test
    fun nothingFoundAnywhereIsAnEmptyAnswer() {
        rig.server.on("api.aniskip.com/v2/skip-times/40748/1", Reply(404, notFound))
        rig.server.on(introDb, Reply(404, """{"error":"no submissions","code":"no_submissions"}"""))
        assertEquals(emptyList<NetSegment>(), skip(tmdb = 1).value())
    }

    @Test
    fun anOutageIsReportedSoTheLookupCanBeRetried() {
        rig.server.on("api.aniskip.com/v2/skip-times/40748/1", Reply(503))
        rig.server.on(introDb, Reply(404))
        val result = skip(tmdb = 1)
        assertEquals(NetResult.Reason.NETWORK, (result as NetResult.Failure).reason)
    }

    @Test
    fun oneProviderDownDoesNotHideTheOther() {
        rig.server.on("api.aniskip.com/v2/skip-times/40748/1", Reply(503))
        rig.server.json(introDb, """{"tvdb_id":1,"intro":[{"start_ms":1000,"end_ms":2000}]}""")
        assertEquals(1, skip(tmdb = 1).value().size)
    }

    @Test
    fun noIdsAtAllSendsNothing() {
        assertTrue(skip(mal = null, tmdb = null).value().isEmpty())
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun theMappingPicksTheMalEntryOfThatExactSeason() {
        rig.server.on(
            "github.com/anibridge/anibridge-mappings/releases/download/v3/mappings.min.json",
            Reply(body = """{"mal:3000":{"tmdb_show:34179:s2":{"1-26":"1-26"}}}"""),
        )
        blocking { rig.mappings.refreshIfDue() }
        rig.server.json("api.aniskip.com/v2/skip-times/3000/7", aniSkipBody)
        // 111 is season 1's MAL ID, which is what a title row anchored on season 1 would hold.
        val segments = skip(mal = 111, tmdb = 34179, season = 2, episode = 7).value()
        assertEquals(2, segments.size)
    }

    @Test
    fun aManualMatchWithNoMalIdStillFindsAniSkipThroughTheMapping() {
        rig.server.on(
            "github.com/anibridge/anibridge-mappings/releases/download/v3/mappings.min.json",
            Reply(body = """{"mal:3000":{"tmdb_show:34179:s2":{"1-26":"1-26"}}}"""),
        )
        blocking { rig.mappings.refreshIfDue() }
        rig.server.json("api.aniskip.com/v2/skip-times/3000/7", aniSkipBody)
        assertEquals(2, skip(mal = null, tmdb = 34179, season = 2, episode = 7).value().size)
    }
}
