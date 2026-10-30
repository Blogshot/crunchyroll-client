package com.github.blogshot.crtv.core.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class HttpStatusException(val code: Int, url: String, detail: String = "") :
    IOException("HTTP $code für $url" + if (detail.isNotEmpty()) " · $detail" else "")

/** Kurzbeschreibung einer Fehlantwort: Crunchyroll-Fehlercodes oder ob Cloudflare blockt. Keine Inhalte. */
fun describeFailure(response: okhttp3.Response): String {
    val body = runCatching { response.peekBody(4096).string() }.getOrDefault("")
    val obj = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    val fields = listOf("error", "code", "message").mapNotNull { k ->
        obj?.get(k)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.let { "$k=$it" }
    }
    val cf = listOfNotNull(
        "cloudflare".takeIf { response.header("server")?.contains("cloudflare", true) == true && obj == null },
        response.header("cf-mitigated")?.let { "cf-mitigated=$it" },
        "html".takeIf { body.trimStart().startsWith("<") },
    )
    return (fields + cf).joinToString(" ")
}

/**
 * Katalog über `/content/v2`. Die Pfade sind aus der Web-App abgeleitet und werden in M0 mit echten Antworten
 * als Fixtures festgehalten. Das Parsing ist absichtlich nachsichtig: Fehlende Felder ergeben leere Werte statt
 * eines Absturzes, damit eine API-Änderung eine Rail leer lässt statt die ganze Startseite.
 *
 * [http] muss den AuthInterceptor enthalten.
 */
class CrunchyrollContentApi(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://www.crunchyroll.com",
    private val locale: String = "de-DE",
) : ContentApi {

    override suspend fun continueWatching(accountId: String, limit: Int): List<EpisodeCard> =
        data(get("/content/v2/discover/$accountId/history") { addQueryParameter("page_size", "$limit") })
            .mapNotNull { item ->
                val o = item.obj ?: return@mapNotNull null
                val panel = o["panel"]?.obj ?: return@mapNotNull null
                toEpisode(panel)?.copy(
                    playheadSec = o["playhead"]?.long,
                    fullyWatched = o["fully_watched"]?.bool ?: false,
                )
            }
            // Fertig gesehene Folgen gehören nicht in „Weiterschauen“.
            .filterNot { it.fullyWatched }

    override suspend fun watchlist(accountId: String, start: Int, limit: Int): Page<MediaCard> {
        val root = get("/content/v2/discover/$accountId/watchlist") {
            addQueryParameter("order", "desc")
            addQueryParameter("start", "$start")
            addQueryParameter("n", "$limit")
        }
        val items = data(root).mapNotNull { it.obj?.get("panel")?.obj }.mapNotNull(::toSeriesCard)
        return Page(items, root.total ?: items.size)
    }

    override suspend fun browse(sortBy: String, start: Int, limit: Int): Page<MediaCard> {
        val root = get("/content/v2/discover/browse") {
            addQueryParameter("sort_by", sortBy)
            addQueryParameter("start", "$start")
            addQueryParameter("n", "$limit")
        }
        val items = data(root).mapNotNull { it.obj }.mapNotNull(::toSeriesCard)
        return Page(items, root.total ?: items.size)
    }

    override suspend fun search(query: String, limit: Int): List<MediaCard> {
        val root = get("/content/v2/discover/search") {
            addQueryParameter("q", query)
            addQueryParameter("n", "$limit")
            addQueryParameter("type", "series,movie_listing")
        }
        // Antwort ist nach Typ gruppiert: data = [{type, items: [...]}, ...]
        return data(root)
            .flatMap { it.obj?.get("items")?.arr.orEmpty() }
            .mapNotNull { it.obj }
            .mapNotNull(::toSeriesCard)
    }

    override suspend fun series(seriesId: String): MediaCard =
        data(get("/content/v2/cms/series/$seriesId")).firstNotNullOfOrNull { it.obj?.let(::toSeriesCard) }
            ?: throw IOException("Serie $seriesId nicht gefunden")

    override suspend fun seasons(seriesId: String): List<Season> =
        data(get("/content/v2/cms/series/$seriesId/seasons")).mapNotNull { el ->
            val o = el.obj ?: return@mapNotNull null
            Season(
                id = o.str("id") ?: return@mapNotNull null,
                title = o.str("title").orEmpty(),
                seasonNumber = o["season_number"]?.int ?: 0,
                audioLocales = o["audio_locales"]?.arr.orEmpty().mapNotNull { it.prim },
            )
        }.sortedBy { it.seasonNumber }

    override suspend fun episodes(seasonId: String): List<EpisodeCard> =
        data(get("/content/v2/cms/seasons/$seasonId/episodes")).mapNotNull { it.obj?.let(::toEpisode) }
            .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }

    private fun toSeriesCard(o: JsonObject): MediaCard? {
        val id = o.str("id") ?: return null
        return MediaCard(
            id = id,
            type = o.str("type") ?: "series",
            title = o.str("title").orEmpty(),
            description = o.str("description").orEmpty(),
            posterTall = o.image("poster_tall", 480),
            posterWide = o.image("poster_wide", 960),
        )
    }

    /** Folge entweder als flaches CMS-Objekt oder als Panel mit `episode_metadata`. */
    private fun toEpisode(o: JsonObject): EpisodeCard? {
        val id = o.str("id") ?: return null
        val meta = o["episode_metadata"]?.obj ?: o
        return EpisodeCard(
            id = id,
            seriesId = meta.str("series_id"),
            seriesTitle = meta.str("series_title").orEmpty(),
            title = o.str("title").orEmpty(),
            seasonNumber = meta["season_number"]?.int,
            episodeNumber = meta["episode_number"]?.int,
            durationMs = meta["duration_ms"]?.long ?: 0,
            thumbnail = o.image("thumbnail", 640),
        )
    }

    /** Bilder kommen als `images.<name> = [[{source, width, height}, ...]]`; nimm die kleinste Variante ≥ [minWidth]. */
    private fun JsonObject.image(name: String, minWidth: Int): String? {
        val variants = this["images"]?.obj?.get(name)?.arr?.firstOrNull()?.arr.orEmpty().mapNotNull { it.obj }
        val sorted = variants.sortedBy { it["width"]?.int ?: 0 }
        val pick = sorted.firstOrNull { (it["width"]?.int ?: 0) >= minWidth } ?: sorted.lastOrNull()
        return pick?.str("source")
    }

    private suspend fun get(path: String, query: HttpUrl.Builder.() -> Unit = {}): JsonObject =
        withContext(Dispatchers.IO) {
            val url = (baseUrl + path).toHttpUrl().newBuilder()
                .addQueryParameter("locale", locale)
                .apply(query)
                .build()
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw HttpStatusException(response.code, url.encodedPath, describeFailure(response))
                json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            }
        }

    private fun data(root: JsonObject): List<JsonElement> = root["data"]?.arr.orEmpty()
    private val JsonObject.total get() = this["total"]?.int

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private val JsonElement.obj get() = this as? JsonObject
private val JsonElement.arr get() = this as? JsonArray
private val JsonElement.prim get() = runCatching { jsonPrimitive.contentOrNull }.getOrNull()
private val JsonElement.int get() = runCatching { jsonPrimitive.intOrNull }.getOrNull()
private val JsonElement.long get() = runCatching { jsonPrimitive.longOrNull }.getOrNull()
private val JsonElement.bool get() = runCatching { jsonPrimitive.booleanOrNull }.getOrNull()
private fun JsonObject.str(key: String) = this[key]?.prim
