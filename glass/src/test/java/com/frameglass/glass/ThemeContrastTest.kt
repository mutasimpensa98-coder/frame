package com.frameglass.glass

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    private val all = listOf(false, true).flatMap { light -> FrameAccent.entries.map { FramePalettes.of(light, it) } }
    private val backdrops = listOf(0, 64, 127, 191, 255).map { Color(it, it, it) }

    private fun FramePalette.name() = "${if (isLight) "light" else "dark"}/${accent.toArgb32()}"

    private fun Color.toArgb32() = Integer.toHexString((value shr 32).toInt())

    private fun assertReads(what: String, front: Color, back: Color) {
        val solid = if (front.alpha < 1f) front.compositeOver(back) else front
        val ratio = GlassContrast.ratio(solid, back)
        assertTrue("$what is $ratio:1", ratio >= GlassContrast.MinRatio)
    }

    @Test
    fun darkWithWhiteAccentIsTheLookFromBeforeThemes() {
        val dark = FramePalettes.of(light = false, FrameAccent.White)
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
    fun darkGlassRecipesAreTheOnesFromBeforeThemes() {
        fun check(kind: GlassKind, blur: Int, tint: Float, sampled: Boolean, limit: Float) {
            val recipe = kind.recipe(light = false)
            assertEquals(blur.dp, recipe.blur)
            assertEquals(tint, recipe.baseTint)
            assertEquals(sampled, recipe.sampled)
            assertEquals(limit, recipe.textLimit)
            assertEquals(13f, recipe.tintLevel)
            assertEquals(255f, recipe.worstLevel)
        }
        check(GlassKind.TabBar, 24, 0.40f, sampled = true, GlassContrast.WhiteTextLimit)
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
        val light = FramePalettes.of(light = true, FrameAccent.White)
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
            for (surface in listOf(palette.canvas, palette.elevated, palette.card)) {
                assertReads("${palette.name()} content", palette.content, surface)
                assertReads("${palette.name()} secondary", palette.secondaryContent, surface)
                assertReads("${palette.name()} error", palette.error, surface)
                assertReads("${palette.name()} warning", palette.warning, surface)
            }
        }
    }

    @Test
    fun everyAccentReadsAsTextOnEverySurfaceOfItsTheme() {
        for (palette in all) {
            for (surface in listOf(palette.canvas, palette.elevated, palette.card)) {
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
    fun marksOnArtReadOverTheirDarkScrims() {
        val scrim = Color.Black.copy(alpha = 0.6f).compositeOver(Color(127, 127, 127))
        for (palette in all) {
            assertReads("${palette.name()} line on art", palette.mediaAccent, scrim)
        }
    }

    @Test
    fun onLightThePrimaryPillIsDark() {
        val pill = FramePalettes.of(light = true, FrameAccent.White)
        assertEquals(Color(0xFF141414), pill.accentFill)
        assertEquals(Color.White, pill.onAccent)
    }

    @Test
    fun amberStaysAmberAsAFillOnLightAndTakesDarkText() {
        val amber = FramePalettes.of(light = true, FrameAccent.Amber)
        assertEquals(amber.mediaAccent, amber.accentFill)
        assertEquals(Color(0xFF0D0D0D), amber.onAccent)
        assertTrue(amber.accent != amber.accentFill)
    }

    @Test
    fun glassKeepsItsTextOverAnyBackdrop() {
        for (light in listOf(false, true)) {
            val palette = FramePalettes.of(light, FrameAccent.White)
            for (kind in GlassKind.entries) {
                val recipe = kind.recipe(light)
                val soft = recipe.textLimit == GlassContrast.SecondaryTextLimit ||
                    recipe.textLimit == GlassContrast.SecondaryDarkTextLimit
                val text = if (soft) palette.secondaryContent else palette.content
                for (backdrop in backdrops) {
                    val level = backdrop.red * 255f
                    // Only a sampler reads the real level. Others hold it at their worst backdrop.
                    val seen = if (recipe.sampled) level else recipe.worstLevel
                    val tint = GlassContrast.tintAlpha(recipe.baseTint, seen, recipe.textLimit, recipe.tintLevel)
                    val surface = palette.canvas.copy(alpha = tint).compositeOver(backdrop)
                    assertReads("$kind light=$light over $level", text, surface)
                    if (kind == GlassKind.Sheet || kind == GlassKind.Menu) {
                        assertReads(
                            "$kind row light=$light over $level",
                            palette.secondaryContent,
                            palette.rowFill.compositeOver(surface),
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
                val tint = GlassContrast.tintAlpha(recipe.baseTint, seen, recipe.textLimit, recipe.tintLevel)
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
            assertEquals(recipe.baseTint, GlassContrast.tintAlpha(recipe.baseTint, 0f, recipe.textLimit, recipe.tintLevel))
        }
    }

    @Test
    fun plainReadsFollowTheWindowInFront() {
        val app = ThemeWindow(FramePalettes.of(light = true, FrameAccent.Blue))
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
        val light = ThemeWindow(FramePalettes.of(light = true, FrameAccent.White))
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
        dark.palette = FramePalettes.of(light = true, FrameAccent.Blue)
        assertEquals(1, writesDuring { ThemeBridge.published(dark) })
        assertEquals(0, writesDuring { ThemeBridge.published(dark) })

        ThemeBridge.leave(dark)
    }

    @Test
    fun anyAccentAndThemeGivesOnePalette() {
        for (light in listOf(false, true)) {
            for (accent in FrameAccent.entries) {
                assertSame(FramePalettes.of(light, accent), FramePalettes.of(light, accent))
            }
        }
    }
}
