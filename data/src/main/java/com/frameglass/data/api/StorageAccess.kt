package com.frameglass.data.api

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

/**
 * Whether the app may read the phone's files by path (brief 4.6). All Files access exists from Android 11. On
 * Android 10 the two storage permissions do the same job together with the manifest's requestLegacyExternalStorage,
 * and maxSdkVersion keeps them off every newer phone (waiver of 2026-11-19).
 */
object StorageAccess {
    /** True on Android 10 only: access is a permission dialog there, not a page in the phone's Settings. */
    val usesLegacyPermissions: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    /** Read lists and plays. Write is what a confirmed delete and the private folder need. */
    val legacyPermissions: Array<String> =
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun isGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            legacyPermissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        }

    /**
     * Where [volume] is mounted. StorageVolume.getDirectory needs API 30. On Android 10 the phone's own storage has a
     * public path, and a card or drive is found through the app's own folder on it, the one public way to a mount
     * point there. A drive Android 10 does not show to apps has no such folder and stays out.
     */
    fun directoryOf(context: Context, volume: StorageVolume): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return volume.directory
        @Suppress("DEPRECATION")
        if (volume.isPrimary) return Environment.getExternalStorageDirectory()
        val uuid = volume.uuid ?: return null
        val manager = context.getSystemService(StorageManager::class.java)
        return context.getExternalFilesDirs(null).filterNotNull().firstNotNullOfOrNull { appFolder ->
            val owner = runCatching { manager.getStorageVolume(appFolder) }.getOrNull()
            if (owner?.uuid == uuid) mountPointOf(appFolder.absolutePath) else null
        }
    }

    /** "/storage/1234-ABCD/Android/data/…" gives "/storage/1234-ABCD". Pure, so a JVM test covers it. */
    internal fun mountPointOf(appFolder: String): File? {
        val at = appFolder.indexOf(APP_FOLDERS)
        return if (at > 0) File(appFolder.substring(0, at)) else null
    }

    private const val APP_FOLDERS = "/Android/data/"
}
