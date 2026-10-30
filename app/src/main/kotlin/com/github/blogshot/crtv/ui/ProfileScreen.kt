package com.github.blogshot.crtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.github.blogshot.crtv.ui.theme.GlassButton as Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.blogshot.crtv.AppContainer
import com.github.blogshot.crtv.core.content.HttpStatusException
import com.github.blogshot.crtv.core.content.Loadable
import com.github.blogshot.crtv.core.content.Profile
import com.github.blogshot.crtv.ui.theme.CrColors
import com.github.blogshot.crtv.ui.theme.GlassClickable
import com.github.blogshot.crtv.ui.theme.GlassOutlinedButton as OutlinedButton
import com.github.blogshot.crtv.ui.components.ErrorDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val c: AppContainer) : ViewModel() {
    private val _profiles = MutableStateFlow<Loadable<List<Profile>>>(Loadable.Loading)
    val profiles: StateFlow<Loadable<List<Profile>>> = _profiles

    private val _switching = MutableStateFlow(false)
    val switching: StateFlow<Boolean> = _switching

    /** Website-Cookie, solange die Profilwahl über die Website offen ist. */
    private val _webSession = MutableStateFlow<String?>(null)
    val webSession: StateFlow<String?> = _webSession

    /** Mitschrift der Website-Profilwahl, zur Diagnose. */
    private val _webLog = MutableStateFlow<List<String>>(emptyList())
    val webLog: StateFlow<List<String>> = _webLog

    val userAgent get() = c.config.userAgent

    init { load() }

    fun openWeb() {
        viewModelScope.launch {
            val cookie = c.session.webCookie()
            if (cookie == null) _webLog.value = listOf("Keine Website-Sitzung gespeichert. Bitte ab- und neu anmelden.")
            _webSession.value = cookie
        }
    }

    fun onWebResult(result: WebProfileResult, onDone: () -> Unit) {
        _webSession.value = null
        _webLog.value = result.lines.ifEmpty { listOf("Die Seite hat keine Konto- oder Token-Aufrufe gemacht.") }
        viewModelScope.launch {
            result.cookie?.let { c.session.adoptWebCookie(it) }
            val chosen = result.selectedProfileId
            when {
                chosen != null -> choose(Profile(chosen, "", null, isPrimary = false, isSelected = false), onDone)
                result.profiles != null -> _profiles.value = Loadable.Content(result.profiles)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _profiles.value = Loadable.Loading
            _profiles.value = runCatching {
                try {
                    c.profiles.profiles()
                } catch (e: HttpStatusException) {
                    if (e.code != 401 && e.code != 403) throw e
                    // Endpunkt lehnt das Token ab: Varianten durchprobieren, die erste funktionierende liefert die Profile.
                    val report = c.profileProbe.run(c.session.accessToken(), c.session.webCookie())
                    report.rotatedCookie?.let { c.session.adoptWebCookie(it) }
                    report.profiles ?: throw ProfileProbeFailed(e, report.lines)
                }
            }.fold(
                onSuccess = { list ->
                    // Nur ein Profil: nichts zu wählen, direkt weiter.
                    if (list.size <= 1) c.session.confirmProfile()
                    Loadable.Content(list)
                },
                onFailure = { Loadable.Error(it) },
            )
        }
    }

    /** Notausgang, wenn die Profil-API nicht mitspielt: mit dem Standardprofil des Logins weitermachen. */
    fun skip(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { c.session.confirmProfile() }
            onDone()
        }
    }

    fun choose(profile: Profile, onDone: () -> Unit) {
        viewModelScope.launch {
            _switching.value = true
            val result = runCatching { c.session.switchProfile(profile.id) }
            _switching.value = false
            if (result.isSuccess) {
                // Weiterschauen, Watchlist usw. gehören zum Profil: alten Stand verwerfen.
                c.cache.invalidate("")
                onDone()
            } else {
                _profiles.value = Loadable.Error(IllegalStateException("Profilwechsel fehlgeschlagen", result.exceptionOrNull()))
            }
        }
    }
}

/** Profilliste abgelehnt, und keine Variante der Diagnose hat geholfen. */
class ProfileProbeFailed(cause: Throwable, val lines: List<String>) : Exception(cause.message, cause)

/** „Wer schaut?“ nach dem Login und über die Seitenleiste. */
@Composable
fun ProfileScreen(vm: ProfileViewModel, onDone: () -> Unit, onLogout: () -> Unit) {
    val state by vm.profiles.collectAsStateWithLifecycle()
    val switching by vm.switching.collectAsStateWithLifecycle()
    val webSession by vm.webSession.collectAsStateWithLifecycle()
    val webLog by vm.webLog.collectAsStateWithLifecycle()

    webSession?.let { cookie ->
        WebProfiles(vm.userAgent, cookie) { vm.onWebResult(it, onDone) }
        return
    }

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
                ErrorDetail(s.error)
                (s.error as? ProfileProbeFailed)?.let { failed ->
                    Column(
                        Modifier.fillMaxWidth(0.85f).heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Diagnose (bitte als Screenshot schicken):", style = MaterialTheme.typography.labelMedium)
                        failed.lines.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = CrColors.TextMuted) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = vm::openWeb) { Text("Über die Website wählen") }
                    OutlinedButton(onClick = vm::load) { Text("Erneut versuchen") }
                    OutlinedButton(onClick = { vm.skip(onDone) }) { Text("Ohne Profilwahl weiter") }
                    OutlinedButton(onClick = onLogout) { Text("Abmelden") }
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
        if (webLog.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth(0.85f).heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Website-Mitschrift (bitte als Screenshot schicken):", style = MaterialTheme.typography.labelMedium)
                webLog.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = CrColors.TextMuted) }
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
        GlassClickable(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            focusScale = 1.1f,
            modifier = modifier.size(150.dp).onFocusChanged { focused = it.isFocused },
        ) { _ ->
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = profile.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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
