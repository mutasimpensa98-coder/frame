package com.frameglass.playerui

import com.frameglass.data.api.SubtitleFailure
import com.frameglass.data.api.SubtitleResult
import com.frameglass.net.NetResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineSubtitleSearchTest {
    private fun result(id: String, language: String, hashMatch: Boolean = false) = SubtitleResult(
        token = id,
        provider = "OpenSubtitles",
        language = language,
        release = id,
        hashMatch = hashMatch,
        downloads = null,
        format = null,
        seasonPack = false,
    )

    @Test
    fun arabicComesBeforeAnEnglishHashMatch() {
        val ranked = listOf(result("en-hash", "en", hashMatch = true), result("ar-1", "ar"), result("en-1", "en"))
        assertEquals(listOf("ar-1", "en-hash", "en-1"), OnlineOrder.arabicFirst(ranked).map { it.token })
    }

    @Test
    fun rankingStaysInsideEachLanguage() {
        val ranked = listOf(result("ar-hash", "ar", hashMatch = true), result("ar-2", "ar"), result("en-1", "en"), result("en-2", "en"))
        assertEquals(ranked, OnlineOrder.arabicFirst(ranked))
    }

    @Test
    fun everyFailureHasItsOwnWords() {
        val expected = mapOf(
            NetResult.Reason.OFFLINE_SETTING to OnlineFailure.OFFLINE,
            NetResult.Reason.NO_KEY to OnlineFailure.NO_KEY,
            NetResult.Reason.NETWORK to OnlineFailure.NETWORK,
            NetResult.Reason.RATE_LIMIT to OnlineFailure.LIMIT,
            NetResult.Reason.NOT_FOUND to OnlineFailure.NOT_IN_PACK,
            NetResult.Reason.BAD_RESPONSE to OnlineFailure.UNREADABLE,
        )
        NetResult.Reason.entries.forEach { reason ->
            assertEquals(expected[reason], OnlineFailure.of(SubtitleFailure(reason)))
        }
    }

    @Test
    fun anErrorFromOutsideTheClientReadsAsANetworkFailure() {
        assertEquals(OnlineFailure.NETWORK, OnlineFailure.of(IllegalStateException()))
    }

    @Test
    fun onlyNotFoundMeansAnEmptySearch() {
        assertTrue(SubtitleFailure(NetResult.Reason.NOT_FOUND).meansNothingFound())
        assertFalse(SubtitleFailure(NetResult.Reason.NETWORK).meansNothingFound())
        assertFalse(IllegalStateException().meansNothingFound())
    }
}
