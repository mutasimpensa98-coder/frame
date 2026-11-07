package com.frameglass.data.logic

import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.NameKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeysTest {
    private fun key(kind: TitleKind, nameKind: String, season: Int?, episode: Int?, absolute: Int? = null) =
        PlayableKeys.of(7, kind, nameKind, season, episode, absolute, "abc")

    @Test
    fun movieKeyIsTheTitle() {
        assertEquals("t:7", key(TitleKind.MOVIE, NameKinds.MOVIE, null, null))
    }

    @Test
    fun episodeKeyUsesParserNumbers() {
        assertEquals("t:7:s2:e5", key(TitleKind.SERIES, NameKinds.EPISODE, 2, 5))
    }

    @Test
    fun missingSeasonReadsAsOneSoBothNamingsShareProgress() {
        assertEquals(key(TitleKind.SERIES, NameKinds.EPISODE, 1, 5), key(TitleKind.SERIES, NameKinds.EPISODE, null, 5))
    }

    @Test
    fun specialWithoutSeasonIsSeasonZero() {
        assertEquals("t:7:s0:e2", key(TitleKind.ANIME, NameKinds.SPECIAL, null, 2))
    }

    @Test
    fun absoluteNumberStandsInForEpisode() {
        assertEquals("t:7:s1:e1071", key(TitleKind.ANIME, NameKinds.EPISODE, null, null, 1071))
    }

    @Test
    fun noParsedEpisodeFallsBackToFile() {
        assertEquals("f:abc", key(TitleKind.SERIES, NameKinds.EPISODE, null, null))
    }

    @Test
    fun extraNeverCollidesWithAnEpisode() {
        assertEquals("f:abc", key(TitleKind.ANIME, NameKinds.EXTRA, null, 1))
    }

    @Test
    fun rehomeMovesOnlyThatTitle() {
        assertEquals("t:9:s1:e2", PlayableKeys.rehome("t:5:s1:e2", 5, 9))
        assertEquals("t:9", PlayableKeys.rehome("t:5", 5, 9))
        assertEquals("t:55:s1:e2", PlayableKeys.rehome("t:55:s1:e2", 5, 9))
        assertEquals("f:5", PlayableKeys.rehome("f:5", 5, 9))
    }

    @Test
    fun remapHandlesSwappedIds() {
        val ids = mapOf(1L to 2L, 2L to 1L)
        assertEquals("t:2:s1:e1", PlayableKeys.remap("t:1:s1:e1", ids))
        assertEquals("t:1", PlayableKeys.remap("t:2", ids))
        assertEquals("u:xyz", PlayableKeys.remap("u:xyz", ids))
    }

    @Test
    fun multiEpisodeFileCoversEachEpisode() {
        val keys = PlayableKeys.covered("t:7:s2:e7", 7, NameKinds.EPISODE, 2, 7, 8)
        assertEquals(listOf("t:7:s2:e7", "t:7:s2:e8"), keys)
        assertEquals(listOf("t:7"), PlayableKeys.covered("t:7", 7, NameKinds.MOVIE, null, null, null))
    }

    @Test
    fun groupKeyHoldsKindNameAndYear() {
        assertEquals("SERIES|show|2020", GroupKeys.key(TitleKind.SERIES, "show", 2020))
        assertEquals("MOVIE|show|-", GroupKeys.key(TitleKind.MOVIE, "show", null))
    }

    @Test
    fun seriesAndAnimeShareAGroup() {
        val found = GroupKeys.pick(TitleKind.SERIES, null, listOf(GroupCandidate(1, TitleKind.ANIME, null)))
        assertEquals(1L, found?.id)
    }

    @Test
    fun missingYearJoinsASingleCandidate() {
        val found = GroupKeys.pick(TitleKind.SERIES, null, listOf(GroupCandidate(1, TitleKind.SERIES, 2021)))
        assertEquals(1L, found?.id)
    }

    @Test
    fun differentYearsStayApart() {
        assertNull(GroupKeys.pick(TitleKind.MOVIE, 2021, listOf(GroupCandidate(1, TitleKind.MOVIE, 1984))))
    }

    @Test
    fun aMovieNeverJoinsASeries() {
        assertNull(GroupKeys.pick(TitleKind.MOVIE, null, listOf(GroupCandidate(1, TitleKind.SERIES, null))))
    }

    @Test
    fun aMissingYearWithTwoCandidatesDoesNotGuess() {
        val candidates = listOf(GroupCandidate(1, TitleKind.MOVIE, 1984), GroupCandidate(2, TitleKind.MOVIE, 2021))
        assertNull(GroupKeys.pick(TitleKind.MOVIE, null, candidates))
    }

    @Test
    fun rangeCoversChildrenButNotSiblings() {
        val low = PathRange.low("Movies")
        val high = PathRange.high("Movies")
        fun inside(path: String) = path.toUtf8Key() >= low.toUtf8Key() && path.toUtf8Key() < high.toUtf8Key()
        assertTrue(inside("Movies/a.mkv"))
        assertTrue(inside("Movies/عربي/ب.mkv"))
        assertTrue(!inside("Movies 2/a.mkv"))
        assertTrue(!inside("Movies.mkv"))
        assertTrue(!inside("Moviesz/a.mkv"))
    }

    @Test
    fun wholeVolumeRangeTakesEmojiPaths() {
        assertTrue("😀/a.mkv".toUtf8Key() < PathRange.high("").toUtf8Key())
    }

    // SQLite's BINARY collation compares UTF-8 bytes.
    private fun String.toUtf8Key(): String = toByteArray().joinToString("") { "%02x".format(it) }

    @Test
    fun parserPathKeepsTheAddedFolderAndItsParent() {
        assertEquals("Show/Season 02/07.mkv", ParsePaths.forParser("Show", "Show/Season 02/07.mkv"))
        assertEquals("Show/Season 02/07.mkv", ParsePaths.forParser("TV/Show/Season 02", "TV/Show/Season 02/07.mkv"))
        assertEquals("a/b.mkv", ParsePaths.forParser("", "a/b.mkv"))
    }

    @Test
    fun cleanNameDropsTagsAndBrackets() {
        assertEquals("Show S01E02", CleanName.of("x/[Group] Show.S01E02.1080p.WEB-DL.mkv", "1080p,WEB-DL"))
        assertEquals("الحلقة ٧", CleanName.of("الحلقة ٧.mkv", null))
    }
}
