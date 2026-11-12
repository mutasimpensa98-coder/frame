package com.frameglass.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CueCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val unset = Long.MIN_VALUE + 1

    @Test
    fun cuesComeBackAsWritten() {
        val dir = folder.newFolder("cues")
        val entries = listOf(
            CueCache.Entry(1_000_000, 2_500_000, "Hello\nthere"),
            CueCache.Entry(3_000_000, unset, "‏مرحبا S01"),
            CueCache.Entry(4_000_000, 5_000_000, ""),
        )
        val file = CueCache.fileFor(dir, "/videos/one.mkv|123|456#2")
        assertTrue(CueCache.write(file, entries))
        assertEquals(entries, CueCache.read(file))
    }

    @Test
    fun theNameHidesThePathAndDependsOnTheVersion() {
        val dir = folder.newFolder("names")
        val name = CueCache.fileFor(dir, "/videos/one.mkv|123|456#2").name
        assertFalse(name.contains("one"))
        assertFalse(name == CueCache.fileFor(dir, "/videos/one.mkv|123|457#2").name)
        assertFalse(name == CueCache.fileFor(dir, "/videos/one.mkv|123|456#3").name)
        assertTrue(name.matches(Regex("[0-9a-f]+\\.cues")))
    }

    @Test
    fun aMissingOrForeignFileIsAMiss() {
        val dir = folder.newFolder("miss")
        assertNull(CueCache.read(CueCache.fileFor(dir, "nothing")))
        val foreign = CueCache.fileFor(dir, "foreign").apply { writeText("not ours") }
        assertNull(CueCache.read(foreign))
    }

    @Test
    fun aCutFileIsAMiss() {
        val dir = folder.newFolder("cut")
        val file = CueCache.fileFor(dir, "cut")
        CueCache.write(file, List(50) { CueCache.Entry(it * 1_000L, it * 1_000L + 500, "line $it") })
        file.writeBytes(file.readBytes().copyOf(40))
        assertNull(CueCache.read(file))
    }

    @Test
    fun trimDropsTheLeastRecentlyUsedFirst() {
        val dir = folder.newFolder("trim")
        val big = "x".repeat(10_000)
        val files = (0 until 4).map { index ->
            CueCache.fileFor(dir, "video $index").also {
                CueCache.write(it, listOf(CueCache.Entry(0, 1, big)))
                it.setLastModified(1_000_000L + index * 10_000L)
            }
        }
        // Reading the oldest makes it the newest.
        CueCache.read(files[0])
        CueCache.trim(dir, maxBytes = 2 * files[1].length() + 100)
        assertTrue(files[0].exists())
        assertTrue(files[3].exists())
        assertFalse(files[1].exists())
        assertFalse(files[2].exists())
    }

    @Test
    fun theReadIsPacedToTheCap() {
        val pace = ReadPace(bytesPerSecond = 1_000_000)
        assertEquals(0L, pace.waitMs(bytes = 500_000, elapsedMs = 600))
        assertEquals(500L, pace.waitMs(bytes = 1_000_000, elapsedMs = 500))
        assertEquals(2_000L, pace.waitMs(bytes = 2_000_000, elapsedMs = 0))
    }
}
