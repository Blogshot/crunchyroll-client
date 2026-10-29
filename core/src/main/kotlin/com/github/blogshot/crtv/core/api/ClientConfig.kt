package com.github.blogshot.crtv.core.api

/**
 * Kennung des Clients gegenüber Crunchyroll. Die Werte stehen nicht im Repo, sondern kommen aus
 * `local.properties` bzw. CI-Secrets (siehe README).
 */
data class ClientConfig(
    val clientId: String,
    val clientSecret: String,
    val userAgent: String,
    /** Feste, zufällige ID dieses Geräts; wird einmal erzeugt und gespeichert. */
    val deviceId: String,
    val deviceName: String = "Google TV",
    val deviceType: String = "Android TV",
    val baseUrl: String = "https://www.crunchyroll.com",
    val activationUrl: String = "https://www.crunchyroll.com/activate",
)
