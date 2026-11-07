package com.frameglass.data.logic

import com.frameglass.data.api.TitleKind
import com.frameglass.data.meta.Keys
import com.frameglass.parser.NameParser
import com.frameglass.parser.ParsedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks what :data assumes about the real parser, using the names from brief section 6.2. */
class ParserIntegrationTest {
    private fun parse(rootRel: String, fileRel: String): ParsedName = NameParser.parse(ParsePaths.forParser(rootRel, fileRel))

    private fun key(p: ParsedName, titleId: Long = 7): String =
        PlayableKeys.of(titleId, p.titleKind(), p.kind.name, p.season, p.episode, p.absolute, "fid")

    private fun group(p: ParsedName) = GroupKeys.key(p.titleKind(), Keys.normalize(p.title), p.year)

    @Test
    fun anAddedSeasonFolderStillFindsItsSeries() {
        val p = parse("TV/Show/Season 02", "TV/Show/Season 02/07.mkv")
        assertEquals("Show", p.title)
        assertEquals("t:7:s2:e7", key(p))
    }

    @Test
    fun anAddedSeriesFolderNamesItsEpisodes() {
        val p = parse("Show", "Show/Season 02/07.mkv")
        assertEquals("Show", p.title)
        assertEquals("t:7:s2:e7", key(p))
    }

    @Test
    fun aMovieKeyIsTheTitle() {
        val p = parse("Movies", "Movies/Quiet.Coast.2026.BluRay.x265.mkv")
        assertEquals(TitleKind.MOVIE, p.titleKind())
        assertEquals("t:7", key(p))
    }

    @Test
    fun versionsOfOneEpisodeShareAKeyAndAGroup() {
        val a = parse("TV", "TV/Sample.Journey.S01E02.1080p.WEB-DL.mkv")
        val b = parse("TV", "TV/Sample.Journey.S01E02.720p.HDTV.mkv")
        assertEquals(key(a), key(b))
        assertEquals(group(a), group(b))
    }

    @Test
    fun episodesOfOneSeriesGroupAcrossFolders() {
        val a = parse("One", "One/[SubsPlease] Frieren - 05 (1080p) [ABCD1234].mkv")
        val b = parse("Two/Stuff", "Two/Stuff/[SubsPlease] Frieren - 06 (1080p) [ABCD1234].mkv")
        assertEquals(group(a), group(b))
        assertNotEquals(key(a), key(b))
    }

    @Test
    fun anAbsoluteNumberGivesAKey() {
        val p = parse("Anime", "Anime/One Piece - 1071 [1080p].mkv")
        assertEquals(TitleKind.ANIME, p.titleKind())
        assertEquals("t:7:s1:e1071", key(p))
    }

    @Test
    fun aDoubleEpisodeCoversBothKeys() {
        val p = parse("TV", "TV/Show.S02E07E08.mkv")
        val keys = PlayableKeys.covered(key(p), 7, p.kind.name, p.season, p.episode, p.episodeEnd)
        assertEquals(listOf("t:7:s2:e7", "t:7:s2:e8"), keys)
    }

    @Test
    fun anExtraGetsAFileKey() {
        val p = parse("Anime", "Anime/[Group] Title - NCOP1.mkv")
        assertEquals("f:fid", key(p))
    }

    @Test
    fun arabicSpellingsShareAGroup() {
        val a = parse("TV", "TV/الْعُنْوَان.S01E01.mkv")
        val b = parse("TV", "TV/العنوان.S01E02.mkv")
        assertEquals(group(a), group(b))
    }

    @Test
    fun searchFindsAcrossArabicSpellings() {
        val pairs = listOf("أحمد" to "احمد", "إيمان" to "ايمان", "مدرسة" to "مدرسه", "موسى" to "موسي", "مؤمن" to "مومن", "شيء" to "شيء", "قائد" to "قايد")
        pairs.forEach { (a, b) ->
            assertEquals(SearchText.clean(Keys.normalize(a)), SearchText.clean(Keys.normalize(b)))
        }
        assertEquals(SearchText.clean(Keys.normalize("الْعُنْوَان")), SearchText.clean(Keys.normalize("العنوان")))
    }

    @Test
    fun aSidecarFallbackReadsBareNumbersBesideTheirFolder() {
        val tag: (String) -> EpisodeTag? = { name ->
            val p = NameParser.parse("Show/Season 02/$name")
            (p.episode ?: p.absolute)?.let { EpisodeTag(p.season, it) }
        }
        val hits = SidecarMatcher.match(
            "Show - S02E07.mkv",
            listOf(SidecarFile("", "s02e07 arabic.srt"), SidecarFile("", "s02e08 arabic.srt")),
            emptyList(),
            tag,
        )
        assertEquals("s02e07 arabic.srt", hits.single().file.name)
        assertTrue(hits.single().language == "ar")
    }
}
