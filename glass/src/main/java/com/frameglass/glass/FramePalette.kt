package com.frameglass.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A ready theme. The player and the feature tour keep [Black] in every theme. */
enum class FrameThemeId {
    /** The near-black canvas the app always had, and the default. */
    Black,

    /** A true black canvas, for AMOLED screens. */
    PureBlack,

    /** Navy, with a blue accent. */
    Midnight,

    /** Deep violet, with a violet accent. */
    Violet,

    /** Deep green, with a green accent. */
    Forest,

    /** Warm brown, with an amber accent. */
    Coffee,

    /** The soft off-white canvas. */
    Light,

    /** Cream, with a brown accent. */
    Paper,
    ;

    /** True for the themes with a light canvas, which take dark text and dark system bar icons. */
    val isLight: Boolean get() = this == Light || this == Paper
}

/** The colour of the primary pill, the tab selection pill, progress lines and chips. */
enum class FrameAccent {
    /** The neutral: white on dark and near-black on light, where the primary pill turns dark. */
    White,

    /** Blue. */
    Blue,

    /** Red. */
    Red,

    /** Green. */
    Green,

    /** Amber. */
    Amber,

    /** Violet. */
    Violet,

    /** Pink. */
    Pink,

    /** Cyan. */
    Cyan,
}

/**
 * The owner's own theme: three numbers, and everything else is derived from them (see [FramePalettes]).
 *
 * @property hue the background hue in degrees, 0 to 360.
 * @property shade how light the background is, from 0, pure black, to 1, the lightest. Up to [DarkShades] the
 * canvas is dark and takes light text. Above it the canvas is light and takes dark text. The mid tones between
 * the two, where neither white nor black text would read, are stepped over.
 * @property surface how far cards and bars sit from the background: 0 below it, a third level with it, 1 well
 * above it.
 */
@Immutable
data class CustomTheme(
    val hue: Float = DefaultHue,
    val shade: Float = DefaultShade,
    val surface: Float = DefaultSurface,
) {
    /** True when this theme has a light canvas and dark text. */
    val isLight: Boolean get() = shade > DarkShades

    companion object {
        /** The hue a custom theme starts from. */
        const val DefaultHue = 220f

        /** The shade a custom theme starts from: a deep blue-grey. */
        const val DefaultShade = 0.26f

        /** The surface tone a custom theme starts from. */
        const val DefaultSurface = 0.8f

        /** The "any colour" accent before the owner picks one, as ARGB. */
        const val DefaultAccent = 0xFF3CD3E0.toInt()

        /** The last [shade] that is still a dark canvas. */
        const val DarkShades = 0.6f
    }
}

/**
 * What the app should look like: a ready [theme], or [custom] in its place when it is set, and an accent.
 * A null [accent] is the theme's own, such as blue on [FrameThemeId.Midnight]. [accentArgb] is any colour
 * at all and wins over [accent]. The default is the dark look with its white accent.
 */
@Immutable
data class FrameLook(
    val theme: FrameThemeId = FrameThemeId.Black,
    val custom: CustomTheme? = null,
    /** Follow the phone's dark or light setting (see [shownWhen]). */
    val followSystem: Boolean = false,
    val accent: FrameAccent? = null,
    val accentArgb: Int? = null,
) {
    /** True when this look has a light canvas. */
    val isLight: Boolean get() = custom?.isLight ?: theme.isLight

    /**
     * The look to show while the phone is in dark mode or not. Without [followSystem] it is this one. With it,
     * the side that matches this theme shows it, and the other side shows Black or Light with the same accent.
     */
    fun shownWhen(systemDark: Boolean): FrameLook = when {
        !followSystem -> this
        systemDark != isLight -> copy(followSystem = false)
        systemDark -> copy(theme = FrameThemeId.Black, custom = null, followSystem = false)
        else -> copy(theme = FrameThemeId.Light, custom = null, followSystem = false)
    }
}

