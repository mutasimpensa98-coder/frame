package com.frameglass.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Golden names for clip detection. The whole phone is scanned, so these sit next to real films and series. */
class ClipNamesTest {
    private fun clips(vararg paths: String) = paths.forEach { path ->
        assertTrue(NameParser.parse(path).clipLike, "<$path> should be a clip")
    }

    private fun notClips(vararg paths: String) = paths.forEach { path ->
        assertFalse(NameParser.parse(path).clipLike, "<$path> should not be a clip")
    }

    @Test
    fun cameraNames() = clips(
        "VID_20240101_120000.mp4",
        "VID20240101120000.mp4",
        "IMG_20240101_120000.mp4",
        "IMG_0123.MOV",
        "IMG_E0123.MOV",
        "PXL_20240101_120000000.mp4",
        "PXL_20240101_120000000.TS.mp4",
        "MVIMG_20240101_120000.mp4",
        "SVID_20240101_120000_1.mp4",
        "DSC_0123.MOV",
        "MVI_0123.MOV",
        "MAH00123.MP4",
        "C0001.MP4",
        "GOPR0123.MP4",
        "GH010123.MP4",
        "GX010123.MP4",
        "DJI_0123.MP4",
    )

    @Test
    fun screenRecordings() = clips(
        "Screenrecorder-2024-01-01-12-00-00-123.mp4",
        "Screen_Recording_20240101_120000_Chrome.mp4",
        "Screen Recording 2024-01-01 at 12.00.00.mov",
        "screen-20240101-120000.mp4",
        "Screencast_20240101_120000.mp4",
        "XRecorder_01012024_120000.mp4",
        "AZRecorder_20240101_120000.mp4",
    )

    @Test
    fun socialAppNames() = clips(
        "VID-20240101-WA0001.mp4",
        "WhatsApp Video 2024-01-01 at 12.00.00.mp4",
        "WhatsApp Video 2024-01-01 at 12.00.00 (1).mp4",
        "signal-2024-01-01-120000.mp4",
        "Snapchat-123456789.mp4",
        "InShot_20240101_120000000.mp4",
        "CapCut_20240101_120000.mp4",
        "VN20240101_120000.mp4",
        "received_123456789012345.mp4",
        "FB_VID_1234567890.mp4",
        "Messenger_creation_abc.mp4",
        "tiktok_7123456789.mp4",
        "ssstik.io_1234567.mp4",
        "video_2024-01-01_12-00-00.mp4",
        "video_20240101_120000.mp4",
        "Video 2024-01-01.mp4",
    )

    @Test
    fun arabicPhoneNames() = clips(
        "فيديو واتساب بتاريخ 2024-01-01 في 12.00.00.mp4",
        "فيديو واتساب بتاريخ ٢٠٢٤-٠١-٠١ في ١٢.٠٠.٠٠.mp4",
        "تسجيل الشاشة 2024-01-01.mp4",
    )

    @Test
    fun namesThatAreOnlyANumberADateOrAHash() = clips(
        "1234567890.mp4",
        "12345.mkv",
        "5_6123456789012345.mp4",
        "123456789_987654321_1234567890123456789_n.mp4",
        "123.mp4",
        "1.mp4",
        "20240101.mp4",
        "20240101_120000.mp4",
        "20240101_120000(0).mp4",
        "2024-01-01.mp4",
        "2024-01-01 12.00.00.mp4",
        "2024-01-01 at 12.00.00.mp4",
        "01-01-2024.mp4",
        "a1b2c3d4e5f60718.mp4",
        "3f2504e0-4f89-11d3-9a0c-0305e82c3301.mp4",
        "0123456789abcdef0123456789abcdef.mp4",
        "v12044gd0000cabc123def456ghi789jkl.mp4",
    )

    @Test
    fun aGenericWordWithNoTitleFolder() = clips("video.mp4", "Video (1).mp4", "Download/video.mp4", "فيديو.mp4")

