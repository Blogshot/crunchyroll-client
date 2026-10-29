package com.github.blogshot.crtv.core.content

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetische Fixtures in der erwarteten Form; werden in M0 durch echte Antworten ersetzt. */
class ContentApiTest {
    private val server = MockWebServer()
    private val api by lazy { CrunchyrollContentApi(OkHttpClient(), server.url("").toString().trimEnd('/')) }

    @After fun tearDown() = server.shutdown()

    private fun fixture(name: String) =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "Fixture $name fehlt" }.readText()

    @Test
    fun `Weiterschauen filtert fertige Folgen und uebernimmt die Position`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("history.json")))
        val items = api.continueWatching("acc")
        assertEquals(listOf("E1"), items.map { it.id })
        assertEquals(754L, items[0].playheadSec)
        assertEquals("Frieren", items[0].seriesTitle)
        assertEquals("https://img/t-640.jpg", items[0].thumbnail)
        val path = server.takeRequest().path!!
        assertTrue(path.startsWith("/content/v2/discover/acc/history?locale=de-DE"))
    }

    @Test
    fun `Staffeln werden sortiert und Folgen ohne Nummer landen hinten`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("seasons.json")))
        assertEquals(listOf(1, 2), api.seasons("S1").map { it.seasonNumber })
        server.enqueue(MockResponse().setBody(fixture("episodes.json")))
        assertEquals(listOf(1, 2, null), api.episodes("SE1").map { it.episodeNumber })
    }

    @Test
    fun `Suche flacht die Gruppen ab und ueberlebt fehlende Bilder`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("search.json")))
        val result = api.search("frieren")
        assertEquals(listOf("S1", "M1"), result.map { it.id })
        assertNull(result[1].posterTall)
    }

    @Test(expected = HttpStatusException::class)
    fun `HTTP-Fehler werden als Exception gemeldet`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        api.watchlist("acc")
    }
}
