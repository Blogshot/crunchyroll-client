package com.github.blogshot.crtv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.blogshot.crtv.core.content.EpisodeCard
import com.github.blogshot.crtv.core.content.MediaCard
import com.github.blogshot.crtv.ui.components.EpisodeTile
import com.github.blogshot.crtv.ui.components.PosterCard
import com.github.blogshot.crtv.ui.components.Rail

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    reselect: Int,
    onOpenSeries: (MediaCard) -> Unit,
    onPlay: (EpisodeCard) -> Unit,
) {
    // rememberLazyListState ist saveable: Navigation mit saveState/restoreState bringt die Position zurück.
    val listState = rememberLazyListState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    // Erneutes Wählen von „Start“ in der Seitenleiste: nach oben springen und aktualisieren, statt nichts zu tun.
    LaunchedEffect(reselect) {
        if (reselect > 0) {
            listState.animateScrollToItem(0)
            vm.refreshAll()
        }
    }

    val cont by vm.continueWatching.state.collectAsStateWithLifecycle()
    val watch by vm.watchlist.state.collectAsStateWithLifecycle()
    val popular by vm.popular.state.collectAsStateWithLifecycle()
    val newest by vm.newest.state.collectAsStateWithLifecycle()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item("continue") {
            Rail("Weiterschauen", cont, vm.continueWatching::refresh, key = { it.id }, emptyText = "Noch nichts angefangen.") {
                EpisodeTile(it, onClick = { onPlay(it) })
            }
        }
        item("watchlist") {
            Rail("Watchlist", watch, vm.watchlist::refresh, key = { it.id }, emptyText = "Watchlist ist leer.") {
                PosterCard(it, onClick = { onOpenSeries(it) })
            }
        }
        item("popular") {
            Rail("Beliebt", popular, vm.popular::refresh, key = { it.id }) {
                PosterCard(it, onClick = { onOpenSeries(it) })
            }
        }
        item("newest") {
            Rail("Neu", newest, vm.newest::refresh, key = { it.id }) {
                PosterCard(it, onClick = { onOpenSeries(it) })
            }
        }
    }
}
