package com.frameglass.data.scan

import android.content.Context
import android.provider.MediaStore
import android.util.Log

/** A folder that holds videos, with the durations MediaStore already knows, by file name. */
internal class SeedFolder(val relDir: String, val durations: Map<String, Long>)

/**
 * The fast first pass. MediaStore answers in milliseconds, so the folders it names are read first, newest
 * first, and their videos appear within seconds. It only points the way: the disk listing still decides what
 * exists, which also brings in the sidecars and any video MediaStore skipped in that folder. It reads the
 * Files table because that is the one All Files access is documented to open.
 */
internal class MediaIndex(private val context: Context) {
    /** Folders per volume uuid. Empty on any failure, because the walk finds everything anyway, only later. */
    fun folders(mounted: List<MountedVolume>): Map<String, List<SeedFolder>> {
        val volumes = mounted.associateBy { it.mediaStoreName }
        val found = LinkedHashMap<String, LinkedHashMap<String, HashMap<String, Long>>>()
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                COLUMNS,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}",
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val volume = volumes[cursor.getString(VOLUME)] ?: continue
                    val name = cursor.getString(NAME) ?: continue
                    val relDir = cursor.getString(FOLDER).orEmpty().trim('/')
                    val durations = found.getOrPut(volume.uuid) { LinkedHashMap() }.getOrPut(relDir) { HashMap() }
                    durations[name] = cursor.getLong(DURATION)
                }
            }
        } catch (e: RuntimeException) {
            Log.w("FrameData", "MediaStore pass failed", e)
        }
        return found.mapValues { (_, folders) -> folders.map { (relDir, durations) -> SeedFolder(relDir, durations) } }
    }

    private companion object {
        val COLUMNS = arrayOf(
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
        )
        const val VOLUME = 0
        const val FOLDER = 1
        const val NAME = 2
        const val DURATION = 3
    }
}
