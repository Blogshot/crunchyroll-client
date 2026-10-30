package com.github.blogshot.crtv.core.content

/** Domain-Modelle für die UI; unabhängig von der Form der API-Antworten. */

data class MediaCard(
    val id: String,
    /** "series" oder "movie_listing"; Folgen tauchen als [EpisodeCard] auf. */
    val type: String,
    val title: String,
    val description: String = "",
    val posterTall: String? = null,
    val posterWide: String? = null,
)

data class EpisodeCard(
    val id: String,
    val seriesId: String?,
    val seriesTitle: String,
    val title: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val durationMs: Long,
    val thumbnail: String?,
    /** Gesehene Sekunden, falls bekannt (Weiterschauen). */
    val playheadSec: Long? = null,
    val fullyWatched: Boolean = false,
)

data class Season(
    val id: String,
    val title: String,
    val seasonNumber: Int,
    val audioLocales: List<String> = emptyList(),
)

data class Page<T>(val items: List<T>, val total: Int)
