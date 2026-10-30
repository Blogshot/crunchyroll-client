package com.github.blogshot.crtv.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.blogshot.crtv.ui.theme.CrColors
import com.github.blogshot.crtv.ui.theme.GlassButton
import com.github.blogshot.crtv.ui.theme.GlassOutlinedButton
import com.github.blogshot.crtv.ui.theme.glass
import kotlinx.coroutines.launch

/** Schwebender Hinweis oben rechts, solange ein Update bereitsteht oder geladen wird. */
@Composable
fun UpdateBanner(updater: Updater, modifier: Modifier = Modifier) {
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val text = when (val s = state) {
        UpdateState.Idle -> return
        is UpdateState.Available -> "Update verfügbar (${s.versionName})"
        is UpdateState.Downloading -> "Lade Update … ${s.percent} %"
        is UpdateState.Failed -> "Update fehlgeschlagen: ${s.message}"
    }
    Row(
        modifier.padding(16.dp).glass(RoundedCornerShape(18.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = CrColors.Text)
        if (state !is UpdateState.Downloading) {
            GlassButton(onClick = { scope.launch { updater.install() } }) {
                Text(if (state is UpdateState.Failed) "Erneut versuchen" else "Installieren")
            }
            GlassOutlinedButton(onClick = updater::dismiss) { Text("Später") }
        }
    }
}
