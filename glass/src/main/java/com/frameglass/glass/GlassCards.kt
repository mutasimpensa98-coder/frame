package com.frameglass.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val BadgeShape = RoundedCornerShape(8.dp)

// White text on this blue and black text on this yellow both pass 4.5:1.
private val TmdbBlue = Color(0xFF0F6CDB)
private val ImdbYellow = Color(0xFFF5C518)
private val ProgressHeight = 3.dp
private val AvatarSize = 84.dp
private val CheckSize = 22.dp
private val ChipHeight = 40.dp
private val ChipIcon = 18.dp
private const val UnavailableAlpha = 0.4f
private const val ActivityMillis = 1600
private const val ActivitySegment = 0.3f
private const val ActivityRest = 0.65f
private val PosterLineScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))
private val CardTitle = FrameType.Body.copy(fontWeight = FontWeight.W600)

// Strong enough that the white and 70% white lines hold 4.5:1 over a pure white still. These marks
// sit on art, so they keep this look in both themes.
private val CardTextScrim = Brush.verticalGradient(
    0.15f to Color.Transparent,
    0.5f to Color.Black.copy(alpha = 0.56f),
    0.75f to Color.Black.copy(alpha = 0.70f),
    1f to Color.Black.copy(alpha = 0.84f),
)

/** Whose rating this is, which sets the badge colour: blue for TMDB, yellow for IMDb. */
enum class RatingBrand { Tmdb, Imdb, Other }

/** A rating and its source as shown, such as TMDB or IMDb. */
@Immutable
data class CardRating(val value: Double, val source: String, val brand: RatingBrand = RatingBrand.Other)

/**
 * A thin progress line that always fills from the left, in every language (D6). With a [track] it sits on
 * a surface and takes the accent. Without one it sits on art and takes the accent's tone for art.
 */
@Composable
fun ProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    track: Color = Color.Transparent,
) {
    val value = progress.coerceIn(0f, 1f)
    val colors = FrameTheme.colors
    val line = if (track.alpha > 0f) colors.accent else colors.mediaAccent
    Canvas(
        modifier
            .height(ProgressHeight)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) },
    ) {
        val stroke = size.height
        val middle = size.height / 2f
        val start = stroke / 2f
        val end = size.width - stroke / 2f
        if (track.alpha > 0f) {
            drawLine(track, Offset(start, middle), Offset(end, middle), stroke, StrokeCap.Round)
        }
        if (value > 0f) {
            val filled = Offset(start + (end - start) * value, middle)
            drawLine(line, Offset(start, middle), filled, stroke, StrokeCap.Round)
        }
    }
}

/**
 * A thin line for work with no known end: a short segment runs along it, and rests while motion is
 * reduced (brief 7.9). It reads from the start edge, because this is not a playback line (D6).
 */
@Composable
fun ActivityLine(modifier: Modifier = Modifier) {
    val colors = FrameTheme.colors
    val phase = if (rememberEffectsLevel() == EffectsLevel.Full) {
        rememberInfiniteTransition(label = "activityLine").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(ActivityMillis, easing = LinearEasing)),
            label = "activityPhase",
        )
    } else {
        null
    }
    Canvas(
        modifier
            .height(ProgressHeight)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val stroke = size.height
        val middle = size.height / 2f
        val start = stroke / 2f
        val end = size.width - stroke / 2f
        val length = (end - start) * ActivitySegment
        // Read while drawing, so the run redraws this and recomposes nothing.
        val head = start + (end - start + length) * (phase?.value ?: ActivityRest)
        val tail = (head - length).coerceAtLeast(start)
        val front = head.coerceAtMost(end)
        drawLine(colors.track, Offset(start, middle), Offset(end, middle), stroke, StrokeCap.Round)
        if (front > tail) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            drawLine(
                color = colors.accent,
                start = Offset(if (rtl) size.width - tail else tail, middle),
                end = Offset(if (rtl) size.width - front else front, middle),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun RatingBadge(rating: CardRating, modifier: Modifier = Modifier) {
    val value = String.format(LocalLocale.current.platformLocale, "%.1f", rating.value)
    val description = stringResource(R.string.glass_rating, value, rating.source)
    val labelled = modifier.semantics { contentDescription = description }
    val label: @Composable (Color) -> Unit = { ink ->
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = FrameType.Label, color = ink, maxLines = 1)
            Text(bidiIsolate(rating.source), style = FrameType.Caption, color = ink, maxLines = 1)
        }
    }
    when (rating.brand) {
        RatingBrand.Tmdb -> Box(labelled.background(TmdbBlue, BadgeShape)) { label(Color.White) }
        RatingBrand.Imdb -> Box(labelled.background(ImdbYellow, BadgeShape)) { label(Color.Black) }
        // Tint and rim only: one blurred badge per poster would break the limit of 3 blurred layers.
        RatingBrand.Other -> GlassSurface(GlassKind.Badge, BadgeShape, labelled, blur = false) {
            label(FrameTheme.colors.content)
        }
    }
}

