package com.github.blogshot.crtv.core

import com.github.blogshot.crtv.core.api.AuthInterceptor
import com.github.blogshot.crtv.core.api.ClientConfig
import com.github.blogshot.crtv.core.api.ClientCredentials
import com.github.blogshot.crtv.core.api.ClientCredentialsStore
import com.github.blogshot.crtv.core.api.CrunchyrollAuthApi
import com.github.blogshot.crtv.core.api.DefaultClientCredentialsProvider
import com.github.blogshot.crtv.core.api.TokenAuthenticator
import com.github.blogshot.crtv.core.api.WebsiteClientIdSource
import com.github.blogshot.crtv.core.auth.InMemoryTokenStore
import com.github.blogshot.crtv.core.auth.LoginResult
import com.github.blogshot.crtv.core.auth.SessionManager
import com.github.blogshot.crtv.core.content.CrunchyrollContentApi
import com.github.blogshot.crtv.core.content.CrunchyrollProfileApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/**
 * M0: echte API mit dem Testkonto. Läuft nur, wenn CR_USERNAME/CR_PASSWORD gesetzt sind (Workflow „API-Probe“).
 * Gibt ausschließlich Statuscodes, Anzahlen und Feldnamen aus, nie Werte.
 */
class ApiProbeTest {
    private val user = System.getenv("CR_USERNAME").orEmpty()
    private val pass = System.getenv("CR_PASSWORD").orEmpty()
    private val ua = "Mozilla/5.0 (Linux; Android 12; Google TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    private fun log(msg: String) = println("PROBE $msg")

    @Test
    fun probe() = runBlocking {
        assumeTrue("Keine Zugangsdaten gesetzt", user.isNotBlank() && pass.isNotBlank())
        val http = OkHttpClient()

        val website = WebsiteClientIdSource(http, ua)
        val webCreds = website.fetch()
        log("website client id: ${if (webCreds != null) "gefunden" else "NICHT gefunden"}")

        val store = object : ClientCredentialsStore {
            var v: ClientCredentials? = null
            override suspend fun load() = v
            override suspend fun save(credentials: ClientCredentials) { v = credentials }
        }
        val creds = DefaultClientCredentialsProvider(store, website)
        val config = ClientConfig(userAgent = ua, deviceId = UUID.randomUUID().toString())
        val auth = CrunchyrollAuthApi(http, config, creds)

        val login = auth.passwordLogin(user, pass)
        log("password login: ${login::class.simpleName} ${(login as? LoginResult.Failed)?.cause?.message.orEmpty()}")
        if (login !is LoginResult.Success) {
            rawLogin(http, creds.get(), config)
            rawGrant(http, creds.get(), "anonym (client_id)", "grant_type" to "client_id")
            rawGrant(http, creds.get(), "etp_rt_cookie ohne Cookie", "grant_type" to "etp_rt_cookie")
            val dc = runCatching { auth.requestDeviceCode() }
            log("device code (Web-Kennung): ${if (dc.isSuccess) "ok" else "fehlgeschlagen (${dc.exceptionOrNull()?.message})"}")
            return@runBlocking
        }
        log("token: accountId=${login.tokens.accountId != null} profileId=${login.tokens.profileId != null}")

        val code = runCatching { auth.requestDeviceCode() }
        log("device code: ${if (code.isSuccess) "ok" else "fehlgeschlagen (${code.exceptionOrNull()?.message})"}")

        val session = SessionManager(auth, InMemoryTokenStore(login.tokens))
        val api = http.newBuilder()
            .addInterceptor(AuthInterceptor(session, ua))
            .authenticator(TokenAuthenticator(session, ua))
            .build()
        val acc = login.tokens.accountId.orEmpty()

        val profiles = runCatching { CrunchyrollProfileApi(api).profiles() }
        log("profiles: ${profiles.map { "${it.size} Profile" }.getOrElse { "Fehler ${it.message}" }}")
        profiles.getOrNull()?.firstOrNull()?.let { p ->
            val switched = runCatching { session.switchProfile(p.id) }
            log("profile switch: ${if (switched.isSuccess) "ok" else "Fehler ${switched.exceptionOrNull()?.message}"}")
        }
        val refreshed = runCatching { session.accessToken(rejected = session.accessToken()) }
        log("refresh: ${if (refreshed.isSuccess) "ok" else "Fehler ${refreshed.exceptionOrNull()?.message}"}")

        val content = CrunchyrollContentApi(api)
        val browse = runCatching { content.browse() }
        log("browse: ${browse.map { "${it.items.size} Einträge" }.getOrElse { "Fehler ${it.message}" }}")
        log("continue watching: ${runCatching { content.continueWatching(acc).size }.fold({ "$it" }, { "Fehler ${it.message}" })}")
        log("watchlist: ${runCatching { content.watchlist(acc).items.size }.fold({ "$it" }, { "Fehler ${it.message}" })}")
        log("search: ${runCatching { content.search("one piece").size }.fold({ "$it" }, { "Fehler ${it.message}" })}")
        browse.getOrNull()?.items?.firstOrNull()?.let { series ->
            val seasons = runCatching { content.seasons(series.id) }
            log("seasons: ${seasons.map { "${it.size}" }.getOrElse { "Fehler ${it.message}" }}")
            seasons.getOrNull()?.firstOrNull()?.let { season ->
                log("episodes: ${runCatching { content.episodes(season.id).size }.fold({ "$it" }, { "Fehler ${it.message}" })}")
                shape(api, "/content/v2/cms/seasons/${season.id}/episodes?locale=de-DE")
            }
            shape(api, "/content/v2/cms/series/${series.id}/seasons?locale=de-DE")
        }
        shape(api, "/content/v2/discover/$acc/history?locale=de-DE&page_size=1")
        shape(api, "/content/v2/discover/$acc/watchlist?locale=de-DE&n=1")
        shape(api, "/content/v2/discover/browse?locale=de-DE&n=1")
        shape(api, "/accounts/v1/me/multiprofile")
    }

