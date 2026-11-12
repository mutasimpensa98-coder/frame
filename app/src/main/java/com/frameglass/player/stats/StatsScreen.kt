package com.frameglass.player.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.DayTotal
import com.frameglass.data.api.WatchStats
import com.frameglass.data.api.frameServices
import com.frameglass.data.api.watchStats
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.Skeleton
import com.frameglass.glass.bidiIsolate
import com.frameglass.player.R
import com.frameglass.player.settings.SettingRow
import com.frameglass.player.settings.SettingsDivider
import com.frameglass.player.settings.SettingsPage
import com.frameglass.player.settings.SettingsSection
import com.frameglass.player.ui.durationLabel
import com.frameglass.player.ui.localNumber
import java.time.LocalDate
import java.time.format.TextStyle

private const val MillisPerMinute = 60_000L
private val ChartHeight = 140.dp
private val BarGap = FrameSpacing.S8
private val BarTop = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
private val SkeletonHeight = 120.dp

/** Some of the bar shows even on an empty day, so the week reads as seven days and not as gaps. */
private const val MinBarShare = 0.02f
private const val PastDayAlpha = 0.55f

/** "إحصائياتي": time watched, from the local log. Nothing on this page is ever sent anywhere (R2). */
@Composable
internal fun StatsScreen() {
    val library = LocalContext.current.frameServices.library
    val stats by remember(library) { library.watchStats }.collectAsStateWithLifecycle(null as WatchStats?)

    SettingsPage(stringResource(R.string.app_stats)) {
        val current = stats
        if (current == null) {
            item(key = "skeleton") { Skeleton(Modifier.fillMaxWidth().height(SkeletonHeight), FrameShapes.Card) }
            return@SettingsPage
        }
        item(key = "totals") {
            SettingsSection(stringResource(R.string.app_stats)) {
                SettingRow(stringResource(R.string.app_stats_week), value = watchedLabel(current.weekMs))
                SettingsDivider()
                SettingRow(stringResource(R.string.app_stats_month), value = watchedLabel(current.monthMs))
                SettingsDivider()
                SettingRow(stringResource(R.string.app_stats_all), value = watchedLabel(current.allTimeMs))
                SettingsDivider()
                SettingRow(stringResource(R.string.app_stats_films_finished), value = localNumber(current.filmsFinished))
            }
        }
        item(key = "chart") {
            SettingsSection(stringResource(R.string.app_stats_last_days)) {
                WeekChart(current.lastSevenDays)
            }
        }
        if (current.topSeries.isNotEmpty()) {
            item(key = "series") {
                SettingsSection(stringResource(R.string.app_stats_top_series)) {
                    current.topSeries.forEachIndexed { index, series ->
                        if (index > 0) SettingsDivider()
                        SettingRow(series.name, value = watchedLabel(series.watchedMs))
                    }
                }
            }
        }
        item(key = "note") {
            Text(
                text = stringResource(if (current.empty) R.string.app_stats_empty else R.string.app_stats_note),
                style = FrameType.Label,
                color = FrameColors.SecondaryText,
                modifier = Modifier.padding(horizontal = FrameSpacing.S8, vertical = FrameSpacing.S16),
            )
        }
    }
}

@Composable
private fun watchedLabel(ms: Long): String = durationLabel((ms / MillisPerMinute).toInt())

/**
 * Seven bars, oldest at the start edge, so the week reads in the language's own direction. Today is the full accent.
 * Each bar names its day and its time for TalkBack.
 */
@Composable
private fun WeekChart(days: List<DayTotal>) {
    val locale = LocalLocale.current.platformLocale
    val most = days.maxOfOrNull { it.watchedMs }?.takeIf { it > 0 } ?: 1L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(FrameSpacing.S16),
        horizontalArrangement = Arrangement.spacedBy(BarGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            val name = remember(day.date, locale) {
                LocalDate.parse(day.date).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            }
            val time = watchedLabel(day.watchedMs)
            val today = index == days.lastIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = "$name, $time" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
            ) {
                Box(Modifier.fillMaxWidth().height(ChartHeight), contentAlignment = Alignment.BottomCenter) {
                    val share = (day.watchedMs.toFloat() / most).coerceIn(MinBarShare, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(share)
                            .clip(BarTop)
                            .background(if (today) FrameColors.Accent else FrameColors.Accent.copy(alpha = PastDayAlpha)),
                    )
                }
                Text(
                    text = bidiIsolate(name),
                    style = FrameType.Caption,
                    color = if (today) FrameColors.White else FrameColors.SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
