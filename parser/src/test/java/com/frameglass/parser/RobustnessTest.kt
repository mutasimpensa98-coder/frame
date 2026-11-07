package com.frameglass.parser

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The parser runs over every file on the phone, so no name may break it. */
class RobustnessTest {
    private val fragments = listOf(
        "[", "]", "(", ")", "{", "}", "【", "】", " ", "  ", ".", "..", "_", "-", " - ", "|", "~",
        "/", "\\", "S", "E", "s01", "e02", "S01E02", "E", "Ep", "x", "2x07", "v2", "07", "1", "13.5",
        "2019", "2049", "1080p", "x265-GROUP", "WEB-DL", "OVA", "SP1", "NCOP1", "ABCD1234", "Title",
        "www.site.com", "site.com", "@chan", "الحلقة", "الموسم", "الثاني", "الجزء", "م", "ح", "ج",
        "رقم", "عشر", "والعشرون", "مسلسل", "فيلم", "انمي", "موقع", "مترجم", "مدبلج", "٧", "۱۲",
        "‏", "‫", "‬", "ـ", "َ", "﻿", "\u0001", "\t", "\n",
        "😀", "\uD83D", ".mkv", ".mp4",
    )

    private fun randomName(random: Random): String {
        val out = StringBuilder()
        repeat(random.nextInt(1, 14)) { out.append(fragments[random.nextInt(fragments.size)]) }
        return out.toString()
    }

    private fun hasMark(text: String) = text.any { isInvisibleMark(it) || it == '\u0001' }

    @Test
    fun randomNamesNeverThrowAndStayClean() {
        val random = Random(20261107)
        repeat(50_000) {
            val name = randomName(random)
            val p = NameParser.parse(name)
            val visible = name.any { !it.isWhitespace() && it != '/' && it != '\\' && !hasMark(it.toString()) }
            if (visible) assertTrue(p.title.isNotEmpty(), "empty title for <$name>")
            assertFalse(hasMark(p.title), "mark in title of <$name>: <${p.title}>")
            p.releaseTags.forEach { assertFalse(hasMark(it), "mark in tag of <$name>: <$it>") }
            p.episodeEnd?.let { end -> assertTrue(end > (p.episode ?: -1), "end before start for <$name>") }
            p.year?.let { year -> assertTrue(isPlausibleYear(year), "year $year for <$name>") }
            if (p.kind == NameKind.EXTRA) assertEquals(null, p.episode, "episode on extra <$name>")
        }
    }

    @Test
    fun keyNeverThrowsAndIsIdempotent() {
        val random = Random(7)
        repeat(20_000) {
            val key = ArabicNormalizer.key(randomName(random))
            assertEquals(key, ArabicNormalizer.key(key))
        }
    }

    @Test
    fun veryLongNamesStayFast() {
        val name = "Title.".repeat(1_000) + "-".repeat(2_000) + " ".repeat(2_000) + "S".repeat(2_000) + ".mkv"
        val started = System.nanoTime()
        NameParser.parse(name)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMs < 2_000, "took $elapsedMs ms")
    }

    @Test
    fun tenThousandNamesTakeUnderASecondEach() {
        val names = listOf(
            "Sample.Journey.S01E02.1080p.WEB-DL.mkv",
            "[SubsPlease] Frieren - 05 (1080p) [ABCD1234].mkv",
            "مسلسل العنوان الموسم الثاني الحلقة 12 مترجم.mkv",
        )
        val started = System.nanoTime()
        repeat(10_000) { NameParser.parse(names[it % names.size]) }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMs < 10_000, "took $elapsedMs ms")
    }
}
