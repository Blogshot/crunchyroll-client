package com.github.blogshot.crtv.core.content

import com.github.blogshot.crtv.core.api.ClientConfig
import com.github.blogshot.crtv.core.api.ClientCredentialsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64

/**
 * Diagnose für die Profilliste: Wenn `/accounts/v1/me/multiprofile` das Session-Token ablehnt, probiert die App
 * einige Varianten (andere Header, Cookie, frisch geholtes Token) und zeigt, welche Antwort jeweils kam.
 * Die Berichtszeilen enthalten nur Status, Fehlercodes und Claim-Namen, nie Token- oder ID-Werte.
 * Klappt eine Variante, liefert sie gleich die Profile.
 *
 * [http] ohne AuthInterceptor: Jede Variante setzt ihre Header selbst.
 */
class ProfileProbe(
    private val http: OkHttpClient,
    private val config: ClientConfig,
    private val credentials: ClientCredentialsProvider,
    private val baseUrl: String = "https://www.crunchyroll.com",
) {
    class Report(
        val lines: List<String>,
        val profiles: List<Profile>?,
        /** Hat der Server beim Token-Holen das Cookie rotiert, muss die Session es übernehmen. */
        val rotatedCookie: String?,
    )

    private val parser = CrunchyrollProfileApi(http, baseUrl)

    suspend fun run(accessToken: String, etpRt: String?): Report = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        var found: List<Profile>? = null
        var cookie = etpRt

        lines += "Token: " + describeClaims(accessToken)

        fun attempt(label: String, token: String, browserHeaders: Boolean = true, withCookie: Boolean = false, path: String = MULTIPROFILE) {
            val result = runCatching {
                val request = Request.Builder().url(baseUrl + path).get()
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", config.userAgent)
                    .header("Accept", "application/json")
                    .apply {
                        if (browserHeaders) {
                            header("Origin", "https://www.crunchyroll.com")
                            header("Referer", "https://www.crunchyroll.com/")
                        }
                        if (withCookie && cookie != null) header("Cookie", "etp_rt=$cookie")
                    }
                    .build()
                http.newCall(request).execute().use { r ->
                    val body = r.body?.string().orEmpty()
                    val parsed = if (r.isSuccessful) runCatching { parser.parse(body) }.getOrNull().orEmpty() else emptyList()
                    if (found == null && parsed.isNotEmpty()) found = parsed
                    "HTTP ${r.code}" + errorFields(body).let { if (it.isEmpty()) "" else " · $it" } +
                        if (r.isSuccessful) " · Felder: ${fieldNames(body)} · ${parsed.size} Profile" else ""
                }
            }.getOrElse { "${it::class.simpleName}: ${it.message}" }
            lines += "$label: $result"
        }

        attempt("/accounts/v1/me", accessToken, path = "/accounts/v1/me")
        attempt("/accounts/v1/me/profile", accessToken, path = "/accounts/v1/me/profile")
        attempt("multiprofile", accessToken)
        // Konto-ID im Pfad statt `me`; nur im Request, nicht im Bericht.
        claims(accessToken)?.get("etp_user_id")?.let { (it as? JsonPrimitive)?.contentOrNull }?.let { id ->
            attempt("/accounts/v1/<konto>/multiprofile", accessToken, path = "/accounts/v1/$id/multiprofile")
            attempt("/accounts/v1/<konto>/profiles", accessToken, path = "/accounts/v1/$id/profiles")
        }
        attempt("/accounts/v1/me/profiles", accessToken, path = "/accounts/v1/me/profiles")
        attempt("multiprofile ohne Origin", accessToken, browserHeaders = false)
        if (cookie != null) attempt("multiprofile + Cookie", accessToken, withCookie = true)

        if (cookie != null) {
            for (variant in TokenVariant.entries) {
                if (found != null) break
                val fetched = fetchToken(cookie!!, variant)
                fetched.rotated?.let { cookie = it }
                if (fetched.accessToken == null) {
                    lines += "Token ${variant.label}: ${fetched.status}"
                    continue
                }
                lines += "Token ${variant.label}: ${fetched.status} · " + describeClaims(fetched.accessToken, short = true)
                attempt("multiprofile mit Token ${variant.label}", fetched.accessToken)
                if (found == null) attempt("multiprofile mit Token ${variant.label} + Cookie", fetched.accessToken, withCookie = true)
            }
        } else {
            lines += "Kein Website-Cookie gespeichert, Token-Varianten übersprungen"
        }

        Report(lines, found, cookie.takeIf { it != etpRt })
    }

    private enum class TokenVariant(val label: String, val scope: String?, val device: Boolean) {
        Plain("ohne Gerätedaten", null, false),
        Device("mit Gerätedaten", null, true),
        Offline("mit offline_access", "offline_access", true),
    }

    private class Fetched(val status: String, val accessToken: String?, val rotated: String?)

    private suspend fun fetchToken(etpRt: String, variant: TokenVariant): Fetched {
        val client = runCatching { credentials.get() }.getOrElse { return Fetched("keine Client-Kennung", null, null) }
        val form = FormBody.Builder().add("grant_type", "etp_rt_cookie")
            .apply { variant.scope?.let { add("scope", it) } }
            .apply {
                if (variant.device) {
                    add("device_id", config.deviceId)
                    add("device_name", config.deviceName)
                    add("device_type", config.deviceType)
                }
            }
            .build()
        val request = Request.Builder().url("$baseUrl/auth/v1/token")
            .header("Authorization", Credentials.basic(client.clientId, client.clientSecret))
            .header("User-Agent", config.userAgent)
            .header("Cookie", "etp_rt=$etpRt")
            .post(form)
            .build()
        return runCatching {
            http.newCall(request).execute().use { r ->
                val body = r.body?.string().orEmpty()
                val rotated = r.headers("Set-Cookie").firstOrNull { it.startsWith("etp_rt=") }
                    ?.substringAfter("etp_rt=")?.substringBefore(';')?.takeIf { it.isNotEmpty() }
                val token = if (r.isSuccessful) {
                    runCatching { json.parseToJsonElement(body).jsonObject["access_token"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                } else null
                val fields = errorFields(body)
                Fetched("HTTP ${r.code}" + if (fields.isEmpty()) "" else " · $fields", token, rotated)
            }
        }.getOrElse { Fetched("${it::class.simpleName}: ${it.message}", null, null) }
    }

    private fun fieldNames(body: String): String =
        runCatching { (json.parseToJsonElement(body) as JsonObject).keys.take(12).joinToString(" ") }.getOrDefault("?")

    private fun errorFields(body: String): String {
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return ""
        return listOf("error", "code", "message").mapNotNull { k ->
            (obj[k] as? JsonPrimitive)?.contentOrNull?.let { "$k=$it" }
        }.joinToString(" ")
    }

    internal companion object {
        fun claims(token: String): JsonObject? {
            val payload = token.split('.').getOrNull(1) ?: return null
            return runCatching {
                json.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload.trimEnd('=')))).jsonObject
            }.getOrNull()
        }

        const val MULTIPROFILE = "/accounts/v1/me/multiprofile"
        private val json = Json { ignoreUnknownKeys = true }

        /** Werte nur für Claims, die nichts über das Konto verraten; sonst nur der Name. */
        private val SHOWN_VALUES = setOf(
            "scope", "scp", "scopes", "oauth_scopes", "aud", "iss", "device_type", "client_id", "client_tag",
            "type", "grant_type", "profile_type", "status", "tnt", "maturity", "extended_maturity",
        )

        /** Nur die Berechtigungen, für die Kurzfassung bei frisch geholten Tokens. */
        private val SHORT_KEYS = setOf("scope", "scopes", "oauth_scopes", "profile_type", "client_tag")

        /** Claims eines JWT ohne Signaturprüfung, nur zur Anzeige. */
        fun describeClaims(token: String, short: Boolean = false): String {
            val claims = claims(token) ?: return "JWT nicht lesbar"
            return claims.entries.filter { !short || it.key in SHORT_KEYS }.joinToString(", ") { (k, v) ->
                when {
                    k == "exp" -> (v as? JsonPrimitive)?.longOrNull
                        ?.let { "exp=in ${(it - System.currentTimeMillis() / 1000) / 60} min" } ?: k
                    k in SHOWN_VALUES -> "$k=" + when (v) {
                        is JsonPrimitive -> v.contentOrNull.orEmpty()
                        is JsonArray -> v.joinToString(" ") { (it as? JsonPrimitive)?.contentOrNull ?: "…" }
                        else -> "…"
                    }
                    else -> k
                }
            }
        }
    }
}
