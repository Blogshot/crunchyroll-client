package com.github.blogshot.crtv.core.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SessionManagerTest {
    private var now = 1_000_000L
    private val api = FakeAuthApi { now }

    private fun session(expiresInMs: Long): Pair<SessionManager, InMemoryTokenStore> {
        val store = InMemoryTokenStore(AuthTokens("a0", "r0", now + expiresInMs, "acc", "p1"))
        return SessionManager(api, store, clock = { now }) to store
    }

    @Test
    fun `gueltiges Token wird ohne Refresh geliefert`() = runTest {
        val (sm, _) = session(expiresInMs = 300_000)
        assertEquals("a0", sm.accessToken())
        assertEquals(0, api.refreshCalls.get())
    }

    @Test
    fun `parallele Aufrufe mit abgelaufenem Token loesen genau einen Refresh aus`() = runTest {
        val (sm, store) = session(expiresInMs = 0)
        val tokens = (1..50).map { async { sm.accessToken() } }.awaitAll()
        assertEquals(1, api.refreshCalls.get())
        assertTrue(tokens.all { it == "a1" })
        assertEquals("r1", store.tokens?.refreshToken)
    }

    @Test
    fun `parallele 401 auf dasselbe Token erneuern nur einmal`() = runTest {
        val (sm, _) = session(expiresInMs = 300_000)
        val tokens = (1..20).map { async { sm.accessToken(rejected = "a0") } }.awaitAll()
        assertEquals(1, api.refreshCalls.get())
        assertTrue(tokens.all { it == "a1" })
    }

    @Test
    fun `nach Standby wird beim Aufwachen proaktiv erneuert`() = runTest {
        val (sm, _) = session(expiresInMs = 300_000)
        now += 8 * 60 * 60 * 1000L // eine Nacht im Standby
        sm.refreshIfDue()
        assertEquals(1, api.refreshCalls.get())
        assertEquals("a1", sm.accessToken())
        assertEquals(1, api.refreshCalls.get())
    }

    @Test
    fun `Netzwerkfehler behaelt die Session`() = runTest {
        val (sm, store) = session(expiresInMs = 0)
        api.nextResult = TokenResult.Transient(IOException("offline"))
        try {
            sm.accessToken(); fail()
        } catch (_: TransientAuthException) {
        }
        assertNotNull(store.tokens)
        assertTrue(sm.state.value is SessionState.LoggedIn)

        api.nextResult = null
        assertEquals("a1", sm.accessToken())
    }

    @Test
    fun `invalid_grant meldet ab und loescht den Speicher`() = runTest {
        val (sm, store) = session(expiresInMs = 0)
        api.nextResult = TokenResult.InvalidGrant
        try {
            sm.accessToken(); fail()
        } catch (_: SessionExpiredException) {
        }
        assertNull(store.tokens)
        assertEquals(SessionState.LoggedOut, sm.state.value)
    }

    @Test
    fun `Profilwechsel behaelt das Profil ueber den naechsten Refresh`() = runTest {
        val (sm, store) = session(expiresInMs = 300_000)
        sm.switchProfile("p2")
        assertTrue(api.lastSwitch)
        assertEquals("p2", store.tokens?.profileId)
        assertEquals(true, store.tokens?.profileConfirmed)
        now += 600_000
        sm.accessToken()
        assertTrue(!api.lastSwitch)
        assertEquals("p2", store.tokens?.profileId)
        assertEquals(true, store.tokens?.profileConfirmed)
    }

    @Test
    fun `nach dem Login ist das Profil noch nicht gewaehlt`() = runTest {
        val sm = SessionManager(api, InMemoryTokenStore(), clock = { now })
        sm.onLoggedIn(AuthTokens("a", "r", now + 300_000, "acc", "p1"))
        assertEquals(SessionState.LoggedIn("acc", "p1", false), sm.state.value)
        sm.confirmProfile()
        assertEquals(SessionState.LoggedIn("acc", "p1", true), sm.state.value)
    }

    @Test
    fun `ohne gespeicherte Session ist der Zustand LoggedOut`() = runTest {
        val sm = SessionManager(api, InMemoryTokenStore(), clock = { now })
        sm.restore()
        assertEquals(SessionState.LoggedOut, sm.state.value)
    }
}
