package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.LookupJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class ImdbRulesTest {
    private fun at(text: String) = Instant.parse(text).toEpochMilli()

    @Test
    fun aSpentDayWaitsForTheNextUtcDay() {
        val resume = ImdbRules.resumeAt(at("2026-11-08T21:30:00Z"))
        assertEquals(at("2026-11-09T00:10:00Z"), resume)
    }

    @Test
    fun aLimitJustAfterMidnightStillWaitsForTheFollowingDay() {
        assertEquals(at("2026-11-10T00:10:00Z"), ImdbRules.resumeAt(at("2026-11-09T00:00:01Z")))
    }

    @Test
    fun theWaitIsNeverLongerThanADayAndAMargin() {
        val now = at("2026-11-08T00:00:00Z")
        val wait = ImdbRules.resumeAt(now) - now
        assertTrue(wait > 0 && wait <= TimeUnit.DAYS.toMillis(1) + TimeUnit.MINUTES.toMillis(10))
    }

    @Test
    fun aRatingFromThisWeekIsFresh() {
        val now = TimeUnit.DAYS.toMillis(100)
        assertTrue(ImdbRules.fresh(now - TimeUnit.DAYS.toMillis(6), now))
        assertFalse(ImdbRules.fresh(now - TimeUnit.DAYS.toMillis(7), now))
        assertFalse(ImdbRules.fresh(null, now))
    }

    @Test
    fun aClockThatWentBackDoesNotKeepDataFreshForever() {
        val now = TimeUnit.DAYS.toMillis(100)
        assertFalse(ImdbRules.fresh(now + TimeUnit.DAYS.toMillis(30), now))
    }

    @Test
    fun onlyATitleWithAnImdbIdAndNoTmdbIdIsAnOmdbMatch() {
        assertTrue(ImdbRules.omdbOnly(null, "tt0000001"))
        assertFalse(ImdbRules.omdbOnly(5, "tt0000001"))
        assertFalse(ImdbRules.omdbOnly(5, null))
        assertFalse(ImdbRules.omdbOnly(null, null))
    }

    @Test
    fun anOmdbPersonIdIsNegativeStableAndCaseBlind() {
        val id = ImdbRules.personId("Sample Actor")
        assertTrue(id < 0)
        assertEquals(id, ImdbRules.personId("  sample   ACTOR "))
        assertNotEquals(id, ImdbRules.personId("Other Actor"))
    }

    @Test
    fun omdbIsNotAskedAgainThisWeekForATitleItCouldNotSettle() {
        val now = TimeUnit.DAYS.toMillis(100)
        val yesterday = now - TimeUnit.DAYS.toMillis(1)
        assertTrue(ImdbRules.omdbGaveUp(MatchState.NONE_FOUND, yesterday, now))
        assertTrue(ImdbRules.omdbGaveUp(MatchState.NEEDS_CHOICE, yesterday, now))
        assertFalse(ImdbRules.omdbGaveUp(MatchState.NONE_FOUND, now - TimeUnit.DAYS.toMillis(7), now))
        assertFalse(ImdbRules.omdbGaveUp(MatchState.NONE_FOUND, null, now))
    }

    @Test
    fun aTitleOmdbNeverAnsweredOrThatChangedStateIsAsked() {
        val now = TimeUnit.DAYS.toMillis(100)
        val yesterday = now - TimeUnit.DAYS.toMillis(1)
        for (state in listOf(MatchState.QUEUED, MatchState.UNMATCHED, MatchState.AUTO, MatchState.MANUAL)) {
            assertFalse(state.name, ImdbRules.omdbGaveUp(state, yesterday, now))
        }
    }

    private fun merges(
        kind: TitleKind = TitleKind.SERIES,
        other: TitleKind = TitleKind.SERIES,
        anilist: Long? = null,
        manual: Boolean = false,
        holders: Int = 1,
    ) = ImdbRules.mergesOnImdb(kind, other, anilist, manual, holders)

    @Test
    fun twoPlainTitlesOnOneImdbIdMerge() {
        assertTrue(merges())
        assertTrue(merges(TitleKind.MOVIE, TitleKind.MOVIE))
    }

    @Test
    fun animeCoursOnOneImdbIdStayApart() {
        assertFalse(merges(kind = TitleKind.ANIME))
        assertFalse(merges(other = TitleKind.ANIME))
        assertFalse(merges(anilist = 77))
    }

    @Test
    fun anIdWithTwoHoldersOrAManualMatchNeverMerges() {
        assertFalse(merges(holders = 2))
        assertFalse(merges(manual = true))
    }

    @Test
    fun aServiceComesIntoUseWithOnlineAndItsSwitch() {
        val off = AppSettings(onlineFeatures = false)
        val on = AppSettings(onlineFeatures = true)
        assertEquals(true to true, ServiceUse.cameOn(off, on))
        assertEquals(true to false, ServiceUse.cameOn(on.copy(tmdbEnabled = false), on))
        assertEquals(false to true, ServiceUse.cameOn(on.copy(imdbEnabled = false), on))
        assertEquals(false to false, ServiceUse.cameOn(on, on))
    }

    @Test
    fun aSwitchTurnedOnWhileOnlineIsOffBringsNothingYet() {
        val off = AppSettings(onlineFeatures = false, tmdbEnabled = false, imdbEnabled = false)
        assertEquals(false to false, ServiceUse.cameOn(off, off.copy(tmdbEnabled = true, imdbEnabled = true)))
        assertEquals(false to false, ServiceUse.cameOn(AppSettings(), AppSettings(tmdbEnabled = false)))
        assertFalse(ServiceUse.tmdb(off.copy(tmdbEnabled = true)))
        assertTrue(ServiceUse.imdb(AppSettings(onlineFeatures = true)))
    }

    @Test
    fun anImdbIdHasItsOwnShape() {
        assertTrue(ImdbRules.IMDB_ID.matches("tt0123456"))
        assertFalse(ImdbRules.IMDB_ID.matches("123456"))
        assertFalse(ImdbRules.IMDB_ID.matches("tt12a"))
    }
}

