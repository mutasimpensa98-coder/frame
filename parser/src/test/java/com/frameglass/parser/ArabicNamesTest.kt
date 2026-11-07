package com.frameglass.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArabicNamesTest {
    private fun parse(path: String) = NameParser.parse(path)

    @Test
    fun seasonOrdinalsOneToTen() {
        val words = listOf(
            "الاول", "الثاني", "الثالث", "الرابع", "الخامس",
            "السادس", "السابع", "الثامن", "التاسع", "العاشر",
        )
        words.forEachIndexed { index, word ->
            val name = "العنوان الموسم $word الحلقة 4.mkv"
            val p = parse(name)
            assertEquals(index + 1, p.season, name)
            assertEquals(4, p.episode, name)
            assertEquals("العنوان", p.title, name)
        }
    }

    @Test
    fun seasonOrdinalSpellings() {
        assertEquals(1, parse("العنوان الموسم الأول الحلقة 1.mkv").season)
        assertEquals(1, parse("العنوان الموسم الاوّل الحلقة 1.mkv").season)
        assertEquals(2, parse("العنوان الموسم التاني الحلقة 1.mkv").season)
        assertEquals(3, parse("العنوان الموسم التالت الحلقة 1.mkv").season)
        assertEquals(8, parse("العنوان الموسم التامن الحلقة 1.mkv").season)
        assertEquals(11, parse("العنوان الموسم الحادي عشر الحلقة 1.mkv").season)
        assertEquals(12, parse("العنوان الموسم الثاني عشر الحلقة 1.mkv").season)
    }

    @Test
    fun seasonNumbers() {
        assertEquals(2, parse("العنوان الموسم 2 الحلقة 1.mkv").season)
        assertEquals(2, parse("العنوان الموسم ٢ الحلقة ١.mkv").season)
        assertEquals(2, parse("العنوان موسم 2 حلقة 1.mkv").season)
        assertEquals(2, parse("العنوان الموسم رقم 2 الحلقة 1.mkv").season)
        assertEquals(2, parse("العنوان الموسم2 الحلقة1.mkv").season)
        assertEquals(3, parse("العنوان م3 ح5.mkv").season)
    }

    @Test
    fun partMeansSeasonForASeries() {
        val p = parse("مسلسل العنوان الجزء الثاني الحلقة 5.mp4")
        assertEquals("العنوان", p.title)
        assertEquals(2, p.season)
        assertEquals(5, p.episode)
        assertEquals(2, parse("العنوان ج2 ح5.mp4").season)
    }

    @Test
    fun partStaysInTheTitleOfAMovie() {
        val p = parse("فيلم العنوان الجزء الثاني 2023.mp4")
        assertEquals("العنوان الجزء الثاني", p.title)
        assertEquals(2023, p.year)
        assertEquals(NameKind.MOVIE, p.kind)
        assertNull(p.season)
    }

    @Test
    fun episodeForms() {
        assertEquals(7, parse("العنوان الحلقة 7.mkv").episode)
        assertEquals(7, parse("العنوان الحلقه 7.mkv").episode)
        assertEquals(7, parse("العنوان حلقة 7.mkv").episode)
        assertEquals(7, parse("العنوان الحلقة رقم 7.mkv").episode)
        assertEquals(7, parse("العنوان الحلقة٧.mkv").episode)
        assertEquals(7, parse("العنوان ح7.mkv").episode)
        assertEquals(7, parse("العنوان ح 7.mkv").episode)
        assertEquals(7, parse("العنوان-الحلقة-7.mkv").episode)
        assertEquals(7, parse("العنوان_الحلقة_07.mkv").episode)
        assertEquals(7, parse("العنوان.الحلقة.7.1080p.mkv").episode)
        assertEquals(12, parse("الحلقة ١٢ العنوان.mkv").episode)
        assertEquals(7, parse("العنوان الحَلْقَة 7.mkv").episode)
    }

    @Test
    fun episodeOrdinalsAsWords() {
        assertEquals(1, parse("العنوان الحلقة الأولى.mkv").episode)
        assertEquals(2, parse("العنوان الحلقة الثانية.mkv").episode)
        assertEquals(10, parse("العنوان الحلقة العاشرة.mkv").episode)
        assertEquals(15, parse("العنوان الحلقة الخامسة عشرة.mkv").episode)
        assertEquals(20, parse("العنوان الحلقة العشرون.mkv").episode)
        assertEquals(25, parse("العنوان الحلقة الخامسة والعشرون.mkv").episode)
        assertEquals(25, parse("العنوان الحلقة الخامسة و العشرون.mkv").episode)
        assertEquals(30, parse("العنوان الحلقة الثلاثون والأخيرة.mkv").episode)
    }

    @Test
    fun lastEpisodeWithNoNumber() {
        val p = parse("العنوان الحلقة الأخيرة.mkv")
        assertEquals("العنوان", p.title)
        assertEquals(NameKind.EPISODE, p.kind)
        assertNull(p.episode)
    }

    @Test
    fun episodeNumberThenLastWord() {
        val p = parse("مسلسل العنوان الحلقة 30 والأخيرة.mp4")
        assertEquals("العنوان", p.title)
        assertEquals(30, p.episode)
    }

    @Test
    fun subtitledFlagForms() {
        for (word in listOf("مترجم", "مترجمة", "مترجمه", "ترجمة", "مُترجم")) {
            val name = "العنوان الحلقة 3 $word.mkv"
            assertTrue(parse(name).subtitled, name)
            assertFalse(parse(name).dubbed, name)
        }
        assertTrue(parse("العنوان مترجم الحلقة 3.mkv").subtitled)
        assertTrue(parse("العنوان (مترجم) الحلقة 3.mkv").subtitled)
        assertTrue(parse("فيلم مترجم العنوان 2020.mkv").subtitled)
    }

    @Test
    fun dubbedFlagForms() {
        for (word in listOf("مدبلج", "مدبلجة", "مدبلجه", "دبلجة")) {
            val name = "العنوان الحلقة 3 $word.mkv"
            assertTrue(parse(name).dubbed, name)
            assertFalse(parse(name).subtitled, name)
        }
        assertTrue(parse("كرتون مدبلج العنوان الحلقة 1.mp4").dubbed)
    }

    @Test
    fun flagInAFolder() {
        assertTrue(parse("العنوان مترجم/الحلقة 3.mkv").subtitled)
        assertTrue(parse("العنوان مدبلج/الحلقة 3.mkv").dubbed)
    }

    @Test
    fun typeWordsLeaveTheTitle() {
        assertEquals("العنوان", parse("مسلسل العنوان الحلقة 1.mkv").title)
        assertEquals("العنوان", parse("فيلم العنوان 2020.mkv").title)
        assertEquals("العنوان", parse("فلم العنوان 2020.mkv").title)
        assertEquals("العنوان", parse("انمي العنوان الحلقة 1.mkv").title)
        assertEquals("العنوان", parse("أنمي العنوان الحلقة 1.mkv").title)
        assertEquals("العنوان", parse("كرتون العنوان الحلقة 1.mkv").title)
        assertEquals("العنوان", parse("برنامج العنوان الحلقة 1.mkv").title)
        assertEquals("العنوان", parse("مشاهدة فيلم العنوان 2020.mkv").title)
        assertEquals("العنوان", parse("تحميل مسلسل العنوان الحلقة 1.mkv").title)
    }

    @Test
    fun loneTypeWordStays() {
        assertEquals("مسلسل", parse("مسلسل.mkv").title)
    }

    @Test
    fun typeWordsSetTheKindAndHints() {
        assertEquals(NameKind.EPISODE, parse("مسلسل العنوان.mp4").kind)
        assertEquals(NameKind.MOVIE, parse("فيلم العنوان.mp4").kind)
        assertTrue(parse("انمي العنوان الحلقة 1.mkv").animeHint)
        assertFalse(parse("مسلسل العنوان الحلقة 1.mkv").animeHint)
    }

    @Test
    fun mixedScriptNames() {
        val p = parse("انمي Frieren الحلقة 5 مترجم.mp4")
        assertEquals("Frieren", p.title)
        assertEquals(5, p.episode)
        assertTrue(p.subtitled)
        assertTrue(p.animeHint)
    }

    @Test
    fun trailingNoiseWordsLeaveTheTitle() {
        assertEquals("العنوان", parse("فيلم العنوان كامل.mp4").title)
        assertEquals("العنوان", parse("فيلم العنوان بجودة عالية.mp4").title)
        assertEquals("العنوان", parse("فيلم العنوان اون لاين.mp4").title)
    }

    @Test
    fun noiseWordsInsideARealTitleStay() {
        assertEquals("كامل العدد", parse("مسلسل كامل العدد الحلقة 5.mp4").title)
        assertEquals("موسم الهجرة إلى الشمال", parse("موسم الهجرة إلى الشمال.mkv").title)
    }

    @Test
    fun ringTheFilmIsNotAnEpisode() {
        val p = parse("فيلم الحلقة 2002.mp4")
        assertEquals("الحلقة", p.title)
        assertEquals(2002, p.year)
        assertEquals(NameKind.MOVIE, p.kind)
        assertNull(p.episode)
    }

    @Test
    fun numberTitles() {
        assertEquals("300", parse("فيلم 300.mp4").title)
        assertNull(parse("فيلم 300.mp4").episode)
        val p = parse("فيلم 2012 2009.mp4")
        assertEquals("2012", p.title)
        assertEquals(2009, p.year)
        assertEquals("24", parse("مسلسل 24.mp4").title)
    }

    @Test
    fun yearInsideAnArabicTitle() {
        val p = parse("فيلم القاهرة 30 1966 مترجم.mp4")
        assertEquals("القاهرة 30", p.title)
        assertEquals(1966, p.year)
    }

    @Test
    fun seriesPrefixMakesTrailingNumberAnEpisode() {
        val p = parse("مسلسل العنوان 12.mp4")
        assertEquals("العنوان", p.title)
        assertEquals(12, p.episode)
    }

    @Test
    fun movieKeepsTrailingNumberInTheTitle() {
        assertEquals("العنوان 2", parse("فيلم العنوان 2.mp4").title)
    }

    @Test
    fun dashNumberInArabicIsNotAnime() {
        val p = parse("العنوان - 03.mp4")
        assertEquals("العنوان", p.title)
        assertEquals(3, p.episode)
        assertFalse(p.animeHint)
        assertNull(p.absolute)
    }

    @Test
    fun titleAfterTheEpisodeWords() {
        val p = parse("الحلقة 7 من مسلسل العنوان.mp4")
        assertEquals("العنوان", p.title)
        assertEquals(7, p.episode)
        assertEquals("العنوان", parse("Show/الحلقة 7 من مسلسل العنوان.mp4").title)
    }

    @Test
    fun arabicSiteWordPrefix() {
        assertEquals("العنوان", parse("موقع ايجي بست - فيلم العنوان 2023.mp4").title)
        assertEquals("العنوان", parse("موقع ماي سيما مسلسل العنوان الحلقة 2.mp4").title)
    }

    @Test
    fun arabicTitlesKeepTheirSpelling() {
        assertEquals("الأمير أحمد", parse("مسلسل الأمير أحمد الحلقة 1.mkv").title)
    }

    @Test
    fun specialsInArabic() {
        val p = parse("العنوان الحلقة الخاصة.mkv")
        assertEquals("العنوان", p.title)
        assertEquals(NameKind.SPECIAL, p.kind)
        assertEquals(NameKind.SPECIAL, parse("انمي العنوان أوفا 2.mkv").kind)
    }

    @Test
    fun arabicSeasonFolders() {
        val second = parse("العنوان/الموسم الثاني/الحلقة 3.mkv")
        assertEquals("العنوان", second.title)
        assertEquals(2, second.season)
        assertEquals(3, second.episode)
        assertEquals(10, parse("العنوان/الموسم العاشر/07.mkv").season)
        assertEquals(7, parse("العنوان/الموسم ٢/07.mkv").episode)
        assertEquals(2, parse("العنوان الموسم الثاني/الحلقة 3.mkv").season)
        assertEquals("العنوان", parse("العنوان الموسم الثاني/الحلقة 3.mkv").title)
    }

    @Test
    fun genericArabicFoldersGiveNoTitle() {
        assertEquals("العنوان", parse("مسلسلات/العنوان/الحلقة 3.mkv").title)
        assertEquals("العنوان", parse("أفلام/فيلم العنوان 2020.mkv").title)
        assertEquals(NameKind.MOVIE, parse("أفلام/300.mkv").kind)
    }

    @Test
    fun bidiMarksAroundArabicMarkers() {
        val p = parse("‫العنوان‬ ‏الحلقة‏ ‎5‎.mkv")
        assertEquals("العنوان", p.title)
        assertEquals(5, p.episode)
    }

    @Test
    fun punctuationBeforeTheMarkerLeavesTheTitle() {
        assertEquals("العنوان", parse("العنوان، الحلقة 5.mp4").title)
        assertEquals("العنوان", parse("العنوان : الحلقة 5.mp4").title)
        assertEquals("العنوان", parse("العنوان | الحلقة 5.mp4").title)
    }

    @Test
    fun persianDigitsAreRead() {
        assertEquals(12, parse("العنوان الحلقة ۱۲.mkv").episode)
    }
}
