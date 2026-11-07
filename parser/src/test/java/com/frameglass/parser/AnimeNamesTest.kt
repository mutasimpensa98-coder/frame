package com.frameglass.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimeNamesTest {
    private fun parse(path: String) = NameParser.parse(path)

    private fun check(
        path: String,
        title: String,
        episode: Int? = null,
        season: Int? = null,
        kind: NameKind = NameKind.EPISODE,
    ) {
        val p = parse(path)
        assertEquals(title, p.title, path)
        assertEquals(episode, p.episode, path)
        assertEquals(season, p.season, path)
        assertEquals(kind, p.kind, path)
    }

    @Test
    fun bracketGroupsOfManyShapes() {
        check("[Erai-raws] Sousou no Frieren - 12 [1080p][HEVC][Multiple Subtitle].mkv", "Sousou no Frieren", 12)
        check("[Judas] Vinland Saga - S02E05.mkv", "Vinland Saga", 5, 2)
        check("(Hi10) Title - 03 (BD 1080p).mkv", "Title", 3)
        check("[Anime Time] Long Title Here - 011 [1080p].mkv", "Long Title Here", 11)
        check("[Group]Title - 05[720p].mkv", "Title", 5)
        check("[Sub.Group] Title - 05.mkv", "Title", 5)
        check("Title_-_05_[1080p]_[ABCD1234].mkv", "Title", 5)
        check("[Group] Title.Dotted.Name - 05.mkv", "Title Dotted Name", 5)
    }

    @Test
    fun groupNameGoesToTagsNotTitle() {
        val p = parse("[Erai-raws] Title - 05 [1080p].mkv")
        assertEquals("Title", p.title)
        assertTrue("Erai-raws" in p.releaseTags, p.releaseTags.toString())
    }

    @Test
    fun techInLeadingBracketIsNotAGroup() {
        val p = parse("[1080p] Quiet Coast (2026).mkv")
        assertEquals("Quiet Coast", p.title)
        assertFalse(p.animeHint)
        assertEquals(NameKind.MOVIE, p.kind)
    }

    @Test
    fun crcTagSetsTheHint() {
        val p = parse("Title (2020) [DEADBEEF].mkv")
        assertEquals("Title", p.title)
        assertTrue(p.animeHint)
        assertTrue(p.releaseTags.none { it.contains("DEADBEEF") }, p.releaseTags.toString())
    }

    @Test
    fun allDigitDateInBracketsIsNotACrc() {
        val p = parse("Title [20231005].mkv")
        assertEquals("Title", p.title)
        assertFalse(p.animeHint)
    }

    @Test
    fun revisions() {
        assertEquals(3, parse("[Group] Title - 07v3 [1080p].mkv").revision)
        assertEquals(2, parse("[Group] Title [07v2][1080p].mkv").revision)
        assertEquals(2, parse("[Group] Title - NCOP1v2.mkv").revision)
        assertEquals(2, parse("Show.S01E02v2.mkv").revision)
        assertEquals(2, parse("[Group] Title 07v2 [1080p].mkv").revision)
        assertNull(parse("[Group] Title - 07 [1080p].mkv").revision)
    }

    @Test
    fun runningNumbersAreAbsoluteAndEpisode() {
        val p = parse("[Group] Long Runner - 1071 [1080p].mkv")
        assertEquals(1071, p.absolute)
        assertEquals(1071, p.episode)
        assertEquals(148, parse("[Group] Hunter x Hunter (2011) - 148 [1080p].mkv").absolute)
        assertEquals(99, parse("Naruto - 099.mkv").absolute)
    }

    @Test
    fun anyAnimeNameWithNoSeasonCountsFromTheStart() {
        assertEquals(5, parse("[Group] Title E05.mkv").absolute)
        assertEquals(1071, parse("انمي ون بيس الحلقة 1071 مترجمة.mp4").absolute)
    }

    @Test
    fun aKnownSeasonClearsAbsolute() {
        assertNull(parse("[Group] Title S2 - 05.mkv").absolute)
        assertNull(parse("Title/Season 2/Title - 07.mkv").absolute)
        assertEquals(1071, parse("One Piece/Season 21/One Piece - 1071.mkv").absolute)
    }

    @Test
    fun explicitEpisodeMarkersAreNotAbsolute() {
        assertNull(parse("Show E1100.mkv").absolute)
        assertFalse(parse("Show.S01E02.mkv").animeHint)
    }

    @Test
    fun seasonForms() {
        check("Title 2nd Season - 07 [1080p].mkv", "Title", 7, 2)
        check("Title 3rd Season - 07.mkv", "Title", 7, 3)
        check("Title 4th Season 07.mkv", "Title", 7, 4)
        check("Title Season 2 - 07 [1080p].mkv", "Title", 7, 2)
        check("[Group] Title S2 - 07 [1080p].mkv", "Title", 7, 2)
        check("[Group] Title S02 - 07 [1080p].mkv", "Title", 7, 2)
        check("[Group] Title S2 07 [1080p].mkv", "Title", 7, 2)
        check("Title Second Season - 07.mkv", "Title", 7, 2)
        check("Title Season Two - 07.mkv", "Title", 7, 2)
        check("[Group] Title (Season 2) - 07.mkv", "Title", 7, 2)
        check("[Group] Title [S2] - 07.mkv", "Title", 7, 2)
    }

    @Test
    fun specials() {
        check("[Group] Title OVA - 02.mkv", "Title", 2, kind = NameKind.SPECIAL)
        check("[Group] Title OVA2 [1080p].mkv", "Title", 2, kind = NameKind.SPECIAL)
        check("[Group] Title OVA 3 [1080p].mkv", "Title", 3, kind = NameKind.SPECIAL)
        check("[Group] Title - OVA [1080p].mkv", "Title", kind = NameKind.SPECIAL)
        check("[Group] Title (OVA) [1080p].mkv", "Title", kind = NameKind.SPECIAL)
        check("[Group] Title OAV - 01.mkv", "Title", 1, kind = NameKind.SPECIAL)
        check("[Group] Title OAD - 01.mkv", "Title", 1, kind = NameKind.SPECIAL)
        check("[Group] Title OAD2 [1080p].mkv", "Title", 2, kind = NameKind.SPECIAL)
        check("[Group] Title [OAD 3].mkv", "Title", 3, kind = NameKind.SPECIAL)
        check("[Group] Title ONA - 04.mkv", "Title", 4, kind = NameKind.SPECIAL)
        check("[Group] Title - SP1 [1080p].mkv", "Title", 1, kind = NameKind.SPECIAL)
        check("[Group] Title SP - 02 [1080p].mkv", "Title", 2, kind = NameKind.SPECIAL)
        check("[Group] Title SP2.mkv", "Title", 2, kind = NameKind.SPECIAL)
        check("Title SP3.mkv", "Title", 3, kind = NameKind.SPECIAL)
        check("[Group] Title - Special 2.mkv", "Title", 2, kind = NameKind.SPECIAL)
        check("[Group] Title - Special [1080p].mkv", "Title", kind = NameKind.SPECIAL)
        check("[Group] Title [SP02].mkv", "Title", 2, kind = NameKind.SPECIAL)
    }

    @Test
    fun recapEpisodeIsSpecial() {
        val p = parse("[Group] Title - 13.5 [1080p].mkv")
        assertEquals("Title", p.title)
        assertEquals(NameKind.SPECIAL, p.kind)
        assertEquals(13, p.episode)
    }

    @Test
    fun specialWordsInsideRealTitlesStayInTheTitle() {
        check("The.Special.2020.mkv", "The Special", kind = NameKind.MOVIE)
        check("Special.Ops.Lioness.S01E02.mkv", "Special Ops Lioness", 2, 1)
        check("Casanova.2005.mkv", "Casanova", kind = NameKind.MOVIE)
    }

    @Test
    fun extras() {
        check("[Group] Title - NCOP1.mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title - NCED2 [1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title - NCOP [1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title NCED1a [1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title S2 NCOP 2 [1080p].mkv", "Title", season = 2, kind = NameKind.EXTRA)
        check("[Group] Title [NCOP1][1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title (NCED) [1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title - NC OP [1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title - Creditless Opening [1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title - OP1 [1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title - ED2 [1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title - PV2 [1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title OP2 [1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title - Trailer [1080p].mkv", "Title", kind = NameKind.EXTRA)
        check("[Group] Title [Menu 1].mkv", "Title", kind = NameKind.EXTRA)
    }

    @Test
    fun extraWordsInsideRealTitlesStayInTheTitle() {
        check("Trailer.Park.Boys.S01E02.mkv", "Trailer Park Boys", 2, 1)
        check("The.Menu.2022.mkv", "The Menu", kind = NameKind.MOVIE)
        check("The.Ending.2019.mkv", "The Ending", kind = NameKind.MOVIE)
        check("Sample.Journey.2020.mkv", "Sample Journey", kind = NameKind.MOVIE)
    }

    @Test
    fun allBracketNames() {
        check("[Group][Title][05][1080p].mkv", "Title", 5)
        check("[Group] [Title] [05] [720p].mkv", "Title", 5)
        check("[Group] Title [05][1080p].mkv", "Title", 5)
        check("[Group] Title [12 END][1080p].mkv", "Title", 12)
        check("【Group】Title 第05話【1080p】.mp4", "Title", 5)
        assertEquals(5, parse("[Title] 05.mkv").episode)
        assertEquals("Title", parse("[Title] 05.mkv").title)
    }

    @Test
    fun bareTrailingNumberWithGroup() {
        check("[Group] Title 05 [1080p].mkv", "Title", 5)
        assertEquals(5, parse("[Group] Title 05 [1080p].mkv").absolute)
    }

    @Test
    fun multiEpisodeInFansubForm() {
        val p = parse("[Group] Title - 07-08 [1080p].mkv")
        assertEquals(7, p.episode)
        assertEquals(8, p.episodeEnd)
    }

    @Test
    fun episodeTitleAfterTheNumberIsDropped() {
        check("[Group] Title - 05 - The Name of It [1080p].mkv", "Title", 5)
    }

    @Test
    fun titlesThatAreNumbers() {
        check("[Group] 86 - 07 [1080p].mkv", "86", 7)
        check("[Group] 22-7 - 05 [1080p].mkv", "22-7", 5)
        check("[Group] Mob Psycho 100 S3 - 05 [1080p].mkv", "Mob Psycho 100", 5, 3)
        check("[Group] Mob Psycho 100 - 05 [1080p].mkv", "Mob Psycho 100", 5)
        check("[Group] Steins;Gate 0 - 05 [1080p].mkv", "Steins;Gate 0", 5)
        check("[Group] 100-man no Inochi - 05 [1080p].mkv", "100-man no Inochi", 5)
        check("[Group] 7SEEDS - 05.mkv", "7SEEDS", 5)
        check("2012 - 05 [1080p].mkv", "2012", 5)
    }

    @Test
    fun yearAfterADashIsNotAnEpisode() {
        val p = parse("Title - 2019 [1080p].mkv")
        assertEquals("Title", p.title)
        assertEquals(2019, p.year)
        assertEquals(NameKind.MOVIE, p.kind)
        assertNull(p.episode)
    }

    @Test
    fun yearInBracketsBeforeTheNumber() {
        val p = parse("[Group] Title (2023) - 05 [1080p].mkv")
        assertEquals("Title", p.title)
        assertEquals(2023, p.year)
        assertEquals(5, p.episode)
    }

    @Test
    fun alternativeTitleInBracketsIsDropped() {
        check("[Group] Yofukashi no Uta (Call of the Night) - 05 [1080p].mkv", "Yofukashi no Uta", 5)
    }

    @Test
    fun dubAndDualAudioTags() {
        assertTrue(parse("[Group] Title - 05 [English Dub].mkv").dubbed)
        assertTrue(parse("[Group] Title - 05 [Multi-Subs].mkv").subtitled)
        val p = parse("[Group] Title - 05 (Dual Audio) [1080p].mkv")
        assertTrue("Dual-Audio" in p.releaseTags, p.releaseTags.toString())
        assertFalse(p.dubbed)
    }

    @Test
    fun animeMovieHasNoEpisode() {
        check("[Group] Title The Movie [1080p].mkv", "Title The Movie", kind = NameKind.MOVIE)
    }
}
