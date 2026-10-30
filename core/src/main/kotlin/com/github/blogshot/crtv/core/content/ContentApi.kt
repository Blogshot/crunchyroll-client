package com.github.blogshot.crtv.core.content

/** Lesende Katalog-Endpunkte. Alle Aufrufe brauchen eine aktive Session (siehe AuthInterceptor). */
interface ContentApi {
    suspend fun continueWatching(accountId: String, limit: Int = 20): List<EpisodeCard>
    suspend fun watchlist(accountId: String, start: Int = 0, limit: Int = 50): Page<MediaCard>
    suspend fun browse(sortBy: String = "popularity", start: Int = 0, limit: Int = 50): Page<MediaCard>
    suspend fun search(query: String, limit: Int = 30): List<MediaCard>
    suspend fun series(seriesId: String): MediaCard
    suspend fun seasons(seriesId: String): List<Season>
    suspend fun episodes(seasonId: String): List<EpisodeCard>
}
