package com.github.blogshot.crtv.core.auth

import kotlinx.serialization.Serializable

/** Alles, was nötig ist, um eine Session nach einem Neustart fortzusetzen. */
@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    /** Ablauf des Access-Tokens (epoch ms). */
    val expiresAtMs: Long,
    val accountId: String? = null,
    val profileId: String? = null,
    /** Nutzer hat nach dem Login ein Profil gewählt (oder es gibt nur eines). */
    val profileConfirmed: Boolean = false,
)

/** Persistenter Speicher für die Session. Auf Android verschlüsselt im DataStore. */
interface TokenStore {
    suspend fun load(): AuthTokens?
    suspend fun save(tokens: AuthTokens)
    suspend fun clear()
}

class InMemoryTokenStore(initial: AuthTokens? = null) : TokenStore {
    @Volatile var tokens: AuthTokens? = initial
        private set

    override suspend fun load() = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}
