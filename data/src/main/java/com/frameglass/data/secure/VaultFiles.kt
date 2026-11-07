package com.frameglass.data.secure

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.SecureRandom

/** File handling with no Android in it, so it can be tested on the JVM. */
internal object SafeFiles {
    /** A regular file under [base], with no symbolic link anywhere between them. */
    fun isPlain(file: File, base: File): Boolean {
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return false
        var at: File? = file
        while (at != null && at != base) {
            if (Files.isSymbolicLink(at.toPath())) return false
            at = at.parentFile
        }
        return at == base
    }

    /** Removes exactly this path. A link would go, never what it points at. */
    fun remove(file: File): Boolean = try {
        Files.delete(file.toPath())
        true
    } catch (_: IOException) {
        false
    }
}

internal object VaultFiles {
    private const val BUFFER_BYTES = 1024 * 1024
    private val random = SecureRandom()

    /** Keeps the extension, because the player's extractors use it to pick what to try first. */
    fun randomName(extension: String): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        val name = bytes.joinToString("") { "%02x".format(it) }
        return if (extension.isEmpty()) name else "$name.$extension"
    }

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..8 && it.all(Char::isLetterOrDigit) }.orEmpty()

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    /** True when [file] exists and holds exactly the bytes behind [hash]. */
    fun matches(file: File, hash: String): Boolean = file.isFile && sha256(file) == hash

    /** Renames without ever replacing what is at [target], which File.renameTo would do. */
    fun moveNoReplace(from: File, target: File): Boolean = try {
        Files.move(from.toPath(), target.toPath())
        true
    } catch (_: IOException) {
        false
    }

    /** A rename survives a power cut only once its folder is synced. Not every filesystem allows that, so this is best effort. */
    fun syncFolder(folder: File) {
        runCatching { FileChannel.open(folder.toPath(), StandardOpenOption.READ).use { it.force(true) } }
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * Copies [source] to [part] and returns its SHA-256. The copy is synced and read back before this returns.
     * Anything short of a verified copy, cancellation included, removes [part] and leaves [source] alone.
     * [expected] is the hash [source] must have.
     */
    suspend fun copyVerified(source: File, part: File, expected: String? = null, onBytes: (Int) -> Unit = {}): String {
        var verified = false
        try {
            val sizeBefore = source.length()
            val timeBefore = source.lastModified()
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(source).use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        onBytes(read)
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            val hash = hex(digest.digest())
            if (source.length() != sizeBefore || source.lastModified() != timeBefore) throw IOException("source changed during the copy")
            if (expected != null && hash != expected) throw IOException("source does not match its hash")
            if (sha256(part) != hash) throw IOException("copy does not match the source")
            verified = true
            return hash
        } finally {
            if (!verified) part.delete()
        }
    }
}

/**
 * The stored copies of one move in. Until its row commits the original is untouched, so a failure throws them away.
 * After that they may be the only copy left, and nothing deletes them unless a finished rollback gave the row back.
 */
internal class PlacedCopies {
    private val files = ArrayList<File>()
    private var heldByRow = false

    fun add(file: File) {
        files += file
    }

    fun handToRow() {
        heldByRow = true
    }

    fun takeBack() {
        heldByRow = false
    }

    fun discard() {
        if (!heldByRow) files.forEach { it.delete() }
    }
}