/** Every colour of one theme and accent. Read the one in force through [FrameTheme.colors]. */
@Immutable
class FramePalette internal constructor(
    /** True on a light canvas, for the few things that differ in kind and not only in colour. */
    val isLight: Boolean,
    /** Page background, and the tint of every glass surface. */
    val canvas: Color,
    /** Chips, fields, dropdowns and image placeholders. */
    val elevated: Color,
    /** Grouped cards, such as Settings. */
    val card: Color,
    /** Main text and icons on the canvas and on glass. */
    val content: Color,
    /** Secondary text on the canvas and on glass. */
    val secondaryContent: Color,
    /** The accent as text and lines, tuned to read on every surface of this theme. */
    val accent: Color,
    /** The accent as a fill: the primary pill, the tab pill and selected rows. */
    val accentFill: Color,
    /** Text and icons on [accentFill]. */
    val onAccent: Color,
    /** Secondary text on [accentFill]. */
    val onAccentSecondary: Color,
    /** The accent for marks drawn straight on art and over its dark scrims, in any theme. */
    val mediaAccent: Color,
    /** Text and icons on a [mediaAccent] fill. */
    val onMediaAccent: Color,
    /** Status colour for errors only. */
    val error: Color,
    /** Text and icons on an [error] fill. */
    val onError: Color,
    /** Status colour for warnings only. */
    val warning: Color,
    /** The faintest edge and divider. */
    val hairline: Color,
    /** The edge of outlined controls. */
    val outline: Color,
    /** A weaker [outline]. */
    val outlineVariant: Color,
    internal val rowFill: Color,
    internal val track: Color,
    internal val shimmer: Color,
    internal val scrim: Color,
    internal val tabPill: Color,
    internal val tabPillRim: Color,
    internal val onTabPill: Color,
    internal val pressInk: Color,
    internal val pressStrength: Float,
    /** The level of the glass tint, which is [canvas], for the contrast rule. */
    internal val glassLevel: Float,
    private val contentLimit: Float,
    private val dangerLimit: Float,
    private val secondaryLimit: Float,
    private val rowLimit: Float,
    /** The look this palette draws, to find the same theme with another accent. */
    internal val look: FrameLook,
) {
    /** The backdrop level glass must hold for [text] to read: a ceiling on a dark theme, a floor on a light one. */
    internal fun glassLimit(text: GlassText): Float = when (text) {
        GlassText.Content -> contentLimit
        GlassText.Danger -> dangerLimit
        GlassText.Secondary -> secondaryLimit
        GlassText.Rows -> rowLimit
    }

    /** This palette on its way to [to], for the short fade of a theme change. Kind and limits are already [to]'s. */
    internal fun toward(to: FramePalette, fraction: Float): FramePalette = FramePalette(
        isLight = to.isLight,
        canvas = lerp(canvas, to.canvas, fraction),
        elevated = lerp(elevated, to.elevated, fraction),
        card = lerp(card, to.card, fraction),
        content = lerp(content, to.content, fraction),
        secondaryContent = lerp(secondaryContent, to.secondaryContent, fraction),
        accent = lerp(accent, to.accent, fraction),
        accentFill = lerp(accentFill, to.accentFill, fraction),
        onAccent = lerp(onAccent, to.onAccent, fraction),
        onAccentSecondary = lerp(onAccentSecondary, to.onAccentSecondary, fraction),
        mediaAccent = lerp(mediaAccent, to.mediaAccent, fraction),
        onMediaAccent = lerp(onMediaAccent, to.onMediaAccent, fraction),
        error = lerp(error, to.error, fraction),
        onError = lerp(onError, to.onError, fraction),
        warning = lerp(warning, to.warning, fraction),
        hairline = lerp(hairline, to.hairline, fraction),
        outline = lerp(outline, to.outline, fraction),
        outlineVariant = lerp(outlineVariant, to.outlineVariant, fraction),
        rowFill = lerp(rowFill, to.rowFill, fraction),
        track = lerp(track, to.track, fraction),
        shimmer = lerp(shimmer, to.shimmer, fraction),
        scrim = lerp(scrim, to.scrim, fraction),
        tabPill = lerp(tabPill, to.tabPill, fraction),
        tabPillRim = lerp(tabPillRim, to.tabPillRim, fraction),
        onTabPill = lerp(onTabPill, to.onTabPill, fraction),
        pressInk = to.pressInk,
        pressStrength = lerp(pressStrength, to.pressStrength, fraction),
        glassLevel = to.glassLevel,
        contentLimit = to.contentLimit,
        dangerLimit = to.dangerLimit,
        secondaryLimit = to.secondaryLimit,
        rowLimit = to.rowLimit,
        look = to.look,
    )
}

