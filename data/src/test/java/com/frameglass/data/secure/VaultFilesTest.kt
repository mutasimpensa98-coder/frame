package com.frameglass.data.secure

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

class VaultFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** Bigger than the copy buffer, so a stop lands partway through. */
    private fun source(bytes: Int = 3 * 1024 * 1024): File {
        val data = ByteArray(bytes) { (it % 251).toByte() }
        return folder.newFile("source.mkv").apply { writeBytes(data) }
    }

    private fun hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun aCopyIsExactAndReturnsItsHash() = runBlocking {
        val source = source()
        val part = File(folder.root, "copy.part")
        val hash = VaultFiles.copyVerified(source, part)
        assertArrayEquals(source.readBytes(), part.readBytes())
        assertEquals(hex(source.readBytes()), hash)
        assertEquals(hash, VaultFiles.sha256(part))
    }

    @Test
    fun theOriginalIsNeverTouched() = runBlocking {
        val source = source()
        val before = source.readBytes()
        VaultFiles.copyVerified(source, File(folder.root, "copy.part"))
        assertArrayEquals(before, source.readBytes())
    }

    @Test
    fun progressAddsUpToTheFile() = runBlocking {
        val source = source()
        var seen = 0L
        VaultFiles.copyVerified(source, File(folder.root, "copy.part"), onBytes = { seen += it })
        assertEquals(source.length(), seen)
    }

    @Test
    fun aFailedCopyRemovesThePartAndLeavesTheOriginal() {
        val source = source()
        val before = source.readBytes()
        val part = File(folder.root, "copy.part")
        assertThrows(IOException::class.java) {
            runBlocking { VaultFiles.copyVerified(source, part, onBytes = { throw IOException("disk full") }) }
        }
        assertFalse(part.exists())
        assertArrayEquals(before, source.readBytes())
    }

    @Test
    fun aWrongHashRemovesThePart() {
        val source = source()
        val part = File(folder.root, "copy.part")
        assertThrows(IOException::class.java) {
            runBlocking { VaultFiles.copyVerified(source, part, expected = "0".repeat(64)) }
        }
        assertFalse(part.exists())
    }

    @Test
    fun aSourceThatGrowsDuringTheCopyFails() {
        val source = source()
        val part = File(folder.root, "copy.part")
        var grown = false
        assertThrows(IOException::class.java) {
            runBlocking {
                VaultFiles.copyVerified(source, part, onBytes = {
                    if (!grown) {
                        grown = true
                        source.appendBytes(ByteArray(10))
                    }
                })
            }
        }
        assertFalse(part.exists())
    }

    @Test
    fun aSourceRewrittenInPlaceDuringTheCopyFails() {
        val source = source()
        val part = File(folder.root, "copy.part")
        var touched = false
        assertThrows(IOException::class.java) {
            runBlocking {
                VaultFiles.copyVerified(source, part, onBytes = {
                    if (!touched) {
                        touched = true
                        source.setLastModified(source.lastModified() - 5_000)
                    }
                })
            }
        }
        assertFalse(part.exists())
    }

    @Test
    fun matchesReadsTheBytesNotTheName() {
        val source = source()
        val hash = VaultFiles.sha256(source)
        assertTrue(VaultFiles.matches(source, hash))
        source.appendBytes(ByteArray(1))
        assertFalse(VaultFiles.matches(source, hash))
        assertFalse(VaultFiles.matches(File(folder.root, "gone.mkv"), hash))
    }

    @Test
    fun aRenameNeverReplacesAFile() {
        val from = folder.newFile("from.tmp").apply { writeText("restored") }
        val owner = folder.newFile("owner.srt").apply { writeText("the owner's own") }
        assertFalse(VaultFiles.moveNoReplace(from, owner))
        assertEquals("the owner's own", owner.readText())
        assertEquals("restored", from.readText())
        val free = File(folder.root, "free.srt")
        assertTrue(VaultFiles.moveNoReplace(from, free))
        assertEquals("restored", free.readText())
        assertFalse(from.exists())
    }

    @Test
    fun syncingAFolderNeverFails() {
        VaultFiles.syncFolder(folder.root)
        VaultFiles.syncFolder(File(folder.root, "gone"))
    }

    @Test
    fun copiesGoWhileTheOriginalIsUntouched() {
        val copy = folder.newFile("stored.mkv")
        PlacedCopies().apply { add(copy) }.discard()
        assertFalse(copy.exists())
    }

    @Test
    fun copiesStayOnceTheRowHoldsThem() {
        val copy = folder.newFile("stored.mkv")
        val copies = PlacedCopies().apply {
            add(copy)
            handToRow()
        }
        // What moveOneIn does when anything throws after the original was removed.
        copies.discard()
        assertTrue(copy.exists())
    }

    @Test
    fun aFinishedRollbackGivesTheCopiesBack() {
        val copy = folder.newFile("stored.mkv")
        val copies = PlacedCopies().apply {
            add(copy)
            handToRow()
            takeBack()
        }
        copies.discard()
        assertFalse(copy.exists())
    }

    @Test
    fun aMissingSourceFailsCleanly() {
        val part = File(folder.root, "copy.part")
        assertThrows(IOException::class.java) {
            runBlocking { VaultFiles.copyVerified(File(folder.root, "gone.mkv"), part) }
        }
        assertFalse(part.exists())
    }

    @Test
    fun aCancelledCopyRemovesThePartAndLeavesTheOriginal() {
        val source = source()
        val before = source.readBytes()
        val part = File(folder.root, "copy.part")
        assertThrows(CancellationException::class.java) {
            runBlocking {
                coroutineScope {
                    VaultFiles.copyVerified(source, part, onBytes = { cancel() })
                }
            }
        }
        assertFalse(part.exists())
        assertArrayEquals(before, source.readBytes())
    }

    @Test
    fun anExpectedHashPasses() = runBlocking {
        val source = source()
        val hash = VaultFiles.sha256(source)
        assertEquals(hash, VaultFiles.copyVerified(source, File(folder.root, "copy.part"), expected = hash))
    }

    @Test
    fun storedNamesAreRandomAndHideTheOriginal() {
        val a = VaultFiles.randomName("mkv")
        val b = VaultFiles.randomName("mkv")
        assertNotEquals(a, b)
        assertTrue(a, Regex("""[0-9a-f]{32}\.mkv""").matches(a))
    }

    @Test
    fun onlyAPlainExtensionIsKept() {
        assertEquals("mkv", VaultFiles.extensionOf("Movie.MKV"))
        assertEquals("", VaultFiles.extensionOf("no-extension"))
        assertEquals("", VaultFiles.extensionOf("odd.name with spaces"))
        assertEquals("", VaultFiles.extensionOf("../../x"))
    }
}

class SafeFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun link(link: File, target: File) {
        val made = runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }.isSuccess
        assumeTrue("this machine cannot make links", made)
    }

    @Test
    fun aFileUnderItsBaseIsPlain() {
        val base = folder.newFolder("base")
        val file = File(base, "a/b.mkv").apply { parentFile.mkdirs(); writeText("x") }
        assertTrue(SafeFiles.isPlain(file, base))
    }

    @Test
    fun aFileOutsideTheBaseIsNot() {
        val base = folder.newFolder("base")
        val file = folder.newFile("outside.mkv")
        assertFalse(SafeFiles.isPlain(file, base))
    }

    @Test
    fun aFolderIsNot() {
        val base = folder.newFolder("base")
        assertFalse(SafeFiles.isPlain(File(base, "sub").apply { mkdirs() }, base))
    }

    @Test
    fun aMissingFileIsNot() {
        val base = folder.newFolder("base")
        assertFalse(SafeFiles.isPlain(File(base, "gone.mkv"), base))
    }

    @Test
    fun aLinkToAFileIsNot() {
        val base = folder.newFolder("base")
        val target = folder.newFile("target.mkv")
        val link = File(base, "link.mkv")
        link(link, target)
        assertFalse(SafeFiles.isPlain(link, base))
    }

    @Test
    fun aFileBehindALinkedFolderIsNot() {
        val base = folder.newFolder("base")
        val real = folder.newFolder("real")
        File(real, "a.mkv").writeText("x")
        val link = File(base, "linked")
        link(link, real)
        assertFalse(SafeFiles.isPlain(File(link, "a.mkv"), base))
    }

    @Test
    fun removingALinkLeavesItsTarget() {
        val base = folder.newFolder("base")
        val target = folder.newFile("target.mkv").apply { writeText("keep") }
        val link = File(base, "link.mkv")
        link(link, target)
        assertTrue(SafeFiles.remove(link))
        assertEquals("keep", target.readText())
    }

    @Test
    fun removingSomethingMissingSaysSo() {
        assertFalse(SafeFiles.remove(File(folder.root, "gone.mkv")))
    }
}
