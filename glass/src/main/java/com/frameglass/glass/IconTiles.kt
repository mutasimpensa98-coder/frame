package com.frameglass.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The fill of one coloured icon tile, as in a phone's settings list. Each keeps a white glyph at 4.5:1 and stands
 * apart from the cards of both themes, so one set serves light and dark. IconTileTest holds them to it.
 */
enum class TileHue(val fill: Color) {
    Pink(Color(0xFFD6336C)),
    Blue(Color(0xFF2F6FDE)),
    Indigo(Color(0xFF6A4BE0)),
    Red(Color(0xFFD9342B)),
    Teal(Color(0xFF0B7F72)),
    Orange(Color(0xFFC2410C)),
    Violet(Color(0xFF9333C4)),
    Amber(Color(0xFFA16207)),
    Green(Color(0xFF26803A)),
    Sky(Color(0xFF0B78B5)),
    Grey(Color(0xFF5F6873)),
}

/** The glyph colour on every [TileHue]. */
val TileGlyph: Color = Color.White

/** A filled rounded square with a white glyph. */
@Composable
fun ColorIconTile(
    icon: ImageVector,
    hue: TileHue,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    glyph: Dp = 22.dp,
) {
    Box(
        modifier = modifier.size(size).background(hue.fill, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(glyph), tint = TileGlyph)
    }
}