/** Text and icons over art and its dark scrims, which keep their look in every theme. */
internal object MediaColors {
    val Content = Color.White
    val SecondaryContent = Color.White.copy(alpha = 0.70f)
}

/** The surfaces and text of one theme, before an accent is laid on it. */
internal class ThemeSpec(
    val isLight: Boolean,
    val canvas: Color,
    val elevated: Color,
    val card: Color,
    val content: Color,
    /** Rims, dividers, tracks and row fills are this colour at a low alpha. */
    val ink: Color,
    /** The accent this theme shows when the owner picked none. */
    val accent: AccentTones,
)

/**
 * One accent: its tone for dark themes, which is also what marks on art take, and its tone for light themes.
 * [neutral] is the white of dark themes, which turns into the text colour on light ones. [brightFillOnLight] keeps
 * the dark tone as the fill on light themes, for a colour whose light-theme tone is too dull to be a pill.
 */
internal class AccentTones(
    val dark: Color,
    val light: Color,
    val neutral: Boolean = false,
    val brightFillOnLight: Boolean = false,
)

/**
 * Builds every palette.
 *
 * A ready theme is a [ThemeSpec] with fixed colours. A [CustomTheme] becomes a spec in [customSpec]. Either way
 * [build] then lays the accent on it and tunes the few values that depend on the surfaces, each with the same
 * small search: secondary text starts at 70% and grows stronger until it reads, an accent moves toward the text
 * colour until it reads, and a rim grows until it shows. On the plain dark and light themes every search stops
 * at its starting value, so those two looks are what they were before the other themes existed.
 * ThemeContrastTest holds all of it to the contrast rule.
 */
internal object FramePalettes {
    private val LightContent = Color(0xFF141414)
    private val LightError = Color(0xFFB3261E)
    private val LightWarning = Color(0xFF8A5300)
    private val DarkError = Color(0xFFFF6B5E)
    private val DarkWarning = Color(0xFFFFC14D)

    private const val SecondaryAlpha = 0.70f
    private const val SecondaryAlphaStep = 0.05f
    private const val ChipFill = 0.12f

    // The least a rim must stand out from the canvas to be seen at all, as a contrast ratio.
    private const val HairlineRatio = 1.1f
    private const val OutlineRatio = 1.7f
    private const val SoftOutlineRatio = 1.25f
    private const val RimStep = 0.01f
    private const val MaxRim = 0.6f

    // Above the glass under it on light, as the dark pill is below it on dark: both add contrast.
    private val LightTabPill = Color.White.copy(alpha = 0.64f)
    private val DarkTabPill = Color.Black.copy(alpha = 0.46f)

    // What a mark on art sits on: the poster scrim over mid-grey art.
    private val ArtScrim = Color.Black.copy(alpha = 0.6f).compositeOver(Color(127, 127, 127))

    private val Neutral = AccentTones(dark = Color.White, light = LightContent, neutral = true)

    // Each tone clears 4.5:1 as text on the three surfaces of the plain dark or light theme, and over a 12%
    // wash of itself on a chip. On the other themes build() moves a tone as far as that theme needs.
    private fun tones(accent: FrameAccent): AccentTones = when (accent) {
        FrameAccent.White -> Neutral
        FrameAccent.Blue -> AccentTones(Color(0xFF7AB8FF), Color(0xFF0A54C2))
        FrameAccent.Red -> AccentTones(Color(0xFFFF8A80), Color(0xFFAD2219))
        FrameAccent.Green -> AccentTones(Color(0xFF5FD38D), Color(0xFF0A6636))
        // Amber dark enough to read as text is brown as a fill, so its pills stay amber and carry dark text.
        FrameAccent.Amber -> AccentTones(Color(0xFFFFC14D), Color(0xFF754B00), brightFillOnLight = true)
        FrameAccent.Violet -> AccentTones(Color(0xFFBFA2FF), Color(0xFF6631C2))
        FrameAccent.Pink -> AccentTones(Color(0xFFFF8AD1), Color(0xFFA8185F))
        FrameAccent.Cyan -> AccentTones(Color(0xFF3CD3E0), Color(0xFF00626D))
    }

