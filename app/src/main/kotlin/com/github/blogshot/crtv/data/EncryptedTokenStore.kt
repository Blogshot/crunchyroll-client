package com.github.blogshot.crtv.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.blogshot.crtv.core.api.ClientCredentials
import com.github.blogshot.crtv.core.api.ClientCredentialsStore
import com.github.blogshot.crtv.core.auth.AuthTokens
import com.github.blogshot.crtv.core.auth.TokenStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.sessionStore by preferencesDataStore(name = "session")
private val TOKENS = stringPreferencesKey("tokens")
private val CLIENT = stringPreferencesKey("client")
private val DEVICE_ID = stringPreferencesKey("device_id")

/** Feste Geräte-ID; Crunchyroll ordnet Sessions und Stream-Limits danach zu. */
suspend fun Context.deviceId(): String {
    sessionStore.data.first()[DEVICE_ID]?.let { return it }
    val id = UUID.randomUUID().toString()
    sessionStore.edit { it[DEVICE_ID] = id }
    return id
}

/** Token als JSON, verschlüsselt mit einem AES-Schlüssel aus dem Android Keystore. */
class EncryptedTokenStore(context: Context) : TokenStore {
    private val value = EncryptedValue(context, TOKENS, AuthTokens.serializer())
    override suspend fun load() = value.load()
    override suspend fun save(tokens: AuthTokens) = value.save(tokens)
    override suspend fun clear() = value.clear()
}

/** Client-Kennung (von der Website ermittelt oder von Hand eingegeben), ebenfalls verschlüsselt. */
class EncryptedClientCredentialsStore(context: Context) : ClientCredentialsStore {
    @Serializable
    private data class Stored(val clientId: String, val clientSecret: String)

    private val value = EncryptedValue(context, CLIENT, Stored.serializer())
    override suspend fun load() = value.load()?.let { ClientCredentials(it.clientId, it.clientSecret) }
    override suspend fun save(credentials: ClientCredentials) =
        value.save(Stored(credentials.clientId, credentials.clientSecret))
}

private class EncryptedValue<T>(
    private val context: Context,
    private val key: Preferences.Key<String>,
    private val serializer: KSerializer<T>,
) {
    suspend fun load(): T? {
        val blob = context.sessionStore.data.first()[key] ?: return null
        // Schlüssel weg (z. B. nach Backup-Restore) oder Daten kaputt: als nicht vorhanden behandeln statt abzustürzen.
        return runCatching { json.decodeFromString(serializer, Crypto.decrypt(blob)) }.getOrNull()
    }

    suspend fun save(value: T) {
        val blob = Crypto.encrypt(json.encodeToString(serializer, value))
        context.sessionStore.edit { it[key] = blob }
    }

    suspend fun clear() {
        context.sessionStore.edit { it.remove(key) }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private object Crypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "crtv-session"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(blob: String): String {
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
}
