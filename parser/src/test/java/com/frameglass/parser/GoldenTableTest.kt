package com.frameglass.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One test per row of the table in brief section 6.2. */
class GoldenTableTest {
    private fun parse(path: String) = NameParser.parse(path)

    @Test
    fun seriesWithReleaseTags() {
        val p = parse("Sample.Journey.S01E02.1080p.WEB-DL.mkv")
        assertEquals("Sample Journey", p.title)
        assertEquals(NameKind.EPISODE, p.kind)
        assertEquals(1, p.season)
        assertEquals(2, p.episode)
        assertTrue("1080p" in p.releaseTags, p.releaseTags.toString())
        assertTrue("WEB-DL" in p.releaseTags, p.releaseTags.toString())
    }

    @Test
    fun movieWithYear() {
        val p = parse("Quiet.Coast.2026.BluRay.x265.mkv")
        assertEquals("Quiet Coast", p.title)
        assertEquals(2026, p.year)
        assertEquals(NameKind.MOVIE, p.kind)
        assertNull(p.episode)
        assertTrue("BluRay" in p.releaseTags, p.releaseTags.toString())
        assertTrue("x265" in p.releaseTags, p.releaseTags.toString())
    }

    @Test
    fun lastPlausibleYearWins() {
        val p = parse("Blade.Runner.2049.2017.1080p.mkv")
        assertEquals("Blade Runner 2049", p.title)
        assertEquals(2017, p.year)
        assertEquals(NameKind.MOVIE, p.kind)
    }

    @Test
    fun titleThatLooksLikeAYear() {
        val p = parse("1917.2019.1080p.mkv")
        assertEquals("1917", p.title)
        assertEquals(2019, p.year)
        assertEquals(NameKind.MOVIE, p.kind)
    }

    @Test
    fun fansubEpisode() {
        val p = parse("[SubsPlease] Frieren - 05 (1080p) [ABCD1234].mkv")
        assertEquals("Frieren", p.title)
        assertEquals(5, p.episode)
        assertTrue(p.animeHint)
        assertEquals(NameKind.EPISODE, p.kind)
        assertNull(p.season)
        assertTrue("SubsPlease" in p.releaseTags, p.releaseTags.toString())
    }

    @Test
    fun fansubRevision() {
        val p = parse("[SubsPlease] Frieren - 07v2 (1080p) [ABCD1234].mkv")
        assertEquals("Frieren", p.title)
        assertEquals(7, p.episode)
        assertEquals(2, p.revision)
        assertTrue(p.animeHint)
    }

    @Test
    fun absoluteNumber() {
        val p = parse("One Piece - 1071 [1080p].mkv")
        assertEquals("One Piece", p.title)
        assertEquals(1071, p.absolute)
        assertNull(p.season)
        assertTrue(p.animeHint)
    }

    @Test
    fun ordinalSeason() {
        val p = parse("Title 2nd Season - 07 [1080p].mkv")
        assertEquals("Title", p.title)
        assertEquals(2, p.season)
        assertEquals(7, p.episode)
        assertNull(p.absolute)
        assertTrue(p.animeHint)
    }

    @Test
    fun numberTitleWithEpisode() {
        val p = parse("86 - 07 [1080p].mkv")
        assertEquals("86", p.title)
        assertEquals(7, p.episode)
        assertTrue(p.animeHint)
    }

    @Test
    fun multiEpisode() {
        val p = parse("Show.S02E07E08.mkv")
        assertEquals("Show", p.title)
        assertEquals(2, p.season)
        assertEquals(7, p.episode)
        assertEquals(8, p.episodeEnd)
    }

    @Test
    fun crossForm() {
        val p = parse("Show 2x07.mkv")
        assertEquals("Show", p.title)
        assertEquals(2, p.season)
        assertEquals(7, p.episode)
    }

    @Test
    fun episodeOnly() {
        val first = parse("Show.Ep05.mkv")
        assertEquals("Show", first.title)
        assertEquals(5, first.episode)
        assertNull(first.season)
        val second = parse("Show E1100.mkv")
        assertEquals("Show", second.title)
        assertEquals(1100, second.episode)
    }

    @Test
    fun seasonAndEpisodeFromFolders() {
        val p = parse("Show/Season 02/07.mkv")
        assertEquals("Show", p.title)
        assertEquals(2, p.season)
        assertEquals(7, p.episode)
        assertEquals(NameKind.EPISODE, p.kind)
    }

    @Test
    fun seasonZeroInSpecialsFolder() {
        val p = parse("Show/Specials/S00E03.mkv")
        assertEquals("Show", p.title)
        assertEquals(0, p.season)
        assertEquals(3, p.episode)
        assertEquals(NameKind.SPECIAL, p.kind)
    }

    @Test
    fun ovaIsSpecial() {
        val p = parse("[Group] Title OVA - 02.mkv")
        assertEquals("Title", p.title)
        assertEquals(NameKind.SPECIAL, p.kind)
        assertEquals(2, p.episode)
        assertNull(p.absolute)
        assertTrue(p.animeHint)
    }

    @Test
    fun cleanOpeningIsExtra() {
        val p = parse("[Group] Title - NCOP1.mkv")
        assertEquals("Title", p.title)
        assertEquals(NameKind.EXTRA, p.kind)
        assertNull(p.episode)
    }

    @Test
    fun arabicSeries() {
        val p = parse("مسلسل الاختيار الحلقة 07.mp4")
        assertEquals("الاختيار", p.title)
        assertEquals(7, p.episode)
        assertEquals(NameKind.EPISODE, p.kind)
    }

    @Test
    fun arabicIndicDigitWithFolderTitle() {
        val p = parse("العنوان/الحلقة ٧.mkv")
        assertEquals("العنوان", p.title)
        assertEquals(7, p.episode)
    }

    @Test
    fun arabicSeasonWordAndSubtitledFlag() {
        val p = parse("العنوان الموسم الثاني الحلقة 12 مترجم.mkv")
        assertEquals("العنوان", p.title)
        assertEquals(2, p.season)
        assertEquals(12, p.episode)
        assertTrue(p.subtitled)
        assertFalse(p.dubbed)
    }

    @Test
    fun arabicDubbedFlag() {
        val p = parse("العنوان مدبلج الحلقة 3.mp4")
        assertEquals("العنوان", p.title)
        assertEquals(3, p.episode)
        assertTrue(p.dubbed)
        assertFalse(p.subtitled)
    }

    @Test
    fun arabicMovie() {
        val p = parse("فيلم العنوان 2023 مترجم.mp4")
        assertEquals("العنوان", p.title)
        assertEquals(2023, p.year)
        assertEquals(NameKind.MOVIE, p.kind)
        assertTrue(p.subtitled)
    }

    @Test
    fun siteTagRemoved() {
        val p = parse("www.site.com - Title S01E02.mkv")
        assertEquals("Title", p.title)
        assertEquals(1, p.season)
        assertEquals(2, p.episode)
    }

    @Test
    fun bidiMarksRemoved() {
        val rtlMark = parse("‏Title‏.S01E02.mkv")
        assertEquals("Title", rtlMark.title)
        assertEquals(2, rtlMark.episode)
        val embedding = parse("‫مسلسل العنوان‬ الحلقة 3.mp4")
        assertEquals("العنوان", embedding.title)
        assertEquals(3, embedding.episode)
    }

    @Test
    fun diacriticsShareASearchKey() {
        val p = parse("الْعُنْوَان.S01E01.mkv")
        assertEquals(ArabicNormalizer.key("العنوان"), ArabicNormalizer.key(p.title))
        assertEquals(1, p.episode)
    }
}
