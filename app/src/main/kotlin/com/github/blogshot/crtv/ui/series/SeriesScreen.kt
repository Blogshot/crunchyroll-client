package com.github.blogshot.crtv.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.github.blogshot.crtv.ui.theme.GlassChip as FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.blogshot.crtv.AppContainer
import com.github.blogshot.crtv.core.content.EpisodeCard
import com.github.blogshot.crtv.core.content.Loadable
import com.github.blogshot.crtv.core.content.Resource
import com.github.blogshot.crtv.core.content.Season
import com.github.blogshot.crtv.ui.components.EpisodeTile
import com.github.blogshot.crtv.ui.components.ErrorRow
import com.github.blogshot.crtv.ui.components.Rail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SeriesViewModel(private val c: AppContainer, val seriesId: String) : ViewModel() {
    val series = Resource(viewModelScope, c.cache, "series:$seriesId", maxAgeMs = 60 * 60_000) {
        c.content.series(seriesId)
    }
    val seasons = Resource(viewModelScope, c.cache, "seasons:$seriesId", maxAgeMs = 30 * 60_000) {
        c.content.seasons(seriesId)
    }

    private val _selected = MutableStateFlow<String?>(null)
    /** Gewählte Staffel; bleibt beim Zurückkehren aus dem Player erhalten, weil das ViewModel am Back-Stack hängt. */
    val selected: StateFlow<String?> = _selected

    private val episodeResources = mutableMapOf<String, Resource<List<EpisodeCard>>>()

    fun episodes(seasonId: String): Resource<List<EpisodeCard>> = episodeResources.getOrPut(seasonId) {
        Resource(viewModelScope, c.cache, "episodes:$seasonId", maxAgeMs = 10 * 60_000) {
            c.content.episodes(seasonId)
        }
    }

    fun select(seasonId: String) { _selected.value = seasonId }

    fun onResume() {
        series.refreshIfStale()
        seasons.refreshIfStale()
        _selected.value?.let { episodes(it).refreshIfStale() }
    }
}

@Composable
fun SeriesScreen(vm: SeriesViewModel, onPlay: (EpisodeCard) -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    val series by vm.series.state.collectAsStateWithLifecycle()
    val seasons by vm.seasons.state.collectAsStateWithLifecycle()
    val selectedId by vm.selected.collectAsStateWithLifecycle()

    val seasonList = (seasons as? Loadable.Content)?.data.orEmpty()
    val current = seasonList.firstOrNull { it.id == selectedId } ?: seasonList.firstOrNull()

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item("header") {
            Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = series) {
                    is Loadable.Content -> {
                        Text(s.data.title, style = MaterialTheme.typography.displaySmall)
                        Text(
                            s.data.description,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth(0.6f),
                        )
                    }
                    is Loadable.Error -> ErrorRow(vm.series::refresh)
                    Loadable.Loading -> Text("Lade …")
                }
            }
        }
        item("seasons") {
            when (seasons) {
                is Loadable.Error -> ErrorRow(vm.seasons::refresh)
                else -> SeasonPicker(seasonList, current?.id, vm::select)
            }
        }
        if (current != null) {
            item("episodes:${current.id}") {
                val episodes by vm.episodes(current.id).state.collectAsStateWithLifecycle()
                Rail(current.title.ifEmpty { "Staffel ${current.seasonNumber}" }, episodes, vm.episodes(current.id)::refresh, key = { it.id }) {
                    EpisodeTile(it, onClick = { onPlay(it) }, showSeries = false)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonPicker(seasons: List<Season>, selectedId: String?, onSelect: (String) -> Unit) {
    if (seasons.size < 2) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(seasons, key = { it.id }) { season ->
            FilterChip(selected = season.id == selectedId, onClick = { onSelect(season.id) }) {
                Row { Text(season.title.ifEmpty { "Staffel ${season.seasonNumber}" }) }
            }
        }
    }
}