    private fun own(tone: Long) = Color(tone).let { AccentTones(it, it) }

    // A dark theme's raised surface sits 56.5% of the way from the canvas to its cards, as #1A1A1A does
    // between #0D0D0D and #242424. Chips and bars take the raised colour, which is the "card" of the storyboard.
    private fun spec(theme: FrameThemeId): ThemeSpec = when (theme) {
        FrameThemeId.Black -> dark(0xFF0D0D0D, 0xFF1A1A1A, 0xFF242424, Color.White, Neutral)
        FrameThemeId.PureBlack -> dark(0xFF000000, 0xFF121214, 0xFF202023, Color.White, Neutral)
        FrameThemeId.Midnight -> dark(0xFF131C28, 0xFF1F2B3B, 0xFF28374A, Color(0xFFEAF0F8), own(0xFF5AA9FF))
        FrameThemeId.Violet -> dark(0xFF16111F, 0xFF241B33, 0xFF2F2342, Color(0xFFF1ECFA), own(0xFFA98BFF))
        FrameThemeId.Forest -> dark(0xFF0E1714, 0xFF18261F, 0xFF203227, Color(0xFFE9F3EE), own(0xFF3DDC97))
        FrameThemeId.Coffee -> dark(0xFF18130F, 0xFF261E17, 0xFF31261D, Color(0xFFF5EEE6), own(0xFFFFB13B))
        FrameThemeId.Light -> ThemeSpec(
            isLight = true,
            canvas = Color(0xFFF5F4F1),
            elevated = Color(0xFFE9E7E1),
            card = Color.White,
            content = LightContent,
            ink = Color.Black,
            accent = Neutral,
        )
        FrameThemeId.Paper -> Color(0xFF2A2118).let { brownInk ->
            ThemeSpec(
                isLight = true,
                canvas = Color(0xFFF3EBDD),
                elevated = Color(0xFFE7DDCB),
                card = Color(0xFFFBF5EA),
                content = brownInk,
                ink = brownInk,
                accent = own(0xFF8A4B1F),
            )
        }
    }

    private fun dark(canvas: Long, elevated: Long, card: Long, content: Color, accent: AccentTones) = ThemeSpec(
        isLight = false,
        canvas = Color(canvas),
        elevated = Color(elevated),
        card = Color(card),
        content = content,
        ink = content,
        accent = accent,
    )

    private val specs = FrameThemeId.entries.map(::spec)

    // ---------------------------------------------------------------------------------------- custom theme

    // The canvas runs from black to CIE lightness 36 over the dark shades, then from 58 to 97 over the light
    // ones. Between 36 and 58 neither white nor black text keeps 4.5:1 on a chip, so no shade lands there.
    private const val DarkCanvasMax = 36f
    private const val LightCanvasMin = 58f
    private const val LightCanvasMax = 97f

    // How far a surface may go before the text on it or a chip on it stops reading. A surface that would
    // pass its limit stops there, or goes to the other side of the canvas when that leaves it too close.
    private const val DarkCardMax = 46f
    private const val DarkElevatedMax = 41f
    private const val LightCardMin = 54f
    private const val LightElevatedMin = 56.5f
    private const val MinSeparation = 2.5f
    private const val LightElevatedDrop = 3f

    // The surface slider moves the cards from 10 below the canvas to 20 above it, and the raised surface
    // goes 55% as far.
    private const val SurfaceLow = -10f
    private const val SurfaceHigh = 20f
    private const val ElevatedShare = 0.55f

    private const val CanvasSaturation = 0.42f
    private const val DarkSurfaceSaturation = 0.36f
    private const val LightTextLightness = 95f
    private const val LightTextSaturation = 0.5f
    private const val DarkTextLightness = 12f
    private const val DarkTextSaturation = 0.6f

