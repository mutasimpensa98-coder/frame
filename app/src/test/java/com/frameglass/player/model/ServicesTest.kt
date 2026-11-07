package com.frameglass.player.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicesTest {
    private val on = ServiceSwitch(hasKey = true, enabled = true)
    private val off = ServiceSwitch(hasKey = true, enabled = false)
    private val noKey = ServiceSwitch(hasKey = false, enabled = true)
    private val noKeyOff = ServiceSwitch(hasKey = false, enabled = false)

    private fun state(tmdb: ServiceSwitch, imdb: ServiceSwitch, online: Boolean = true) = ServiceState(online, tmdb, imdb)

    @Test
    fun aSwitchWithNoKeyIsOffAndCannotMove() {
        assertFalse(noKey.on)
        assertFalse(noKey.switchable)
        assertFalse(noKeyOff.on)
        assertTrue(off.switchable)
        assertFalse(off.on)
        assertTrue(on.on)
    }

    @Test
    fun tmdbIdentifiesWheneverItIsOn() {
        assertEquals(Identifier.TMDB, state(on, on).identifier)
        assertEquals(Identifier.TMDB, state(on, off).identifier)
        assertEquals(FallbackNote.NONE, state(on, on).tmdbFallback)
    }

    @Test
    fun imdbTakesOverWhileTmdbIsOffOrHasNoKey() {
        assertEquals(Identifier.IMDB, state(off, on).identifier)
        assertEquals(Identifier.IMDB, state(noKey, on).identifier)
        assertEquals(FallbackNote.THROUGH_IMDB, state(off, on).tmdbFallback)
        assertEquals(FallbackNote.THROUGH_IMDB, state(noKey, on).tmdbFallback)
    }

    @Test
    fun withBothOffNothingIdentifies() {
        assertEquals(Identifier.NONE, state(off, off).identifier)
        assertEquals(Identifier.NONE, state(off, noKey).identifier)
        assertEquals(FallbackNote.NOTHING, state(off, off).tmdbFallback)
        assertEquals(FallbackNote.NOTHING, state(off, noKey).tmdbFallback)
    }

    @Test
    fun aFreshPhoneWithNoKeysSaysOnlyToAddOne() {
        assertEquals(Identifier.NONE, state(noKey, noKey).identifier)
        assertEquals(FallbackNote.NONE, state(noKey, noKey).tmdbFallback)
        assertEquals(FallbackNote.NONE, state(noKey, off).tmdbFallback)
    }

    @Test
    fun onlineFeaturesOffStopsBothAndAddsNoLine() {
        assertEquals(FallbackNote.NONE, state(off, on, online = false).tmdbFallback)
        assertEquals(FallbackNote.NONE, state(on, on, online = false).tmdbFallback)
    }

    @Test
    fun onlineFeaturesOffIsNamedApartFromBothServicesOff() {
        // Fix match must point at the Online switch, not at TMDB and IMDb, when both of those are already on.
        assertEquals(Identifier.OFFLINE, state(on, on, online = false).identifier)
        assertEquals(Identifier.OFFLINE, state(off, on, online = false).identifier)
        assertEquals(Identifier.OFFLINE, state(noKey, noKey, online = false).identifier)
        assertEquals(Identifier.NONE, state(off, off, online = true).identifier)
    }
}
