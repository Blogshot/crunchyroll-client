package com.github.blogshot.crtv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.blogshot.crtv.ui.theme.GlassButton as Button
import androidx.tv.material3.MaterialTheme
import com.github.blogshot.crtv.ui.theme.GlassOutlinedButton as OutlinedButton
import androidx.tv.material3.Text
import com.github.blogshot.crtv.AppContainer
import com.github.blogshot.crtv.CrApp
import com.github.blogshot.crtv.core.auth.SessionState
import com.github.blogshot.crtv.ui.theme.CrColors
import com.github.blogshot.crtv.ui.theme.CrTheme
import com.github.blogshot.crtv.ui.theme.GlassBackground
import com.github.blogshot.crtv.ui.theme.GlassTextField
import com.github.blogshot.crtv.ui.theme.glass
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CrApp).container
        setContent {
            CrTheme {
                GlassBackground { App(container) }
            }
        }
    }
}

@Composable
private fun App(container: AppContainer) {
    val state by container.session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { container.session.restore() }

    when (val s = state) {
        SessionState.Unknown -> Centered { Text("Lade …") }
        SessionState.LoggedOut -> LoginScreen(
            viewModel { LoginViewModel(container.authApi, container.session, container.clientCredentials) },
            container.config.userAgent,
        )
        // Nach dem Login zuerst „Wer schaut?“; die Wahl bleibt gespeichert.
        is SessionState.LoggedIn -> if (!s.profileConfirmed) {
            ProfileScreen(
                viewModel(key = "profiles-initial") { ProfileViewModel(container) },
                onDone = {},
                onLogout = { scope.launch { container.session.logout() } },
            )
        } else {
            AppShell(container)
        }
    }
}

@Composable
private fun LoginScreen(vm: LoginViewModel, userAgent: String) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state == LoginUiState.Web) {
        WebLogin(userAgent, onSession = vm::onWebSession, onCancel = vm::backToStart)
        return
    }
    Centered {
        Column(
            Modifier.width(640.dp).glass(RoundedCornerShape(28.dp)).padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Crunchyroll", style = MaterialTheme.typography.displaySmall, color = CrColors.Accent)
            when (val s = state) {
                LoginUiState.Web -> Unit
                is LoginUiState.Start -> StartPanel(vm, s)
                is LoginUiState.Form -> PasswordForm(vm, s)
                is LoginUiState.NeedClientId -> ClientIdForm(vm, s)
                LoginUiState.CodeLoading -> Text("Hole Anmeldecode …")
                is LoginUiState.ShowCode -> {
                    Text("Öffne ${s.url} auf deinem Handy und gib diesen Code ein:", style = MaterialTheme.typography.titleMedium)
                    Text(s.userCode, style = MaterialTheme.typography.displayMedium)
                    OutlinedButton(onClick = vm::backToStart) { Text("Zurück") }
                }
            }
        }
    }
}

@Composable
private fun StartPanel(vm: LoginViewModel, s: LoginUiState.Start) {
    Text("Melde dich auf der offiziellen Crunchyroll-Seite an. Die App sieht dein Passwort nicht.", style = MaterialTheme.typography.titleMedium)
    s.message?.let { Text(it, color = CrColors.Accent) }
    val primary = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { primary.requestFocus() } }
    Button(
        onClick = vm::openWebLogin,
        enabled = !s.busy,
        modifier = Modifier.focusRequester(primary),
    ) {
        Text(if (s.busy) "Melde an …" else "Bei Crunchyroll anmelden")
    }
    OutlinedButton(onClick = vm::openPasswordForm, enabled = !s.busy) { Text("E-Mail und Passwort (eigene Client-ID)") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = androidx.compose.runtime.remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    Text("Version $version", style = MaterialTheme.typography.labelSmall, color = CrColors.TextMuted)
}

@Composable
private fun PasswordForm(vm: LoginViewModel, s: LoginUiState.Form) {
    var email by rememberSaveable { mutableStateOf("") }
    // Bewusst nicht rememberSaveable: Das Passwort landet nie im gespeicherten Zustand.
    var password by androidx.compose.runtime.remember { mutableStateOf("") }

    Text("Mit deinem Crunchyroll-Konto anmelden", style = MaterialTheme.typography.titleMedium)
    GlassTextField(
        value = email,
        onValueChange = { email = it },
        placeholder = "E-Mail",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
    )
    GlassTextField(
        value = password,
        onValueChange = { password = it },
        placeholder = "Passwort",
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
    )
    s.message?.let { Text(it, color = CrColors.Accent) }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = { vm.login(email, password) }, enabled = !s.busy) {
            Text(if (s.busy) "Melde an …" else "Anmelden")
        }
        OutlinedButton(onClick = vm::startDeviceCode, enabled = !s.busy) { Text("Code am Handy") }
        OutlinedButton(onClick = vm::backToStart, enabled = !s.busy) { Text("Zurück") }
    }
    Text(
        "Dein Passwort wird nur zur Anmeldung gesendet und nicht gespeichert.",
        style = MaterialTheme.typography.bodySmall,
        color = CrColors.TextMuted,
    )
}

@Composable
private fun ClientIdForm(vm: LoginViewModel, s: LoginUiState.NeedClientId) {
    var id by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }

    Text("Client-ID benötigt", style = MaterialTheme.typography.titleMedium)
    Text(
        "Die App konnte die Kennung der Crunchyroll-Web-App nicht automatisch ermitteln. " +
            "Gib sie einmal ein; sie wird verschlüsselt gespeichert.",
        style = MaterialTheme.typography.bodyMedium,
        color = CrColors.TextMuted,
    )
    GlassTextField(value = id, onValueChange = { id = it }, placeholder = "Client-ID")
    GlassTextField(value = secret, onValueChange = { secret = it }, placeholder = "Client-Secret (optional)")
    s.message?.let { Text(it, color = CrColors.Accent) }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = { vm.saveClientId(id, secret) }) { Text("Speichern") }
        OutlinedButton(onClick = vm::backToStart) { Text("Zurück") }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}
