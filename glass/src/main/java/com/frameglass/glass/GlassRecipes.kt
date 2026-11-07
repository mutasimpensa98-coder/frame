package com.frameglass.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

// The worst backdrop for surfaces with no sampler to tell them better: pure white under a dark tint,
// pure black under a light one.
private const val BrightestLevel = 255f
private const val DarkestLevel = 0f

// Tuned up from the brief's 0.52, which let bright art fight the text of a whole sheet.
private const val PanelTint = 0.76f

internal class GlassRecipe(
    val priority: Int,
    val blur: Dp,
    val baseTint: Float,
    val rimWidth: Dp = 1.dp,
    val rimTop: Color = Color.White.copy(alpha = 0.18f),
    val rimBottom: Color = Color.White.copy(alpha = 0.04f),
    val bottomRim: Color = Color.White.copy(alpha = 0.08f),
    val shadow: Shadow? = null,
    val refraction: Boolean = false,
    val textLimit: Float = GlassContrast.WhiteTextLimit,
    val tintLevel: Float = GlassContrast.TintLevel,
    val worstLevel: Float = BrightestLevel,
    /** Follows the real backdrop through [GlassSampler]; other kinds assume [worstLevel]. */
    val sampled: Boolean = false,
    val bottomRimOnly: Boolean = false,
    /** Turns opaque when nothing can be blurred, so a whole page of text never sits over bare content. */
    val solidWithoutBlur: Boolean = false,
)

private val TabBarRecipe = GlassRecipe(
    priority = 2,
    blur = 24.dp,
    baseTint = 0.40f,
    shadow = Shadow(radius = 24.dp, color = Color.Black, offset = DpOffset(0.dp, 8.dp), alpha = 0.40f),
    refraction = true,
    sampled = true,
)
private val TopBarRecipe = GlassRecipe(
    priority = 3,
    blur = 24.dp,
    baseTint = 0.40f,
    sampled = true,
    bottomRimOnly = true,
)
private val PanelShadow =
    Shadow(radius = 32.dp, color = Color.Black, offset = DpOffset(0.dp, 12.dp), alpha = 0.45f)

// Blur reads the sources directly, so a sheet's scrim does not dim what shows through the sheet itself.
private val SheetRecipe = GlassRecipe(
    priority = 0,
    blur = 32.dp,
    baseTint = PanelTint,
    shadow = PanelShadow,
    refraction = true,
    textLimit = GlassContrast.SecondaryTextLimit,
    solidWithoutBlur = true,
)
private val MenuRecipe = GlassRecipe(
    priority = 1,
    blur = 32.dp,
    baseTint = PanelTint,
    shadow = PanelShadow,
    refraction = true,
    textLimit = GlassContrast.SecondaryTextLimit,
    solidWithoutBlur = true,
)
private val ControlRecipe = GlassRecipe(
    priority = 4,
    blur = 16.dp,
    baseTint = 0.46f,
    rimWidth = 0.5.dp,
    rimTop = Color.White.copy(alpha = 0.16f),
    rimBottom = Color.White.copy(alpha = 0.16f),
)
private val BadgeRecipe = GlassRecipe(
    priority = 5,
    blur = 16.dp,
    baseTint = 0.46f,
    rimWidth = 0.5.dp,
    rimTop = Color.White.copy(alpha = 0.16f),
    rimBottom = Color.White.copy(alpha = 0.16f),
)

// Light recipes. A bright edge at the top and a dark hairline at the bottom take the place of the white
// rim, which would vanish on the canvas. Each base tint already clears the contrast rule over pure
// black, so nothing here needs the sampler, and the light theme pays nothing for it.
private val LightRimTop = Color.White.copy(alpha = 0.72f)
private val LightRimBottom = Color.Black.copy(alpha = 0.06f)
private val LightEdge = Color.Black.copy(alpha = 0.10f)
private val LightBottomRim = Color.Black.copy(alpha = 0.08f)
private val LightBarShadow =
    Shadow(radius = 24.dp, color = Color.Black, offset = DpOffset(0.dp, 8.dp), alpha = 0.14f)
private val LightPanelShadow =
    Shadow(radius = 32.dp, color = Color.Black, offset = DpOffset(0.dp, 12.dp), alpha = 0.18f)

private const val LightBarTint = 0.66f
private const val LightTopBarTint = 0.70f
private const val LightPanelTint = 0.84f
private const val LightSmallTint = 0.74f

private val LightTabBarRecipe = GlassRecipe(
    priority = 2,
    blur = 24.dp,
    baseTint = LightBarTint,
    rimTop = LightRimTop,
    rimBottom = LightRimBottom,
    shadow = LightBarShadow,
    refraction = true,
    textLimit = GlassContrast.DarkTextLimit,
    tintLevel = GlassContrast.LightTintLevel,
    worstLevel = DarkestLevel,
)
private val LightTopBarRecipe = GlassRecipe(
    priority = 3,
    blur = 24.dp,
    baseTint = LightTopBarTint,
    bottomRim = LightBottomRim,
    textLimit = GlassContrast.DarkTextLimit,
    tintLevel = GlassContrast.LightTintLevel,
    worstLevel = DarkestLevel,
    bottomRimOnly = true,
)
private val LightSheetRecipe = GlassRecipe(
    priority = 0,
    blur = 32.dp,
    baseTint = LightPanelTint,
    rimTop = LightRimTop,
    rimBottom = LightRimBottom,
    shadow = LightPanelShadow,
    refraction = true,
    textLimit = GlassContrast.SecondaryDarkTextLimit,
    tintLevel = GlassContrast.LightTintLevel,
    worstLevel = DarkestLevel,
    solidWithoutBlur = true,
)
private val LightMenuRecipe = GlassRecipe(
    priority = 1,
    blur = 32.dp,
    baseTint = LightPanelTint,
    rimTop = LightRimTop,
    rimBottom = LightRimBottom,
    shadow = LightPanelShadow,
    refraction = true,
    textLimit = GlassContrast.SecondaryDarkTextLimit,
    tintLevel = GlassContrast.LightTintLevel,
    worstLevel = DarkestLevel,
    solidWithoutBlur = true,
)
private val LightControlRecipe = GlassRecipe(
    priority = 4,
    blur = 16.dp,
    baseTint = LightSmallTint,
    rimWidth = 0.5.dp,
    rimTop = LightEdge,
    rimBottom = LightEdge,
    textLimit = GlassContrast.SecondaryDarkTextLimit,
    tintLevel = GlassContrast.LightTintLevel,
    worstLevel = DarkestLevel,
)
private val LightBadgeRecipe = GlassRecipe(
    priority = 5,
    blur = 16.dp,
    baseTint = LightSmallTint,
    rimWidth = 0.5.dp,
    rimTop = LightEdge,
    rimBottom = LightEdge,
    textLimit = GlassContrast.SecondaryDarkTextLimit,
    tintLevel = GlassContrast.LightTintLevel,
    worstLevel = DarkestLevel,
)

internal fun GlassKind.recipe(light: Boolean): GlassRecipe = when (this) {
    GlassKind.TabBar -> if (light) LightTabBarRecipe else TabBarRecipe
    GlassKind.TopBar -> if (light) LightTopBarRecipe else TopBarRecipe
    GlassKind.Sheet -> if (light) LightSheetRecipe else SheetRecipe
    GlassKind.Menu -> if (light) LightMenuRecipe else MenuRecipe
    GlassKind.Badge -> if (light) LightBadgeRecipe else BadgeRecipe
    GlassKind.Control -> if (light) LightControlRecipe else ControlRecipe
}