    @Test
    fun clipFolders() = clips(
        "DCIM/Camera/Birthday.mp4",
        "DCIM/Family trip.mp4",
        "Pictures/Screenshots/Clip of the game.mp4",
        "DCIM/Screen recordings/Tutorial.mp4",
        "WhatsApp/Media/WhatsApp Video/Funny cat.mp4",
        "Media/WhatsApp Video/Sent/Funny cat.mp4",
        "Movies/Instagram/Sunset.mp4",
        "Movies/Messenger/Sunset.mp4",
        "Movies/TikTok/Dance.mp4",
        "Pictures/Snapchat/Dance.mp4",
    )

    @Test
    fun aSeasonAndAnEpisodeAreNeverAClip() = notClips(
        "Sample.Journey.S01E02.1080p.WEB-DL.mkv",
        "DCIM/Camera/Sample.Journey.S01E02.mkv",
        "WhatsApp/Media/WhatsApp Video/Sample.Journey.S01E02.mkv",
        "Show 2x07.mp4",
        "VID_S01E02.mp4",
        "Show/Season 02/07.mkv",
        "Show/Season 02/07.mp4",
        "العنوان الموسم الثاني الحلقة 12 مترجم.mkv",
    )

    @Test
    fun aYearAndATitleAreNeverAClip() = notClips(
        "Quiet.Coast.2026.BluRay.x265.mkv",
        "DCIM/Camera/Quiet.Coast.2026.mp4",
        "Movies/Instagram/Quiet Coast (2026).mp4",
        "1917.2019.mkv",
        "1408.2007.mkv",
        "1917 2019.mp4",
        "فيلم العنوان 2023 مترجم.mp4",
    )

    @Test
    fun aNumberInATitleFolderStaysAnEpisode() {
        notClips("Frieren/05.mkv", "Frieren/05.mp4")
        assertEquals(5, NameParser.parse("Frieren/05.mp4").episode)
    }

    @Test
    fun filmsNamedByANumberStayFilms() = notClips(
        "300.mkv",
        "1917.mkv",
        "2012.mkv",
        "1917.mp4",
        "Movies/1917.mkv",
        "Downloads/300.mkv",
        "2012 - 05 [1080p].mkv",
        "300 (2006) [1080p].mkv",
    )

    @Test
    fun realNamesStayOutOfClips() = notClips(
        "The Matrix.mkv",
        "Video Killed the Radio Star.mkv",
        "Signal 7.mkv",
        "Record 2.mp4",
        "REC 2007.mkv",
        "Camera Buff.mkv",
        "Downloads/My Trip.mp4",
        "Telegram/Telegram Video/The Matrix.mp4",
        "Telegram Video/Sample Journey E05.mp4",
        "The Messenger (2009)/The.Messenger.mkv",
        "Trip/video.mp4",
        "Quiet Coast 2026/Movie.mkv",
        "[SubsPlease] Frieren - 05 (1080p) [ABCD1234].mkv",
        "One Piece - 1071 [1080p].mkv",
        "مسلسل الاختيار الحلقة 07.mp4",
        "Some_Movie_1080p_x265_10bit_AAC.mkv",
    )

    @Test
    fun aCounterIsNotAnEpisode() {
        val p = NameParser.parse("IMG_0123.MOV")
        assertTrue(p.clipLike)
        assertEquals(NameKind.MOVIE, p.kind)
        assertEquals("IMG 0123", p.title)
        assertNull(p.episode)
        assertNull(p.absolute)
        assertFalse(p.animeHint)
    }

    @Test
    fun aDateIsNotAYear() {
        val p = NameParser.parse("video_2024-01-01_12-00-00.mp4")
        assertTrue(p.clipLike)
        assertEquals("video 2024-01-01 12-00-00", p.title)
        assertNull(p.year)
        assertNull(p.season)
    }

    @Test
    fun aClipInADatedFolderIsNotASeason() {
        val p = NameParser.parse("Camera/2024/01/VID_20240101_120000.mp4")
        assertTrue(p.clipLike)
        assertNull(p.season)
        assertEquals(NameKind.MOVIE, p.kind)
    }
}
