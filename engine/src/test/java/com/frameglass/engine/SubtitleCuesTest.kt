package com.frameglass.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCuesTest {
    private val srt = """
        1
        00:00:01,000 --> 00:00:03,500
        <i>Where were you last night?</i>

        2
        00:00:05,200 --> 00:00:06,000 X1:10 X2:20
        [door closes]

        3
        00:00:07,000 --> 00:00:09,000
        ♪ la la la ♪

        4
        00:00:10,000 --> 00:00:12,250
        - [sighs]
        - At the harbour, with Nadir.

        5
        00:00:12,000 --> 00:00:13,000
        Overlapping line.
    """.trimIndent()

    private val ass = """
        [Script Info]
        Title: An invented episode

        [V4+ Styles]
        Format: Name, Fontname, Fontsize
        Style: Default,Arial,20
        Style: Signs,Arial,20

        [Events]
        Format: Layer, Style, Start, End, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,Default,0:00:01.00,0:00:02.50,,0,0,0,,Hello, again, my friend
        Comment: 0,Default,0:00:03.00,0:00:04.00,,0,0,0,,A note for the typesetter
        Dialogue: 0,Signs,0:00:05.00,0:00:06.00,,0,0,0,,{\pos(10,10)}BAKERY
        Dialogue: 0,Default,0:00:07.00,0:00:08.00,,0,0,0,,{\k20}Ka{\k30}ra
        Dialogue: 0,Default,0:00:09.00,0:00:10.00,,0,0,0,,{\p1}m 0 0 l 10 0{\p0}
        Dialogue: 0,OP Romaji,0:00:11.00,0:00:12.00,,0,0,0,,Opening words
        Dialogue: 0,Default,1:02:03.45,1:02:05.00,,0,0,0,,{\i1}Last line\Nof the night
    """.trimIndent()

    @Test
    fun srtKeepsDialogueAndDropsSoundsAndSongs() {
        assertEquals(
            listOf(CueSpan(1_000, 3_500), CueSpan(10_000, 13_000)),
            SubtitleCues.spans(srt, SubtitleCues.Kind.SRT),
        )
    }

    @Test
    fun vttAllowsShortTimesAndSettings() {
        val vtt = "WEBVTT\n\n01:02.500 --> 01:04.000 align:start\nHi.\n\n00:01:10.000 --> 00:01:11.000\nBye.\n"
        assertEquals(listOf(CueSpan(62_500, 64_000), CueSpan(70_000, 71_000)), SubtitleCues.spans(vtt, SubtitleCues.Kind.VTT))
    }

    @Test
    fun assReadsItsOwnFormatAndSkipsWhatIsNotSpeech() {
        assertEquals(
            listOf(CueSpan(1_000, 2_500), CueSpan(3_723_450, 3_725_000)),
            SubtitleCues.spans(ass, SubtitleCues.Kind.ASS),
        )
    }

    @Test
    fun aWatermarkLineOverTheWholeVideoDoesNotSwallowTheDialogue() {
        val credit = "Dialogue: 0,Default,0:00:00.00,9:59:59.99,,0,0,0,,{\\an8}Made by an invented group\n"
        val events = "Format: Layer, Style, Start, End, Name, MarginL, MarginR, MarginV, Effect, Text\n"
        val withCredit = ass.replace(events, events + credit)
        assertTrue(withCredit.contains(credit))
        assertEquals(SubtitleCues.spans(ass, SubtitleCues.Kind.ASS), SubtitleCues.spans(withCredit, SubtitleCues.Kind.ASS))
    }

    @Test
    fun aCueEndingAtATypedHundredHoursIsDropped() {
        val typo = "$srt\n\n6\n00:20:00,000 --> 99:59:59,000\nThe last words.\n"
        assertEquals(SubtitleCues.spans(srt, SubtitleCues.Kind.SRT), SubtitleCues.spans(typo, SubtitleCues.Kind.SRT))
    }

    @Test
    fun aLongButRealCueStays() {
        val long = "1\n00:00:01,000 --> 00:00:15,000\nA long speech.\n"
        assertEquals(listOf(CueSpan(1_000, 15_000)), SubtitleCues.spans(long, SubtitleCues.Kind.SRT))
    }

    @Test
    fun kindsComeFromMimeTypes() {
        assertEquals(SubtitleCues.Kind.SRT, SubtitleCues.kindOf("application/x-subrip"))
        assertEquals(SubtitleCues.Kind.VTT, SubtitleCues.kindOf("text/vtt"))
        assertEquals(SubtitleCues.Kind.ASS, SubtitleCues.kindOf("text/x-ssa"))
        assertNull(SubtitleCues.kindOf("application/pgs"))
    }

    @Test
    fun retimedSrtScalesEveryTimeAndKeepsTheRest() {
        val source = "1\r\n00:00:10,000 --> 00:00:20,000\r\nOne, two.\r\n\r\n2\r\n01:00:00,000 --> 01:00:01,000 X1:5\r\nThree.\r\n"
        val out = SubtitleCues.retime(source, SubtitleCues.Kind.SRT, 1.5)
        assertEquals(
            "1\r\n00:00:15,000 --> 00:00:30,000\r\nOne, two.\r\n\r\n2\r\n01:30:00,000 --> 01:30:01,500 X1:5\r\nThree.\r\n",
            out,
        )
    }

    @Test
    fun retimedVttKeepsItsSettings() {
        val out = SubtitleCues.retime("WEBVTT\n\n01:02.500 --> 01:04.000 align:start\nHi.\n", SubtitleCues.Kind.VTT, 2.0)
        assertEquals("WEBVTT\n\n00:02:05.000 --> 00:02:08.000 align:start\nHi.\n", out)
    }

    @Test
    fun retimedAssChangesOnlyStartAndEnd() {
        val out = SubtitleCues.retime(ass, SubtitleCues.Kind.ASS, 2.0)
        val lines = out.lines()
        assertTrue(lines.contains("Dialogue: 0,Default,0:00:02.00,0:00:05.00,,0,0,0,,Hello, again, my friend"))
        assertTrue(lines.contains("Comment: 0,Default,0:00:06.00,0:00:08.00,,0,0,0,,A note for the typesetter"))
        assertTrue(lines.contains("Dialogue: 0,Default,2:04:06.90,2:04:10.00,,0,0,0,,{\\i1}Last line\\Nof the night"))
        assertTrue(lines.contains("Style: Default,Arial,20"))
        assertTrue(lines.contains("Title: An invented episode"))
    }

    @Test
    fun aRetimedFileReadsBackStretched() {
        val ratio = FrameRateFix.FROM_25_TO_23976.ratio
        val stretched = SubtitleCues.spans(SubtitleCues.retime(srt, SubtitleCues.Kind.SRT, ratio), SubtitleCues.Kind.SRT)
        val expected = SubtitleCues.spans(srt, SubtitleCues.Kind.SRT)
        assertEquals(expected.size, stretched.size)
        expected.zip(stretched).forEach { (before, after) ->
            assertEquals(before.startMs * ratio, after.startMs.toDouble(), 1.0)
            assertEquals(before.endMs * ratio, after.endMs.toDouble(), 1.0)
        }
    }

    @Test
    fun timesStayInLatinDigitsWhateverTheLocale() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            val out = SubtitleCues.retime("1\n00:00:01,000 --> 00:00:02,000\nA\n", SubtitleCues.Kind.SRT, 1.0)
            assertTrue(out, out.contains("00:00:01,000 --> 00:00:02,000"))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}
