package com.frameglass.player.quick

import com.frameglass.data.api.PickOption
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.ResumeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random

private fun entry(id: Long, label: String? = null, progress: Float = 0.5f, key: String? = "p$id") = ResumeEntry(
    titleId = id, play = PlayRef(playableKey = key), name = "Harbour $id", episodeLabel = label,
    coverPath = "/covers/c$id.jpg", imagePath = null, progress = progress,
)

class ShortcutPlanTest {
    @Test
    fun theResumeItemComesFirstAndAtMostThreeWithStableIds() {
        val specs = ShortcutPlan.of((1L..5L).map { entry(it) })
        assertEquals(listOf("resume-1", "resume-2", "resume-3"), specs.map { it.id })
        assertEquals(listOf(0, 1, 2), specs.map { it.rank })
    }

    @Test
    fun theLauncherLimitAndDuplicatesAreRespected() {
        assertEquals(listOf(1L), ShortcutPlan.of(listOf(entry(1), entry(1), entry(2)), limit = 1).map { it.entry.titleId })
        assertEquals(listOf(1L, 2L), ShortcutPlan.of(listOf(entry(1), entry(1), entry(2))).map { it.entry.titleId })
        assertTrue(ShortcutPlan.of(listOf(entry(1)), limit = 0).isEmpty())
    }

    @Test
    fun anEntryWithNothingToOpenIsLeftOut() {
        assertEquals(listOf(2L), ShortcutPlan.of(listOf(entry(1, key = null), entry(2))).map { it.entry.titleId })
    }
}

class WidgetSlotsTest {
    @Test
    fun threeRowsWithEmptyOnesHidden() {
        assertEquals(listOf(1L, 2L, null), WidgetSlots.of(listOf(entry(1), entry(2))).map { it?.titleId })
        assertEquals(listOf(null, null, null), WidgetSlots.of(emptyList()))
        assertEquals(listOf(1L, 2L, 3L), WidgetSlots.of((1L..4L).map { entry(it) }).map { it?.titleId })
    }

    @Test
    fun progressIsClampedToTheBar() {
        assertEquals(500, WidgetSlots.progressOf(entry(1, progress = 0.5f)))
        assertEquals(0, WidgetSlots.progressOf(entry(1, progress = -1f)))
        assertEquals(WidgetSlots.PROGRESS_MAX, WidgetSlots.progressOf(entry(1, progress = 3f)))
    }
}

class TrailerTest {
    @Test
    fun onlyAPlainYoutubeKeyMakesALink() {
        assertEquals("https://www.youtube.com/watch?v=aB3_-x9Kq0Z", Trailer.url("aB3_-x9Kq0Z"))
        assertNull(Trailer.url(null))
        assertNull(Trailer.url(""))
        assertNull(Trailer.url("abc&list=x"))
        assertNull(Trailer.url("../../evil"))
    }
}

class AirDatesTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun todayTomorrowThisWeekLaterAndPassed() {
        assertEquals(AirDay.Today, AirDates.dayOf("2026-11-08", today))
        assertEquals(AirDay.Tomorrow, AirDates.dayOf("2026-11-09", today))
        assertEquals(AirDay.ThisWeek(LocalDate.of(2026, 11, 12)), AirDates.dayOf("2026-11-12", today))
        assertEquals(AirDay.Later(LocalDate.of(2026, 11, 20)), AirDates.dayOf("2026-11-20", today))
        // TMDB's date is the network's: the day after it still reads as tonight.
        assertEquals(AirDay.Today, AirDates.dayOf("2026-11-07", today))
        assertNull(AirDates.dayOf("2026-11-06", today))
        assertNull(AirDates.dayOf("2026-11", today))
    }

    @Test
    fun theFullDateNamesTheWeekdayAndAddsTheYearOnlyWhenItDiffers() {
        assertEquals("Thursday 12 November", AirDates.fullDate(LocalDate.of(2026, 11, 12), today, Locale.ENGLISH))
        assertEquals("Friday 1 January 2027", AirDates.fullDate(LocalDate.of(2027, 1, 1), today, Locale.ENGLISH))
        val arabic = AirDates.fullDate(LocalDate.of(2026, 11, 12), today, Locale.forLanguageTag("ar-LY"))
        assertTrue(arabic, arabic.contains("الخميس"))
    }

    @Test
    fun theShortDateIsTheWeekdayWithinAWeek() {
        assertEquals("Thursday", AirDates.shortDate(AirDay.ThisWeek(LocalDate.of(2026, 11, 12)), today, Locale.ENGLISH))
        assertEquals("20 November", AirDates.shortDate(AirDay.Later(LocalDate.of(2026, 11, 20)), today, Locale.ENGLISH))
        assertNull(AirDates.shortDate(AirDay.Today, today, Locale.ENGLISH))
    }
}

class TonightPickTest {
    private val pool = listOf(
        PickOption(1, isMovie = true, genres = listOf("Drama")),
        PickOption(2, isMovie = true, genres = listOf("Comedy")),
        PickOption(3, isMovie = false, genres = listOf("Drama", "Mystery")),
        PickOption(4, isMovie = false, genres = emptyList()),
    )

    @Test
    fun filtersByKindAndGenre() {
        assertEquals(listOf(1L, 2L), TonightPick.matching(pool, PickKind.FILM, null).map { it.titleId })
        assertEquals(listOf(3L, 4L), TonightPick.matching(pool, PickKind.SERIES, null).map { it.titleId })
        assertEquals(listOf(1L, 3L), TonightPick.matching(pool, PickKind.ANY, "Drama").map { it.titleId })
        assertEquals(listOf(3L), TonightPick.matching(pool, PickKind.SERIES, "Drama").map { it.titleId })
        assertNull(TonightPick.next(pool, PickKind.FILM, "Mystery", emptyList()))
    }

    @Test
    fun anotherPickNeverRepeatsTheOneShownAndWalksThroughTheRest() {
        val random = Random(7)
        var recent = emptyList<Long>()
        val seen = ArrayList<Long>()
        repeat(40) {
            val next = TonightPick.next(pool, PickKind.ANY, null, recent, random)!!
            if (seen.isNotEmpty()) assertNotEquals(seen.last(), next)
            seen += next
            recent = TonightPick.remember(recent, next)
        }
        // With four titles and a memory of five, each round of four shows all four.
        assertEquals(setOf(1L, 2L, 3L, 4L), seen.take(4).toSet())
    }

    @Test
    fun aSingleChoiceIsStillOfferedAgain() {
        val one = listOf(PickOption(9, isMovie = true, genres = emptyList()))
        assertEquals(9L, TonightPick.next(one, PickKind.ANY, null, listOf(9L)))
    }

    @Test
    fun theMemoryKeepsTheNewestAndNoDuplicates() {
        assertEquals(listOf(2L, 3L, 4L, 5L, 1L), TonightPick.remember(listOf(1L, 2L, 3L, 4L, 5L), 1L))
        assertEquals(listOf(2L, 3L, 4L, 5L, 6L), TonightPick.remember(listOf(1L, 2L, 3L, 4L, 5L), 6L))
    }

    @Test
    fun genreChipsPutTheCommonestFirst() {
        assertEquals(listOf("Drama", "Comedy", "Mystery"), TonightPick.genres(pool))
    }
}
