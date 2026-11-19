package com.frameglass.data.scan

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.frameglass.data.logic.TrashIndex
import java.io.File

/**
 * Decides what the system trash can take, and sends it there. The trash arrives in API 30: on Android 10 nothing is
 * recoverable, so every confirmed delete is a direct one and the sheet says so (waiver of 2026-11-19).
 */
internal class MediaStoreTrash(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** A file not here has no trash. Always empty on Android 10. */
    @Suppress("DEPRECATION")
    fun lookup(paths: Collection<String>): Map<String, Uri> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyMap()
        val found = ArrayList<Pair<String, Uri>>()
        for (chunk in paths.distinct().chunked(CHUNK)) {
            val marks = chunk.joinToString(",") { "?" }
            try {
                resolver.query(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    COLUMNS,
                    "${MediaStore.MediaColumns.DATA} IN ($marks)",
                    chunk.toTypedArray(),
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(PATH) ?: continue
                        val volume = cursor.getString(VOLUME) ?: continue
                        found += path to uriOf(volume, cursor.getLong(ID), cursor.getInt(TYPE))
                    }
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "MediaStore lookup failed: ${e.javaClass.simpleName}")
            }
        }
        return TrashIndex.byPath(found)
    }

    /** createTrashRequest accepts video URIs and refuses a video through the Files table. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun uriOf(volume: String, id: Long, type: Int): Uri =
        if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            MediaStore.Video.Media.getContentUri(volume, id)
        } else {
            MediaStore.Files.getContentUri(volume, id)
        }

    /** All Files access allows this write. False sends the file to the system's own confirmation. */
    fun trash(uri: Uri): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && try {
        val values = ContentValues(1).apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }
        resolver.update(uri, values, null, null) == 1
    } catch (e: SecurityException) {
        Log.w(TAG, "direct trash refused: ${e.javaClass.simpleName}")
        false
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "direct trash refused: ${e.javaClass.simpleName}")
        false
    }

    /** Null when Android will not even offer a confirmation for these items. */
    fun consentRequest(uris: List<Uri>): IntentSender? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) MediaStore.createTrashRequest(resolver, uris, true).intentSender else null
    } catch (e: RuntimeException) {
        Log.w(TAG, "trash request refused: ${e.javaClass.simpleName}")
        null
    }

    private companion object {
        const val TAG = "FrameData"
        const val CHUNK = 200
        val COLUMNS = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA,
        )
        const val ID = 0
        const val VOLUME = 1
        const val TYPE = 2
        const val PATH = 3
    }
}

/**
 * Android 10 only, after the app removed a file by path. Its MediaStore does not watch the disk, so the row would
 * stay, and with it the entry other apps show, until the phone's next media scan. Newer phones drop it by themselves.
 */
internal object MediaRows {
    @Suppress("DEPRECATION")
    fun forget(context: Context, path: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return
        try {
            val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            context.contentResolver.delete(files, "${MediaStore.MediaColumns.DATA} = ?", arrayOf(path))
        } catch (e: RuntimeException) {
            Log.w("FrameData", "MediaStore row stays: ${e.javaClass.simpleName}")
        }
    }
}

/** Phone storage as Android reports it. */
internal object DeviceStorage {
    private val root: File get() = Environment.getExternalStorageDirectory()

    fun totalBytes(): Long = runCatching { StatFs(root.path).totalBytes }.getOrDefault(0)

    fun freeBytes(): Long = runCatching { StatFs(root.path).availableBytes }.getOrDefault(0)

    fun freeBytes(folder: File): Long = runCatching { StatFs(folder.path).availableBytes }.getOrDefault(0)
}
