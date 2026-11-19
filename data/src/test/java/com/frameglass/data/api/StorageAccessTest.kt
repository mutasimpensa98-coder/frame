package com.frameglass.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** The Android 10 path to a card or drive: the mount point is what stands before the app folders. */
class StorageAccessTest {
    @Test
    fun theMountPointIsWhatStandsBeforeTheAppFolders() {
        assertEquals(
            File("/storage/1234-ABCD"),
            StorageAccess.mountPointOf("/storage/1234-ABCD/Android/data/com.example.player/files"),
        )
        assertEquals(
            File("/storage/emulated/0"),
            StorageAccess.mountPointOf("/storage/emulated/0/Android/data/com.example.player/files"),
        )
    }

    @Test
    fun aPathWithNoAppFolderHasNoMountPoint() {
        assertNull(StorageAccess.mountPointOf("/data/user/0/com.example.player/files"))
        assertNull(StorageAccess.mountPointOf("/Android/data/com.example.player/files"))
        assertNull(StorageAccess.mountPointOf(""))
    }
}