    /** Diagnose bei fehlgeschlagenem Login: Status, Fehlercodes und ob Cloudflare antwortet. Keine Werte. */
    private fun rawLogin(http: OkHttpClient, client: ClientCredentials, config: ClientConfig) {
        val variants = listOf(
            "mit Geräteangaben" to true,
            "ohne Geräteangaben" to false,
        )
        for ((name, withDevice) in variants) {
            val form = okhttp3.FormBody.Builder()
                .add("grant_type", "password")
                .add("username", user)
                .add("password", pass)
                .add("scope", "offline_access")
                .apply {
                    if (withDevice) {
                        add("device_id", config.deviceId)
                        add("device_name", config.deviceName)
                        add("device_type", config.deviceType)
                    }
                }
                .build()
            val req = Request.Builder()
                .url("https://www.crunchyroll.com/auth/v1/token")
                .header("Authorization", okhttp3.Credentials.basic(client.clientId, client.clientSecret))
                .header("User-Agent", ua)
                .post(form)
                .build()
            runCatching {
                http.newCall(req).execute().use { r ->
                    val body = r.body?.string().orEmpty()
                    val obj = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                    val cf = r.header("server")?.contains("cloudflare", true) == true
                    val html = body.trimStart().startsWith("<")
                    log(
                        "raw login ($name): HTTP ${r.code} server-cloudflare=$cf html=$html " +
                            "error=${obj?.get("error")} code=${obj?.get("code")} keys=${obj?.keys?.sorted()} " +
                            "cf-mitigated=${r.header("cf-mitigated")}",
                    )
                }
            }.onFailure { log("raw login ($name): Fehler ${it.message}") }
        }
    }

    private fun rawGrant(http: OkHttpClient, client: ClientCredentials, name: String, vararg fields: Pair<String, String>) {
        val form = okhttp3.FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder()
            .url("https://www.crunchyroll.com/auth/v1/token")
            .header("Authorization", okhttp3.Credentials.basic(client.clientId, client.clientSecret))
            .header("User-Agent", ua)
            .post(form)
            .build()
        runCatching {
            http.newCall(req).execute().use { r ->
                val obj = runCatching { Json.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject }.getOrNull()
                log("raw grant ($name): HTTP ${r.code} error=${obj?.get("error")} keys=${obj?.keys?.sorted()}")
            }
        }.onFailure { log("raw grant ($name): Fehler ${it.message}") }
    }

    /** Nur Feldnamen der Antwort und des ersten Elements in `data`/`profiles`. */
    private fun shape(http: OkHttpClient, path: String) {
        val label = path.substringBefore('?').replace(Regex("[0-9A-Z]{8,}|[0-9a-f-]{36}"), "{id}")
        runCatching {
            http.newCall(Request.Builder().url("https://www.crunchyroll.com$path").build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                val first = (root?.get("data") as? JsonArray ?: root?.get("profiles") as? JsonArray)?.firstOrNull() as? JsonObject
                log("shape $label: HTTP ${r.code} keys=${root?.keys?.sorted()} first=${first?.keys?.sorted()}")
                (first?.get("panel") as? JsonObject)?.let { log("shape $label panel=${it.keys.sorted()}") }
            }
        }.onFailure { log("shape $label: Fehler ${it.message}") }
    }
}
