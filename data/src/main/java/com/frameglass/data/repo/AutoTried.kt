package com.frameglass.data.repo

import java.io.File
import java.security.MessageDigest

/**
 * Remembers which files the automatic subtitle download already tried, found or not, so it runs once per file. One
 * empty file per video in app storage, named by a digest of its key, so the Room schema stays as it is.
 */
internal class AutoTried(private val folder: File) {
    fun wasTried(fileKey: String): Boolean = markerOf(fileKey).exists()

    fun markTried(fileKey: String) {
        runCatching {
            folder.mkdirs()
            markerOf(fileKey).createNewFile()
        }
    }

    private fun markerOf(fileKey: String): File = File(folder, digest(fileKey))

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
