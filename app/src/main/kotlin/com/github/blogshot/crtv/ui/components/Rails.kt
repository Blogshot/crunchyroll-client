package com.github.blogshot.crtv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.blogshot.crtv.ui.theme.GlassButton as Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.blogshot.crtv.core.content.EpisodeCard
import com.github.blogshot.crtv.core.content.Loadable
import com.github.blogshot.crtv.core.content.MediaCard
import com.github.blogshot.crtv.ui.theme.GlassClickable
import com.github.blogshot.crtv.ui.theme.LocalBackdrop

/**
 * Eine horizontale Reihe mit eigenem Lade- und Fehlerzustand. Scheitert eine Rail, bleiben die anderen nutzbar,
 * und „Erneut versuchen“ lädt nur diese eine Rail neu.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> Rail(
    title: String,
    state: Loadable<List<T>>,
    onRetry: () -> Unit,
    key: (T) -> String,
    emptyText: String = "Nichts hier.",
    itemContent: @Composable LazyItemScope.(T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp))
        when (state) {
            Loadable.Loading -> Placeholder("Lade …")
            is Loadable.Error -> ErrorRow(onRetry)
            is Loadable.Content -> if (state.data.isEmpty()) {
                Placeholder(emptyText)
            } else {
                LazyRow(
                    // Kommt der Fokus von oben/unten zurück, landet er auf der zuletzt fokussierten Karte.
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.data, key = key) { itemContent(it) }
                }
            }
        }
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.height(120.dp).padding(start = 48.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ErrorRow(onRetry: () -> Unit) {
    Column(Modifier.padding(start = 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Konnte nicht geladen werden.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text("Erneut versuchen") }
    }
}

@Composable
fun PosterCard(item: MediaCard, onClick: () -> Unit, width: Dp = 160.dp) {
    val backdrop = LocalBackdrop.current
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GlassClickable(
            onClick = onClick,
            // Fokussierte Serie färbt den weichgezeichneten Hintergrund der ganzen App.
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .onFocusChanged { if (it.isFocused) backdrop.value = item.posterWide ?: item.posterTall },
        ) { _ ->
            AsyncImage(
                model = item.posterTall,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EpisodeTile(item: EpisodeCard, onClick: () -> Unit, showSeries: Boolean = true) {
    val backdrop = LocalBackdrop.current
    Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GlassClickable(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .onFocusChanged { if (it.isFocused) backdrop.value = item.thumbnail },
        ) { _ ->
            AsyncImage(
                model = item.thumbnail,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showSeries && item.seriesTitle.isNotEmpty()) {
            Text(item.seriesTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
        Text(episodeLabel(item), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        item.playheadSec?.let { sec ->
            val total = (item.durationMs / 1000).coerceAtLeast(1)
            Text("${sec / 60} von ${total / 60} Min.", style = MaterialTheme.typography.labelMedium)
        }
    }
}

fun episodeLabel(e: EpisodeCard): String {
    val prefix = listOfNotNull(
        e.seasonNumber?.let { "S$it" },
        e.episodeNumber?.let { "F$it" },
    ).joinToString(" ")
    return if (prefix.isEmpty()) e.title else "$prefix · ${e.title}"
}
