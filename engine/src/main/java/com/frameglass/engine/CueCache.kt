package com.frameglass.engine

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/**
 * Text cues read out of a video, kept in app cache so the next open of the same file reads a small file instead of
 * the whole video again. One file per video and track, named by a hash, so no video or path name is on disk.
 * Least recently used files go first once the folder passes [MAX_BYTES]. Pure JVM.
 */
object CueCache {
    /** [endUs] is Media3's TIME_UNSET for a cue that lasts until the next one. */
    data class Entry(val startUs: Long, val endUs: Long, val text: String)

    const val MAX_BYTES = 50L * 1024 * 1024
    private const val MAGIC = 0x46474331
    private const val SUFFIX = ".cues"
    private const val NAME_LENGTH = 40

    /** [identity] names the file and its version, such as its path, size and change time, plus the track. */
    fun fileFor(dir: File, identity: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        val name = digest.joinToString("") { String.format(Locale.ROOT, "%02x", it) }.take(NAME_LENGTH)
        return File(dir, name + SUFFIX)
    }

    /** Written beside the target first, so a kill part way leaves no half file under the real name. */
    fun write(file: File, entries: List<Entry>): Boolean = try {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".part")
        DataOutputStream(temp.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(entries.size)
            for (entry in entries) {
                out.writeLong(entry.startUs)
                out.writeLong(entry.endUs)
                val bytes = entry.text.toByteArray(Charsets.UTF_8)
                out.writeInt(bytes.size)
                out.write(bytes)
            }
        }
        temp.renameTo(file) || run {
            temp.delete()
            false
        }
    } catch (_: IOException) {
        false
    }

    /** Null when the file is missing or not one of ours. A hit counts as a use for [trim]. */
    fun read(file: File): List<Entry>? {
        if (!file.isFile) return null
        return try {
            val entries = DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC) return null
                val count = input.readInt()
                if (count < 0) return null
                List(count) {
                    val start = input.readLong()
                    val end = input.readLong()
                    val size = input.readInt()
                    if (size < 0 || size > file.length()) return null
                    val bytes = ByteArray(size)
                    input.readFully(bytes)
                    Entry(start, end, String(bytes, Charsets.UTF_8))
                }
            }
            file.setLastModified(System.currentTimeMillis())
            entries
        } catch (_: IOException) {
            null
        }
    }

    /** Deletes the least recently used files until the folder holds at most [maxBytes]. */
    fun trim(dir: File, maxBytes: Long = MAX_BYTES) {
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(SUFFIX) } ?: return
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }
}

/** Caps how fast a background read goes, so playback of the same file keeps the storage it needs. */
class ReadPace(private val bytesPerSecond: Long) {
    /** How long to wait now, having read [bytes] in [elapsedMs]. */
    fun waitMs(bytes: Long, elapsedMs: Long): Long = (bytes * 1000 / bytesPerSecond - elapsedMs).coerceAtLeast(0)
}