class MetaRoutesTest {
    private fun route(job: LookupJob, tmdbId: Long?, imdbId: String?, tmdb: Boolean, omdb: Boolean) =
        MetaRoutes.of(job, tmdbId, imdbId, tmdb, omdb)

    @Test
    fun withTmdbOnNothingChanges() {
        assertEquals(MetaRoute.TMDB_MATCH, route(LookupJob.MATCH, null, null, tmdb = true, omdb = true))
        assertEquals(MetaRoute.TMDB_FETCH, route(LookupJob.REFRESH, 7, "tt1", tmdb = true, omdb = true))
        assertEquals(MetaRoute.TMDB_FETCH, route(LookupJob.MATCH, 7, null, tmdb = true, omdb = false))
        assertEquals(MetaRoute.TMDB_EPISODES, route(LookupJob.EPISODES, 7, null, tmdb = true, omdb = false))
    }

    @Test
    fun withTmdbOffNewTitlesMatchThroughOmdb() {
        assertEquals(MetaRoute.OMDB_MATCH, route(LookupJob.MATCH, null, null, tmdb = false, omdb = true))
        assertEquals(MetaRoute.OMDB_FETCH, route(LookupJob.REFRESH, null, "tt1", tmdb = false, omdb = true))
        assertEquals(MetaRoute.OMDB_EPISODES, route(LookupJob.EPISODES, null, "tt1", tmdb = false, omdb = true))
    }

    @Test
    fun withTmdbOffATmdbMatchOnlyRefreshesItsImdbRating() {
        assertEquals(MetaRoute.IMDB_RATING, route(LookupJob.REFRESH, 7, "tt1", tmdb = false, omdb = true))
        assertEquals(MetaRoute.DROP, route(LookupJob.REFRESH, 7, null, tmdb = false, omdb = true))
        assertEquals(MetaRoute.DROP, route(LookupJob.EPISODES, 7, "tt1", tmdb = false, omdb = true))
        assertEquals(MetaRoute.DROP, route(LookupJob.REFRESH, 7, "tt1", tmdb = false, omdb = false))
    }

    @Test
    fun anOmdbMatchLooksForTmdbOnceTmdbAnswers() {
        for (job in LookupJob.entries) {
            assertEquals(MetaRoute.TMDB_UPGRADE, route(job, null, "tt1", tmdb = true, omdb = false))
        }
    }

    @Test
    fun withBothOffARowWaits() {
        assertEquals(MetaRoute.WAIT, route(LookupJob.MATCH, null, null, tmdb = false, omdb = false))
        assertEquals(MetaRoute.WAIT, route(LookupJob.REFRESH, null, "tt1", tmdb = false, omdb = false))
    }

    @Test
    fun withImdbOffNoRouteUsesOmdb() {
        for (job in LookupJob.entries) {
            for (tmdbId in listOf(null, 7L)) {
                for (imdbId in listOf(null, "tt1")) {
                    assertFalse(route(job, tmdbId, imdbId, tmdb = true, omdb = false).omdb)
                    assertFalse(route(job, tmdbId, imdbId, tmdb = false, omdb = false).omdb)
                }
            }
        }
    }

    @Test
    fun withTmdbOffNoRouteUsesTmdb() {
        val tmdbRoutes = setOf(MetaRoute.TMDB_FETCH, MetaRoute.TMDB_EPISODES, MetaRoute.TMDB_MATCH, MetaRoute.TMDB_UPGRADE)
        for (job in LookupJob.entries) {
            for (tmdbId in listOf(null, 7L)) {
                for (imdbId in listOf(null, "tt1")) {
                    for (omdb in listOf(true, false)) {
                        assertFalse(route(job, tmdbId, imdbId, tmdb = false, omdb = omdb) in tmdbRoutes)
                    }
                }
            }
        }
    }

    @Test
    fun aTitleOmdbGaveUpOnIsLeftForTmdb() {
        val gaveUp = MetaRoutes.of(LookupJob.MATCH, null, null, tmdb = false, omdb = true, omdbGaveUp = true)
        assertEquals(MetaRoute.DROP, gaveUp)
        assertEquals(MetaRoute.TMDB_MATCH, MetaRoutes.of(LookupJob.MATCH, null, null, tmdb = true, omdb = true, omdbGaveUp = true))
        assertEquals(MetaRoute.WAIT, MetaRoutes.of(LookupJob.MATCH, null, null, tmdb = false, omdb = false, omdbGaveUp = true))
    }

    @Test
    fun onlyAMatchJobMatches() {
        assertEquals(MetaRoute.DROP, route(LookupJob.REFRESH, null, null, tmdb = true, omdb = true))
        assertEquals(MetaRoute.DROP, route(LookupJob.EPISODES, null, null, tmdb = false, omdb = true))
    }
}
