package com.frameglass.glass

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private fun alexandria(weight: Int) = Font(
    resId = R.font.alexandria,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Alexandria = FontFamily(alexandria(500), alexandria(600), alexandria(700))

private val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.W400),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.W500),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.W600),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.W700),
)

// Letter spacing stays zero in both languages (brief 7.8). Trim.None keeps the room that
// Arabic diacritics and descenders need now that target 36 drops the compact metrics.
private fun style(family: FontFamily, size: TextUnit, line: TextUnit, weight: FontWeight) = TextStyle(
    fontFamily = family,
    fontSize = size,
    lineHeight = line,
    fontWeight = weight,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** The type scale from brief 7.7: Alexandria for titles, IBM Plex Sans Arabic for UI. */
object FrameType {
    /** 34 / 42 sp, 700. */
    val Display = style(Alexandria, 34.sp, 42.sp, FontWeight.W700)

    /** 24 / 32 sp, 700. */
    val TitleLarge = style(Alexandria, 24.sp, 32.sp, FontWeight.W700)

    /** 20 / 28 sp, 600. */
    val Title = style(Alexandria, 20.sp, 28.sp, FontWeight.W600)

    /** 15 / 24 sp, 400. */
    val Body = style(PlexArabic, 15.sp, 24.sp, FontWeight.W400)

    /** 13 / 18 sp, 500. */
    val Label = style(PlexArabic, 13.sp, 18.sp, FontWeight.W500)

    /** 12 / 16 sp, 400. */
    val Caption = style(PlexArabic, 12.sp, 16.sp, FontWeight.W400)
}
