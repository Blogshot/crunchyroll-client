package com.github.blogshot.crtv.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.blogshot.crtv.core.auth.AuthTokens
import com.github.blogshot.crtv.core.auth.TokenStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.sessionStore by preferencesDataStore(name = "session")
private val TOKENS = stringPreferencesKey("tokens")
private val DEVICE_ID = stringPreferencesKey("device_id")

/** Feste Geräte-ID; Crunchyroll ordnet Sessions und Stream-Limits danach zu. */
suspend fun Context.deviceId(): String {
    sessionStore.data.first()[DEVICE_ID]?.let { return it }
    val id = UUID.randomUUID().toString()
    sessionStore.edit { it[DEVICE_ID] = id }
    return id
}

/** Token als JSON, verschlüsselt mit einem AES-Schlüssel aus dem Android Keystore. */
class EncryptedTokenStore(private val context: Context) : TokenStore {

    override suspend fun load(): AuthTokens? {
        val blob = context.sessionStore.data.first()[TOKENS] ?: return null
        // Schlüssel weg (z. B. nach Backup-Restore) oder Daten kaputt: als abgemeldet behandeln statt abzustürzen.
        return runCatching { json.decodeFromString<AuthTokens>(decrypt(blob)) }.getOrNull()
    }

    override suspend fun save(tokens: AuthTokens) {
        val blob = encrypt(json.encodeToString(AuthTokens.serializer(), tokens))
        context.sessionStore.edit { it[TOKENS] = blob }
    }

    override suspend fun clear() {
        context.sessionStore.edit { it.remove(TOKENS) }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "crtv-session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        val json = Json { ignoreUnknownKeys = true }
    }
}
