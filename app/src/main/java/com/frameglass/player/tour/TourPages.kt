package com.frameglass.player.tour

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.frameglass.glass.CardRating
import com.frameglass.glass.ColorIconTile
import com.frameglass.glass.CoverImage
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameType
import com.frameglass.glass.ProgressLine
import com.frameglass.glass.RatingBadge
import com.frameglass.glass.RatingBrand
import com.frameglass.glass.TileHue
import com.frameglass.glass.bidiIsolate
import com.frameglass.player.R
import com.frameglass.player.ui.toCard
import kotlinx.coroutines.delay
import com.frameglass.playerui.R as PlayerR

private const val CardStepMillis = 70L
private const val RowStepMillis = 80L
private const val PillDelayMillis = 250L
private const val UnblurDelayMillis = 700L
private const val SwapDelayMillis = 1_400L
private const val ThumbBlur = 4f
private const val ThumbZoom = 1.15f
private const val RimAlpha = 0.14f
private const val PanelAlpha = 0.05f
private const val EdgeAlpha = 0.06f
private const val SampleSource = "SubDL"
private val Rise = 16.dp
private val CheckGreen = Color(0xFF5BD08A)
private val BarIdle = Color(0xFF3A3A42)
private val ThinText = Color(0xFFC8C8D0)
private val SubtitleShadow = Shadow(Color.Black, blurRadius = 6f)
private val Strong = FrameType.Label.copy(fontWeight = FontWeight.W600)
private val Heading = FrameType.Body.copy(fontWeight = FontWeight.W600)

// Bar heights of the sample week, Saturday to Friday, and the day that stands out.
private val WeekBars = listOf(0.22f, 0.48f, 0.30f, 0.64f, 0.40f, 1f, 0.56f)
private const val WeekToday = 5

/**
 * The scale of one illustration: [dp] turns a storyboard pixel into this phone's room. [entered] turns true when
 * the page first becomes the current one, which starts its entrance. With [reduced] there is no motion at all.
 */
internal class Stage(private val scale: Float, val entered: Boolean, val reduced: Boolean) {
    fun dp(px: Float): Dp = (px * scale).dp

    fun dp(px: Int): Dp = dp(px.toFloat())
}

/**
 * Runs from 0 to 1 once, [delayMillis] after the page is entered. Pages composed again later start at 1, and with
 * reduced motion it is 1 from the start (brief 7.9).
 */
@Composable
private fun Stage.entrance(delayMillis: Long = 0L): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(if (entered || reduced) 1f else 0f) }
    LaunchedEffect(entered, reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else if (entered && progress.value < 1f) {
            delay(delayMillis)
            progress.animateTo(1f, FrameMotion.slowSpatial())
        }
    }
    return progress
}

/** Fades in and rises into place as [progress] runs, read while drawing. */
private fun Modifier.riseIn(progress: Animatable<Float, AnimationVector1D>): Modifier = graphicsLayer {
    val value = progress.value
    alpha = value.coerceIn(0f, 1f)
    translationY = (1f - value) * Rise.toPx()
}

/** A see-through panel with a 1 dp rim, as the glass in the storyboard. Tint and rim only: no blurred layer. */
@Composable
private fun Modifier.panel(shape: Shape): Modifier {
    val colors = FrameTheme.colors
    return clip(shape)
        .background(colors.content.copy(alpha = PanelAlpha))
        .border(1.dp, colors.content.copy(alpha = RimAlpha), shape)
}

@Composable
private fun Modifier.artEdge(shape: Shape): Modifier =
    clip(shape).border(1.dp, FrameTheme.colors.content.copy(alpha = EdgeAlpha), shape)

