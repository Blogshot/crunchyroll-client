package com.github.blogshot.crtv.core.content

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ResourceTest {
    private var now = 0L
    private val cache = MemoryCache { now }

    private fun TestScope.resource(fetch: suspend () -> List<String>) =
        Resource(this.backgroundScope, cache, "home", maxAgeMs = 1_000, clock = { now }, fetch = fetch)

    @Test
    fun `erster Aufruf laedt und speichert`() = runTest {
        val r = resource { listOf("a") }
        assertEquals(Loadable.Loading, r.state.value)
        runCurrent()
        assertEquals(Loadable.Content(listOf("a")), r.state.value)
    }

    @Test
    fun `frischer Cache wird sofort gezeigt und nicht neu geladen`() = runTest {
        cache.put("home", listOf("cached"))
        var calls = 0
        val r = resource { calls++; listOf("net") }
        assertEquals(Loadable.Content(listOf("cached")), r.state.value)
        runCurrent()
        assertEquals(0, calls)
    }

    @Test
    fun `veralteter Cache wird gezeigt und im Hintergrund ersetzt`() = runTest {
        cache.put("home", listOf("old"))
        now = 5_000
        val gate = CompletableDeferred<Unit>()
        val r = resource { gate.await(); listOf("new") }
        runCurrent()
        assertEquals(Loadable.Content(listOf("old"), refreshing = true), r.state.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(Loadable.Content(listOf("new")), r.state.value)
    }

    @Test
    fun `Fehler behaelt vorhandene Daten und haengt den Fehler an`() = runTest {
        cache.put("home", listOf("old"))
        now = 5_000
        val r = resource { throw IOException("offline") }
        runCurrent()
        val s = r.state.value as Loadable.Content
        assertEquals(listOf("old"), s.data)
        assertTrue(s.error is IOException)
    }

    @Test
    fun `Fehler ohne Cache ergibt Error und Erneut-versuchen klappt`() = runTest {
        var fail = true
        val r = resource { if (fail) throw IOException("offline") else listOf("ok") }
        runCurrent()
        assertTrue(r.state.value is Loadable.Error)
        fail = false
        r.refresh()
        runCurrent()
        assertEquals(Loadable.Content(listOf("ok")), r.state.value)
    }

    @Test
    fun `parallele Aktualisierungen werden zusammengelegt`() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val r = resource { calls++; gate.await(); listOf("x") }
        repeat(5) { r.refresh() }
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, calls)
    }
}
