package com.frameglass.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeneralNamesTest {
    private fun parse(path: String) = NameParser.parse(path)

    private fun movie(path: String, title: String, year: Int? = null) {
        val p = parse(path)
        assertEquals(title, p.title, path)
        assertEquals(year, p.year, path)
        assertEquals(NameKind.MOVIE, p.kind, path)
        assertNull(p.episode, path)
    }

    private fun episode(path: String, title: String, season: Int?, number: Int?, end: Int? = null) {
        val p = parse(path)
        assertEquals(title, p.title, path)
        assertEquals(season, p.season, path)
        assertEquals(number, p.episode, path)
        assertEquals(end, p.episodeEnd, path)
        assertEquals(NameKind.EPISODE, p.kind, path)
    }

    @Test
    fun seasonEpisodeSpellings() {
        episode("Show.s1e2.mkv", "Show", 1, 2)
        episode("Show S01 E02.mkv", "Show", 1, 2)
        episode("Show.S01.E02.mkv", "Show", 1, 2)
        episode("Show_S01_E02.mkv", "Show", 1, 2)
        episode("Show-S01E02.mkv", "Show", 1, 2)
        episode("Show - S01E02 - Pilot.mkv", "Show", 1, 2)
        episode("Show S01EP02.mkv", "Show", 1, 2)
        episode("Show.Season.2.Episode.7.1080p.mkv", "Show", 2, 7)
        episode("Show Season 2 Ep 7.mkv", "Show", 2, 7)
        episode("Show.2x07.Name.mkv", "Show", 2, 7)
        episode("Show.S2024E05.mkv", "Show", 2024, 5)
    }

    @Test
    fun episodeRanges() {
        episode("Show.S02E07-E08.mkv", "Show", 2, 7, 8)
        episode("Show.S02E07-08.mkv", "Show", 2, 7, 8)
        episode("Show.S02E07 E08.mkv", "Show", 2, 7, 8)
        episode("Show.S02E07E08E09.mkv", "Show", 2, 7, 9)
        episode("Show 2x07-08.mkv", "Show", 2, 7, 8)
        episode("Show 2x07x08.mkv", "Show", 2, 7, 8)
        episode("Show.E05-E06.mkv", "Show", null, 5, 6)
        episode("Show Episodes 3-4.mkv", "Show", null, 3, 4)
        assertNull(parse("Show.S02E07-1080p.mkv").episodeEnd)
    }

    @Test
    fun resolutionIsNotCrossForm() {
        movie("Quiet Coast 1920x1080.mkv", "Quiet Coast")
        movie("Quiet Coast 720x480.mkv", "Quiet Coast")
        assertTrue("1920x1080" in parse("Quiet Coast 1920x1080.mkv").releaseTags)
        movie("4x4.2019.mkv", "4x4", 2019)
    }

    @Test
    fun yearRules() {
        movie("Title.2019.mkv", "Title", 2019)
        movie("Title (2019).mkv", "Title", 2019)
        movie("Title [2019].mkv", "Title", 2019)
        movie("Title.1080p.2019.mkv", "Title", 2019)
        movie("Title.2019.Arabic.mkv", "Title", 2019)
        movie("Title.2019-GROUP.mkv", "Title", 2019)
        movie("Title.1999.2000.mkv", "Title 1999", 2000)
        movie("Blade Runner 2049 (2017).mkv", "Blade Runner 2049", 2017)
        movie("Blade.Runner.2049.mkv", "Blade Runner 2049")
    }

    @Test
    fun yearsInsideTitles() {
        movie("2001.A.Space.Odyssey.1968.1080p.mkv", "2001 A Space Odyssey", 1968)
        movie("2001.A.Space.Odyssey.mkv", "2001 A Space Odyssey")
        movie("Class.of.1984.1982.mkv", "Class of 1984", 1982)
        movie("Class.of.1984.mkv", "Class of 1984")
        movie("Class of 1984 (1982).mkv", "Class of 1984", 1982)
        movie("Summer.of.1942.mkv", "Summer of 1942")
    }

    @Test
    fun titlesThatAreNumbers() {
        movie("1917.mkv", "1917")
        movie("2012.mkv", "2012")
        movie("2012.2009.mkv", "2012", 2009)
        movie("300.2006.1080p.mkv", "300", 2006)
        movie("300.mkv", "300")
        movie("300 (2006) [1080p].mkv", "300", 2006)
        movie("12.Monkeys.1995.mkv", "12 Monkeys", 1995)
        movie("10.Cloverfield.Lane.2016.mkv", "10 Cloverfield Lane", 2016)
        movie("1408.2007.mkv", "1408", 2007)
        movie("2046.2004.mkv", "2046", 2004)
        movie("2.0.2018.1080p.mkv", "2.0", 2018)
        movie("Movies/1917.mkv", "1917")
        movie("Downloads/300.mkv", "300")
    }

    @Test
    fun seriesWhoseTitlesAreNumbers() {
        episode("24.S01E02.mkv", "24", 1, 2)
        episode("1923.S01E02.mkv", "1923", 1, 2)
        episode("1883.S01E02.mkv", "1883", 1, 2)
        episode("9-1-1.S01E02.mkv", "9-1-1", 1, 2)
        episode("The.100.S01E02.mkv", "The 100", 1, 2)
        episode("86.S01E02.mkv", "86", 1, 2)
    }

    @Test
    fun sequelNumbersStayInMovieTitles() {
        movie("Rocky 4.mkv", "Rocky 4")
        movie("Apollo 13.mkv", "Apollo 13")
        movie("Ocean's 11 (2001).mkv", "Ocean's 11", 2001)
    }

    @Test
    fun paddedTrailingNumberIsAnEpisode() {
        episode("Show 07.mkv", "Show", null, 7)
    }

    @Test
    fun yearBeforeTheEpisode() {
        val p = parse("The.Flash.2014.S01E02.mkv")
        assertEquals("The Flash", p.title)
        assertEquals(2014, p.year)
        assertEquals(1, p.season)
        assertEquals(2014, parse("Show.S01E02.2014.1080p.mkv").year)
        assertEquals(2014, parse("Show (2014) - S01E02 - Name [1080p].mkv").year)
    }

    @Test
    fun releaseTagsAreCutFromTheTitle() {
        movie("Title.BluRay.mkv", "Title")
        movie("Title.WEB-DL.mkv", "Title")
        movie("Title.WEB.DL.1080p.mkv", "Title")
        movie("Title.Blu-ray.mkv", "Title")
        movie("Title.Blu.Ray.x264.mkv", "Title")
        movie("Title.x264-GROUP.mkv", "Title")
        movie("Title.WEB.1080p.mkv", "Title")
        movie("Title.PROPER.1080p.mkv", "Title")
        movie("Title.HD.mkv", "Title")
        movie("Title CD1.avi", "Title")
    }

    @Test
    fun wordsThatLookLikeTagsStayInTitles() {
        movie("Charlotte's.Web.2006.mkv", "Charlotte's Web", 2006)
        movie("Internal.Affairs.2002.mkv", "Internal Affairs", 2002)
        movie("It.2017.mkv", "It", 2017)
        movie("Cam.2020.mkv", "Cam", 2020)
        episode("Extended.Family.S01E02.mkv", "Extended Family", 1, 2)
    }

    @Test
    fun releaseTagList() {
        val p = parse("Show.S01E02.1080p.WEB-DL.DDP5.1.H.264-GROUP.mkv")
        assertEquals("Show", p.title)
        for (tag in listOf("1080p", "WEB-DL", "DDP5.1", "H264", "GROUP")) {
            assertTrue(tag in p.releaseTags, "$tag not in ${p.releaseTags}")
        }
        assertTrue("HEVC" in parse("Title.2019.WEB-DL.HEVC-PSA.mkv").releaseTags)
        assertTrue("10bit" in parse("Title.2019.10.bit.x265.mkv").releaseTags)
        assertTrue("REPACK" in parse("Title.2019.REPACK.1080p.mkv").releaseTags)
    }

    @Test
    fun siteTags() {
        episode("www.site.com.Title.S01E02.mkv", "Title", 1, 2)
        episode("[www.site.com] Title S01E02.mkv", "Title", 1, 2)
        episode("(www.site.net) Title S01E02.mkv", "Title", 1, 2)
        episode("www.site.co.uk - Title S01E02.mkv", "Title", 1, 2)
        episode("site.com - Title S01E02.mkv", "Title", 1, 2)
        episode("Site.com.Title.S01E02.mkv", "Title", 1, 2)
        episode("site.tv - Title S01E02.mkv", "Title", 1, 2)
        episode("Title S01E02 - www.site.com.mkv", "Title", 1, 2)
        episode("Title S01E02 (www.site.com).mkv", "Title", 1, 2)
        movie("Title - site.com.mkv", "Title")
        movie("Title www.site.com.mkv", "Title")
    }

    @Test
    fun siteTagIsNotABracketGroup() {
        assertFalse(parse("[www.site.com] Title S01E02.mkv").animeHint)
        assertFalse(parse("[site.com] Title S01E02.mkv").animeHint)
        assertFalse(parse("[YTS.MX] Title (2019).mkv").animeHint)
        assertFalse(parse("[EgyBest] Title (2019).mkv").animeHint)
    }

    @Test
    fun knownSiteNames() {
        movie("EgyBest.Title.2020.mkv", "Title", 2020)
        movie("Title.EgyBest.2020.mkv", "Title", 2020)
        movie("MyCima Title 2020.mkv", "Title", 2020)
        movie("@channel Title 2020.mkv", "Title", 2020)
        movie("Title 2020 @channel.mkv", "Title", 2020)
    }

    @Test
    fun domainsThatAreTitlesSurvive() {
        movie("The.Net.1995.mkv", "The Net", 1995)
        movie("Dot.Com.2007.mkv", "Dot Com", 2007)
        episode("Mad.TV.S01E02.mkv", "Mad TV", 1, 2)
        episode("The.Net.S01E02.mkv", "The Net", 1, 2)
    }

    @Test
    fun folderFallback() {
        episode("Show/S01E02.mkv", "Show", 1, 2)
        episode("Show/Season 1/S01E02 - Pilot.mkv", "Show", 1, 2)
        episode("Show/S02/07.mkv", "Show", 2, 7)
        episode("Show/Season.02/07.mkv", "Show", 2, 7)
        episode("Show/Saison 2/07.mkv", "Show", 2, 7)
        episode("Show/2nd Season/07.mkv", "Show", 2, 7)
        episode("Show/2/07.mkv", "Show", 2, 7)
        episode("Show/Season 1/Show 12.mkv", "Show", 1, 12)
        episode("Show/Season 1/07 - Name.mkv", "Show", 1, 7)
        episode("Show/Season 1/07. Name.mkv", "Show", 1, 7)
        episode("Show\\Season 02\\07.mkv", "Show", 2, 7)
        episode("Show/12.mkv", "Show", null, 12)
        episode("Show/Show 12.mkv", "Show", null, 12)
        episode("Show/Episode 3.mkv", "Show", null, 3)
    }

    @Test
    fun folderTitleIsCleaned() {
        val p = parse("Show (2019)/Season 01/S01E02.mkv")
        assertEquals("Show", p.title)
        assertEquals(2019, p.year)
        episode("Show.S01.1080p.WEB-DL/07.mkv", "Show", 1, 7)
        episode("Show - Season 2/07.mkv", "Show", 2, 7)
        episode("Show S02 1080p/07.mkv", "Show", 2, 7)
        episode("Show.Season.2/E07.mkv", "Show", 2, 7)
        val anime = parse("[Group] Show [1080p]/07.mkv")
        assertEquals("Show", anime.title)
        assertEquals(7, anime.episode)
        assertTrue(anime.animeHint)
        assertTrue("1080p" in anime.releaseTags, anime.releaseTags.toString())
    }

    @Test
    fun outerFoldersAreSkipped() {
        episode("TV Shows/Show/Season 1/S01E02.mkv", "Show", 1, 2)
        episode("Download/Telegram/Show/E05.mkv", "Show", null, 5)
    }

    @Test
    fun fileTitleWinsOverItsFolders() {
        episode("Some Folder/Show.S01E02.mkv", "Show", 1, 2)
        movie("Movies/Quiet Coast/Quiet.Coast.2026.mkv", "Quiet Coast", 2026)
    }

    @Test
    fun folderYearNeedsTheSameTitle() {
        assertNull(parse("Stuff 2019/Quiet.Coast.mkv").year)
        assertEquals(2026, parse("Quiet Coast (2026)/movie.mkv").year)
    }

    @Test
    fun genericFileNameTakesTheFolderTitle() {
        movie("Quiet Coast (2026)/movie.mkv", "Quiet Coast", 2026)
    }

    @Test
    fun aSeasonFolderMakesAnEpisodeOfAnyFile() {
        val p = parse("Show/Season 1/Pilot.mkv")
        assertEquals("Show", p.title)
        assertEquals(1, p.season)
        assertEquals(NameKind.EPISODE, p.kind)
    }

    @Test
    fun windowsCopySuffixIsNotPartOfTheTitle() {
        movie("Title (1).mkv", "Title")
        movie("Movie (1).mkv", "Movie")
        episode("Show S01E02 (1).mkv", "Show", 1, 2)
    }

    @Test
    fun discFoldersGiveNoTitle() {
        episode("Show/Disc 1/07.mkv", "Show", null, 7)
        episode("Show/CD2/07.mkv", "Show", null, 7)
    }

    @Test
    fun specialsAndExtrasFolders() {
        val special = parse("Show/Specials/Episode 3.mkv")
        assertEquals("Show", special.title)
        assertEquals(0, special.season)
        assertEquals(3, special.episode)
        assertEquals(NameKind.SPECIAL, special.kind)
        assertEquals(NameKind.SPECIAL, parse("Show/Season 0/E03.mkv").kind)
        val extra = parse("Show/Extras/Making Of.mkv")
        assertEquals("Show", extra.title)
        assertEquals(NameKind.EXTRA, extra.kind)
        assertNull(extra.episode)
        assertEquals(NameKind.EXTRA, parse("Show/NCOP/NCOP1.mkv").kind)
    }

    @Test
    fun tailExtras() {
        assertEquals(NameKind.EXTRA, parse("Title.2019.1080p-sample.mkv").kind)
        assertEquals(NameKind.EXTRA, parse("Title.2019.sample.mkv").kind)
        assertEquals(NameKind.EXTRA, parse("Title.2019.Trailer.mkv").kind)
        assertEquals(NameKind.EXTRA, parse("Frieren Trailer.mp4").kind)
        assertEquals("Frieren", parse("Frieren Trailer.mp4").title)
        assertEquals(NameKind.EPISODE, parse("Show.S01E02.The.Trailer.Goes.Wrong.mkv").kind)
    }

    @Test
    fun invisibleCharactersDoNotBreakMarkers() {
        episode("Sh​ow.S‏01E02.mkv", "Show", 1, 2)
        episode("﻿Show.S01E02.mkv", "Show", 1, 2)
    }

    @Test
    fun initialsFromDottedNames() {
        episode("Agents.of.S.H.I.E.L.D.S01E02.mkv", "Agents of S.H.I.E.L.D.", 1, 2)
        movie("L.A.Confidential.1997.mkv", "L.A. Confidential", 1997)
    }

    @Test
    fun oddInputsNeverThrowAndNeverGiveABlankTitle() {
        val odd = listOf(
            "S01E02.mkv", "07.mkv", "E05.mkv", "-.mkv", "...mkv", "[].mkv", "()", "[Group].mkv",
            "(((.mkv", "]]].mkv", " - .mkv", "الحلقة ٧.mkv", ".mkv", "---", "a", "1",
        )
        for (name in odd) assertTrue(parse(name).title.isNotBlank(), "blank title for '$name'")
        assertEquals("", parse("").title)
        assertEquals("", parse("///").title)
    }

    @Test
    fun noExtensionStillParses() {
        movie("Quiet.Coast.2026", "Quiet Coast", 2026)
    }

    @Test
    fun otherExtensionsAreKnown() {
        for (extension in listOf("mp4", "MKV", "avi", "webm", "ts", "m2ts", "mov", "wmv")) {
            movie("Quiet.Coast.2026.$extension", "Quiet Coast", 2026)
        }
    }

    @Test
    fun noDependenceOnDefaultLocale() {
        val saved = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
        try {
            assertEquals("iii", ArabicNormalizer.key("III"))
            assertEquals(2, parse("TITLE SEASON 2 EPISODE 7.mkv").season)
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}
