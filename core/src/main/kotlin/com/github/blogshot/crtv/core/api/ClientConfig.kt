package com.github.blogshot.crtv.core.api

/** Geräte- und Endpunktdaten. Die Client-Kennung kommt separat aus einem [ClientCredentialsProvider]. */
data class ClientConfig(
    val userAgent: String,
    /** Feste, zufällige ID dieses Geräts; wird einmal erzeugt und gespeichert. */
    val deviceId: String,
    val deviceName: String = "Google TV",
    val deviceType: String = "Android TV",
    val baseUrl: String = "https://www.crunchyroll.com",
    val activationUrl: String = "https://www.crunchyroll.com/activate",
)
