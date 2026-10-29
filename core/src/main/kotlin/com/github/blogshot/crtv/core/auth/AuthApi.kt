package com.github.blogshot.crtv.core.auth

/** Ergebnis eines Token-Requests. Nur [InvalidGrant] beendet eine Session. */
sealed interface TokenResult {
    data class Success(val tokens: AuthTokens) : TokenResult

    /** Refresh-Token ist ungültig oder widerrufen: neu einloggen. */
    data object InvalidGrant : TokenResult

    /** Netzwerk, Timeout, 5xx, Rate-Limit: Session behalten und später erneut versuchen. */
    data class Transient(val cause: Throwable?) : TokenResult
}

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSec: Long,
    val intervalSec: Long,
)

sealed interface DevicePoll {
    data object Pending : DevicePoll
    data object Expired : DevicePoll
    data class Approved(val tokens: AuthTokens) : DevicePoll
    data class Failed(val cause: Throwable?) : DevicePoll
}

sealed interface LoginResult {
    data class Success(val tokens: AuthTokens) : LoginResult
    data object WrongCredentials : LoginResult
    /** Keine Client-Kennung verfügbar: UI fragt den Nutzer danach. */
    data object MissingClientId : LoginResult
    data class Failed(val cause: Throwable?) : LoginResult
}

interface AuthApi {
    suspend fun passwordLogin(username: String, password: String): LoginResult

    /** Tauscht die Sitzung der offiziellen Login-Seite (Cookie `etp_rt`) in Token. */
    suspend fun exchangeWebSession(etpRt: String): LoginResult
    /** @param switchProfile true, wenn auf [profileId] gewechselt werden soll (eigener Grant-Typ). */
    suspend fun refresh(
        refreshToken: String,
        profileId: String?,
        switchProfile: Boolean = false,
        viaCookie: Boolean = false,
    ): TokenResult
    suspend fun requestDeviceCode(): DeviceCode
    suspend fun pollDeviceCode(deviceCode: String): DevicePoll
}