    // Tinted text is kept only where it has contrast to spare. Nearer the middle the text is pure white or black.
    private const val TintedTextRatio = 7f

    private const val HueGrid = 1f
    private const val UnitGrid = 200f

    /** The canvas lightness of a custom [shade]. */
    internal fun canvasLightness(shade: Float): Float {
        val s = shade.coerceIn(0f, 1f)
        return if (s <= CustomTheme.DarkShades) {
            DarkCanvasMax * s / CustomTheme.DarkShades
        } else {
            lerp(LightCanvasMin, LightCanvasMax, (s - CustomTheme.DarkShades) / (1f - CustomTheme.DarkShades))
        }
    }

    // A surface [delta] away from a dark canvas at [from]: it turns back at black, and at [ceiling] it stops, or
    // goes below the canvas when stopping would leave it too close to tell apart.
    private fun darkSurface(from: Float, delta: Float, ceiling: Float): Float {
        var target = from + delta
        if (target < 0f) target = from + abs(delta)
        if (target > ceiling) {
            val room = ceiling - from
            target = if (room >= max(MinSeparation, abs(delta) / 2f)) ceiling else max(0f, from - abs(delta))
        }
        return target
    }

    // The same on a light canvas, where white is the wall and [floor] the limit.
    private fun lightSurface(from: Float, delta: Float, floor: Float): Float {
        val target = min(100f, from + delta)
        return if (target < floor) min(100f, from + abs(delta)) else target
    }

    internal fun customSpec(custom: CustomTheme): ThemeSpec {
        val light = custom.isLight
        val base = canvasLightness(custom.shade)
        val offset = lerp(SurfaceLow, SurfaceHigh, custom.surface.coerceIn(0f, 1f))
        val canvas = ThemeMath.tone(custom.hue, CanvasSaturation, base)
        val card: Color
        val elevated: Color
        if (light) {
            card = ThemeMath.tone(custom.hue, CanvasSaturation, lightSurface(base, offset, LightCardMin))
            val drop = -max(LightElevatedDrop, ElevatedShare * abs(offset))
            elevated = ThemeMath.tone(custom.hue, CanvasSaturation, lightSurface(base, drop, LightElevatedMin))
        } else {
            val cardAt = darkSurface(base, offset, DarkCardMax)
            card = ThemeMath.tone(custom.hue, DarkSurfaceSaturation, cardAt)
            // The raised surface goes the way the cards went, which is not always the way the slider points: near
            // black a card can only go up. Chips and fields are drawn in the raised colour, so it never sits
            // level with the canvas.
            val lift = cardAt - base
            val share = ElevatedShare * lift
            val step = if (abs(share) >= MinSeparation) share else if (lift < 0f) -MinSeparation else MinSeparation
            elevated = ThemeMath.tone(custom.hue, DarkSurfaceSaturation, darkSurface(base, step, DarkElevatedMax))
        }
        val surfaces = listOf(canvas, elevated, card)
        val tinted = if (light) {
            ThemeMath.tone(custom.hue, DarkTextSaturation, DarkTextLightness)
        } else {
            ThemeMath.tone(custom.hue, LightTextSaturation, LightTextLightness)
        }
        val content = when {
            surfaces.all { GlassContrast.ratio(tinted, it) >= TintedTextRatio } -> tinted
            light -> Color.Black
            else -> Color.White
        }
        return ThemeSpec(
            isLight = light,
            canvas = canvas,
            elevated = elevated,
            card = card,
            content = content,
            ink = content,
            accent = tones(FrameAccent.Cyan),
        )
    }

    // ------------------------------------------------------------------------------------------- building

