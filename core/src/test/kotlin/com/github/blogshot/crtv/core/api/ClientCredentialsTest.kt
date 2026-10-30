package com.github.blogshot.crtv.core.api

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientCredentialsTest {
    private val server = MockWebServer()
    private val website by lazy { WebsiteClientIdSource(OkHttpClient(), "ua", server.url("").toString().trimEnd('/')) }

    private class MemStore(var value: ClientCredentials? = null) : ClientCredentialsStore {
        override suspend fun load() = value
        override suspend fun save(credentials: ClientCredentials) { value = credentials }
    }

    @After fun tearDown() = server.shutdown()

    @Test
    fun `Kennung wird aus der App-Konfiguration der Startseite gelesen`() {
        val html = """<script>window.__APP_CONFIG__ = {"cxApiParams":{"accountAuthClientId":"abc_123-x","anonClientId":"zzz"}};</script>"""
        assertEquals(ClientCredentials("abc_123-x"), WebsiteClientIdSource.parse(html))
        assertNull(WebsiteClientIdSource.parse("<html>nichts</html>"))
    }

    @Test
    fun `Website-Kennung wird gespeichert und danach ohne Netz benutzt`() = runTest {
        server.enqueue(MockResponse().setBody("""accountAuthClientId: "web1""""))
        val store = MemStore()
        val provider = DefaultClientCredentialsProvider(store, website)
        assertEquals("web1", provider.get().clientId)
        assertEquals("web1", store.value?.clientId)
        assertEquals("web1", DefaultClientCredentialsProvider(store, website).get().clientId)
        assertEquals(1, server.requestCount)
    }

    @Test(expected = MissingClientCredentialsException::class)
    fun `ohne Website und ohne gespeicherte Kennung wird nach Eingabe gefragt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        DefaultClientCredentialsProvider(MemStore(), website).get()
    }

    @Test
    fun `nach invalid_client wird die gespeicherte Kennung uebersprungen`() = runTest {
        server.enqueue(MockResponse().setBody("""accountAuthClientId:"neu""""))
        val store = MemStore(ClientCredentials("alt"))
        val provider = DefaultClientCredentialsProvider(store, website)
        assertEquals("alt", provider.get().clientId)
        provider.invalidate()
        assertEquals("neu", provider.get().clientId)
        provider.setManually(ClientCredentials("manuell", "s"))
        assertEquals(ClientCredentials("manuell", "s"), provider.get())
    }
}
