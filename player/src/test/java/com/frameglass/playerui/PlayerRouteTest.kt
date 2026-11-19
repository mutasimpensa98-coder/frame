package com.frameglass.playerui

import com.frameglass.data.api.PlayRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a player intent carries, as the plain values behind its extras. */
class PlayerRouteTest {
    private fun roundTrip(ref: PlayRef, popUp: Boolean) = PlayerRoute.request(PlayerRoute.extras(ref, popUp))

    @Test
    fun everyKindOfRefComesBackAsItWent() {
        val refs = listOf(
            PlayRef(playableKey = "tv:42:s1e3"),
            PlayRef(fileRowId = 7L),
            PlayRef(fileRowId = 0L),
            PlayRef(uri = "content://media/external/video/media/9"),
        )
        for (ref in refs) {
            assertEquals(PlayRequest(ref, popUp = false), roundTrip(ref, popUp = false))
            assertEquals(PlayRequest(ref, popUp = true), roundTrip(ref, popUp = true))
        }
    }

    @Test
    fun aNormalPlayCarriesNoPopUpExtra() {
        val extras = PlayerRoute.extras(PlayRef(fileRowId = 7L), popUp = false)
        assertEquals(1, extras.size)
        assertFalse(PlayerRoute.request(extras).popUp)
    }

    @Test
    fun thePopUpExtraIsOneMoreValue() {
        val plain = PlayerRoute.extras(PlayRef(playableKey = "movie:5"), popUp = false)
        val popUp = PlayerRoute.extras(PlayRef(playableKey = "movie:5"), popUp = true)
        assertEquals(plain.size + 1, popUp.size)
        assertTrue(PlayerRoute.request(popUp).popUp)
    }

    @Test
    fun nothingToPlayIsNeverAPopUp() {
        val onlyTheFlag = PlayerRoute.extras(PlayRef(), popUp = true)
        val request = PlayerRoute.request(onlyTheFlag)
        assertNull(request.ref)
        assertFalse(request.popUp)
        assertEquals(PlayRequest(null, popUp = false), PlayerRoute.request(emptyMap()))
    }

    @Test
    fun openWithFromAnotherAppPlaysItsUriFullScreen() {
        val request = PlayerRoute.request(emptyMap(), viewUri = "content://downloads/3")
        assertEquals(PlayRequest(PlayRef(uri = "content://downloads/3"), popUp = false), request)
    }

    @Test
    fun anExtraWinsOverTheViewUri() {
        val extras = PlayerRoute.extras(PlayRef(playableKey = "movie:5"), popUp = false)
        assertEquals(PlayRef(playableKey = "movie:5"), PlayerRoute.request(extras, viewUri = "content://downloads/3").ref)
    }

    /** The player is exported, so another app can send the extra. Only this app knows its value. */
    @Test
    fun aPopUpExtraFromAnotherAppPlaysNormally() {
        val own = PlayerRoute.extras(PlayRef(fileRowId = 7L), popUp = true)
        val name = (own.keys - PlayerRoute.extras(PlayRef(fileRowId = 7L), popUp = false).keys).single()
        for (foreign in listOf<Any>(true, "true", "", 1L)) {
            val request = PlayerRoute.request(own + (name to foreign))
            assertEquals(PlayRequest(PlayRef(fileRowId = 7L), popUp = false), request)
        }
        assertTrue(PlayerRoute.request(own).popUp)
    }
}
