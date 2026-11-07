package com.frameglass.player.ui

import com.frameglass.data.api.AccentColor
import com.frameglass.data.api.ThemeMode
import com.frameglass.glass.FrameAccent
import com.frameglass.glass.FrameThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CommonTest {
    @Test
    fun aPathShortensToItsLastTwoFolders() {
        assertEquals("Movies/Old", shortPath("/storage/emulated/0/Movies/Old"))
        assertEquals("Movies", shortPath("/Movies"))
        assertEquals("", shortPath(""))
    }

    @Test
    fun everyThemeAndAccentMapsToItsOwnName() {
        assertEquals(
            listOf(FrameThemeMode.System, FrameThemeMode.Dark, FrameThemeMode.Light),
            ThemeMode.entries.map { it.toFrame() },
        )
        assertEquals(AccentColor.entries.map { it.name }, AccentColor.entries.map { it.toFrame().name.uppercase() })
        assertEquals(FrameAccent.entries.size, AccentColor.entries.size)
    }
}
