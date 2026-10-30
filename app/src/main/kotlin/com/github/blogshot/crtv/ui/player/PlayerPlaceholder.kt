package com.github.blogshot.crtv.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Platzhalter bis M3 (ExoPlayer + Widevine). */
@Composable
fun PlayerPlaceholder(episodeId: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Player folgt in M3", style = MaterialTheme.typography.headlineMedium)
        Text("Folge $episodeId", style = MaterialTheme.typography.bodyLarge)
    }
}
