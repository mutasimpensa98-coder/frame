package com.frameglass.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule the player closes on: is the video that is playing among the files a delete just removed. */
class DeletedFilesTest {
    private val deleted = DeletedFiles(fileRowIds = setOf(7L, 9L), paths = setOf("/storage/emulated/0/Movies/Show/e2.mkv"))

    @Test
    fun thePlayingFileIsFoundByItsRow() {
        assertTrue(deleted.holds(fileRowId = 7L, uri = "/storage/emulated/0/Movies/Show/e1.mkv"))
        assertTrue(deleted.holds(fileRowId = 9L, uri = null))
    }

    @Test
    fun aVideoOpenedWithNoRowIsFoundByItsPath() {
        assertTrue(deleted.holds(fileRowId = null, uri = "/storage/emulated/0/Movies/Show/e2.mkv"))
    }

    @Test
    fun anotherVideoPlaysOn() {
        assertFalse(deleted.holds(fileRowId = 8L, uri = "/storage/emulated/0/Movies/Show/e3.mkv"))
        assertFalse(deleted.holds(fileRowId = null, uri = "/storage/emulated/0/Movies/Show/E2.mkv"))
    }

    @Test
    fun anOpenWithUriOrNothingPlayingNeverMatches() {
        assertFalse(deleted.holds(fileRowId = null, uri = "content://downloads/3"))
        assertFalse(deleted.holds(fileRowId = null, uri = null))
        assertFalse(DeletedFiles(emptySet(), emptySet()).holds(fileRowId = 7L, uri = "/a.mkv"))
    }
}