    private fun build(spec: ThemeSpec, accent: AccentTones, look: FrameLook): FramePalette {
        val light = spec.isLight
        val surfaces = listOf(spec.canvas, spec.elevated, spec.card)
        val content = spec.content
        val ink = spec.ink
        val rowFill = ink.copy(alpha = if (light) 0.05f else 0.06f)

        fun readsAsText(color: Color) = surfaces.all { GlassContrast.reads(color, it) }
        // Chips sit on the canvas and on raised surfaces, washed with their own colour.
        fun readsOnChips(color: Color) = listOf(spec.canvas, spec.elevated).all {
            GlassContrast.reads(color, color.copy(alpha = ChipFill).compositeOver(it))
        }

        val wanted = when {
            accent.neutral -> if (light) content else accent.dark
            light -> accent.light
            else -> accent.dark
        }
        val tone = ThemeMath.fit(wanted, content) { readsAsText(it) && readsOnChips(it) }

        // Secondary text is 70% where that reads, and stronger where it would drop under the contrast rule: on a
        // surface, on a row of a sheet that has turned solid, or on a list row picked in selection mode, which is
        // the canvas under a wash of the accent.
        val sheetRow = rowFill.compositeOver(spec.canvas)
        val pickedRow = tone.copy(alpha = ChipFill).compositeOver(spec.canvas)
        var soft = SecondaryAlpha
        while (soft < 1f && !(surfaces + sheetRow + pickedRow).all { GlassContrast.reads(content.copy(alpha = soft), it) }) {
            soft += SecondaryAlphaStep
        }
        val secondary = content.copy(alpha = soft.coerceAtMost(1f))
        val mediaTone = ThemeMath.fit(accent.dark, Color.White) { GlassContrast.reads(it, ArtScrim) }
        // A pill is a shape, not text, so it keeps the colour as it was picked wherever that shape shows on every
        // surface. A colour too close to them takes the tone that reads.
        val showsAsFill = surfaces.all { GlassContrast.reads(wanted, it, GlassContrast.MinLargeRatio) }
        val fill = when {
            light && accent.brightFillOnLight -> mediaTone
            showsAsFill -> wanted
            else -> tone
        }
        val onFill = GlassContrast.readableOn(fill)
        val error = ThemeMath.fit(if (light) LightError else DarkError, content, holds = ::readsAsText)
        val warning = ThemeMath.fit(if (light) LightWarning else DarkWarning, content, holds = ::readsAsText)
        val contentLimit = GlassContrast.limit(content, spec.canvas, light)
        // The error colour is softer than the text, so a bar that carries it must hold the stricter of the two.
        val errorLimit = GlassContrast.limit(error, spec.canvas, light)

        return FramePalette(
            isLight = light,
            canvas = spec.canvas,
            elevated = spec.elevated,
            card = spec.card,
            content = content,
            secondaryContent = secondary,
            accent = tone,
            accentFill = fill,
            onAccent = onFill,
            onAccentSecondary = secondaryOn(fill, onFill),
            mediaAccent = mediaTone,
            onMediaAccent = GlassContrast.readableOn(mediaTone),
            error = error,
            onError = GlassContrast.readableOn(error),
            warning = warning,
            hairline = rim(ink, if (light) 0.07f else 0.08f, spec.canvas, HairlineRatio),
            outline = rim(ink, 0.24f, spec.canvas, OutlineRatio),
            outlineVariant = rim(ink, 0.12f, spec.canvas, SoftOutlineRatio),
            rowFill = rowFill,
            track = rim(ink, 0.12f, spec.canvas, SoftOutlineRatio),
            shimmer = if (light) Color.White.copy(alpha = 0.60f) else ink.copy(alpha = 0.07f),
            scrim = Color.Black.copy(alpha = if (light) 0.32f else 0.55f),
            tabPill = when {
                !accent.neutral -> fill
                light -> LightTabPill
                else -> DarkTabPill
            },
            tabPillRim = if (accent.neutral) ink.copy(alpha = 0.06f) else Color.Transparent,
            onTabPill = if (accent.neutral) content else onFill,
            pressInk = ink,
            pressStrength = if (light) 0.10f else 0.22f,
            glassLevel = GlassContrast.tintLevel(spec.canvas),
            contentLimit = contentLimit,
            dangerLimit = if (light) max(contentLimit, errorLimit) else min(contentLimit, errorLimit),
            secondaryLimit = GlassContrast.limit(secondary, spec.canvas, light),
            rowLimit = GlassContrast.limit(secondary, spec.canvas, light, rowFill),
            look = look,
        )
    }

