package com.frameglass.data.secure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The AndroidKeyStore itself needs a device. This covers the sealing around it with a software key. */
class BlobCipherTest {
    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val shared = key()
    private val cipher = BlobCipher { shared }

    @Test
    fun opensWhatItSealed() {
        assertEquals("abc123 مفتاح", cipher.open(cipher.seal("abc123 مفتاح")))
    }

    @Test
    fun theBlobHidesTheKey() {
        assertFalse(cipher.seal("abc123").contains("abc123"))
        assertFalse(String(Base64.getDecoder().decode(cipher.seal("abc123"))).contains("abc123"))
    }

    @Test
    fun everySealUsesAFreshIv() {
        assertNotEquals(cipher.seal("same"), cipher.seal("same"))
    }

    @Test
    fun aTamperedBlobDoesNotOpen() {
        val bytes = Base64.getDecoder().decode(cipher.seal("abc123"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        assertNull(cipher.open(Base64.getEncoder().encodeToString(bytes)))
    }

    @Test
    fun anotherKeyDoesNotOpen() {
        val other = BlobCipher(::key)
        assertNull(other.open(cipher.seal("abc123")))
    }

    @Test
    fun garbageDoesNotThrow() {
        assertNull(cipher.open("not base64 !!"))
    }
}
