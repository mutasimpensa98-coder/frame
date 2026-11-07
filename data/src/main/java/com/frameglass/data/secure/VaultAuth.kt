package com.frameglass.data.secure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.frameglass.data.logic.Clock
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Named records in the encrypted key store. */
internal interface SecretStore {
    suspend fun readSecret(name: String): String?
    suspend fun writeSecret(name: String, value: String?)
    fun watchSecret(name: String): Flow<String?>
}

/** A password or recovery code as PBKDF2-HMAC-SHA256 output. The iteration count travels with it, so it can rise later. */
internal class Credential(val iterations: Int, val salt: ByteArray, val hash: ByteArray) {
    fun encode(): String {
        val base64 = Base64.getEncoder()
        return "$iterations:${base64.encodeToString(salt)}:${base64.encodeToString(hash)}"
    }

    companion object {
        fun decode(text: String): Credential? {
            val parts = text.split(':')
            if (parts.size != 3) return null
            val iterations = parts[0].toIntOrNull() ?: return null
            return try {
                val base64 = Base64.getDecoder()
                Credential(iterations, base64.decode(parts[1]), base64.decode(parts[2]))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

internal class PasswordHasher(private val iterations: Int = ITERATIONS, private val random: SecureRandom = SecureRandom()) {
    private fun derive(secret: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(Normalizer.normalize(secret, Normalizer.Form.NFC).toCharArray(), salt, rounds, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun hash(secret: String): Credential {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return Credential(iterations, salt, derive(secret, salt, iterations))
    }

    fun matches(secret: String, credential: Credential): Boolean =
        MessageDigest.isEqual(derive(secret, credential.salt, credential.iterations), credential.hash)

    companion object {
        const val ITERATIONS = 300_000
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
    }
}

/** 16 characters with no 0, O, 1, I or L, so it survives being copied onto paper. */
internal object RecoveryCode {
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val LENGTH = 16
    private const val GROUP = 4

    fun generate(random: SecureRandom = SecureRandom()): String =
        String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }).chunked(GROUP).joinToString("-")

    /** Reads what the owner types: any case, with or without separators, and Arabic-Indic digits per F-A6. */
    fun normalize(input: String): String = input.mapNotNull { c ->
        val digit = Character.digit(c, 10)
        when {
            digit >= 0 -> digit.digitToChar()
            c.isLetter() -> c.uppercaseChar()
            else -> null
        }
    }.joinToString("")
}

/** A typo costs nothing. After that the wait doubles, up to an hour. */
internal object AttemptPolicy {
    const val FREE_ATTEMPTS = 2
    const val FIRST_DELAY_MS = 5_000L
    const val MAX_DELAY_MS = 3_600_000L

    fun delayMs(failures: Int): Long {
        if (failures <= FREE_ATTEMPTS) return 0
        val doublings = (failures - FREE_ATTEMPTS - 1).coerceAtMost(MAX_DOUBLINGS)
        return (FIRST_DELAY_MS shl doublings).coerceAtMost(MAX_DELAY_MS)
    }

    private const val MAX_DOUBLINGS = 16
}

/**
 * The private folder's password. Only derived hashes reach the store, sealed like the API keys. Wrong attempts are
 * kept there too, so restarting the app does not reset the wait.
 */
internal class VaultAuth(
    private val store: SecretStore,
    private val clock: Clock,
    private val hasher: PasswordHasher = PasswordHasher(),
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val gate = Mutex()

    val configured: Flow<Boolean> = store.watchSecret(PASSWORD).map { it != null }
    val biometric: Flow<Boolean> = store.watchSecret(BIOMETRIC).map { it == ON }

    suspend fun isConfigured(): Boolean = store.readSecret(PASSWORD) != null

    suspend fun biometricEnabled(): Boolean = store.readSecret(BIOMETRIC) == ON

    suspend fun setBiometric(enabled: Boolean) = store.writeSecret(BIOMETRIC, if (enabled) ON else null)

    private suspend fun hashed(secret: String): String = withContext(Dispatchers.Default) { hasher.hash(secret).encode() }

    /** Returns the recovery code, which exists in clear only until this returns. */
    suspend fun setup(password: String): String {
        require(password.isNotBlank())
        return gate.withLock {
            check(!isConfigured())
            val code = RecoveryCode.generate()
            store.writeSecret(RECOVERY, hashed(RecoveryCode.normalize(code)))
            store.writeSecret(PASSWORD, hashed(password))
            clearFailures()
            code
        }
    }

    suspend fun setPassword(password: String) {
        require(password.isNotBlank())
        gate.withLock { store.writeSecret(PASSWORD, hashed(password)) }
    }

    suspend fun checkPassword(password: String): Boolean = attempt(PASSWORD, password)

    suspend fun checkRecovery(code: String): Boolean = attempt(RECOVERY, RecoveryCode.normalize(code))

    private suspend fun attempt(record: String, secret: String): Boolean = gate.withLock {
        val credential = store.readSecret(record)?.let(Credential::decode) ?: return@withLock false
        waitOut()
        // Counted before the check, so killing the app during the check saves nothing.
        val failures = (store.readSecret(FAILURES)?.toIntOrNull() ?: 0) + 1
        store.writeSecret(FAILURES, failures.toString())
        store.writeSecret(BLOCKED_UNTIL, (clock.now() + AttemptPolicy.delayMs(failures)).toString())
        val matched = withContext(Dispatchers.Default) { hasher.matches(secret, credential) }
        if (matched) withContext(NonCancellable) { clearFailures() }
        matched
    }

    /** The cap keeps a clock set far back from turning into a very long wait. */
    private suspend fun waitOut() {
        val until = store.readSecret(BLOCKED_UNTIL)?.toLongOrNull() ?: return
        val left = (until - clock.now()).coerceIn(0, AttemptPolicy.MAX_DELAY_MS)
        if (left > 0) wait(left)
    }

    private suspend fun clearFailures() {
        store.writeSecret(FAILURES, null)
        store.writeSecret(BLOCKED_UNTIL, null)
    }

    private companion object {
        const val PASSWORD = "vault.password"
        const val RECOVERY = "vault.recovery"
        const val BIOMETRIC = "vault.biometric"
        const val FAILURES = "vault.failures"
        const val BLOCKED_UNTIL = "vault.blockedUntil"
        const val ON = "1"
    }
}
