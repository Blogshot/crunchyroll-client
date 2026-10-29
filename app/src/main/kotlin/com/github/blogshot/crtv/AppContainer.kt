package com.github.blogshot.crtv

import android.content.Context
import android.os.Build
import com.github.blogshot.crtv.core.api.AuthInterceptor
import com.github.blogshot.crtv.core.api.ClientConfig
import com.github.blogshot.crtv.core.api.CrunchyrollAuthApi
import com.github.blogshot.crtv.core.api.TokenAuthenticator
import com.github.blogshot.crtv.core.auth.SessionManager
import com.github.blogshot.crtv.core.auth.SessionState
import com.github.blogshot.crtv.core.content.ContentApi
import com.github.blogshot.crtv.core.content.CrunchyrollContentApi
import com.github.blogshot.crtv.core.content.MemoryCache
import com.github.blogshot.crtv.data.EncryptedTokenStore
import com.github.blogshot.crtv.data.deviceId
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
        clientId = BuildConfig.CR_CLIENT_ID,
        clientSecret = BuildConfig.CR_CLIENT_SECRET,
        userAgent = BuildConfig.CR_USER_AGENT.ifBlank { "Crunchyroll/ANDROIDTV" },
        deviceId = runBlocking { context.deviceId() },
        deviceName = Build.MODEL ?: "Google TV",
    )

    private val baseHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val authApi = CrunchyrollAuthApi(baseHttp, config)

    val session = SessionManager(api = authApi, store = EncryptedTokenStore(context))

    /** Für alle API-Aufrufe außer den Token-Endpunkten. */
    val apiHttp: OkHttpClient = baseHttp.newBuilder()
        .addInterceptor(AuthInterceptor(session, config.userAgent))
        .authenticator(TokenAuthenticator(session, config.userAgent))
        .build()

    val content: ContentApi = CrunchyrollContentApi(apiHttp)

    /** Überlebt Navigation und Tabwechsel; Bildschirme zeigen beim Zurückkehren sofort den letzten Stand. */
    val cache = MemoryCache()

    val accountId: String
        get() = (session.state.value as? SessionState.LoggedIn)?.accountId.orEmpty()
}
