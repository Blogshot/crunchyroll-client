package com.github.blogshot.crtv.ui.search

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.blogshot.crtv.AppContainer
import com.github.blogshot.crtv.core.content.Loadable
import com.github.blogshot.crtv.core.content.MediaCard
import com.github.blogshot.crtv.ui.components.ErrorRow
import com.github.blogshot.crtv.ui.components.PosterCard
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(private val c: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<Loadable<List<MediaCard>>>(Loadable.Content(emptyList()))
    val results: StateFlow<Loadable<List<MediaCard>>> = _results

    init {
        _query.debounce(400).distinctUntilChanged().onEach { run(it) }.launchIn(viewModelScope)
    }

    fun onQuery(value: String) { _query.value = value }

    fun retry() { viewModelScope.launch { run(_query.value) } }

    private suspend fun run(q: String) {
        if (q.isBlank()) {
            _results.value = Loadable.Content(emptyList()); return
        }
        _results.value = Loadable.Loading
        _results.value = runCatching { c.content.search(q.trim()) }
            .fold({ Loadable.Content(it) }, { Loadable.Error(it) })
    }
}

@Composable
fun SearchScreen(vm: SearchViewModel, onOpenSeries: (MediaCard) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        BasicTextField(
            value = query,
            onValueChange = vm::onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.border, RoundedCornerShape(8.dp))
                .padding(16.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Serie oder Film suchen", style = MaterialTheme.typography.headlineSmall)
                inner()
            },
        )
        when (val r = results) {
            Loadable.Loading -> Text("Suche …", modifier = Modifier.padding(horizontal = 48.dp))
            is Loadable.Error -> ErrorRow(vm::retry, r.error)
            is Loadable.Content -> if (r.data.isEmpty() && query.isNotBlank()) {
                Text("Keine Treffer.", modifier = Modifier.padding(horizontal = 48.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(r.data, key = { it.id }) { PosterCard(it, onClick = { onOpenSeries(it) }) }
                }
            }
        }
    }
}
