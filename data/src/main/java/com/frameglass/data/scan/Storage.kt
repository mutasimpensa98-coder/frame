package com.frameglass.data.scan

import android.content.Context
import android.database.ContentObserver
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import java.io.File

internal data class MountedVolume(
    val uuid: String,
    val path: String,
    val label: String?,
    val removable: Boolean,
    /** How MediaStore names this volume, which differs from [uuid] on the primary one. */
    val mediaStoreName: String? = null,
)

/** Row key per section 4.6: volume UUID plus relative path, so a drive can leave and return intact. */
internal class Volumes(private val context: Context) {
    private val manager: StorageManager = context.getSystemService(StorageManager::class.java)

    fun keyOf(volume: StorageVolume): String? =
        if (volume.isPrimary) PRIMARY else volume.uuid ?: volume.directory?.name

    fun mounted(): List<MountedVolume> = manager.storageVolumes.mapNotNull { volume ->
        val directory = volume.directory ?: return@mapNotNull null
        val state = volume.state
        if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) return@mapNotNull null
        val key = keyOf(volume) ?: return@mapNotNull null
        MountedVolume(key, directory.absolutePath, volume.getDescription(context), volume.isRemovable, volume.mediaStoreVolumeName)
    }

    /** Splits an absolute path into its volume and the path relative to that volume. */
    fun locate(path: String): Pair<MountedVolume, String>? {
        val canonical = runCatching { File(path).canonicalPath }.getOrDefault(path).trimEnd('/')
        val volume = mounted().filter { canonical == it.path || canonical.startsWith(it.path + "/") }
            .maxByOrNull { it.path.length } ?: return null
        return volume to canonical.removePrefix(volume.path).removePrefix("/")
    }

    fun pathMap(): Map<String, String> = mounted().associate { it.uuid to it.path }

    companion object {
        const val PRIMARY = "primary"
    }
}

/** Tells whether anything changed since the last scan. */
internal class MediaSignal(private val context: Context) {
    fun read(): String = runCatching {
        val generations = MediaStore.getExternalVolumeNames(context).sorted()
            .joinToString(",") { "$it=${MediaStore.getGeneration(context, it)}" }
        "${MediaStore.getVersion(context)}|$generations"
    }.getOrDefault("")
}

internal object Extensions {
    val VIDEO = setOf(
        "mkv", "mp4", "m4v", "mov", "avi", "wmv", "asf", "flv", "f4v", "webm", "ts", "m2ts", "mts", "mpg", "mpeg",
        "vob", "ogv", "3gp", "rm", "rmvb", "divx", "mpe", "ogm", "3g2",
    )
}

/** A ContentObserver while the app is visible and a StorageVolumeCallback for mount and unmount. */
internal class ChangeWatcher(
    private val context: Context,
    private val volumes: Volumes,
    private val onMediaChanged: () -> Unit,
    private val onMounted: () -> Unit,
    private val onUnmounted: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var observing = false

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = onMediaChanged()
    }

    private val volumeCallback = object : StorageManager.StorageVolumeCallback() {
        override fun onStateChanged(volume: StorageVolume) {
            when (volume.state) {
                Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY -> onMounted()
                else -> volumes.keyOf(volume)?.let(onUnmounted)
            }
        }
    }

    fun startVolumeCallback() {
        val manager = context.getSystemService(StorageManager::class.java)
        manager.registerStorageVolumeCallback(context.mainExecutor, volumeCallback)
    }

    fun setVisible(visible: Boolean) {
        if (visible == observing) return
        observing = visible
        val resolver = context.contentResolver
        if (visible) {
            resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        } else {
            resolver.unregisterContentObserver(observer)
        }
    }
}
