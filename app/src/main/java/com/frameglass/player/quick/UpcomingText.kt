package com.frameglass.player.quick

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.frameglass.data.api.UpcomingEpisode
import com.frameglass.player.R
import java.time.LocalDate

/** Details: «الحلقة 6 من الموسم 2 تنزل الخميس 12 نوفمبر», or null once the date has passed. */
@Composable
internal fun upcomingLine(upcoming: UpcomingEpisode): String? {
    val locale = LocalLocale.current.platformLocale
    val today = LocalDate.now()
    val day = AirDates.dayOf(upcoming.airDate, today) ?: return null
    val number = String.format(locale, "%d", upcoming.number)
    val season = String.format(locale, "%d", upcoming.season)
    return when (day) {
        AirDay.Today -> stringResource(R.string.app_upcoming_today, number, season)
        AirDay.Tomorrow -> stringResource(R.string.app_upcoming_tomorrow, number, season)
        is AirDay.ThisWeek -> stringResource(R.string.app_upcoming_on, number, season, AirDates.fullDate(day.date, today, locale))
        is AirDay.Later -> stringResource(R.string.app_upcoming_on, number, season, AirDates.fullDate(day.date, today, locale))
    }
}

/** A Home card: «حلقة جديدة يوم الخميس». */
@Composable
internal fun upcomingHint(upcoming: UpcomingEpisode): String? {
    val locale = LocalLocale.current.platformLocale
    val today = LocalDate.now()
    val day = AirDates.dayOf(upcoming.airDate, today) ?: return null
    return when (day) {
        AirDay.Today -> stringResource(R.string.app_upcoming_short_today)
        AirDay.Tomorrow -> stringResource(R.string.app_upcoming_short_tomorrow)
        else -> AirDates.shortDate(day, today, locale)?.let { stringResource(R.string.app_upcoming_short_on, it) }
    }
}

/** Opens the trailer in the YouTube app or the browser. The app itself sends nothing to YouTube. */
internal fun Context.openTrailer(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.app_trailer_none, Toast.LENGTH_SHORT).show()
    }
}
