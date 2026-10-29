package com.github.blogshot.crtv.core.api

import com.github.blogshot.crtv.core.auth.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/** Hängt das aktuelle Access-Token an jeden API-Request. */
class AuthInterceptor(private val session: SessionManager, private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { session.accessToken() }
        return chain.proceed(chain.request().withAuth(token, userAgent))
    }
}

/**
 * Bei 401 einmal erneuern und wiederholen. Der zweite 401 auf denselben Request geht an den Aufrufer,
 * damit eine kaputte Antwort keine Endlosschleife auslöst.
 */
class TokenAuthenticator(private val session: SessionManager, private val userAgent: String) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val rejected = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        val fresh = runCatching { runBlocking { session.accessToken(rejected = rejected) } }.getOrNull() ?: return null
        return response.request.withAuth(fresh, userAgent)
    }
}

private fun Request.withAuth(token: String, userAgent: String) = newBuilder()
    .header("Authorization", "Bearer $token")
    .header("User-Agent", userAgent)
    .build()
