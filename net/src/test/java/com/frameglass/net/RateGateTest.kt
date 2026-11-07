package com.frameglass.net

import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateGateTest {
    private val host = "graphql.anilist.co"
    private var now = 2_000_000_000_000L
    private val gate = RateGate { now }

    @Test
    fun retryAfterIsObeyedOn429() {
        gate.observe(host, 429, headersOf("Retry-After", "30"))
        assertEquals(30_000L, gate.waitMs(host))
    }

    @Test
    fun aZeroRemainingWithADeltaResetWaitsThatLong() {
        gate.observe(host, 200, headersOf("x-ratelimit-remaining", "0", "x-ratelimit-reset", "10"))
        assertEquals(10_000L, gate.waitMs(host))
    }

    @Test
    fun aZeroRemainingWithAnEpochResetWaitsUntilThen() {
        val reset = (now + 45_000L) / 1000L
        gate.observe(host, 200, headersOf("X-RateLimit-Remaining", "0", "X-RateLimit-Reset", reset.toString()))
        assertEquals(45_000L, gate.waitMs(host))
    }

    @Test
    fun aZeroRemainingWithNoResetWaitsOneWindow() {
        gate.observe(host, 200, headersOf("X-RateLimit-Limit", "30", "X-RateLimit-Remaining", "0"))
        assertEquals(60_000L, gate.waitMs(host))
    }

    @Test
    fun remainingRequestsDoNotBlock() {
        gate.observe(host, 200, headersOf("X-RateLimit-Limit", "30", "X-RateLimit-Remaining", "29"))
        assertEquals(0L, gate.waitMs(host))
    }

    @Test
    fun theDailyQuotaHeadersBlockToo() {
        gate.observe(host, 429, headersOf("X-UsageLimit-Remaining", "0", "X-UsageLimit-Reset", "3600"))
        assertEquals(3_600_000L, gate.waitMs(host))
    }

    @Test
    fun a429WithNoHintsStillBacksOff() {
        gate.observe(host, 429, headersOf())
        assertEquals(30_000L, gate.waitMs(host))
    }

    @Test
    fun aHostIsLimitedAloneAndTheWaitRunsOut() {
        gate.observe(host, 429, headersOf("Retry-After", "5"))
        assertEquals(0L, gate.waitMs("api.aniskip.com"))
        now += 5_000L
        assertEquals(0L, gate.waitMs(host))
    }

    @Test
    fun aLaterHeaderCannotShortenAWait() {
        gate.observe(host, 429, headersOf("Retry-After", "60"))
        gate.observe(host, 429, headersOf("Retry-After", "5"))
        assertEquals(60_000L, gate.waitMs(host))
    }
}

class RateLimitedCallsTest {
    private val rig = Rig()

    @After
    fun clean() = rig.clean()

    @Test
    fun a429IsRetriedOnceAfterTheWait() {
        rig.server.on(
            "$TMDB/search/multi",
            Reply(429, headers = mapOf("Retry-After", "4")),
            Reply(body = page(multiRow(tvRow(1, "Sample"), "tv"))),
        )
        val results = blocking { rig.client.searchManual("Sample", "en-US") }.value()
        assertEquals(1, results.size)
        assertEquals(listOf(4_000L), rig.pauses.toList())
        assertEquals(2, rig.server.hits("$TMDB/search/multi").size)
    }

    @Test
    fun aSecond429IsReportedAsRateLimit() {
        rig.server.on("$TMDB/search/multi", Reply(429, headers = mapOf("Retry-After", "1")))
        val result = blocking { rig.client.searchManual("Sample", "en-US") }
        assertEquals(NetResult.Reason.RATE_LIMIT, (result as NetResult.Failure).reason)
        assertEquals(2, rig.server.hits("$TMDB/search/multi").size)
    }

    @Test
    fun aLongWaitFailsWithoutSendingAnything() {
        rig.server.on("$TMDB/search/multi", Reply(429, headers = mapOf("Retry-After", "3600")))
        blocking { rig.client.searchManual("Sample", "en-US") }
        val before = rig.server.requests.size
        val result = blocking { rig.client.searchManual("Sample", "en-US") }
        assertEquals(NetResult.Reason.RATE_LIMIT, (result as NetResult.Failure).reason)
        assertEquals(before, rig.server.requests.size)
        assertTrue(rig.pauses.isEmpty())
    }
}

private fun mapOf(name: String, value: String): Map<String, String> = kotlin.collections.mapOf(name to value)
