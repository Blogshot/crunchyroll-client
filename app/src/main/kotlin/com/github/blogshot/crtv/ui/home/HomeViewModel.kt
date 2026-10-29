package com.github.blogshot.crtv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.blogshot.crtv.AppContainer
import com.github.blogshot.crtv.core.content.Resource

/** Jede Rail ist eine eigene [Resource]: eigener Cache, eigener Fehler, eigenes „Erneut versuchen“. */
class HomeViewModel(c: AppContainer) : ViewModel() {
    val continueWatching = Resource(viewModelScope, c.cache, "continue", maxAgeMs = 60_000) {
        c.content.continueWatching(c.accountId)
    }
    val watchlist = Resource(viewModelScope, c.cache, "watchlist") {
        c.content.watchlist(c.accountId).items
    }
    val popular = Resource(viewModelScope, c.cache, "browse:popularity", maxAgeMs = 30 * 60_000) {
        c.content.browse("popularity").items
    }
    val newest = Resource(viewModelScope, c.cache, "browse:newly_added", maxAgeMs = 30 * 60_000) {
        c.content.browse("newly_added").items
    }

    private val all = listOf(continueWatching, watchlist, popular, newest)

    /** Bei jedem Zurückkehren auf die Startseite: nur veraltete Rails im Hintergrund neu laden. */
    fun onResume() = all.forEach { it.refreshIfStale() }

    fun refreshAll() = all.forEach { it.refresh() }
}
