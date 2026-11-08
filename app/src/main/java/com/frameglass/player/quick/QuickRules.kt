package com.frameglass.player.quick

import com.frameglass.data.api.PickOption
import com.frameglass.data.api.ResumeEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.random.Random

/** One launcher shortcut, before Android turns it into a ShortcutInfo. */
internal data class ShortcutSpec(val id: String, val entry: ResumeEntry, val rank: Int)

/** Long-press shortcuts on the launcher icon: the resume item first, then up to two more in progress. */
internal object ShortcutPlan {
    const val MAX = 3
    private const val ID_PREFIX = "resume-"

    fun of(entries: List<ResumeEntry>, limit: Int = MAX): List<ShortcutSpec> =
        entries.filter { it.play.playableKey != null }
            .distinctBy { it.titleId }
            .take(limit.coerceIn(0, MAX))
            .mapIndexed { rank, entry -> ShortcutSpec(ID_PREFIX + entry.titleId, entry, rank) }
}

/** The three rows of the Continue watching widget. A row with no entry is hidden, and none at all shows the empty text. */
internal object WidgetSlots {
    const val ROWS = 3

    fun of(entries: List<ResumeEntry>): List<ResumeEntry?> {
        val shown = entries.distinctBy { it.titleId }.take(ROWS)
        return List(ROWS) { shown.getOrNull(it) }
    }

    /** ProgressBar steps, so a thin line still shows a start. */
    const val PROGRESS_MAX = 1000

    fun progressOf(entry: ResumeEntry): Int = (entry.progress.coerceIn(0f, 1f) * PROGRESS_MAX).toInt()
}

/** The trailer opens on YouTube outside the app. A key that is not a plain YouTube id opens nothing. */
internal object Trailer {
    private val KEY = Regex("[A-Za-z0-9_-]{6,20}")

    fun url(key: String?): String? = key?.takeIf(KEY::matches)?.let { "https://www.youtube.com/watch?v=$it" }
}

/** When an announced episode comes out, as the screens word it. */
internal sealed interface AirDay {
    data object Today : AirDay
    data object Tomorrow : AirDay

    /** Within the coming week, so the weekday alone says it. */
    data class ThisWeek(val date: LocalDate) : AirDay
    data class Later(val date: LocalDate) : AirDay
}

internal object AirDates {
    private const val WEEK_DAYS = 6L

    /** Null when [airDate] is not a date or has passed. */
    fun dayOf(airDate: String, today: LocalDate): AirDay? {
        val date = runCatching { LocalDate.parse(airDate) }.getOrNull() ?: return null
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days < 0 -> null
            days == 0L -> AirDay.Today
            days == 1L -> AirDay.Tomorrow
            days <= WEEK_DAYS -> AirDay.ThisWeek(date)
            else -> AirDay.Later(date)
        }
    }

    /** "Thursday 12 November", with the year only when it is not this year. Digits follow [locale]. */
    fun fullDate(date: LocalDate, today: LocalDate, locale: Locale): String =
        format(if (date.year == today.year) "EEEE d MMMM" else "EEEE d MMMM y", date, locale)

    /** The short form for a Home card: the weekday within a week, else the day and month. */
    fun shortDate(day: AirDay, today: LocalDate, locale: Locale): String? = when (day) {
        AirDay.Today, AirDay.Tomorrow -> null
        is AirDay.ThisWeek -> format("EEEE", day.date, locale)
        is AirDay.Later -> format(if (day.date.year == today.year) "d MMMM" else "d MMMM y", day.date, locale)
    }

    private fun format(pattern: String, date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofPattern(pattern, locale).withDecimalStyle(DecimalStyle.of(locale)).format(date)
}

internal enum class PickKind { ANY, FILM, SERIES }

/** "What should I watch tonight?": a random pick from the pool, never the same title twice in a row. */
internal object TonightPick {
    /** Titles shown lately stay out while others are left, so "Another" walks through the choices. */
    const val MEMORY = 5

    fun matching(pool: List<PickOption>, kind: PickKind, genre: String?): List<PickOption> = pool.filter { option ->
        val kindFits = when (kind) {
            PickKind.ANY -> true
            PickKind.FILM -> option.isMovie
            PickKind.SERIES -> !option.isMovie
        }
        kindFits && (genre == null || genre in option.genres)
    }

    /** [recent] is newest last. Null when nothing fits the filters. */
    fun next(pool: List<PickOption>, kind: PickKind, genre: String?, recent: List<Long>, random: Random = Random): Long? {
        val options = matching(pool, kind, genre)
        if (options.isEmpty()) return null
        val fresh = options.filter { it.titleId !in recent }
        val from = fresh.ifEmpty { options.filter { it.titleId != recent.lastOrNull() } }.ifEmpty { options }
        return from[random.nextInt(from.size)].titleId
    }

    fun remember(recent: List<Long>, shown: Long): List<Long> = (recent - shown + shown).takeLast(MEMORY)

    /** The genre chips: the pool's genres, the most common first. */
    fun genres(pool: List<PickOption>, max: Int = 12): List<String> =
        pool.flatMap { it.genres }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(max)
            .map { it.key }
}
