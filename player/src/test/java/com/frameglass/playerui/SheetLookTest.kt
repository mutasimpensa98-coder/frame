package com.frameglass.playerui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.frameglass.glass.GlassContrast
import org.junit.Assert.assertTrue
import org.junit.Test

/** The worst case for an SDR frame: a sheet over pure white. HDR highlights can pass this and need the phone. */
class SheetLookTest {
    private val scrimOverWhite = Color.Black.copy(alpha = SheetLook.SCRIM_ALPHA).compositeOver(Color.White)
    private val rowOverWhite = Color.White.copy(alpha = SheetLook.ROW_FILL_ALPHA).compositeOver(scrimOverWhite)
    private val secondary = Color.White.copy(alpha = 0.70f)

    @Test
    fun whiteTextReadsOnARow() {
        assertTrue(GlassContrast.ratio(Color.White, rowOverWhite) >= GlassContrast.MinRatio)
    }

    @Test
    fun secondaryTextReadsOnARow() {
        val text = secondary.compositeOver(rowOverWhite)
        assertTrue(GlassContrast.ratio(text, rowOverWhite) >= GlassContrast.MinRatio)
    }

    @Test
    fun secondaryTextReadsOnTheBareScrim() {
        val text = secondary.compositeOver(scrimOverWhite)
        assertTrue(GlassContrast.ratio(text, scrimOverWhite) >= GlassContrast.MinRatio)
    }
}
