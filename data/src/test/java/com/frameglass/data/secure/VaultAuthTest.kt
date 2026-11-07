package com.frameglass.data.secure

import com.frameglass.data.logic.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryStore : SecretStore {
    val values = MutableStateFlow<Map<String, String>>(emptyMap())
    override suspend fun readSecret(name: String): String? = values.value[name]
    override suspend fun writeSecret(name: String, value: String?) {
        values.value = if (value == null) values.value - name else values.value + (name to value)
    }
    override fun watchSecret(name: String): Flow<String?> = values.map { it[name] }
}

private class FakeTime(var now: Long = 1_000_000) : Clock {
    val waits = ArrayList<Long>()
    override fun now(): Long = now
    val wait: suspend (Long) -> Unit = { ms ->
        waits += ms
        now += ms
    }
}

/** Fast hashing here. The real strength is checked on its own below. */
private fun auth(store: SecretStore, time: FakeTime = FakeTime()) = VaultAuth(store, time, PasswordHasher(iterations = 1_000), time.wait)

class PasswordHasherTest {
    @Test
    fun theRealIterationCountMeetsTheBrief() {
        assertTrue(PasswordHasher.ITERATIONS >= 300_000)
    }

    @Test
    fun theDefaultHasherUsesIt() {
        assertEquals(PasswordHasher.ITERATIONS, PasswordHasher().hash("x").iterations)
    }

    @Test
    fun theRightSecretMatchesAndAWrongOneDoesNot() {
        val hasher = PasswordHasher(1_000)
        val credential = hasher.hash("كلمة السر")
        assertTrue(hasher.matches("كلمة السر", credential))
        assertFalse(hasher.matches("كلمة سر", credential))
    }

    @Test
    fun theSaltIsFreshEveryTime() {
        val hasher = PasswordHasher(1_000)
        val a = hasher.hash("same")
        val b = hasher.hash("same")
        assertFalse(a.salt.contentEquals(b.salt))
        assertFalse(a.hash.contentEquals(b.hash))
    }

    @Test
    fun aCredentialRemembersItsOwnStrength() {
        val weak = PasswordHasher(1_000).hash("x")
        val back = Credential.decode(weak.encode())!!
        assertEquals(1_000, back.iterations)
        assertTrue(PasswordHasher(2_000).matches("x", back))
    }

    @Test
    fun aDamagedRecordIsNoRecord() {
        assertNull(Credential.decode("not a record"))
        assertNull(Credential.decode("abc:AAAA:AAAA"))
        assertNull(Credential.decode("1000:not base64!:AAAA"))
    }
}

class RecoveryCodeTest {
    @Test
    fun sixteenUnambiguousCharactersInGroups() {
        repeat(50) {
            val code = RecoveryCode.generate()
            assertTrue(code, Regex("""[A-Z2-9]{4}(-[A-Z2-9]{4}){3}""").matches(code))
            val plain = code.replace("-", "")
            assertEquals(RecoveryCode.LENGTH, plain.length)
            assertTrue(plain.all { it in RecoveryCode.ALPHABET })
        }
    }

    @Test
    fun theAlphabetHasNothingToMisread() {
        "0O1IL".forEach { assertFalse(it in RecoveryCode.ALPHABET) }
    }

    @Test
    fun codesDiffer() {
        assertEquals(50, List(50) { RecoveryCode.generate() }.toSet().size)
    }

    @Test
    fun typingIsForgiving() {
        assertEquals("ABCD2345", RecoveryCode.normalize("abcd-2345"))
        assertEquals("ABCD2345", RecoveryCode.normalize(" AB CD 23 45 "))
        assertEquals("AB23", RecoveryCode.normalize("ab٢٣"))
    }
}

class AttemptPolicyTest {
    @Test
    fun aTypoCostsNothing() {
        assertEquals(0, AttemptPolicy.delayMs(1))
        assertEquals(0, AttemptPolicy.delayMs(2))
    }

    @Test
    fun theWaitGrows() {
        val waits = (3..12).map(AttemptPolicy::delayMs)
        assertEquals(waits.sorted(), waits)
        assertTrue(waits.zipWithNext().all { (a, b) -> b > a || b == AttemptPolicy.MAX_DELAY_MS })
        assertEquals(5_000, waits.first())
        assertEquals(10_000, waits[1])
    }

    @Test
    fun theWaitStopsAtAnHour() {
        assertEquals(AttemptPolicy.MAX_DELAY_MS, AttemptPolicy.delayMs(10_000))
        assertEquals(3_600_000, AttemptPolicy.MAX_DELAY_MS)
    }
}

class VaultAuthTest {
    @Test
    fun setupReturnsACodeAndNothingInClearReachesTheStore() = runBlocking {
        val store = MemoryStore()
        val code = auth(store).setup("سري جدا")
        val saved = store.values.value.values.joinToString("|")
        assertFalse(saved.contains("سري جدا"))
        assertFalse(saved.contains(code))
        assertFalse(saved.contains(code.replace("-", "")))
    }