/** Page 1: the app's own icon, from its launcher layers. */
@Composable
internal fun WelcomeArt(stage: Stage) {
    val appear = stage.entrance()
    val shape = RoundedCornerShape(stage.dp(32))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(stage.dp(132))
                .riseIn(appear)
                .clip(shape)
                .border(1.dp, FrameTheme.colors.content.copy(alpha = 0.08f), shape),
        ) {
            Image(painterResource(R.drawable.app_icon_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.app_icon_foreground), null, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SampleChip(text: String, selected: Boolean, small: Boolean = false) {
    val colors = FrameTheme.colors
    val shape = FrameShapes.Chip
    val look = if (selected) {
        Modifier.background(colors.accentFill, shape)
    } else {
        Modifier.border(1.dp, colors.content.copy(alpha = RimAlpha), shape)
    }
    Text(
        text = bidiIsolate(text),
        style = if (small) FrameType.Caption else FrameType.Label,
        color = if (selected) colors.onAccent else colors.secondaryContent,
        maxLines = 1,
        modifier = Modifier
            .then(look)
            .padding(horizontal = if (small) 10.dp else 12.dp, vertical = if (small) 3.dp else 6.dp),
    )
}

private fun TourArt.Sample.card(): CardRating? = rating?.let {
    CardRating(it, if (tmdb) "TMDB" else "IMDb", if (tmdb) RatingBrand.Tmdb else RatingBrand.Imdb)
}

private fun TourPoster.rating(): CardRating? = when (this) {
    is TourPoster.Cover -> rating?.toCard()
    is TourPoster.Placeholder -> TourArt.sample(design).card()
}

private fun TourPoster.progress(): Float? = when (this) {
    is TourPoster.Cover -> progress
    is TourPoster.Placeholder -> TourArt.sample(design).progress
}

/** A poster: the owner's own cover, or a drawn one. */
@Composable
private fun PosterArt(poster: TourPoster, modifier: Modifier, marks: Boolean = true) {
    val shape = FrameShapes.Poster
    Box(modifier.artEdge(shape)) {
        when (poster) {
            is TourPoster.Cover -> CoverImage(poster.path, null, Modifier.fillMaxSize(), shape)
            is TourPoster.Placeholder -> PlaceholderPoster(poster.design, Modifier.fillMaxSize())
        }
        if (marks) {
            poster.rating()?.let { RatingBadge(it, Modifier.align(Alignment.TopStart).padding(6.dp)) }
            poster.progress()?.let {
                ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp))
            }
        }
    }
}

/** Page 2: the filter chips over six posters that fade and rise in one after another. */
@Composable
internal fun LibraryArt(stage: Stage, posters: List<TourPoster>) {
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(stage.dp(6))) {
            SampleChip(stringResource(R.string.app_filter_all), selected = true)
            SampleChip(stringResource(R.string.app_filter_movies), selected = false)
            SampleChip(stringResource(R.string.app_filter_series), selected = false)
        }
        Spacer(Modifier.weight(1f))
        repeat(2) { line ->
            if (line > 0) Spacer(Modifier.height(stage.dp(12)))
            Row(horizontalArrangement = Arrangement.spacedBy(stage.dp(10))) {
                repeat(3) { column ->
                    val slot = line * 3 + column
                    val appear = stage.entrance(slot * CardStepMillis)
                    PosterArt(
                        poster = posters.getOrElse(slot) { TourPoster.Placeholder(slot) },
                        modifier = Modifier.size(stage.dp(80), stage.dp(120)).riseIn(appear),
                    )
                }
            }
        }
    }
}

/** Page 3: a frame with two subtitles, the automatic subtitle note and the player's tools. */
@Composable
internal fun PlayerArt(stage: Stage) {
    val colors = FrameTheme.colors
    val appear = stage.entrance(PillDelayMillis)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(stage.dp(22)))
        Box(
            Modifier
                .fillMaxWidth()
                .height(stage.dp(146))
                .artEdge(RoundedCornerShape(stage.dp(12)))
                .drawBehind { with(TourArt) { nightScene() } },
        ) {
            Text(
                text = bidiIsolate(stringResource(R.string.tour_sample_line_second)),
                style = FrameType.Caption.copy(shadow = SubtitleShadow),
                color = ThinText,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = stage.dp(8)),
            )
            Text(
                text = stringResource(R.string.tour_sample_line_main),
                style = Heading.copy(shadow = SubtitleShadow),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = stage.dp(10)),
            )
        }
        Spacer(Modifier.height(stage.dp(16)))
        Row(
            modifier = Modifier
                .riseIn(appear)
                .panel(FrameShapes.Chip)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = CheckGreen)
            Text(
                text = stringResource(PlayerR.string.player_auto_loaded_arabic, bidiIsolate(SampleSource)),
                style = FrameType.Label,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = stringResource(PlayerR.string.player_auto_change),
                style = FrameType.Label,
                color = colors.secondaryContent,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(stage.dp(20)))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = stage.dp(12)),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(
                Icons.Rounded.Subtitles,
                Icons.Rounded.GraphicEq,
                Icons.Rounded.Speed,
                Icons.Rounded.Repeat,
                Icons.Rounded.Bedtime,
                Icons.Rounded.MoreHoriz,
            ).forEach { icon ->
                Icon(icon, null, Modifier.size(stage.dp(22)), tint = colors.content)
            }
        }
    }
}

