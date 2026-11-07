package com.frameglass.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.frameglass.net.ApiKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Lost keys can be typed again, so a damaged file only costs that. The private folder's records live here too. If
 * they are lost the folder asks for a new password, and its files are still there behind it.
 */
private val Context.secretsDataStore by preferencesDataStore(
    name = "secrets",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** AES-GCM sealing of one string. The IV sits in front of the ciphertext. */
internal class BlobCipher(private val key: () -> SecretKey) {
    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + body)
    }

    /** Returns null for a blob that cannot be opened, such as one from a key that no longer exists. */
    fun open(blob: String): String? = try {
        val bytes = Base64.getDecoder().decode(blob)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES),
        )
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}

/**
 * R4. Keys are typed on the phone and sealed with an AndroidKeyStore AES-GCM key. The blobs live in their own
 * DataStore file, which the F-L5 backup never reads. Plain keys are never cached and never logged.
 */
internal class KeyVault(
    private val context: Context,
    scope: CoroutineScope,
) : SecretStore {
    private val store get() = context.secretsDataStore
    private val cipher = BlobCipher(::keystoreKey)
    private val loaded = CompletableDeferred<Unit>()

    @Volatile
    private var blobs: Map<ApiKey, String> = emptyMap()

    init {
        scope.launch {
            store.data.collect { prefs ->
                blobs = ApiKey.entries.mapNotNull { kind -> prefs[prefKey(kind)]?.let { kind to it } }.toMap()
                loaded.complete(Unit)
            }
        }
    }

    private fun prefKey(kind: ApiKey) = stringPreferencesKey("key.${kind.name}")

    suspend fun awaitLoaded() = loaded.await()

    fun has(kind: ApiKey): Flow<Boolean> = store.data.map { prefKey(kind) in it }.distinctUntilChanged()

    fun read(kind: ApiKey): String? = blobs[kind]?.let(cipher::open)

    suspend fun write(kind: ApiKey, value: String?) {
        val trimmed = value?.trim()?.ifEmpty { null }
        store.edit { prefs ->
            if (trimmed == null) prefs.remove(prefKey(kind)) else prefs[prefKey(kind)] = cipher.seal(trimmed)
        }
    }

    /** A record that no longer opens reads as absent, so the folder can be set up again. */
    override suspend fun readSecret(name: String): String? =
        store.data.first()[stringPreferencesKey(name)]?.let(cipher::open)

    override suspend fun writeSecret(name: String, value: String?) {
        val key = stringPreferencesKey(name)
        store.edit { prefs -> if (value == null) prefs.remove(key) else prefs[key] = cipher.seal(value) }
    }

    override fun watchSecret(name: String): Flow<String?> {
        val key = stringPreferencesKey(name)
        return store.data.map { prefs -> prefs[key]?.let(cipher::open) }.distinctUntilChanged()
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "frameglass.apikeys"
    }
}
