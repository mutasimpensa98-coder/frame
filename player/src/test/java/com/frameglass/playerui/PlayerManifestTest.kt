package com.frameglass.playerui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Any change left out here recreates the activity, so a language or theme change would touch the video surface. */
class PlayerManifestTest {
    private val handled = Regex("""android:configChanges="([^"]+)"""")
        .find(File("src/main/AndroidManifest.xml").readText())!!
        .groupValues[1]
        .split('|')
        .toSet()

    @Test
    fun theActivityHandlesEveryChangeItself() {
        val needed = setOf(
            "mcc", "mnc", "locale", "touchscreen", "keyboard", "keyboardHidden", "navigation", "orientation",
            "screenLayout", "uiMode", "screenSize", "smallestScreenSize", "layoutDirection", "fontScale",
            "fontWeightAdjustment", "density", "colorMode", "grammaticalGender", "assetsPaths",
        )
        assertEquals(emptySet<String>(), needed - handled)
    }
}
