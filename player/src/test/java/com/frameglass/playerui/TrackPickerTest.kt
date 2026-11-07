package com.frameglass.playerui

import com.frameglass.data.api.TrackPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackPickerTest {
    private fun row(
        group: Int,
        language: String?,
        label: String? = null,
        forced: Boolean = false,
        sidecar: String? = null,
        supported: Boolean = true,
    ) = TrackRow(
        group = group,
        track = 0,
        language = language,
        label = label,
        codec = null,
        channels = 0,
        forced = forced,
        signs = TrackPicker.looksLikeSigns(label),
        sidecarPath = sidecar,
        isDefault = false,
        selected = false,
        supported = supported,
    )

    private fun picked(rows: List<TrackRow>, prefs: TrackPrefs = TrackPrefs()) =
        (TrackPicker.subtitle(rows, prefs) as? SubtitleChoice.Track)?.row

    @Test
    fun fullArabicOutranksForcedAndSigns() {
        val rows = listOf(row(0, "ar", "Signs"), row(1, "ar", forced = true), row(2, "ar", "Full"))
        assertEquals(2, picked(rows)?.group)
    }

    @Test
    fun arabicSidecarOutranksEmbeddedArabic() {
        val rows = listOf(row(0, "ar"), row(1, "ar", sidecar = "/a.ar.srt"))
        assertEquals(1, picked(rows)?.group)
    }

    @Test
    fun onlySignsArabicIsStillArabic() {
        val rows = listOf(row(0, "en"), row(1, "ar", "Signs & Songs"))
        assertEquals(1, picked(rows)?.group)
    }

    @Test
    fun unnamedSidecarIsUsedWhenNoArabicExists() {
        val rows = listOf(row(0, "en"), row(1, null, sidecar = "/a.srt"))
        assertEquals(1, picked(rows)?.group)
    }

    @Test
    fun noArabicAndNoSidecarKeepsTheDefault() {
        assertEquals(SubtitleChoice.Keep, TrackPicker.subtitle(listOf(row(0, "en")), TrackPrefs()))
    }

    @Test
    fun rememberedOffWins() {
        assertEquals(SubtitleChoice.Off, TrackPicker.subtitle(listOf(row(0, "ar")), TrackPrefs(subtitlesOff = true)))
    }

    @Test
    fun rememberedFileWins() {
        val rows = listOf(row(0, "ar"), row(1, "en", sidecar = "/b.srt"))
        assertEquals(1, picked(rows, TrackPrefs(subtitleUri = "/b.srt"))?.group)
    }

    @Test
    fun memoryRestoresTheSecondTrackOfALanguage() {
        val rows = listOf(row(0, "en"), row(1, "ar"), row(2, "ar"))
        val stored = TrackMemory.encode(rows, rows[2])
        assertEquals(2, picked(rows, TrackPrefs(subtitleLanguage = stored))?.group)
    }

    @Test
    fun memoryFallsBackToTheLanguageInAnotherFile() {
        val stored = "ar#3"
        val rows = listOf(row(0, "en"), row(1, "ar"))
        assertEquals(1, picked(rows, TrackPrefs(subtitleLanguage = stored))?.group)
    }

    @Test
    fun audioMemoryIgnoresUnsupportedTracks() {
        val rows = listOf(row(0, "ja", supported = false), row(1, "ja"), row(2, "en"))
        assertEquals(1, TrackPicker.audio(rows, TrackPrefs(audioLanguage = "ja#0"))?.group)
    }
}