    @Test
    fun thePasswordAndTheCodeBothOpen() = runBlocking {
        val vault = auth(MemoryStore())
        val code = vault.setup("secret")
        assertTrue(vault.checkPassword("secret"))
        assertTrue(vault.checkRecovery(code))
        assertTrue(vault.checkRecovery(code.lowercase().replace("-", " ")))
    }

    @Test
    fun theWrongOnesDoNot() = runBlocking {
        val vault = auth(MemoryStore())
        vault.setup("secret")
        assertFalse(vault.checkPassword("Secret"))
        assertFalse(vault.checkRecovery("AAAA-BBBB-CCCC-DDDD"))
    }

    @Test
    fun theCodeIsNotThePassword() = runBlocking {
        val vault = auth(MemoryStore())
        val code = vault.setup("secret")
        assertFalse(vault.checkPassword(code))
        assertFalse(vault.checkRecovery("secret"))
    }

    @Test
    fun aSecondSetupIsRefused() {
        val store = MemoryStore()
        val vault = auth(store)
        runBlocking { vault.setup("secret") }
        assertThrows(IllegalStateException::class.java) { runBlocking { vault.setup("takeover") } }
        assertTrue(runBlocking { vault.checkPassword("secret") })
    }

    @Test
    fun aBlankPasswordIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { auth(MemoryStore()).setup("   ") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { auth(MemoryStore()).setPassword("") } }
    }

    @Test
    fun configuredFollowsTheStore() = runBlocking {
        val vault = auth(MemoryStore())
        assertFalse(vault.isConfigured())
        assertFalse(vault.configured.first())
        vault.setup("secret")
        assertTrue(vault.isConfigured())
        assertTrue(vault.configured.first())
    }

    @Test
    fun nothingToCheckAgainstIsAFailureNotACrash() = runBlocking {
        assertFalse(auth(MemoryStore()).checkPassword("anything"))
    }

    @Test
    fun aNewPasswordReplacesTheOldAndKeepsTheCode() = runBlocking {
        val vault = auth(MemoryStore())
        val code = vault.setup("old")
        vault.setPassword("new")
        assertTrue(vault.checkPassword("new"))
        assertFalse(vault.checkPassword("old"))
        assertTrue(vault.checkRecovery(code))
    }

    @Test
    fun theWaitGrowsWithEveryWrongAttempt() = runBlocking {
        val time = FakeTime()
        val vault = auth(MemoryStore(), time)
        vault.setup("secret")
        repeat(5) { vault.checkPassword("wrong") }
        assertEquals(listOf(5_000L, 10_000L), time.waits)
    }

    @Test
    fun restartingTheAppDoesNotResetTheWait() = runBlocking {
        val store = MemoryStore()
        val time = FakeTime()
        auth(store, time).setup("secret")
        repeat(3) { auth(store, time).checkPassword("wrong") }
        auth(store, time).checkPassword("wrong")
        assertEquals(listOf(5_000L), time.waits)
    }

    @Test
    fun theRightPasswordStillWaitsThenClearsTheCount() = runBlocking {
        val time = FakeTime()
        val vault = auth(MemoryStore(), time)
        vault.setup("secret")
        repeat(3) { vault.checkPassword("wrong") }
        assertTrue(vault.checkPassword("secret"))
        assertEquals(listOf(5_000L), time.waits)
        repeat(2) { vault.checkPassword("wrong") }
        assertEquals(listOf(5_000L), time.waits)
    }

    @Test
    fun theCodeSharesTheSameLimit() = runBlocking {
        val time = FakeTime()
        val vault = auth(MemoryStore(), time)
        vault.setup("secret")
        repeat(3) { vault.checkPassword("wrong") }
        vault.checkRecovery("AAAA-BBBB-CCCC-DDDD")
        assertEquals(listOf(5_000L), time.waits)
    }

    @Test
    fun aClockSetBackCannotBuildAVeryLongWait() = runBlocking {
        val time = FakeTime(now = 10_000_000_000)
        val vault = auth(MemoryStore(), time)
        vault.setup("secret")
        repeat(30) { vault.checkPassword("wrong") }
        time.waits.clear()
        time.now = 1_000
        vault.checkPassword("wrong")
        assertEquals(AttemptPolicy.MAX_DELAY_MS, time.waits.single())
    }

    @Test
    fun fingerprintIsRememberedInTheStore() = runBlocking {
        val store = MemoryStore()
        val vault = auth(store)
        assertFalse(vault.biometricEnabled())
        vault.setBiometric(true)
        assertTrue(auth(store).biometricEnabled())
        assertTrue(vault.biometric.first())
        vault.setBiometric(false)
        assertFalse(vault.biometricEnabled())
    }

    @Test
    fun aWipedRecordCanBeSetUpAgain() = runBlocking {
        val store = MemoryStore()
        val vault = auth(store)
        vault.setup("lost")
        store.values.value = emptyMap()
        assertNotNull(vault.setup("found"))
        assertNotEquals(false, vault.checkPassword("found"))
    }
}
