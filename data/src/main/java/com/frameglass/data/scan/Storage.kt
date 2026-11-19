package com.frameglass.data.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.frameglass.data.api.StorageAccess
import java.io.File
import java.util.Locale

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

    /** All Files access, or the two storage permissions on Android 10. Without it every list call fails. */
    fun readable(): Boolean = StorageAccess.isGranted(context)

    fun keyOf(volume: StorageVolume): String? =
        if (volume.isPrimary) PRIMARY else volume.uuid ?: StorageAccess.directoryOf(context, volume)?.name

    fun mounted(): List<MountedVolume> = manager.storageVolumes.mapNotNull { volume ->
        val directory = StorageAccess.directoryOf(context, volume) ?: return@mapNotNull null
        val state = volume.state
        if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) return@mapNotNull null
        val key = keyOf(volume) ?: return@mapNotNull null
        MountedVolume(key, directory.absolutePath, volume.getDescription(context), volume.isRemovable, mediaStoreName(volume))
    }

    /** StorageVolume.getMediaStoreVolumeName needs API 30. Android 10 names its volumes by the same rule. */
    private fun mediaStoreName(volume: StorageVolume): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> volume.mediaStoreVolumeName
        volume.isPrimary -> MediaStore.VOLUME_EXTERNAL_PRIMARY
        else -> volume.uuid?.lowercase(Locale.US)
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
        val generations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.getExternalVolumeNames(context).sorted()
                .joinToString(",") { "$it=${MediaStore.getGeneration(context, it)}" }
        } else {
            videoTally()
        }
        "${MediaStore.getVersion(context)}|$generations"
    }.getOrDefault("")

    /**
     * Android 10 has no MediaStore generation. How many videos it lists and the newest change among them move
     * whenever a video is added, removed or rewritten, which is all the signal is for.
     */
    private fun videoTally(): String {
        val columns = arrayOf(MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DATE_ADDED)
        return context.contentResolver
            .query(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), columns, null, null, null)
            ?.use { cursor ->
                var modified = 0L
                var added = 0L
                while (cursor.moveToNext()) {
                    modified = maxOf(modified, cursor.getLong(0))
                    added = maxOf(added, cursor.getLong(1))
                }
                "videos=${cursor.count},$modified,$added"
            }.orEmpty()
    }
}

internal object Extensions {
    val VIDEO = setOf(
        "mkv", "mp4", "m4v", "mov", "avi", "wmv", "asf", "flv", "f4v", "webm", "ts", "m2ts", "mts", "mpg", "mpeg",
        "vob", "ogv", "3gp", "rm", "rmvb", "divx", "mpe", "ogm", "3g2",
    )
}

/**
 * A ContentObserver while the app is visible and a StorageVolumeCallback for mount and unmount. That callback needs
 * API 30, so Android 10 listens for the system's own mount broadcasts instead.
 */
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

    private fun onVolumeChanged(volume: StorageVolume) {
        when (volume.state) {
            Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY -> onMounted()
            else -> volumes.keyOf(volume)?.let(onUnmounted)
        }
    }

    fun startVolumeCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            VolumeCallbackApi30.register(context, ::onVolumeChanged)
        } else {
            // All five are sent by the system only, and the branch runs on Android 10 alone, where no export flag exists.
            val mountEvents = IntentFilter(Intent.ACTION_MEDIA_MOUNTED)
            mountEvents.addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            mountEvents.addAction(Intent.ACTION_MEDIA_EJECT)
            mountEvents.addAction(Intent.ACTION_MEDIA_REMOVED)
            mountEvents.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            mountEvents.addDataScheme("file")
            context.registerReceiver(MountReceiver(), mountEvents)
        }
    }

    /** Android 10 only. The broadcast names the volume, and one that does not still means a rescan is due. */
    private inner class MountReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val volume = intent.getParcelableExtra<StorageVolume>(StorageVolume.EXTRA_STORAGE_VOLUME)
            when {
                volume != null -> onVolumeChanged(volume)
                intent.action == Intent.ACTION_MEDIA_MOUNTED -> onMounted()
            }
        }
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

/** A class of its own that also does the registering, so Android 10 never has to load a type it does not have. */
@RequiresApi(Build.VERSION_CODES.R)
private class VolumeCallbackApi30(private val onChanged: (StorageVolume) -> Unit) : StorageManager.StorageVolumeCallback() {
    override fun onStateChanged(volume: StorageVolume) = onChanged(volume)

    companion object {
        fun register(context: Context, onChanged: (StorageVolume) -> Unit) {
            val manager = context.getSystemService(StorageManager::class.java)
            manager.registerStorageVolumeCallback(context.mainExecutor, VolumeCallbackApi30(onChanged))
        }
    }
}
