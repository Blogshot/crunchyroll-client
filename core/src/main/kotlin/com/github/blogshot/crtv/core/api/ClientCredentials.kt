package com.github.blogshot.crtv.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Kennung der App gegenüber Crunchyrolls OAuth (nicht die Zugangsdaten des Kontos). Sie steht bewusst nicht im APK.
 */
data class ClientCredentials(val clientId: String, val clientSecret: String = "")

class MissingClientCredentialsException : IOException("Keine Client-Kennung verfügbar")

interface ClientCredentialsProvider {
    /** @throws MissingClientCredentialsException wenn keine Quelle eine Kennung liefert. */
    suspend fun get(): ClientCredentials

    /** Nach einem Fehler mit der Kennung (z. B. 401 `invalid_client`): beim nächsten [get] neu ermitteln. */
    suspend fun invalidate() {}
}

/** Gespeicherte Kennung (manuell eingegeben oder zuletzt erfolgreich ermittelt). */
interface ClientCredentialsStore {
    suspend fun load(): ClientCredentials?
    suspend fun save(credentials: ClientCredentials)
}

/**
 * Liest die öffentliche Kennung der Web-App aus der Startseite von crunchyroll.com
 * (`window.__APP_CONFIG__ … cxApiParams.accountAuthClientId`).
 */
class WebsiteClientIdSource(
    private val http: OkHttpClient,
    private val userAgent: String,
    private val baseUrl: String = "https://www.crunchyroll.com",
) {
    suspend fun fetch(): ClientCredentials? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/").header("User-Agent", userAgent).build()
        val html = runCatching {
            http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return@withContext null
        parse(html)
    }

    companion object {
        private val pattern = Regex("""["']?accountAuthClientId["']?\s*:\s*["']([A-Za-z0-9_\-]+)["']""")

        fun parse(html: String): ClientCredentials? =
            pattern.find(html)?.groupValues?.get(1)?.let { ClientCredentials(it) }
    }
}

/**
 * Reihenfolge: gespeicherte Kennung, sonst Website. Eine von der Website gelesene Kennung wird gespeichert,
 * damit nicht jeder Start die Startseite lädt. Liefert keine Quelle etwas, fragt die UI den Nutzer.
 */
class DefaultClientCredentialsProvider(
    private val store: ClientCredentialsStore,
    private val website: WebsiteClientIdSource,
) : ClientCredentialsProvider {
    private val mutex = Mutex()
    private var cached: ClientCredentials? = null
    private var skipStored = false

    override suspend fun get(): ClientCredentials = mutex.withLock {
        cached?.let { return@withLock it }
        val stored = if (skipStored) null else store.load()
        val found = stored ?: website.fetch()?.also { store.save(it) }
        found?.also { cached = it } ?: throw MissingClientCredentialsException()
    }

    override suspend fun invalidate() = mutex.withLock {
        cached = null
        skipStored = true
    }

    /** Manuelle Eingabe aus der UI. */
    suspend fun setManually(credentials: ClientCredentials) = mutex.withLock {
        store.save(credentials)
        cached = credentials
        skipStored = false
    }
}
