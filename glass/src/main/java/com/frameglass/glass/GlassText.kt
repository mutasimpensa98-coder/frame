package com.frameglass.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

private val FirstStrongIsolate = Char(0x2068)
private val PopDirectionalIsolate = Char(0x2069)

/** Wraps [text] in Unicode isolates, so its direction and punctuation stay its own (F-A5). */
fun bidiIsolate(text: String): String = "$FirstStrongIsolate$text$PopDirectionalIsolate"

/** Time left as readers say it, such as "15 min left", rounded up to the minute. */
@Composable
fun timeLeftLabel(timeLeftMs: Long): String {
    val minutes = ((timeLeftMs.coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        minutes < 1 -> stringResource(R.string.glass_left_under_minute)
        hours == 0 -> pluralStringResource(R.plurals.glass_minutes_left, minutes, minutes)
        rest == 0 -> pluralStringResource(R.plurals.glass_hours_left, hours, hours)
        else -> stringResource(
            R.string.glass_hours_minutes_left,
            pluralStringResource(R.plurals.glass_hours, hours, hours),
            pluralStringResource(R.plurals.glass_minutes, rest, rest),
        )
    }
}
