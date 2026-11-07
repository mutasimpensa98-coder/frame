package com.frameglass.data.logic

import com.frameglass.data.api.AppSettings
import com.frameglass.data.db.RootFolder
import java.io.File

/**
 * What one scan covers. Whole-phone roots are worked out for each run and never stored, so the list of added
 * folders stays exactly what the owner added. A folder added by hand keeps its own run, because that is how
 * a hidden or .nomedia folder gets in.
 */
internal object ScanRoots {
    private const val AUTOMATIC_ID = -1L

    fun plan(added: List<RootFolder>, mounted: List<String>, wholePhone: Boolean): List<RootFolder> {
        if (!wholePhone) return added
        val wholeVolumes = added.filter { it.relPath.isEmpty() }.mapTo(HashSet()) { it.volumeUuid }
        val automatic = mounted.filter { it !in wholeVolumes }
            .map { RootFolder(id = AUTOMATIC_ID, volumeUuid = it, relPath = "", addedAt = 0) }
        return automatic + added
    }

    /** True when a folder added by hand holds this file. */
    fun covers(added: List<RootFolder>, volume: String, relPath: String): Boolean =
        added.any { it.volumeUuid == volume && (it.relPath.isEmpty() || relPath.startsWith(it.relPath + "/")) }
}

/** Where the search goes on a volume. Paths are relative to the volume. */
internal object ScanRules {
    private const val ANDROID = "android"

    // Since Android 11 WhatsApp and Telegram keep their public media here. MediaStore lists it, so the walk need not.
    private const val APP_MEDIA_ROOT = "android/media"
    private const val APP_MEDIA = "$APP_MEDIA_ROOT/"

    private val SYSTEM_FOLDERS = setOf(
        "thumbnails", "thumbnail", "thumbs", "thumb", "caches", "lost.dir", "lost+found",
        "system volume information", "\$recycle.bin", "found.000", "whatsapp animated gifs",
    )
    private val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "dng", "bmp")

    /** App caches, thumbnails and system folders hold no video worth showing, whatever the settings say. */
    fun isJunkFolder(name: String): Boolean {
        val lower = name.lowercase()
        val plain = lower.trimStart('.')
        return plain in SYSTEM_FOLDERS || plain.endsWith("cache") || lower.startsWith(".trash") || lower.startsWith(".recycle")
    }

    /** Whether the walk enters the folder [name] at [rel]. Android/ counts as a hidden folder. */
    fun walks(rel: String, name: String, hidden: Boolean): Boolean {
        if (isJunkFolder(name)) return false
        return hidden || (!name.startsWith('.') && !rel.equals(ANDROID, ignoreCase = true))
    }

    /** Whether a folder that MediaStore names may join the library. */
    fun indexAdmits(relDir: String, hidden: Boolean): Boolean {
        val parts = relDir.split('/')
        if (parts.any(::isJunkFolder)) return false
        if (hidden) return true
        if (parts.any { it.startsWith('.') }) return false
        return !parts.first().equals(ANDROID, ignoreCase = true) || isAppMedia("$relDir/")
    }

    /** Files the walk leaves alone: MediaStore brought them in, so a walk cannot call them missing. */
    fun isAppMedia(relPath: String): Boolean = relPath.startsWith(APP_MEDIA, ignoreCase = true)

    /** A .nomedia at or above this folder says nothing about what apps publish inside it. */
    fun isAppMediaRoot(relPath: String): Boolean = relPath.equals(APP_MEDIA_ROOT, ignoreCase = true)

    /**
     * MediaStore keeps trashed and half-written files beside the others under these names. With hidden files
     * on, a trashed video would otherwise come back as a move of its own row.
     */
    fun isStaged(name: String): Boolean = name.startsWith(".trashed-") || name.startsWith(".pending-")

    /** Saves one stat call per photo, which matters with 100,000 of them in DCIM. */
    fun isPlainFile(name: String): Boolean = ParsePaths.extension(name) in PHOTO_EXTENSIONS

    /**
     * When a file joined the library. The first whole-phone scan finds everything at once, so the time of the
     * scan would say nothing: recent clips and Recently added need the time the file landed on the phone.
     */
    fun firstSeen(mtime: Long, now: Long): Long = if (mtime in 1..now) mtime else now
}

/** Asks whether the automatic pass reaches a folder, with one .nomedia probe per folder. */
internal class AutoReach(private val volumePath: String, private val opts: AppSettings) {
    private val nomedia = HashMap<String, Boolean>()

    private fun holdsNomedia(rel: String): Boolean =
        nomedia.getOrPut(rel) { File(File(volumePath, rel), ".nomedia").exists() }

    fun reaches(relDir: String): Boolean {
        if (!ScanRules.indexAdmits(relDir, opts.scanHiddenFolders)) return false
        if (opts.scanNomediaFolders) return true
        var rel = relDir
        while (rel.isNotEmpty() && !ScanRules.isAppMediaRoot(rel)) {
            if (holdsNomedia(rel)) return false
            rel = ParsePaths.folderOf(rel)
        }
        return true
    }
}
