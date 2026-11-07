package com.frameglass.net

import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardTest {
    private val rig = Rig()
    private val target = File(rig.dir, "image.bin")

    @After
    fun clean() = rig.clean()

    private fun assertBlocked(result: NetResult<*>) {
        assertEquals(NetResult.Reason.BAD_RESPONSE, (result as NetResult.Failure).reason)
    }

    @Test
    fun aHostOutsideTheListNeverReachesTheTransport() {
        assertBlocked(blocking { rig.client.download("https://example.com/a.jpg", target) })
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun aSubdomainOfAnAllowedHostIsNotAllowed() {
        assertBlocked(blocking { rig.client.download("https://x.image.tmdb.org/a.jpg", target) })
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun cleartextIsRefusedOnAnAllowedHost() {
        assertBlocked(blocking { rig.client.download("http://image.tmdb.org/a.jpg", target) })
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun aRedirectToAnOutsideHostStopsBeforeTheHop() {
        rig.server.on("image.tmdb.org/a.jpg", Reply(302, headers = mapOf("Location" to "https://example.com/b.jpg")))
        assertBlocked(blocking { rig.client.download("https://image.tmdb.org/a.jpg", target) })
        assertEquals(1, rig.server.requests.size)
        assertFalse(target.exists())
    }

    @Test
    fun theGithubReleaseRedirectIsFollowed() {
        val asset = "https://release-assets.githubusercontent.com/github-production-release-asset/1/2"
        rig.server.on("github.com/a/b", Reply(302, headers = mapOf("Location" to asset)))
        rig.server.on("release-assets.githubusercontent.com/github-production-release-asset/1/2", Reply(body = "payload"))
        blocking { rig.client.download("https://github.com/a/b", target) }.value()
        assertEquals("payload", target.readText())
    }

    @Test
    fun redirectsCannotLoopForever() {
        rig.server.on("image.tmdb.org/a.jpg", Reply(302, headers = mapOf("Location" to "https://image.tmdb.org/a.jpg")))
        val result = blocking { rig.client.download("https://image.tmdb.org/a.jpg", target) }
        assertEquals(NetResult.Reason.NETWORK, (result as NetResult.Failure).reason)
    }

    @Test
    fun aBearerTokenIsNotCarriedAcrossHosts() {
        rig.config.tmdb = V4_TOKEN
        val moved = "https://image.tmdb.org/moved"
        rig.server.on("$TMDB/search/tv", Reply(302, headers = mapOf("Location" to moved)))
        rig.server.on("image.tmdb.org/moved", Reply(body = page()))
        blocking { rig.client.match(parsed("Sample")) }
        assertNull(rig.server.hits("image.tmdb.org/moved").single().header("Authorization"))
    }

    @Test
    fun providerKeyHeadersAreNotCarriedAcrossHosts() {
        val moved = "https://image.tmdb.org/moved"
        rig.server.on("api.subsource.net/start", Reply(302, headers = mapOf("Location" to moved)))
        rig.server.on("image.tmdb.org/moved", Reply(body = "ok"))
        val request = Request.Builder().url("https://api.subsource.net/start")
            .header("Api-Key", V3_KEY)
            .header("X-API-Key", V3_KEY)
            .header("Authorization", "Bearer $V3_KEY")
            .build()
        blocking { rig.http.fetch(request, "probe") }.value()
        val hop = rig.server.hits("image.tmdb.org/moved").single()
        assertNull(hop.header("Api-Key"))
        assertNull(hop.header("X-API-Key"))
        assertNull(hop.header("Authorization"))
    }

    @Test
    fun aSameHostRedirectKeepsTheKeySoACanonicalUrlStillWorks() {
        val moved = "https://api.subsource.net/canonical"
        rig.server.on("api.subsource.net/start", Reply(301, headers = mapOf("Location" to moved)))
        rig.server.on("api.subsource.net/canonical", Reply(body = "ok"))
        val request = Request.Builder().url("https://api.subsource.net/start").header("X-API-Key", V3_KEY).build()
        blocking { rig.http.fetch(request, "probe") }.value()
        assertEquals(V3_KEY, rig.server.hits("api.subsource.net/canonical").single().header("X-API-Key"))
    }

    @Test
    fun theCustomTmdbHostIsAllowedAndFollowsSettings() {
        rig.config.tmdbBaseUrl = "https://worker.example.dev"
        rig.server.on("worker.example.dev/3/search/multi", Reply(body = page()))
        blocking { rig.client.searchManual("x", "en-US") }.value()

        rig.config.tmdbBaseUrl = DEFAULT_TMDB_BASE
        assertBlocked(blocking { rig.client.download("https://worker.example.dev/a.jpg", target) })
        assertEquals(1, rig.server.requests.size)
    }

    @Test
    fun aCleartextCustomBaseFailsSoftly() {
        rig.config.tmdbBaseUrl = "http://worker.example.dev"
        assertBlocked(blocking { rig.client.searchManual("x", "en-US") })
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun theOnlineSwitchStopsCallsTheClientDoesNotGuard() {
        rig.config.onlineEnabled = false
        val result = blocking { rig.http.get(MAPPINGS_URL.toUrl(), "probe") }
        assertEquals(NetResult.Reason.OFFLINE_SETTING, (result as NetResult.Failure).reason)
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun everyClientCallReportsTheOnlineSwitch() {
        rig.config.onlineEnabled = false
        val calls: List<suspend () -> NetResult<*>> = listOf(
            { rig.client.match(parsed("Sample")) },
            { rig.client.searchManual("x", "ar") },
            { rig.client.details(MetaKind.TV, 1, "ar") },
            { rig.client.season(1, 1, "ar") },
            { rig.client.imdbRating("tt1") },
            { rig.client.download("https://image.tmdb.org/a.jpg", target) },
            { rig.client.animeEpisode(1, null, 1) },
            { rig.client.skipSegments(1, 1, 1, 1, 1000) },
        )
        for (call in calls) {
            val result = blocking { call() }
            assertEquals(NetResult.Reason.OFFLINE_SETTING, (result as NetResult.Failure).reason)
        }
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun theClientHasTheBriefSettings() {
        val client = rig.okHttp
        assertEquals(TimeUnit.SECONDS.toMillis(8), client.connectTimeoutMillis.toLong())
        assertEquals(TimeUnit.SECONDS.toMillis(15), client.readTimeoutMillis.toLong())
        assertFalse(ConnectionSpec.CLEARTEXT in client.connectionSpecs)
        assertFalse(client.followRedirects)
        assertTrue(client.interceptors.first() is GuardInterceptor)
    }

    @Test
    fun theCustomHostIsNotHardCoded() = assertFalse("worker.example.dev" in ALLOWED_HOSTS)

    @Test
    fun section49HostsAreAllListed() {
        val required = setOf(
            "api.themoviedb.org", "image.tmdb.org", "graphql.anilist.co", "github.com",
            "release-assets.githubusercontent.com", "api.aniskip.com", "api.theintrodb.org",
            "api.opensubtitles.com", "www.opensubtitles.com", "dl.opensubtitles.com",
            "api.subdl.com", "dl.subdl.com", "api.subsource.net", "www.omdbapi.com",
        )
        assertEquals(emptySet<String>(), required - ALLOWED_HOSTS)
    }

    /** No login means no VIP host, and any host added for subtitles must be one the code really uses. */
    @Test
    fun noSubtitleHostIsListedBeyondTheConfirmedOnes() {
        val subtitleHosts = ALLOWED_HOSTS.filter { "subtitles" in it || "subdl" in it || "subsource" in it }.toSet()
        val confirmed = setOf(
            "api.opensubtitles.com", "www.opensubtitles.com", "dl.opensubtitles.com",
            "api.subdl.com", "dl.subdl.com", "api.subsource.net",
        )
        assertEquals(confirmed, subtitleHosts)
    }
}

private fun String.toUrl() = with(okhttp3.HttpUrl.Companion) { toHttpUrl() }
