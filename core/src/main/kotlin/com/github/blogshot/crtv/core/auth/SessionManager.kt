package com.github.blogshot.crtv.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

sealed interface SessionState {
    /** Gespeicherte Session noch nicht geladen. */
    data object Unknown : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val accountId: String?, val profileId: String?, val profileConfirmed: Boolean) : SessionState
}

// IOException, damit OkHttp-Interceptors die Fehler sauber an den Aufrufer durchreichen.
class SessionExpiredException : IOException("Session abgelaufen, neu einloggen")

/** Token konnte gerade nicht erneuert werden; die Session bleibt bestehen. */
class TransientAuthException(cause: Throwable?) : IOException("Token-Erneuerung vorübergehend fehlgeschlagen", cause)

/**
 * Einziger Besitzer der Token.
 *
 * Crunchyroll rotiert das Refresh-Token bei jeder Erneuerung. Zwei parallele Refreshes verbrauchen dasselbe
 * Token doppelt, und der zweite endet mit `invalid_grant`. Deshalb laufen alle Refreshes über eine [Mutex],
 * und Wartende übernehmen das Ergebnis des laufenden Refreshes, statt einen eigenen zu starten.
 */
class SessionManager(
    private val api: AuthApi,
    private val store: TokenStore,
    private val clock: () -> Long = System::currentTimeMillis,
    /** So lange vor Ablauf gilt ein Access-Token schon als verbraucht. */
    private val refreshSkewMs: Long = 60_000,
) {
    private val mutex = Mutex()
    private var tokens: AuthTokens? = null
    private var loaded = false

    private val _state = MutableStateFlow<SessionState>(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Lädt die gespeicherte Session. Mehrfacher Aufruf ist harmlos. */
    suspend fun restore() = mutex.withLock { ensureLoaded() }

    /**
     * Liefert ein gültiges Access-Token.
     *
     * @param rejected Token, das der Server gerade mit 401 abgelehnt hat. Ist das aktuelle Token noch dieses,
     *   wird erneuert; hat ein anderer Aufrufer inzwischen erneuert, kommt sofort das neue Token zurück.
     * @throws SessionExpiredException wenn kein Login besteht oder das Refresh-Token ungültig ist.
     * @throws TransientAuthException bei Netzwerkproblemen; die Session bleibt erhalten.
     */
    suspend fun accessToken(rejected: String? = null): String = mutex.withLock {
        ensureLoaded()
        val current = tokens ?: throw SessionExpiredException()
        val stillValid = current.expiresAtMs - refreshSkewMs > clock()
        if (stillValid && current.accessToken != rejected) return@withLock current.accessToken
        refreshLocked(current, current.profileId).accessToken
    }

    /** Erzwingt ein neues Access-Token, z. B. wenn ein Endpunkt das aktuelle als ungültig ablehnt. */
    suspend fun forceRefresh() = mutex.withLock {
        ensureLoaded()
        val current = tokens ?: throw SessionExpiredException()
        refreshLocked(current, current.profileId)
        Unit
    }

    /** Proaktive Erneuerung, z. B. beim Zurückkehren aus dem Standby. Wirft nie. */
    suspend fun refreshIfDue(withinMs: Long = refreshSkewMs * 2) {
        runCatching {
            mutex.withLock {
                ensureLoaded()
                val current = tokens ?: return@withLock
                if (current.expiresAtMs - withinMs <= clock()) refreshLocked(current, current.profileId)
            }
        }
    }

    /** Holt ein Token für das gewählte Profil desselben Kontos und merkt sich die Wahl. */
    suspend fun switchProfile(profileId: String) = mutex.withLock {
        ensureLoaded()
        val current = tokens ?: throw SessionExpiredException()
        refreshLocked(current, profileId, switch = true, confirm = true)
        Unit
    }

    /** Das aktuelle Profil bestätigen, ohne neues Token (z. B. wenn das Konto nur ein Profil hat). */
    suspend fun confirmProfile() = mutex.withLock {
        ensureLoaded()
        val current = tokens ?: throw SessionExpiredException()
        if (current.profileConfirmed) return@withLock
        val next = current.copy(profileConfirmed = true)
        store.save(next)
        setTokens(next)
    }

    suspend fun onLoggedIn(newTokens: AuthTokens) = mutex.withLock {
        loaded = true
        store.save(newTokens)
        setTokens(newTokens)
    }

    suspend fun logout() = mutex.withLock {
        loaded = true
        store.clear()
        setTokens(null)
    }

    private suspend fun refreshLocked(
        current: AuthTokens,
        profileId: String?,
        switch: Boolean = false,
        confirm: Boolean = false,
    ): AuthTokens =
        when (val result = api.refresh(current.refreshToken, profileId, switch, current.refreshViaCookie)) {
            is TokenResult.Success -> {
                val next = result.tokens.copy(
                    accountId = result.tokens.accountId ?: current.accountId,
                    profileId = result.tokens.profileId ?: profileId,
                    profileConfirmed = confirm || current.profileConfirmed,
                )
                // Erst speichern, dann benutzen: Das alte Refresh-Token ist ab jetzt verbraucht.
                store.save(next)
                setTokens(next)
                next
            }
            TokenResult.InvalidGrant -> {
                store.clear()
                setTokens(null)
                throw SessionExpiredException()
            }
            is TokenResult.Transient -> throw TransientAuthException(result.cause)
        }

    private suspend fun ensureLoaded() {
        if (loaded) return
        setTokens(store.load())
        loaded = true
    }

    private fun setTokens(value: AuthTokens?) {
        tokens = value
        _state.value = if (value == null) SessionState.LoggedOut
        else SessionState.LoggedIn(value.accountId, value.profileId, value.profileConfirmed)
    }
}
