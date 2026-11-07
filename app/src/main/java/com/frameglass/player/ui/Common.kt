package com.frameglass.player.ui

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.frameglass.data.api.AccentColor
import com.frameglass.data.api.RatingInfo
import com.frameglass.data.api.RatingSource
import com.frameglass.data.api.ThemeMode
import com.frameglass.data.api.TitleKind
import com.frameglass.glass.CardRating
import com.frameglass.glass.FrameAccent
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.FrameThemeMode
import com.frameglass.glass.FrameType
import com.frameglass.glass.PressableGlass
import com.frameglass.glass.RatingBrand
import com.frameglass.glass.bidiIsolate
import com.frameglass.player.FrameApp
import com.frameglass.player.R
import com.frameglass.player.model.UiModel
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DisabledAlpha = 0.4f
private const val MillisPerSecond = 1_000L
private const val LogoSampleSize = 4
private const val VisibleAlpha = 128
private const val GreyTolerance = 40
private const val ColouredLogoShare = 0.08f

@Composable
internal fun rememberUiModel(): UiModel = (LocalContext.current.applicationContext as FrameApp).ui

@Composable
internal fun statusInset(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
internal fun navigationInset(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Screens with art under the status bar say so here, and the root sets the icons from the sum. Each screen
 * setting them itself would not work: the leaving screen is disposed after the entering one has started.
 */
@Stable
internal class ArtUnderBars {
    private val owners = mutableStateListOf<Any>()

    val present: Boolean get() = owners.isNotEmpty()

    fun add(owner: Any) {
        owners += owner
    }

    fun remove(owner: Any) {
        owners -= owner
    }
}

internal val LocalArtUnderBars = staticCompositionLocalOf<ArtUnderBars?> { null }

/** While [active], the status bar keeps light icons on the light theme, because dark art sits under them. */
@Composable
internal fun ArtUnderStatusBar(active: Boolean) {
    val bars = LocalArtUnderBars.current ?: return
    DisposableEffect(bars, active) {
        val owner = Any()
        if (active) bars.add(owner)
        onDispose { bars.remove(owner) }
    }
}

/** Lets a row inside a padded grid run from edge to edge, so chips scroll under the screen margin. */
internal fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + 2 * extra))
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-extra, 0) }
}

/** True when enough of a logo's visible pixels carry colour that one flat tint would destroy it. */
private fun logoHasColour(path: String): Boolean {
    val options = BitmapFactory.Options().apply { inSampleSize = LogoSampleSize }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return false
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    bitmap.recycle()
    val visible = pixels.filter { it ushr 24 >= VisibleAlpha }
    val coloured = visible.count { pixel ->
        val red = pixel shr 16 and 0xFF
        val green = pixel shr 8 and 0xFF
        val blue = pixel and 0xFF
        maxOf(red, green, blue) - minOf(red, green, blue) > GreyTolerance
    }
    return visible.isNotEmpty() && coloured > visible.size * ColouredLogoShare
}

/** A title logo where one exists, else a strong typeset title (brief 7.1). */
@Composable
internal fun TitleMark(
    logoPath: String?,
    name: String,
    logoModifier: Modifier,
    modifier: Modifier = Modifier,
    style: TextStyle = FrameType.Display,
) {
    var failed by remember(logoPath) { mutableStateOf(false) }
    val colors = FrameTheme.colors
    // Null until a logo has been checked on light. Dark shows every logo as it is, with no check.
    val coloured by produceState<Boolean?>(if (colors.isLight) null else false, logoPath, colors.isLight) {
        value = colors.isLight && logoPath != null && withContext(Dispatchers.IO) { logoHasColour(logoPath) }
    }
    if (logoPath != null && !failed && coloured != true) {
        AsyncImage(
            model = File(logoPath),
            contentDescription = name,
            // No flash of a wrongly tinted logo while the check runs.
            modifier = logoModifier.alpha(if (coloured == null) 0f else 1f),
            contentScale = ContentScale.Fit,
            // White title logos are drawn for dark art, so on the light canvas they take the text colour.
            colorFilter = if (colors.isLight) ColorFilter.tint(colors.content) else null,
            onState = { if (it is AsyncImagePainter.State.Error) failed = true },
        )
    } else {
        Text(
            text = bidiIsolate(name),
            style = style,
            color = FrameColors.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

/** One centred message and an optional action, for empty screens. */
@Composable
internal fun CenteredMessage(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(horizontal = FrameSpacing.S32), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
        ) {
            Text(title, style = FrameType.Title, color = FrameColors.White, textAlign = TextAlign.Center)
            if (body != null) {
                Text(body, style = FrameType.Body, color = FrameColors.SecondaryText, textAlign = TextAlign.Center)
            }
            action?.invoke()
        }
    }
}

/** A small fact chip such as NEW, SUB or a file size (F-L10). */
@Composable
internal fun FactChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = bidiIsolate(text),
        style = FrameType.Caption,
        color = FrameColors.White,
        maxLines = 1,
        modifier = modifier
            .background(FrameColors.Elevated, FactChipShape)
            .padding(horizontal = FrameSpacing.S8, vertical = 2.dp)
            .widthIn(max = 140.dp),
    )
}

