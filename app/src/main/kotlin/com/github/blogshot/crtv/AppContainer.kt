package com.github.blogshot.crtv

import android.content.Context
import android.os.Build
import com.github.blogshot.crtv.core.api.AuthInterceptor
import com.github.blogshot.crtv.core.api.ClientConfig
import com.github.blogshot.crtv.core.api.CrunchyrollAuthApi
import com.github.blogshot.crtv.core.api.DefaultClientCredentialsProvider
import com.github.blogshot.crtv.core.api.WebsiteClientIdSource
import com.github.blogshot.crtv.core.api.TokenAuthenticator
import com.github.blogshot.crtv.core.auth.SessionManager
import com.github.blogshot.crtv.core.auth.SessionState
import com.github.blogshot.crtv.core.content.ContentApi
import com.github.blogshot.crtv.core.content.CrunchyrollContentApi
import com.github.blogshot.crtv.core.content.CrunchyrollProfileApi
import com.github.blogshot.crtv.core.content.MemoryCache
import com.github.blogshot.crtv.core.content.ProfileApi
import com.github.blogshot.crtv.core.content.ProfileProbe
import com.github.blogshot.crtv.data.EncryptedClientCredentialsStore
import com.github.blogshot.crtv.data.EncryptedTokenStore
import com.github.blogshot.crtv.data.deviceId
import com.github.blogshot.crtv.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Handverdrahtete Abhängigkeiten; genau ein [SessionManager] pro Prozess. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Einmaliger, kleiner DataStore-Read beim Start; die ID muss feststehen, bevor der erste Request läuft.
    val config = ClientConfig(
        userAgent = USER_AGENT,
        deviceId = runBlocking { context.deviceId() },
        deviceName = Build.MODEL ?: "Google TV",
    )

    private val baseHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Kennung der App: zuerst gespeichert, sonst von crunchyroll.com, sonst fragt der Login-Bildschirm. */
    val clientCredentials = DefaultClientCredentialsProvider(
        store = EncryptedClientCredentialsStore(context),
        website = WebsiteClientIdSource(baseHttp, USER_AGENT),
    )

    val authApi = CrunchyrollAuthApi(baseHttp, config, clientCredentials)

    val session = SessionManager(api = authApi, store = EncryptedTokenStore(context))

    val updater = Updater(context.applicationContext, baseHttp)

    /** Für alle API-Aufrufe außer den Token-Endpunkten. */
    val apiHttp: OkHttpClient = baseHttp.newBuilder()
        .addInterceptor(AuthInterceptor(session, config.userAgent))
        .authenticator(TokenAuthenticator(session, config.userAgent))
        .build()

    val content: ContentApi = CrunchyrollContentApi(apiHttp)
    val profiles: ProfileApi = CrunchyrollProfileApi(apiHttp)
    val profileProbe = ProfileProbe(baseHttp, config, clientCredentials)

    /** Überlebt Navigation und Tabwechsel; Bildschirme zeigen beim Zurückkehren sofort den letzten Stand. */
    val cache = MemoryCache()

    val accountId: String
        get() = (session.state.value as? SessionState.LoggedIn)?.accountId.orEmpty()

    private companion object {
        // Die Client-Kennung stammt von der Web-App, also meldet sich die App auch wie ein Browser.
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; Google TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    }
}
