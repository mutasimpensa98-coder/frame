package com.frameglass.playerui

import org.junit.Assert.assertEquals
import org.junit.Test

class CopiedSubtitlesTest {
    private val folder = "/app/files/player-subs/abc"
    private val original = "$folder/Invented Film.srt"
    private val retimed = "$folder/Invented Film.from_25_to_23976.srt"

    @Test
    fun theOriginalStaysBesideItsRetimedCopy() {
        assertEquals(listOf(retimed, original), CopiedSubtitles.extra(emptyList(), retimed, listOf(retimed, original)))
    }

    @Test
    fun sidecarsAreNotListedTwice() {
        val sidecar = "/storage/Movies/Invented Film.ar.srt"
        assertEquals(listOf(original), CopiedSubtitles.extra(listOf(sidecar), sidecar, listOf(original)))
    }

    @Test
    fun aChosenFileOutsideTheFolderStillComesFirst() {
        val online = "/app/files/online/Invented Film.srt"
        assertEquals(listOf(online, original), CopiedSubtitles.extra(emptyList(), online, listOf(original)))
    }

    @Test
    fun nothingCopiedAddsNothing() {
        assertEquals(emptyList<String>(), CopiedSubtitles.extra(emptyList(), null, emptyList()))
    }
}