private val FactChipShape = RoundedCornerShape(6.dp)

/** The destructive action of a confirmation. It is never the default: it takes its own tap. */
@Composable
internal fun DangerPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = FrameTheme.colors
    val fill = if (enabled) colors.error else colors.error.copy(alpha = DisabledAlpha)
    PressableGlass(
        onClick = onClick,
        modifier = modifier,
        shape = FrameShapes.Chip,
        enabled = enabled,
        highlight = false,
    ) {
        Box(
            modifier = Modifier
                .background(fill, FrameShapes.Chip)
                .heightIn(min = FrameSpacing.S48)
                .padding(horizontal = FrameSpacing.S24, vertical = FrameSpacing.S8),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = FrameType.Body.copy(fontWeight = FontWeight.W600),
                color = colors.onError,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun RatingInfo.toCard(): CardRating = CardRating(
    value = value,
    source = sourceLabel(source),
    brand = when (source) {
        RatingSource.TMDB -> RatingBrand.Tmdb
        RatingSource.IMDB -> RatingBrand.Imdb
    },
)

internal fun sourceLabel(source: RatingSource): String = when (source) {
    RatingSource.TMDB -> "TMDB"
    RatingSource.IMDB -> "IMDb"
}

/** A number through the device locale (F-A6). */
@Composable
internal fun localNumber(value: Int): String = String.format(LocalLocale.current.platformLocale, "%d", value)

@Composable
internal fun ratingText(value: Double): String = String.format(LocalLocale.current.platformLocale, "%.1f", value)

@Composable
internal fun kindLabel(kind: TitleKind): String = stringResource(
    when (kind) {
        TitleKind.MOVIE -> R.string.app_kind_movie
        TitleKind.SERIES -> R.string.app_kind_series
        TitleKind.ANIME -> R.string.app_kind_anime
    },
)

/** "2h 14m", or "45 min" under an hour. */
@Composable
internal fun durationLabel(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> pluralStringResource(R.plurals.app_minutes, rest, rest)
        rest == 0 -> pluralStringResource(R.plurals.app_hours, hours, hours)
        else -> stringResource(
            R.string.app_duration_join,
            pluralStringResource(R.plurals.app_hours, hours, hours),
            pluralStringResource(R.plurals.app_minutes, rest, rest),
        )
    }
}

@Composable
internal fun fileSizeLabel(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** "1:42:07", for the length of one file. */
internal fun clockLabel(durationMs: Long): String = DateUtils.formatElapsedTime(durationMs / MillisPerSecond)

/** A date in the device language. */
@Composable
internal fun dateLabel(epochMs: Long): String {
    val locale = LocalLocale.current.platformLocale
    return remember(epochMs, locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(epochMs)) }
}

/** The end of a path, which is the part that tells two folders apart in a one-line row. */
internal fun shortPath(path: String): String = path.split('/').filter { it.isNotEmpty() }.takeLast(2).joinToString("/")

internal fun ThemeMode.toFrame(): FrameThemeMode = when (this) {
    ThemeMode.SYSTEM -> FrameThemeMode.System
    ThemeMode.DARK -> FrameThemeMode.Dark
    ThemeMode.LIGHT -> FrameThemeMode.Light
}

internal fun AccentColor.toFrame(): FrameAccent = when (this) {
    AccentColor.WHITE -> FrameAccent.White
    AccentColor.BLUE -> FrameAccent.Blue
    AccentColor.RED -> FrameAccent.Red
    AccentColor.GREEN -> FrameAccent.Green
    AccentColor.AMBER -> FrameAccent.Amber
    AccentColor.VIOLET -> FrameAccent.Violet
}

/** Fragments joined with a dot, each isolated so its punctuation stays its own (F-A5). */
internal fun metaLine(parts: List<String>): String = parts.joinToString("  •  ") { bidiIsolate(it) }
