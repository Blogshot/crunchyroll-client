package com.github.blogshot.crtv.core.content

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/** Was ein Bildschirm (oder eine einzelne Rail) anzeigt. Es gibt keinen Zustand „leer ohne Grund“. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    /** [error] ist gesetzt, wenn die letzte Aktualisierung scheiterte und [data] aus dem Cache stammt. */
    data class Content<T>(val data: T, val refreshing: Boolean = false, val error: Throwable? = null) : Loadable<T>

    data class Error(val error: Throwable) : Loadable<Nothing>
}

/** Prozessweiter Cache; überlebt Navigation und Tabwechsel. */
class MemoryCache(private val clock: () -> Long = System::currentTimeMillis) {
    private class Entry(val value: Any?, val storedAtMs: Long)
    private val map = ConcurrentHashMap<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): Pair<T, Long>? = map[key]?.let { it.value as T to it.storedAtMs }
    fun put(key: String, value: Any?) { map[key] = Entry(value, clock()) }
    fun invalidate(prefix: String) { map.keys.removeIf { it.startsWith(prefix) } }
}

/**
 * Stale-while-revalidate: zeigt sofort den Cache, lädt im Hintergrund nach, wenn er älter als [maxAgeMs] ist.
 * Ein Fehler ersetzt vorhandene Daten nie, sondern hängt sich als Hinweis an. Parallele Aktualisierungen
 * werden zusammengelegt.
 */
class Resource<T>(
    private val scope: CoroutineScope,
    private val cache: MemoryCache,
    private val key: String,
    private val maxAgeMs: Long = 5 * 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fetch: suspend () -> T,
) {
    private val _state = MutableStateFlow<Loadable<T>>(Loadable.Loading)
    val state: StateFlow<Loadable<T>> = _state.asStateFlow()
    private var job: Job? = null

    init {
        cache.get<T>(key)?.let { (value, _) -> _state.value = Loadable.Content(value) }
        refreshIfStale()
    }

    /** Beim Öffnen und beim Zurückkehren auf den Bildschirm aufrufen. */
    fun refreshIfStale() {
        val cached = cache.get<T>(key)
        if (cached == null || clock() - cached.second >= maxAgeMs) refresh()
    }

    /** „Erneut versuchen“ und gezielte Invalidierung (z. B. Weiterschauen nach der Wiedergabe). */
    fun refresh() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = when (val s = _state.value) {
                is Loadable.Content -> s.copy(refreshing = true, error = null)
                else -> Loadable.Loading
            }
            _state.value = try {
                val value = fetch()
                cache.put(key, value)
                Loadable.Content(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when (val s = _state.value) {
                    is Loadable.Content -> s.copy(refreshing = false, error = e)
                    else -> Loadable.Error(e)
                }
            }
        }
    }
}
