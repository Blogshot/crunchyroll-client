package com.github.blogshot.crtv.core.auth

import com.github.blogshot.crtv.core.api.AuthInterceptor
import com.github.blogshot.crtv.core.api.ClientConfig
import com.github.blogshot.crtv.core.api.CrunchyrollAuthApi
import com.github.blogshot.crtv.core.api.TokenAuthenticator
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpAuthTest {
    private val server = MockWebServer()
    private val now = 1_000_000L

    @After fun tearDown() = server.shutdown()

    private fun config() = ClientConfig(
        clientId = "id", clientSecret = "secret", userAgent = "test-ua", deviceId = "dev-1",
        baseUrl = server.url("").toString().trimEnd('/'),
    )

    private fun tokenJson(n: Int) =
        """{"access_token":"a$n","refresh_token":"r$n","expires_in":300,"account_id":"acc","profile_id":"p1","country":"DE"}"""

    @Test
    fun `refresh sendet rotierendes Token und parst die Antwort`() = runTest {
        server.enqueue(MockResponse().setBody(tokenJson(1)))
        val api = CrunchyrollAuthApi(OkHttpClient(), config()) { now }

        val result = api.refresh("r0", "p1") as TokenResult.Success

        assertEquals("a1", result.tokens.accessToken)
        assertEquals(now + 300_000, result.tokens.expiresAtMs)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("grant_type=refresh_token"))
        assertTrue(body.contains("refresh_token=r0"))
        assertTrue(body.contains("device_id=dev-1"))
    }

    @Test
    fun `nur invalid_grant gilt als Abmeldung`() = runTest {
        val api = CrunchyrollAuthApi(OkHttpClient(), config()) { now }
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        assertEquals(TokenResult.InvalidGrant, api.refresh("r0", null))

        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(api.refresh("r0", null) is TokenResult.Transient)

        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"too_many_requests"}"""))
        assertTrue(api.refresh("r0", null) is TokenResult.Transient)
    }

    @Test
    fun `401 erneuert einmal und wiederholt den Request`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/auth/v1/token" -> MockResponse().setBody(tokenJson(1))
                request.getHeader("Authorization") == "Bearer a1" -> MockResponse().setBody("ok")
                else -> MockResponse().setResponseCode(401)
            }
        }
        val store = InMemoryTokenStore(AuthTokens("a0", "r0", Long.MAX_VALUE))
        val session = SessionManager(CrunchyrollAuthApi(OkHttpClient(), config()), store)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session, "test-ua"))
            .authenticator(TokenAuthenticator(session, "test-ua"))
            .build()

        val body = client.newCall(Request.Builder().url(server.url("/content/v2/discover/home_feed")).build())
            .execute().use { it.body?.string() }

        assertEquals("ok", body)
        assertEquals("r1", store.tokens?.refreshToken)
    }
}