@Composable
private fun CardChip(text: String, modifier: Modifier = Modifier) {
    GlassSurface(GlassKind.Badge, BadgeShape, modifier, blur = false) {
        Text(
            text = text,
            style = FrameType.Caption,
            color = FrameTheme.colors.content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = FrameSpacing.S8, vertical = FrameSpacing.S4),
        )
    }
}

@Composable
private fun WatchedMark(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.glass_watched)
    val colors = FrameTheme.colors
    Box(
        modifier = modifier
            .padding(FrameSpacing.S8)
            .size(CheckSize)
            .background(colors.mediaAccent, CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = colors.onMediaAccent)
    }
}

/** A 2:3 poster with its title and year below, a rating badge, progress and watched marks. */
@Composable
fun PosterCard(
    title: String,
    coverPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    year: Int? = null,
    rating: CardRating? = null,
    progress: Float? = null,
    watched: Boolean = false,
    unavailable: Boolean = false,
    coverModifier: Modifier = Modifier,
) {
    val dimmed = if (unavailable) Modifier.alpha(UnavailableAlpha) else Modifier
    val colors = FrameTheme.colors
    PressableGlass(
        onClick = onClick,
        modifier = modifier,
        shape = FrameShapes.Poster,
        highlight = false,
    ) {
        Column {
            Box(
                coverModifier
                    .fillMaxWidth()
                    .aspectRatio(FrameSizes.PosterRatio)
                    .then(dimmed)
                    .clip(FrameShapes.Poster),
            ) {
                CoverImage(coverPath, null, Modifier.matchParentSize())
                if (rating != null) {
                    RatingBadge(rating, Modifier.align(Alignment.TopStart).padding(FrameSpacing.S8))
                }
                if (watched) {
                    WatchedMark(Modifier.align(Alignment.TopEnd))
                }
                if (progress != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(FrameSpacing.S24)
                            .background(PosterLineScrim),
                    )
                    ProgressLine(
                        progress = progress,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(FrameSpacing.S8),
                    )
                }
            }
            Spacer(Modifier.height(FrameSpacing.S8))
            Text(
                text = bidiIsolate(title),
                style = FrameType.Body,
                color = if (unavailable) colors.secondaryContent else colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = when {
                unavailable -> stringResource(R.string.glass_unavailable)
                year != null -> String.format(LocalLocale.current.platformLocale, "%d", year)
                else -> null
            }
            if (detail != null) {
                Text(
                    text = detail,
                    style = FrameType.Caption,
                    color = colors.secondaryContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A 16:9 card with the title on the art, for frames and episodes. It takes the same rating, watched and
 * progress marks as [PosterCard], plus the time-left chip and New episode badge of Continue watching. A portrait
 * picture, such as a poster or an upright phone video, is pillarboxed by [WideArt].
 */
@Composable
fun LandscapeCard(
    title: String,
    imagePath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    episodeLabel: String? = null,
    subtitle: String? = null,
    progress: Float? = null,
    timeLeftMs: Long? = null,
    newEpisode: Boolean = false,
    imageModifier: Modifier = Modifier,
    rating: CardRating? = null,
    watched: Boolean = false,
    unavailable: Boolean = false,
) {
    val detail = if (unavailable) stringResource(R.string.glass_unavailable) else subtitle
    val colors = FrameTheme.colors
    PressableGlass(
        onClick = onClick,
        modifier = modifier,
        shape = FrameShapes.Poster,
        highlight = false,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(FrameSizes.LandscapeRatio)
                .then(if (unavailable) Modifier.alpha(UnavailableAlpha) else Modifier)
                .clip(FrameShapes.Poster),
        ) {
            WideArt(imagePath, imageModifier.matchParentSize())
            Box(Modifier.matchParentSize().background(CardTextScrim))
            if (rating != null) {
                RatingBadge(rating, Modifier.align(Alignment.TopStart).padding(FrameSpacing.S8))
            }
            if (newEpisode) {
                Text(
                    text = stringResource(R.string.glass_new_episode),
                    style = FrameType.Caption,
                    color = colors.onMediaAccent,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(FrameSpacing.S8)
                        .background(colors.mediaAccent, BadgeShape)
                        .padding(horizontal = FrameSpacing.S8, vertical = FrameSpacing.S4),
                )
            } else if (timeLeftMs != null) {
                CardChip(
                    text = timeLeftLabel(timeLeftMs),
                    modifier = Modifier.align(Alignment.TopEnd).padding(FrameSpacing.S8),
                )
            } else if (watched) {
                WatchedMark(Modifier.align(Alignment.TopEnd))
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = FrameSpacing.S12, vertical = FrameSpacing.S8),
            ) {
                if (episodeLabel != null) {
                    Text(
                        text = bidiIsolate(episodeLabel),
                        style = FrameType.Caption,
                        color = MediaColors.Content,
                        maxLines = 1,
                    )
                }
                Text(
                    text = bidiIsolate(title),
                    style = CardTitle,
                    color = MediaColors.Content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) {
                    Text(
                        text = bidiIsolate(detail),
                        style = FrameType.Caption,
                        color = MediaColors.SecondaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (progress != null) {
                    Spacer(Modifier.height(FrameSpacing.S4))
                    ProgressLine(progress, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** A section title with an optional small View all chip at the end. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
) {
    val colors = FrameTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FrameSpacing.S16),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = bidiIsolate(title),
            style = FrameType.Title,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onViewAll != null) {
            PressableGlass(onClick = onViewAll, shape = FrameShapes.Chip, highlight = false) {
                Text(
                    text = stringResource(R.string.glass_view_all),
                    style = FrameType.Label,
                    color = colors.content,
                    maxLines = 1,
                    modifier = Modifier
                        .background(colors.elevated, FrameShapes.Chip)
                        .padding(horizontal = FrameSpacing.S12, vertical = 6.dp),
                )
            }
        }
    }
}

/** A round cast photo with the name below, and the character when there is one. */
@Composable
fun CastAvatar(
    name: String,
    photoPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    character: String? = null,
) {
    val colors = FrameTheme.colors
    PressableGlass(onClick = onClick, modifier = modifier.width(AvatarSize), shape = FrameShapes.Poster, highlight = false) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (photoPath != null) {
                CoverImage(photoPath, null, Modifier.size(AvatarSize), shape = CircleShape)
            } else {
                Box(
                    modifier = Modifier
                        .size(AvatarSize)
                        .background(colors.elevated, CircleShape)
                        .border(0.5.dp, colors.hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, null, Modifier.size(36.dp), tint = colors.secondaryContent)
                }
            }
            Spacer(Modifier.height(FrameSpacing.S8))
            Text(
                text = bidiIsolate(name),
                style = FrameType.Label,
                color = colors.content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (character != null) {
                Text(
                    text = bidiIsolate(character),
                    style = FrameType.Caption,
                    color = colors.secondaryContent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A cast member as a name pill, for a cast with no photos at all, such as one from OMDb. A row of empty avatars
 * would read as missing art.
 */
@Composable
fun CastChip(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    character: String? = null,
) {
    val colors = FrameTheme.colors
    PressableGlass(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize(),
        shape = FrameShapes.Chip,
        highlight = false,
    ) {
        Row(
            modifier = Modifier
                .background(colors.elevated, FrameShapes.Chip)
                .border(0.5.dp, colors.hairline, FrameShapes.Chip)
                .heightIn(min = ChipHeight)
                .padding(start = FrameSpacing.S12, end = FrameSpacing.S16),
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Person, null, Modifier.size(ChipIcon), tint = colors.secondaryContent)
            Text(
                text = bidiIsolate(name),
                style = FrameType.Label,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (character != null) {
                Text(
                    text = bidiIsolate(character),
                    style = FrameType.Caption,
                    color = colors.secondaryContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}