/**
 * Page 4: a Continue watching card and the next two episodes with their pictures blurred. The next one to watch
 * comes clear once, as an episode does when it is started.
 */
@Composable
internal fun ContinueArt(stage: Stage) {
    val colors = FrameTheme.colors
    val clear = stage.entrance(UnblurDelayMillis)
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.app_continue_watching), style = Heading, color = colors.content, maxLines = 1)
        Spacer(Modifier.height(stage.dp(8)))
        Box(
            Modifier
                .fillMaxWidth()
                .height(stage.dp(128))
                .artEdge(FrameShapes.Poster)
                .drawBehind { with(TourArt) { hillsScene() } },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = stage.dp(12), vertical = stage.dp(10)),
            ) {
                Text(stringResource(R.string.tour_sample_series), style = Strong, color = Color.White, maxLines = 1)
                Text(
                    stringResource(R.string.tour_sample_series_detail),
                    style = FrameType.Caption,
                    color = ThinText,
                    maxLines = 1,
                )
                Spacer(Modifier.height(stage.dp(6)))
                ProgressLine(0.68f, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(stage.dp(14)))
        EpisodeRow(stage, second = false, clear, R.string.tour_sample_episode_one, R.string.tour_sample_episode_one_length)
        Spacer(Modifier.height(stage.dp(10)))
        EpisodeRow(stage, second = true, null, R.string.tour_sample_episode_two, R.string.tour_sample_episode_two_length)
    }
}

@Composable
private fun EpisodeRow(
    stage: Stage,
    second: Boolean,
    clear: Animatable<Float, AnimationVector1D>?,
    title: Int,
    length: Int,
) {
    val colors = FrameTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(stage.dp(10))) {
        Box(
            Modifier
                .size(stage.dp(96), stage.dp(54))
                .clip(RoundedCornerShape(stage.dp(8))),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // RenderEffect needs API 31; the app's floor is 33.
                        val blur = (1f - (clear?.value ?: 0f)).coerceIn(0f, 1f) * ThumbBlur * density
                        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Clamp) else null
                        scaleX = ThumbZoom
                        scaleY = ThumbZoom
                    }
                    .drawBehind { with(TourArt) { episodeStill(second) } },
            )
            Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(stage.dp(18))
                    .graphicsLayer { alpha = (1f - (clear?.value ?: 0f)).coerceIn(0f, 1f) },
            )
        }
        Column {
            Text(stringResource(title), style = FrameType.Label, color = colors.content, maxLines = 1)
            Text(stringResource(length), style = FrameType.Caption, color = colors.secondaryContent, maxLines = 1)
        }
    }
}

