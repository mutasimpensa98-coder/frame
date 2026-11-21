package com.frameglass.glass

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    // Built once for the whole class: a few thousand palettes.
    private companion object {
        val black = FramePalettes.of(FrameThemeId.Black)
        val light = FramePalettes.of(FrameThemeId.Light)

        // "Any colour" accents that sit at the corners of the picker, and three no picker would give.
        val anyColours = listOf(0xFFFF0000, 0xFF0000FF, 0xFFFFFF00, 0xFF00FF00, 0xFF808080, 0xFF000000, 0xFFFFFFFF)
            .map { it.toInt() }

        // The eight ready themes with their own accent, each of the eight accents, and any colour.
        val ready: List<FramePalette> = FrameThemeId.entries.flatMap { theme ->
            val named = (listOf<FrameAccent?>(null) + FrameAccent.entries).map { FramePalettes.of(theme, it) }
            named + anyColours.map { FramePalettes.of(FrameLook(theme = theme, accentArgb = it)) }
        }

        // A sweep of the custom theme: every 30 degrees of hue, the ends, the middle and the quarters of the other
        // two sliders, and both sides of the step between dark and light canvases.
        const val firstLightShade = CustomTheme.DarkShades + 0.005f
        val shades =
            listOf(0f, 0.15f, 0.3f, 0.45f, 0.5f, 0.55f, CustomTheme.DarkShades, firstLightShade, 0.7f, 0.8f, 0.9f, 1f)
        val customThemes: List<CustomTheme> = (0 until 360 step 30).flatMap { hue ->
            shades.flatMap { shade ->
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { surface -> CustomTheme(hue.toFloat(), shade, surface) }
            }
        }
        val custom: List<FramePalette> = customThemes.flatMap { theme ->
            listOf(
                FrameLook(custom = theme),
                FrameLook(custom = theme, accent = FrameAccent.White),
                FrameLook(custom = theme, accent = FrameAccent.Amber),
                FrameLook(custom = theme, accentArgb = 0xFF0000FF.toInt()),
                FrameLook(custom = theme, accentArgb = 0xFFFFFF00.toInt()),
            ).map(FramePalettes::of)
        }
        val all = ready + custom
        val backdrops = listOf(0, 64, 127, 191, 255).map { Color(it, it, it) }
    }

    private fun FramePalette.name() = look.toString()

    private fun FramePalette.surfaces() = listOf(canvas, elevated, card)

    private fun assertReads(what: String, front: Color, back: Color, minimum: Float = GlassContrast.MinRatio) {
        val solid = if (front.alpha < 1f) front.compositeOver(back) else front
        val ratio = GlassContrast.ratio(solid, back)
        assertTrue("$what is $ratio:1", ratio >= minimum)
    }

    private fun hex(color: Color) = "#%08X".format(color.toArgb())

    // The tint a surface of this theme draws over a backdrop of [level], as GlassSurface works it out.
    private fun FramePalette.glassTint(recipe: GlassRecipe, level: Float) =
        GlassContrast.tintAlpha(recipe.baseTint, level, glassLimit(recipe.text), glassLevel, isLight)

    @Test
    fun darkWithWhiteAccentIsTheLookFromBeforeThemes() {
        val dark = black
        assertSame(FramePalettes.Default, dark)
        assertSame(dark, FramePalettes.of(FrameThemeId.Black, FrameAccent.White))
        assertFalse(dark.isLight)
        assertEquals(Color(0xFF0D0D0D), dark.canvas)
        assertEquals(Color(0xFF1A1A1A), dark.elevated)
        assertEquals(Color(0xFF242424), dark.card)
        assertEquals(Color.White, dark.content)
        assertEquals(Color.White.copy(alpha = 0.70f), dark.secondaryContent)
        assertEquals(Color.White, dark.accent)
        assertEquals(Color.White, dark.accentFill)
        assertEquals(Color(0xFF0D0D0D), dark.onAccent)
        assertEquals(Color(0xFF0D0D0D).copy(alpha = 0.70f), dark.onAccentSecondary)
        assertEquals(Color.White, dark.mediaAccent)
        assertEquals(Color(0xFF0D0D0D), dark.onMediaAccent)
        assertEquals(Color(0xFFFF6B5E), dark.error)
        assertEquals(Color(0xFF0D0D0D), dark.onError)
        assertEquals(Color(0xFFFFC14D), dark.warning)
        assertEquals(Color.White.copy(alpha = 0.08f), dark.hairline)
        assertEquals(Color.White.copy(alpha = 0.24f), dark.outline)
        assertEquals(Color.White.copy(alpha = 0.12f), dark.outlineVariant)
        assertEquals(Color.White.copy(alpha = 0.06f), dark.rowFill)
        assertEquals(Color.White.copy(alpha = 0.12f), dark.track)
        assertEquals(Color.White.copy(alpha = 0.07f), dark.shimmer)
        assertEquals(Color.Black.copy(alpha = 0.55f), dark.scrim)
        assertEquals(Color.Black.copy(alpha = 0.46f), dark.tabPill)
        assertEquals(Color.White.copy(alpha = 0.06f), dark.tabPillRim)
        assertEquals(Color.White, dark.onTabPill)
        assertEquals(Color.White, dark.pressInk)
        assertEquals(0.22f, dark.pressStrength)
    }

    @Test
    fun lightIsTheLookFromBeforeTheOtherThemes() {
        assertTrue(light.isLight)
        assertEquals(Color(0xFFF5F4F1), light.canvas)
        assertEquals(Color(0xFFE9E7E1), light.elevated)
        assertEquals(Color.White, light.card)
        assertEquals(Color(0xFF141414), light.content)
        assertEquals(Color(0xFF141414).copy(alpha = 0.70f), light.secondaryContent)
        assertEquals(Color(0xFF141414), light.accent)
        assertEquals(Color(0xFF141414), light.accentFill)
        assertEquals(Color.White, light.onAccent)
        assertEquals(Color.White, light.mediaAccent)
        assertEquals(Color(0xFFB3261E), light.error)
        assertEquals(Color(0xFF8A5300), light.warning)
        assertEquals(Color.Black.copy(alpha = 0.07f), light.hairline)
        assertEquals(Color.Black.copy(alpha = 0.24f), light.outline)
        assertEquals(Color.Black.copy(alpha = 0.12f), light.outlineVariant)
        assertEquals(Color.Black.copy(alpha = 0.05f), light.rowFill)
        assertEquals(Color.Black.copy(alpha = 0.12f), light.track)
        assertEquals(Color.White.copy(alpha = 0.60f), light.shimmer)
        assertEquals(Color.Black.copy(alpha = 0.32f), light.scrim)
        assertEquals(Color.White.copy(alpha = 0.64f), light.tabPill)
        assertEquals(Color.Black.copy(alpha = 0.06f), light.tabPillRim)
        assertEquals(Color(0xFF141414), light.onTabPill)
        assertEquals(Color.Black, light.pressInk)
        assertEquals(0.10f, light.pressStrength)
    }

    @Test
    fun theOldAccentsKeepTheirTonesOnTheTwoOldThemes() {
        val tones = mapOf(
            FrameAccent.Blue to (0xFF7AB8FF to 0xFF0A54C2),
            FrameAccent.Red to (0xFFFF8A80 to 0xFFAD2219),
            FrameAccent.Green to (0xFF5FD38D to 0xFF0A6636),
            FrameAccent.Amber to (0xFFFFC14D to 0xFF754B00),
            FrameAccent.Violet to (0xFFBFA2FF to 0xFF6631C2),
        )
        for ((accent, pair) in tones) {
            assertEquals("$accent on dark", Color(pair.first), FramePalettes.of(FrameThemeId.Black, accent).accent)
            assertEquals("$accent on light", Color(pair.second), FramePalettes.of(FrameThemeId.Light, accent).accent)
            assertEquals(Color(pair.first), FramePalettes.of(FrameThemeId.Light, accent).mediaAccent)
        }
    }

    @Test
    fun theReadyThemesAreTheOnesOfTheStoryboard() {
        fun check(theme: FrameThemeId, canvas: Long, raised: Long, accent: Long, isLight: Boolean = false) {
            val palette = FramePalettes.of(theme)
            assertEquals("$theme canvas", Color(canvas), palette.canvas)
            // The storyboard's "card" is the raised surface on a dark theme and the card on a light one.
            assertEquals("$theme surface", Color(raised), if (isLight) palette.card else palette.elevated)
            assertEquals("$theme accent", Color(accent), palette.accentFill)
            assertEquals(isLight, palette.isLight)
            assertEquals(isLight, theme.isLight)
        }
        check(FrameThemeId.Black, 0xFF0D0D0D, 0xFF1A1A1A, 0xFFFFFFFF)
        check(FrameThemeId.PureBlack, 0xFF000000, 0xFF121214, 0xFFFFFFFF)
        check(FrameThemeId.Midnight, 0xFF131C28, 0xFF1F2B3B, 0xFF5AA9FF)
        check(FrameThemeId.Violet, 0xFF16111F, 0xFF241B33, 0xFFA98BFF)
        check(FrameThemeId.Forest, 0xFF0E1714, 0xFF18261F, 0xFF3DDC97)
        check(FrameThemeId.Coffee, 0xFF18130F, 0xFF261E17, 0xFFFFB13B)
        check(FrameThemeId.Light, 0xFFF5F4F1, 0xFFFFFFFF, 0xFF141414, isLight = true)
        check(FrameThemeId.Paper, 0xFFF3EBDD, 0xFFFBF5EA, 0xFF8A4B1F, isLight = true)
    }

    @Test
    fun darkGlassRecipesAreTheOnesFromBeforeThemes() {
        fun check(kind: GlassKind, blur: Int, tint: Float, sampled: Boolean, limit: Float) {
            val recipe = kind.recipe(light = false)
            assertEquals(blur.dp, recipe.blur)
            assertEquals(tint, recipe.baseTint)
            assertEquals(sampled, recipe.sampled)
            assertEquals(255f, recipe.worstLevel)
            // What the surface ends up with is the old tint: the limit of its text, or a base that already clears it.
            val old = GlassContrast.tintAlpha(tint, 255f, limit, GlassContrast.TintLevel)
            assertEquals("$kind", old, black.glassTint(recipe, 255f), 0f)
            // A bar follows its backdrop, and does so as it did.
            for (level in listOf(0f, 100f, 118f, 119f, 180f)) {
                val before = GlassContrast.tintAlpha(tint, level, limit, GlassContrast.TintLevel)
                if (sampled) assertEquals("$kind over $level", before, black.glassTint(recipe, level), 0f)
            }
        }
        check(GlassKind.TabBar, 24, 0.40f, sampled = true, GlassContrast.WhiteTextLimit)
        // The bar of actions is the tab bar wherever plain text is all the backdrop has to carry: up to the level
        // the error colour holds on, which on the dark look is every backdrop darker than its own cards.
        val bar = GlassKind.ActionBar.recipe(light = false)
        val tabs = GlassKind.TabBar.recipe(light = false)
        assertEquals(tabs.blur, bar.blur)
        assertEquals(tabs.baseTint, bar.baseTint)
        assertEquals(tabs.shadow!!.alpha, bar.shadow!!.alpha)
        assertTrue(bar.sampled)
        for (level in listOf(0f, 13f, 26f, 36f)) assertEquals(black.glassTint(tabs, level), black.glassTint(bar, level), 0f)
        assertTrue(black.glassLimit(GlassText.Danger) < black.glassLimit(GlassText.Content))
        check(GlassKind.TopBar, 24, 0.40f, sampled = true, GlassContrast.WhiteTextLimit)
        check(GlassKind.Sheet, 32, 0.76f, sampled = false, GlassContrast.SecondaryTextLimit)
        check(GlassKind.Menu, 32, 0.76f, sampled = false, GlassContrast.SecondaryTextLimit)
        check(GlassKind.Badge, 16, 0.46f, sampled = false, GlassContrast.WhiteTextLimit)
        check(GlassKind.Control, 16, 0.46f, sampled = false, GlassContrast.WhiteTextLimit)
        assertEquals(Color.White.copy(alpha = 0.18f), GlassKind.TabBar.recipe(false).rimTop)
        assertEquals(Color.White.copy(alpha = 0.04f), GlassKind.TabBar.recipe(false).rimBottom)
        assertEquals(Color.White.copy(alpha = 0.16f), GlassKind.Badge.recipe(false).rimTop)
        assertEquals(0.5.dp, GlassKind.Badge.recipe(false).rimWidth)
        assertEquals(Color.White.copy(alpha = 0.08f), GlassKind.TopBar.recipe(false).bottomRim)
        assertEquals(0.40f, GlassKind.TabBar.recipe(false).shadow!!.alpha)
        assertEquals(0.45f, GlassKind.Sheet.recipe(false).shadow!!.alpha)
        assertNull(GlassKind.TopBar.recipe(false).shadow)
    }

    @Test
    fun theTwoOldThemesKeepTheBriefsLimits() {
        assertEquals(GlassContrast.TintLevel, black.glassLevel)
        assertEquals(GlassContrast.WhiteTextLimit, black.glassLimit(GlassText.Content))
        assertEquals(GlassContrast.SecondaryTextLimit, black.glassLimit(GlassText.Secondary))
        assertEquals(GlassContrast.LightTintLevel, light.glassLevel)
        // The light canvas is a shade off grey, so its limits sit three levels to the safe side of the old ones.
        // Every light surface still draws its old tint: see lightGlassNeedsNoSampler.
        assertEquals(GlassContrast.DarkTextLimit + 3f, light.glassLimit(GlassText.Content))
        assertEquals(GlassContrast.SecondaryDarkTextLimit + 3f, light.glassLimit(GlassText.Secondary))
        // On pure greys the measure is the brief's own.
        val grey = Color(0xFFF4F4F4)
        assertEquals(GlassContrast.DarkTextLimit, GlassContrast.limit(light.content, grey, light = true))
        assertEquals(GlassContrast.SecondaryDarkTextLimit, GlassContrast.limit(light.secondaryContent, grey, light = true))
    }

    @Test
    fun theRuleKeepsTheBriefsNumbers() {
        assertEquals(0.57f, GlassContrast.tintAlpha(0.40f, 255f, GlassContrast.WhiteTextLimit), 0.005f)
        assertEquals(0.69f, GlassContrast.tintAlpha(0.40f, 255f, GlassContrast.SecondaryTextLimit), 0.005f)
        assertEquals(0.40f, GlassContrast.tintAlpha(0.40f, 100f, GlassContrast.WhiteTextLimit), 0f)
    }

    @Test
    fun theRuleAppliesTheSameWayUnderALightTint() {
        val tint = GlassContrast.LightTintLevel
        val limit = GlassContrast.DarkTextLimit
        assertEquals(limit / tint, GlassContrast.tintAlpha(0.2f, 0f, limit, tint), 0.001f)
        assertEquals(0.2f, GlassContrast.tintAlpha(0.2f, 200f, limit, tint), 0f)
    }

    @Test
    fun lightLimitsAreTheLowestLevelsThatHold() {
        fun grey(level: Int) = Color(level, level, level)
        val strong = GlassContrast.DarkTextLimit.toInt()
        val soft = GlassContrast.SecondaryDarkTextLimit.toInt()
        assertReads("text at its limit", light.content, grey(strong))
        assertReads("secondary text at its limit", light.secondaryContent, grey(soft))
        assertTrue(GlassContrast.ratio(light.content, grey(strong - 1)) < GlassContrast.MinRatio)
        val below = light.secondaryContent.compositeOver(grey(soft - 1))
        assertTrue(GlassContrast.ratio(below, grey(soft - 1)) < GlassContrast.MinRatio)
    }

    @Test
    fun textAndIconsReadOnEverySurfaceOfEveryTheme() {
        for (palette in all) {
            for (surface in palette.surfaces()) {
                assertReads("${palette.name()} content", palette.content, surface)
                assertReads("${palette.name()} secondary", palette.secondaryContent, surface)
                assertReads("${palette.name()} error", palette.error, surface)
                assertReads("${palette.name()} warning", palette.warning, surface)
            }
        }
    }

    @Test
    fun textSwitchesBetweenLightAndDarkWithTheBackground() {
        for (palette in all) {
            val lightText = palette.content.luminance() > 0.5f
            assertEquals(palette.name(), !palette.isLight, lightText)
            assertEquals(palette.name(), palette.isLight, palette.canvas.luminance() > 0.179f)
        }
    }

    @Test
    fun everyAccentReadsAsTextOnEverySurfaceOfItsTheme() {
        for (palette in all) {
            for (surface in palette.surfaces()) {
                assertReads("${palette.name()} accent", palette.accent, surface)
            }
        }
    }

    @Test
    fun everyAccentCarriesTextOnItsPill() {
        for (palette in all) {
            assertReads("${palette.name()} pill text", palette.onAccent, palette.accentFill)
            assertReads("${palette.name()} pill secondary", palette.onAccentSecondary, palette.accentFill)
            assertReads("${palette.name()} media pill", palette.onMediaAccent, palette.mediaAccent)
            assertReads("${palette.name()} error pill", palette.onError, palette.error)
        }
    }

    @Test
    fun theSelectedChipKeepsItsText() {
        for (palette in all) {
            for (surface in listOf(palette.canvas, palette.elevated)) {
                val chip = palette.accent.copy(alpha = 0.12f).compositeOver(surface)
                assertReads("${palette.name()} chip", palette.accent, chip)
            }
        }
    }

    @Test
    fun rimsAndSelectionMarksShowOnEveryTheme() {
        for (palette in all) {
            assertReads("${palette.name()} hairline", palette.hairline, palette.canvas, 1.1f)
            assertReads("${palette.name()} outline", palette.outline, palette.canvas, 1.7f)
            assertReads("${palette.name()} soft outline", palette.outlineVariant, palette.canvas, 1.25f)
            assertReads("${palette.name()} track", palette.track, palette.canvas, 1.25f)
            // The ring and the check of a selected swatch, and a progress line on its track.
            for (surface in palette.surfaces()) {
                assertReads("${palette.name()} ring", palette.accent, surface, GlassContrast.MinLargeRatio)
            }
            val track = palette.track.compositeOver(palette.elevated)
            assertReads("${palette.name()} progress", palette.accent, track, GlassContrast.MinLargeRatio)
        }
    }

    @Test
    fun aPickedRowKeepsItsTextUnderTheAccentWash() {
        // Selection mode lays 12% of the accent over a whole list row, which sits on the canvas, and rims it.
        for (palette in all) {
            val row = palette.accent.copy(alpha = 0.12f).compositeOver(palette.canvas)
            assertReads("${palette.name()} text on a picked row", palette.content, row)
            assertReads("${palette.name()} secondary on a picked row", palette.secondaryContent, row)
            // The fact chips of a row are raised surfaces of their own and keep their text.
            assertReads("${palette.name()} chip on a picked row", palette.content, palette.elevated)
            assertReads("${palette.name()} row rim", palette.accent.copy(alpha = 0.56f), palette.canvas, 1.7f)
        }
    }

    @Test
    fun theCheckOfAPickedPictureShowsOnAnyPicture() {
        // The check is a disc of the accent fill inside a ring of the canvas colour, over a picture dimmed with 45%
        // of the canvas. The ring is what the disc is seen against, whatever the picture holds.
        for (palette in all) {
            assertReads("${palette.name()} check", palette.onAccent, palette.accentFill)
            val disc = GlassContrast.ratio(palette.accentFill, palette.canvas)
            val glyph = GlassContrast.ratio(palette.onAccent, palette.canvas)
            // A fill as light as a light canvas, amber on Light, is carried by its dark check.
            assertTrue("${palette.name()} disc $disc, glyph $glyph", maxOf(disc, glyph) >= GlassContrast.MinLargeRatio)
            // The rim of the picture has the canvas on its outer side, where it reads as text does. What it does
            // against the picture itself depends on the picture, and no theme can promise that.
            assertReads("${palette.name()} picture rim", palette.accent, palette.canvas)
        }
    }

    @Test
    fun marksOnArtReadOverTheirDarkScrims() {
        val scrim = Color.Black.copy(alpha = 0.6f).compositeOver(Color(127, 127, 127))
        for (palette in all) {
            assertReads("${palette.name()} line on art", palette.mediaAccent, scrim)
        }
    }

    @Test
    fun onLightThePrimaryPillIsDark() {
        assertEquals(Color(0xFF141414), light.accentFill)
        assertEquals(Color.White, light.onAccent)
    }

    @Test
    fun amberStaysAmberAsAFillOnLightAndTakesDarkText() {
        for (theme in listOf(FrameThemeId.Light, FrameThemeId.Paper)) {
            val amber = FramePalettes.of(theme, FrameAccent.Amber)
            assertEquals(amber.mediaAccent, amber.accentFill)
            assertEquals(Color(0xFF0D0D0D), amber.onAccent)
            assertTrue(amber.accent != amber.accentFill)
        }
    }

    @Test
    fun glassTakesTheColourOfItsTheme() {
        // The tint of every glass surface is the canvas, so a navy theme has navy glass. Its level is the canvas's.
        val navy = FramePalettes.of(FrameThemeId.Midnight)
        assertEquals(GlassContrast.level(navy.canvas), navy.glassLevel, 0.5f)
        assertTrue(navy.canvas.blue > navy.canvas.red)
        for (palette in all) {
            assertEquals(palette.name(), GlassContrast.level(palette.canvas), palette.glassLevel, 0.5f)
        }
    }

    @Test
    fun glassKeepsItsTextOverAnyBackdrop() {
        // The accent plays no part in glass, so one palette per theme is enough.
        val themes = FrameThemeId.entries.map { FramePalettes.of(it) } +
            customThemes.map { FramePalettes.of(FrameLook(custom = it)) }
        for (palette in themes) {
            for (kind in GlassKind.entries) {
                val recipe = kind.recipe(palette.isLight)
                val texts = when (recipe.text) {
                    GlassText.Content -> listOf(palette.content)
                    // The bar of selection mode: plain labels, and delete in the error colour.
                    GlassText.Danger -> listOf(palette.content, palette.error)
                    GlassText.Secondary, GlassText.Rows -> listOf(palette.secondaryContent)
                }
                for (backdrop in backdrops) {
                    val level = backdrop.red * 255f
                    // Only a sampler reads the real level. Others hold it at their worst backdrop.
                    val seen = if (recipe.sampled) level else recipe.worstLevel
                    val tint = palette.glassTint(recipe, seen)
                    val surface = palette.canvas.copy(alpha = tint).compositeOver(backdrop)
                    for (text in texts) assertReads("$kind ${palette.name()} over $level", text, surface)
                    if (kind == GlassKind.Sheet || kind == GlassKind.Menu) {
                        assertReads(
                            "$kind row ${palette.name()} over $level",
                            palette.secondaryContent,
                            palette.rowFill.compositeOver(surface),
                        )
                        // Below Android 13, and with no blur slot free, a sheet is the plain canvas.
                        assertReads(
                            "$kind flat row ${palette.name()}",
                            palette.secondaryContent,
                            palette.rowFill.compositeOver(palette.canvas),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theTabPillCarriesItsContentOverAnyBackdrop() {
        for (palette in all) {
            val recipe = GlassKind.TabBar.recipe(palette.isLight)
            for (backdrop in backdrops) {
                val seen = if (recipe.sampled) backdrop.red * 255f else recipe.worstLevel
                val tint = palette.glassTint(recipe, seen)
                val bar = palette.canvas.copy(alpha = tint).compositeOver(backdrop)
                val pill = palette.tabPill.compositeOver(bar)
                assertReads("${palette.name()} tab pill", palette.onTabPill, pill)
            }
        }
    }

    @Test
    fun lightGlassNeedsNoSampler() {
        for (kind in GlassKind.entries) {
            val recipe = kind.recipe(light = true)
            assertTrue(!recipe.sampled)
            assertEquals(0f, recipe.worstLevel)
            // On the plain light theme the base tint is already enough over pure black. The bar of actions is the
            // one surface that needs more, for its labels in the error colour, and holds that always.
            if (kind == GlassKind.ActionBar) {
                assertTrue(light.glassTint(recipe, 0f) > recipe.baseTint)
            } else {
                assertEquals("$kind", recipe.baseTint, light.glassTint(recipe, 0f))
            }
        }
    }

    @Test
    fun aCustomThemeRunsFromPureBlackToLight() {
        for (hue in 0 until 360 step 30) {
            assertEquals(Color.Black, FramePalettes.of(FrameLook(custom = CustomTheme(hue.toFloat(), 0f, 0.5f))).canvas)
            var last = -1f
            for (step in 0..200) {
                val theme = CustomTheme(hue.toFloat(), step / 200f, 0.5f)
                val canvas = FramePalettes.customSpec(theme).canvas
                val lightness = ThemeMath.lightness(canvas)
                assertTrue("hue $hue shade ${theme.shade} is $lightness after $last", lightness >= last - 0.6f)
                // What a shade asks for is what it gets, to the nearest step of 8-bit colour.
                assertEquals(FramePalettes.canvasLightness(theme.shade), lightness, 1.2f)
                assertEquals(theme.shade > CustomTheme.DarkShades, theme.isLight)
                last = lightness
            }
            assertTrue(last > 95f)
        }
    }

    @Test
    fun aShadeIsNeverRoundedAcrossTheStepBetweenDarkAndLight() {
        for (step in 0..4000) {
            val theme = CustomTheme(220f, step / 4000f, 0.5f)
            assertEquals("shade ${theme.shade}", theme.isLight, FramePalettes.of(FrameLook(custom = theme)).isLight)
        }
    }

    @Test
    fun theSurfaceSliderMovesCardsAwayFromTheCanvas() {
        val below = FramePalettes.of(FrameLook(custom = CustomTheme(220f, 0.25f, 0f)))
        val level = FramePalettes.of(FrameLook(custom = CustomTheme(220f, 0.25f, 1f / 3f)))
        val above = FramePalettes.of(FrameLook(custom = CustomTheme(220f, 0.25f, 1f)))
        fun lift(palette: FramePalette) = ThemeMath.lightness(palette.card) - ThemeMath.lightness(palette.canvas)
        assertEquals(-10f, lift(below), 1.5f)
        assertEquals(0f, lift(level), 1.5f)
        assertEquals(20f, lift(above), 1.5f)
        // Chips and fields never vanish into the canvas, wherever the slider is.
        for (palette in custom) {
            val gap = ThemeMath.lightness(palette.elevated) - ThemeMath.lightness(palette.canvas)
            assertTrue("${palette.name()} raised surface is $gap away", kotlin.math.abs(gap) >= 1.5f)
            // On a dark canvas the raised surface lies between the canvas and the cards, on whichever side they are.
            val cards = lift(palette)
            if (!palette.isLight && kotlin.math.abs(cards) >= 4f) {
                assertTrue("${palette.name()} raised $gap, cards $cards", gap * cards > 0f)
                assertTrue("${palette.name()} raised $gap, cards $cards", kotlin.math.abs(gap) <= kotlin.math.abs(cards) + 0.6f)
            }
        }
    }

    @Test
    fun theDefaultCustomThemeIsTheOneOfTheStoryboard() {
        // Blue-grey with a cyan accent: #1B2740 and #2A3855 on the board, to within a step or two of derivation.
        val palette = FramePalettes.of(FrameLook(custom = CustomTheme()))
        fun assertNear(wanted: Long, color: Color, tolerance: Float) {
            val target = Color(wanted)
            for ((a, b) in listOf(target.red to color.red, target.green to color.green, target.blue to color.blue)) {
                assertEquals("${hex(color)} for ${hex(target)}", a * 255f, b * 255f, tolerance)
            }
        }
        assertNear(0xFF1B2740, palette.canvas, 4f)
        assertNear(0xFF2A3855, palette.elevated, 6f)
        assertEquals(Color(0xFF3CD3E0), palette.accentFill)
        assertEquals(Color(CustomTheme.DefaultAccent), palette.accentFill)
    }

    @Test
    fun derivationIsDeterministic() {
        for (theme in customThemes) {
            val first = FramePalettes.customSpec(theme)
            val second = FramePalettes.customSpec(theme.copy())
            assertEquals(first.canvas, second.canvas)
            assertEquals(first.elevated, second.elevated)
            assertEquals(first.card, second.card)
            assertEquals(first.content, second.content)
            // Every derived colour is a whole 8-bit ARGB, so it is the same on any phone.
            for (color in listOf(first.canvas, first.elevated, first.card, first.content)) {
                assertEquals(color, Color(color.toArgb()))
            }
        }
        // Values that differ by less than the sliders can show give one palette.
        val a = FramePalettes.of(FrameLook(custom = CustomTheme(200f, 0.4f, 0.5f), accentArgb = 0x00FF8800))
        val near = CustomTheme(200.2f, 0.4001f, 0.5001f)
        val b = FramePalettes.of(FrameLook(custom = near, accentArgb = 0xFFFF8800.toInt()))
        assertSame(a, b)
        assertSame(
            FramePalettes.of(FrameLook(custom = CustomTheme(-160f, 0.4f, 0.5f))),
            FramePalettes.of(FrameLook(custom = CustomTheme(200f, 0.4f, 0.5f))),
        )
    }

    @Test
    fun anyLookGivesOnePalette() {
        for (theme in FrameThemeId.entries) {
            for (accent in listOf<FrameAccent?>(null) + FrameAccent.entries) {
                assertSame(FramePalettes.of(theme, accent), FramePalettes.of(theme, accent))
            }
        }
        // A theme whose own accent is the neutral one is that theme with White.
        assertSame(FramePalettes.of(FrameThemeId.Light), FramePalettes.of(FrameThemeId.Light, FrameAccent.White))
        val navy = FramePalettes.of(FrameThemeId.Midnight)
        assertNotSame(navy, FramePalettes.of(FrameThemeId.Midnight, FrameAccent.White))
        // The accent dots ask for the theme on screen with another accent, and get the one palette of that pair.
        val coffee = FramePalettes.of(FrameThemeId.Coffee)
        val red = FramePalettes.of(FrameThemeId.Coffee, FrameAccent.Red)
        assertSame(red, FramePalettes.withAccent(coffee, FrameAccent.Red))
        assertSame(coffee, FramePalettes.withAccent(red, null))
    }

    @Test
    fun followThePhonePicksTheMatchingSide() {
        fun shown(look: FrameLook, dark: Boolean) = look.copy(followSystem = true).shownWhen(dark)
        val navy = FrameLook(theme = FrameThemeId.Midnight, accent = FrameAccent.Red)
        assertEquals(navy, shown(navy, dark = true))
        assertEquals(FrameLook(theme = FrameThemeId.Light, accent = FrameAccent.Red), shown(navy, dark = false))
        val paper = FrameLook(theme = FrameThemeId.Paper)
        assertEquals(paper, shown(paper, dark = false))
        assertEquals(FrameLook(theme = FrameThemeId.Black), shown(paper, dark = true))
        val black = FrameLook()
        assertEquals(black, shown(black, dark = true))
        assertEquals(FrameLook(theme = FrameThemeId.Light), shown(black, dark = false))
        // A custom theme sits on the side its canvas belongs to.
        val deep = FrameLook(custom = CustomTheme(10f, 0.2f, 0.5f))
        assertEquals(deep, shown(deep, dark = true))
        assertEquals(FrameLook(theme = FrameThemeId.Light), shown(deep, dark = false))
        val pale = FrameLook(custom = CustomTheme(10f, 0.9f, 0.5f), accentArgb = 0xFF112233.toInt())
        assertEquals(pale, shown(pale, dark = false))
        assertEquals(FrameLook(theme = FrameThemeId.Black, accentArgb = 0xFF112233.toInt()), shown(pale, dark = true))
        // Without the switch a look is what it says, whatever the phone does.
        assertEquals(paper, paper.shownWhen(systemDark = true))
    }

    @Test
    fun aFadeEndsOnTheTargetAndTakesItsKindAtOnce() {
        val from = FramePalettes.of(FrameThemeId.Coffee)
        val to = FramePalettes.of(FrameThemeId.Paper)
        val start = from.toward(to, 0f)
        val end = from.toward(to, 1f)
        assertEquals(from.canvas.toArgb(), start.canvas.toArgb())
        assertEquals(to.canvas.toArgb(), end.canvas.toArgb())
        assertEquals(to.content.toArgb(), end.content.toArgb())
        assertEquals(to.accentFill.toArgb(), end.accentFill.toArgb())
        // Dark icons, recipes and limits switch with the first frame, so no frame mixes two kinds.
        assertTrue(start.isLight)
        assertEquals(to.glassLevel, start.glassLevel)
        assertEquals(to.glassLimit(GlassText.Rows), start.glassLimit(GlassText.Rows))
    }

    @Test
    fun aPickedColourGoesToTheFieldAndBack() {
        for (hue in 0 until 360 step 15) {
            for (saturation in listOf(0.1f, 0.5f, 1f)) {
                val (backHue, backSaturation) = accentHueSaturation(accentArgb(hue.toFloat(), saturation))
                assertEquals("hue of $hue at $saturation", hue.toFloat(), backHue, 1.5f)
                assertEquals(saturation, backSaturation, 0.01f)
            }
        }
        assertEquals(0f to 0f, accentHueSaturation(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), accentArgb(123f, 0f))
    }

    @Test
    fun plainReadsFollowTheWindowInFront() {
        val app = ThemeWindow(FramePalettes.of(FrameThemeId.Light, FrameAccent.Blue))
        val player = ThemeWindow(FramePalettes.Default)
        ThemeBridge.enter(app)
        assertSame(app.palette, ThemeBridge.palette)
        ThemeBridge.enter(player)
        assertSame(player.palette, ThemeBridge.palette)
        assertEquals(Color.White, FrameColors.White)
        ThemeBridge.enter(app)
        assertSame(app.palette, ThemeBridge.palette)
        ThemeBridge.leave(player)
        assertSame(app.palette, ThemeBridge.palette)
        ThemeBridge.leave(app)
        assertSame(FramePalettes.Default, ThemeBridge.palette)
    }

    @Test
    fun dropsBackToTheFrontWindowOnlyWhenColoursReallyDiffer() {
        val dark = ThemeWindow(FramePalettes.Default)
        val otherDark = ThemeWindow(FramePalettes.Default)
        val light = ThemeWindow(FramePalettes.of(FrameThemeId.Light))
        fun writesDuring(block: () -> Unit): Int {
            var writes = 0
            Snapshot.observe(writeObserver = { writes++ }, block = block)
            return writes
        }

        // The app and the player share a palette in the default dark look, so nothing ever recomposes.
        ThemeBridge.enter(dark)
        ThemeBridge.enter(otherDark)
        assertEquals(0, writesDuring { ThemeBridge.stopped(dark) })

        // A light app behind the dark player is repaired once, as it stops.
        ThemeBridge.enter(light)
        ThemeBridge.enter(dark)
        assertEquals(1, writesDuring { ThemeBridge.stopped(light) })

        // The window in front stopping is not a swap, and neither is a window with nothing new.
        assertEquals(0, writesDuring { ThemeBridge.stopped(dark) })
        assertEquals(0, writesDuring { ThemeBridge.published(dark) })

        // A theme change in the window in front tells readers once, and only once.
        dark.palette = FramePalettes.of(FrameThemeId.Midnight)
        assertEquals(1, writesDuring { ThemeBridge.published(dark) })
        assertEquals(0, writesDuring { ThemeBridge.published(dark) })

        ThemeBridge.leave(dark)
    }

    @Test
    fun aLookAskedForOnSeveralThreadsAtOnceIsOnePalette() {
        // A swatch works its palette out off the main thread while a slider is dragged, so two threads can ask
        // for the same look at the same moment. Neither waits for the other, and both get the same instance.
        val look = FrameLook(custom = CustomTheme(137f, 0.31f, 0.42f), accentArgb = 0xFF12AB34.toInt())
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val built = (1..8).map { pool.submit<FramePalette> { FramePalettes.of(look) } }.map { it.get() }
            built.forEach { assertSame(built.first(), it) }
            assertSame(built.first(), FramePalettes.of(look))
        } finally {
            pool.shutdown()
        }
    }
}
