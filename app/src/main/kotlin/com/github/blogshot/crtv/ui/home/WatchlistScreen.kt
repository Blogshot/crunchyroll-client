package com.github.blogshot.crtv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.blogshot.crtv.AppContainer
import com.github.blogshot.crtv.core.content.Loadable
import com.github.blogshot.crtv.core.content.MediaCard
import com.github.blogshot.crtv.core.content.Resource
import com.github.blogshot.crtv.ui.components.ErrorRow
import com.github.blogshot.crtv.ui.components.PosterCard

class WatchlistViewModel(c: AppContainer) : ViewModel() {
    // Gleicher Cache-Schlüssel wie die Rail auf der Startseite: beide zeigen denselben Stand.
    val watchlist = Resource(viewModelScope, c.cache, "watchlist") {
        c.content.watchlist(c.accountId, limit = 100).items
    }
}

@Composable
fun WatchlistScreen(vm: WatchlistViewModel, reselect: Int, onOpenSeries: (MediaCard) -> Unit) {
    val gridState = rememberLazyGridState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.watchlist.refreshIfStale() }
    LaunchedEffect(reselect) {
        if (reselect > 0) {
            gridState.animateScrollToItem(0)
            vm.watchlist.refresh()
        }
    }
    val state by vm.watchlist.state.collectAsStateWithLifecycle()

    when (val s = state) {
        Loadable.Loading -> Text("Lade …", modifier = Modifier.padding(48.dp))
        is Loadable.Error -> ErrorRow(vm.watchlist::refresh)
        is Loadable.Content -> if (s.data.isEmpty()) {
            Text("Watchlist ist leer.", modifier = Modifier.padding(48.dp))
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(s.data, key = { it.id }) { PosterCard(it, onClick = { onOpenSeries(it) }) }
            }
        }
    }
}
