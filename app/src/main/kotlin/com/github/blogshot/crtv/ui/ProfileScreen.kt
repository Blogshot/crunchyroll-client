package com.github.blogshot.crtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.blogshot.crtv.AppContainer
import com.github.blogshot.crtv.core.content.Loadable
import com.github.blogshot.crtv.core.content.Profile
import com.github.blogshot.crtv.ui.theme.CrColors
import com.github.blogshot.crtv.ui.theme.glass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val c: AppContainer) : ViewModel() {
    private val _profiles = MutableStateFlow<Loadable<List<Profile>>>(Loadable.Loading)
    val profiles: StateFlow<Loadable<List<Profile>>> = _profiles

    private val _switching = MutableStateFlow(false)
    val switching: StateFlow<Boolean> = _switching

    init { load() }

    fun load() {
        viewModelScope.launch {
            _profiles.value = Loadable.Loading
            _profiles.value = runCatching { c.profiles.profiles() }.fold(
                onSuccess = { list ->
                    // Nur ein Profil: nichts zu wählen, direkt weiter.
                    if (list.size <= 1) c.session.confirmProfile()
                    Loadable.Content(list)
                },
                onFailure = { Loadable.Error(it) },
            )
        }
    }

    fun choose(profile: Profile, onDone: () -> Unit) {
        viewModelScope.launch {
            _switching.value = true
            val ok = runCatching { c.session.switchProfile(profile.id) }.isSuccess
            _switching.value = false
            if (ok) {
                // Weiterschauen, Watchlist usw. gehören zum Profil: alten Stand verwerfen.
                c.cache.invalidate("")
                onDone()
            } else {
                _profiles.value = Loadable.Error(IllegalStateException("Profilwechsel fehlgeschlagen"))
            }
        }
    }
}

/** „Wer schaut?“ nach dem Login und über die Seitenleiste. */
@Composable
fun ProfileScreen(vm: ProfileViewModel, onDone: () -> Unit, onLogout: () -> Unit) {
    val state by vm.profiles.collectAsStateWithLifecycle()
    val switching by vm.switching.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Wer schaut?", style = MaterialTheme.typography.displaySmall)
        when (val s = state) {
            Loadable.Loading -> Text("Lade Profile …")
            is Loadable.Error -> {
                Text("Profile konnten nicht geladen werden.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = vm::load) { Text("Erneut versuchen") }
                    Button(onClick = onLogout) { Text("Abmelden") }
                }
            }
            is Loadable.Content -> {
                val first = remember { FocusRequester() }
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    s.data.forEachIndexed { i, profile ->
                        ProfileTile(
                            profile = profile,
                            enabled = !switching,
                            onClick = { vm.choose(profile, onDone) },
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
                LaunchedEffect(s.data) { if (s.data.isNotEmpty()) runCatching { first.requestFocus() } }
                if (switching) Text("Wechsle Profil …", color = CrColors.TextMuted)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfileTile(profile: Profile, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.width(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CircleShape),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = modifier.size(150.dp).onFocusChanged { focused = it.isFocused },
        ) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = profile.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().glass(CircleShape, focused = focused, borderWidth = 2.dp),
            )
        }
        Text(
            profile.name,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) CrColors.Text else CrColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
