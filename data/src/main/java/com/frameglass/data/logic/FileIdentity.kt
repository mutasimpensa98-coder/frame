package com.frameglass.data.logic

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal data class FileIdentityResult(val fileId: String, val osHash: String)

/**
 * Section 4.5. fileId is SHA-256 of the size, the first 256 KiB and the last 256 KiB. The same read yields
 * the OpenSubtitles hash, which is the size plus the 64-bit little-endian word sums of the first and last
 * 64 KiB. The hash is stored locally and leaves the phone only on a subtitle search the owner taps.
 */
internal object FileIdentity {
    const val CHUNK = 256 * 1024
    const val OS_CHUNK = 64 * 1024

    fun compute(file: File): FileIdentityResult =
        RandomAccessFile(file, "r").use { raf ->
            val size = raf.length()
            val headLength = minOf(size, CHUNK.toLong()).toInt()
            val head = ByteArray(headLength)
            raf.seek(0)
            raf.readFully(head)
            val tail = ByteArray(headLength)
            raf.seek(size - headLength)
            raf.readFully(tail)
            compute(size, head, tail)
        }

    fun compute(size: Long, head: ByteArray, tail: ByteArray): FileIdentityResult {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ByteBuffer.allocate(8).putLong(size).array())
        digest.update(head)
        digest.update(tail)
        val fileId = digest.digest().joinToString("") { "%02x".format(it) }

        val osLength = minOf(size, OS_CHUNK.toLong()).toInt()
        val headSum = wordSum(head, 0, osLength)
        val tailSum = wordSum(tail, tail.size - osLength, osLength)
        return FileIdentityResult(fileId, "%016x".format(size + headSum + tailSum))
    }

    private fun wordSum(bytes: ByteArray, offset: Int, length: Int): Long {
        val words = ByteBuffer.wrap(bytes, offset, length).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
        var sum = 0L
        while (words.hasRemaining()) sum += words.get()
        return sum
    }
}
