package com.frameglass.playerui

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoGeometryTest {
    private val screenWidth = 3120
    private val screenHeight = 1440
    private val box = VideoGeometry.fit(screenWidth, screenHeight, 16f / 9f)

    private fun transform(mode: AspectMode, zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f) =
        VideoGeometry.transform(screenWidth, screenHeight, box, mode, zoom, panX, panY)

    @Test
    fun widescreenVideoIsPillarboxedOnThisPhone() {
        assertEquals(VideoBox(280, 0, 2560, 1440), box)
    }

    @Test
    fun fitIsIdentity() {
        assertEquals(VideoTransform(), transform(AspectMode.FIT))
    }

    @Test
    fun fillStretchesEachAxis() {
        val t = transform(AspectMode.FILL)
        assertEquals(screenWidth.toFloat() / 2560, t.scaleX, 0.001f)
        assertEquals(1f, t.scaleY, 0.001f)
    }

    @Test
    fun cropCoversTheScreenAndKeepsTheShape() {
        val t = transform(AspectMode.CROP)
        assertEquals(t.scaleX, t.scaleY, 0.001f)
        assertEquals(screenWidth.toFloat() / 2560, t.scaleX, 0.001f)
    }

    @Test
    fun panStopsAtTheVideoEdge() {
        val t = transform(AspectMode.FIT, zoom = 2f, panX = 99_999f, panY = -99_999f)
        assertEquals((2560 * 2f - screenWidth) / 2f, t.translationX, 0.001f)
        assertEquals(-(1440 * 2f - screenHeight) / 2f, t.translationY, 0.001f)
    }

    @Test
    fun noPanWithoutZoom() {
        assertEquals(0f, transform(AspectMode.FIT, panX = 500f).translationX, 0f)
    }

    private fun subtitles(mode: AspectMode, fit: VideoBox = box) =
        VideoGeometry.subtitleBox(screenWidth, screenHeight, fit, mode)

    @Test
    fun subtitlesShareTheFitBoxWhenAModeOnlyEnlargesThePicture() {
        assertEquals(box, subtitles(AspectMode.FIT))
        assertEquals(box, subtitles(AspectMode.FILL))
        assertEquals(box, subtitles(AspectMode.CROP))
        assertEquals(box, subtitles(AspectMode.RATIO_16_9))
    }

    @Test
    fun subtitlesFollowAPictureNarrowedByTheMode() {
        assertEquals(VideoBox(600, 0, 1920, 1440), subtitles(AspectMode.RATIO_4_3))
    }

    @Test
    fun subtitlesStayCentredOnTheScreenInEveryMode() {
        val wide = VideoGeometry.fit(screenWidth, screenHeight, 2.39f)
        for (fit in listOf(box, wide)) {
            for (mode in AspectMode.entries) {
                val shown = subtitles(mode, fit)
                assertEquals(screenWidth.toFloat(), (shown.left + shown.right).toFloat(), 1f)
                assertEquals(screenHeight.toFloat(), (shown.top + shown.bottom).toFloat(), 1f)
            }
        }
    }

    @Test
    fun thePictureInPictureWindowIsTheWholeBox() {
        val window = VideoGeometry.fit(640, 360, 16f / 9f)
        assertEquals(VideoBox(0, 0, 640, 360), window)
        assertEquals(window, VideoGeometry.subtitleBox(640, 360, window, AspectMode.FIT))
    }

    @Test
    fun pinchNearOneSnapsToOne() {
        assertEquals(1f, VideoGeometry.clampZoom(1.02f), 0f)
        assertEquals(4f, VideoGeometry.clampZoom(9f), 0f)
    }

    @Test
    fun memoryRoundTrips() {
        assertEquals(AspectMode.CROP, AspectMemory.decode(AspectMemory(AspectMode.CROP, 1f).encode()).mode)
        assertEquals(1.5f, AspectMemory.decode(AspectMemory(AspectMode.FIT, 1.5f).encode()).zoom, 0.001f)
        assertEquals(AspectMemory(AspectMode.FIT, 1f), AspectMemory.decode("garbage"))
        assertEquals(AspectMemory(AspectMode.FIT, 1f), AspectMemory.decode(null))
    }
}
