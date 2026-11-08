package com.frameglass.data.logic

import com.frameglass.data.api.ApiKeyKind
import com.frameglass.data.api.SubtitleSiteInfo
import com.frameglass.net.ApiKey
import com.frameglass.net.SubtitleSite
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSiteListTest {
    private val sites = listOf(
        SubtitleSite("OpenSubtitles", ApiKey.OPENSUBTITLES, episodesOnly = false),
        SubtitleSite("SubDL", ApiKey.SUBDL, episodesOnly = false),
        SubtitleSite("SubSource", ApiKey.SUBSOURCE, episodesOnly = false),
        SubtitleSite("Gestdown", null, episodesOnly = true),
    )

    private val keyless = SubtitleSiteInfo("Gestdown", null, seriesOnly = true)
    private val openSubtitles = SubtitleSiteInfo("OpenSubtitles", ApiKeyKind.OPENSUBTITLES, seriesOnly = false)
    private val subDl = SubtitleSiteInfo("SubDL", ApiKeyKind.SUBDL, seriesOnly = false)
    private val subSource = SubtitleSiteInfo("SubSource", ApiKeyKind.SUBSOURCE, seriesOnly = false)

    @Test
    fun withNoKeyOnlyTheKeylessSiteSearches() {
        val list = SubtitleSiteList.of(sites, emptySet())
        assertEquals(listOf(keyless), list.active)
        assertEquals(listOf(openSubtitles, subDl, subSource), list.withFreeKey)
    }

    @Test
    fun aSavedKeyMovesItsSiteToActiveInTheDefaultOrder() {
        val list = SubtitleSiteList.of(sites, setOf(ApiKeyKind.SUBSOURCE, ApiKeyKind.OPENSUBTITLES))
        assertEquals(listOf(openSubtitles, subSource, keyless), list.active)
        assertEquals(listOf(subDl), list.withFreeKey)
    }

    @Test
    fun aMetadataKeyAddsNoSubtitleSite() {
        val list = SubtitleSiteList.of(sites, setOf(ApiKeyKind.TMDB, ApiKeyKind.OMDB))
        assertEquals(listOf(keyless), list.active)
    }
}