/** Page 5: tonight's pick, which swaps once, over a week of watching. */
@Composable
internal fun TonightArt(stage: Stage, posters: List<TourPoster>) {
    val colors = FrameTheme.colors
    val covers = posters.filterIsInstance<TourPoster.Cover>()
    val pick: TourPoster = covers.getOrNull(0) ?: TourPoster.Placeholder(TourArt.TonightPick)
    val before: TourPoster = covers.getOrNull(1) ?: TourPoster.Placeholder(TourArt.TonightFirst)
    val swap = stage.entrance(SwapDelayMillis)
    val swapped by remember { derivedStateOf { swap.value > 0.5f } }
    val shown = if (swapped) pick else before
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(stage.dp(156))
                .panel(RoundedCornerShape(stage.dp(18)))
                .padding(stage.dp(14)),
            horizontalArrangement = Arrangement.spacedBy(stage.dp(12)),
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(stage.dp(6))) {
                Text(stringResource(R.string.app_tonight), style = Strong, color = colors.content, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(stage.dp(5))) {
                    SampleChip(stringResource(R.string.app_filter_all), selected = true, small = true)
                    SampleChip(stringResource(R.string.app_tonight_films), selected = false, small = true)
                    SampleChip(stringResource(R.string.tour_sample_genre), selected = false, small = true)
                }
                PickMeta(shown)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(stage.dp(6))) {
                    SmallPill(Icons.Rounded.PlayArrow, stringResource(R.string.app_tonight_play), primary = true)
                    SmallPill(Icons.Rounded.Shuffle, stringResource(R.string.app_tonight_another), primary = false)
                }
            }
            Box(Modifier.size(stage.dp(62), stage.dp(93))) {
                PosterArt(before, Modifier.fillMaxSize().graphicsLayer { alpha = (1f - swap.value).coerceIn(0f, 1f) }, marks = false)
                PosterArt(pick, Modifier.fillMaxSize().graphicsLayer { alpha = swap.value.coerceIn(0f, 1f) }, marks = false)
            }
        }
        Spacer(Modifier.height(stage.dp(16)))
        Text(stringResource(R.string.tour_sample_week), style = Strong, color = colors.content, maxLines = 1)
        Spacer(Modifier.height(stage.dp(10)))
        // Days run left to right in both languages, like every time line here (D6).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val days = stringArrayResource(R.array.tour_week_days)
            Row(
                modifier = Modifier.fillMaxWidth().height(stage.dp(72)),
                horizontalArrangement = Arrangement.spacedBy(stage.dp(9)),
                verticalAlignment = Alignment.Bottom,
            ) {
                WeekBars.forEachIndexed { day, share ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(share)
                            .background(if (day == WeekToday) colors.content else BarIdle, RoundedCornerShape(4.dp)),
                    )
                }
            }
            Spacer(Modifier.height(stage.dp(6)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(stage.dp(9))) {
                days.forEachIndexed { day, letter ->
                    Text(
                        text = letter,
                        style = FrameType.Caption,
                        color = if (day == WeekToday) colors.content else colors.secondaryContent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The year and rating of the pick shown: the owner's own for a real cover, a sample for a drawn one. */
@Composable
private fun PickMeta(poster: TourPoster) {
    val colors = FrameTheme.colors
    val year = when (poster) {
        is TourPoster.Cover -> poster.year
        is TourPoster.Placeholder -> TourArt.sample(poster.design).year
    }
    val rating = poster.rating()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (year != null) {
            Text(
                text = String.format(LocalLocale.current.platformLocale, "%d", year),
                style = FrameType.Caption,
                color = colors.secondaryContent,
                maxLines = 1,
            )
        }
        if (rating != null) RatingBadge(rating)
    }
}

@Composable
private fun SmallPill(icon: ImageVector, text: String, primary: Boolean) {
    val colors = FrameTheme.colors
    val ink = if (primary) colors.onAccent else colors.content
    val fill = if (primary) colors.accentFill else colors.content.copy(alpha = 0.10f)
    Row(
        modifier = Modifier
            .background(fill, FrameShapes.Chip)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = ink)
        Text(text, style = Strong, color = ink, maxLines = 1)
    }
}

private class SampleRow(val icon: ImageVector, val hue: TileHue, val title: Int, val hint: Int)

private val PrivacyRows = listOf(
    SampleRow(Icons.Rounded.Lock, TileHue.Blue, R.string.app_settings_private, R.string.tour_row_private_hint),
    SampleRow(Icons.Rounded.Backup, TileHue.Green, R.string.tour_row_backup, R.string.tour_row_backup_hint),
    SampleRow(Icons.Rounded.CleaningServices, TileHue.Orange, R.string.tour_row_cleanup, R.string.tour_row_cleanup_hint),
    SampleRow(Icons.Rounded.Widgets, TileHue.Violet, R.string.tour_row_widget, R.string.tour_row_widget_hint),
)

/** Page 6: four Settings rows with their coloured tiles, rising in one after another. */
@Composable
internal fun PrivacyArt(stage: Stage) {
    val colors = FrameTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .panel(RoundedCornerShape(stage.dp(18)))
            .padding(horizontal = stage.dp(14), vertical = stage.dp(6)),
    ) {
        PrivacyRows.forEachIndexed { index, row ->
            val appear = stage.entrance(index * RowStepMillis)
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.content.copy(alpha = 0.10f))
            Row(
                modifier = Modifier.fillMaxWidth().riseIn(appear).padding(vertical = stage.dp(10)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(stage.dp(10)),
            ) {
                ColorIconTile(row.icon, row.hue, size = stage.dp(30), glyph = stage.dp(18))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(row.title), style = FrameType.Label, color = colors.content, maxLines = 1)
                    Text(
                        stringResource(row.hint),
                        style = FrameType.Caption,
                        color = colors.secondaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.width(stage.dp(18)),
                    tint = colors.secondaryContent,
                )
            }
        }
    }
}