    // The ink at [alpha], or as much stronger as it takes to stand [ratio] apart from the canvas.
    private fun rim(ink: Color, alpha: Float, canvas: Color, ratio: Float): Color {
        var a = alpha
        while (a < MaxRim && !GlassContrast.reads(ink.copy(alpha = a), canvas, ratio)) a += RimStep
        return ink.copy(alpha = a)
    }

    // 70% where that reads, and stronger on tones where it would drop under the contrast rule.
    private fun secondaryOn(fill: Color, on: Color): Color {
        var alpha = SecondaryAlpha
        while (alpha < 1f && GlassContrast.ratio(on.copy(alpha = alpha).compositeOver(fill), fill) < GlassContrast.MinRatio) {
            alpha += SecondaryAlphaStep
        }
        return on.copy(alpha = alpha.coerceAtMost(1f))
    }

    // --------------------------------------------------------------------------------------------- lookup

    private const val MaxPassing = 160
    private const val Opaque = 0xFF000000.toInt()

    // The ready themes with the named accents are few and are kept for good, so each is one instance for the life
    // of the app. Custom themes and "any colour" accents are without number: dragging a slider makes one per step.
    private val kept = HashMap<FrameLook, FramePalette>()
    private val passing = HashMap<FrameLook, FramePalette>()

    /** The look of a plain `FrameTheme { }`, and of any read outside one. */
    val Default: FramePalette = of(FrameLook())

    // Two looks that draw the same thing become one key, so identity means equality for every ready theme.
    private fun key(look: FrameLook): FrameLook {
        val custom = look.custom?.let {
            CustomTheme(
                hue = (ThemeMath.wrapHue(it.hue) / HueGrid).roundToInt() * HueGrid,
                shade = shadeOnGrid(it.shade),
                surface = (it.surface.coerceIn(0f, 1f) * UnitGrid).roundToInt() / UnitGrid,
            )
        }
        val theme = if (custom == null) look.theme else FrameThemeId.Black
        val argb = look.accentArgb?.or(Opaque)
        val ownIsNeutral = custom == null && specs[theme.ordinal].accent.neutral
        val accent = when {
            argb != null -> null
            look.accent == null && ownIsNeutral -> FrameAccent.White
            else -> look.accent
        }
        return FrameLook(theme = theme, custom = custom, followSystem = false, accent = accent, accentArgb = argb)
    }

    // Rounding never carries a shade across the step between dark and light canvases, so the palette is always
    // the kind that CustomTheme.isLight says.
    private fun shadeOnGrid(shade: Float): Float {
        val raw = shade.coerceIn(0f, 1f)
        val rounded = (raw * UnitGrid).roundToInt() / UnitGrid
        return if (raw > CustomTheme.DarkShades && rounded <= CustomTheme.DarkShades) {
            CustomTheme.DarkShades + 1f / UnitGrid
        } else {
            rounded
        }
    }

    /**
     * The palette of [look], taken as it stands: resolve [FrameLook.followSystem] with [FrameLook.shownWhen]
     * first. The same look always gives the same palette, and a ready theme the same instance.
     */
    fun of(look: FrameLook): FramePalette {
        val key = key(look)
        val cache = if (key.custom == null && key.accentArgb == null) kept else passing
        synchronized(this) {
            cache[key]?.let { return it }
            val spec = key.custom?.let(::customSpec) ?: specs[key.theme.ordinal]
            val accent = when {
                key.accentArgb != null -> Color(key.accentArgb).let { AccentTones(it, it) }
                key.accent != null -> tones(key.accent)
                else -> spec.accent
            }
            val built = build(spec, accent, key)
            if (cache === passing && cache.size >= MaxPassing) cache.clear()
            cache[key] = built
            return built
        }
    }

    /** A ready theme with one of the accents, or with its own when [accent] is null. */
    fun of(theme: FrameThemeId, accent: FrameAccent? = null): FramePalette =
        of(FrameLook(theme = theme, accent = accent))

    /** The theme of [palette] with another accent: one of the eight, any colour, or its own when both are null. */
    fun withAccent(palette: FramePalette, accent: FrameAccent?, accentArgb: Int? = null): FramePalette =
        of(palette.look.copy(accent = accent, accentArgb = accentArgb))
}
