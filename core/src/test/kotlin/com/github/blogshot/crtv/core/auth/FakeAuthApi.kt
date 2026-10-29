package com.github.blogshot.crtv.core.auth

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/** Simuliert Crunchyrolls Rotation: Jedes Refresh-Token ist genau einmal gültig. */
class FakeAuthApi(private val clock: () -> Long) : AuthApi {
    val refreshCalls = AtomicInteger()
    var nextResult: TokenResult? = null
    private val validRefreshTokens = mutableSetOf("r0")
    private var counter = 0

    var lastSwitch = false

    override suspend fun refresh(refreshToken: String, profileId: String?, switchProfile: Boolean): TokenResult {
        lastSwitch = switchProfile
        refreshCalls.incrementAndGet()
        delay(50)
        nextResult?.let { return it }
        if (!validRefreshTokens.remove(refreshToken)) return TokenResult.InvalidGrant
        counter++
        val next = "r$counter"
        validRefreshTokens += next
        return TokenResult.Success(AuthTokens("a$counter", next, clock() + 300_000, "acc", profileId))
    }

    override suspend fun passwordLogin(username: String, password: String) = error("nicht benutzt")
    override suspend fun requestDeviceCode() = error("nicht benutzt")
    override suspend fun pollDeviceCode(deviceCode: String) = error("nicht benutzt")
}
