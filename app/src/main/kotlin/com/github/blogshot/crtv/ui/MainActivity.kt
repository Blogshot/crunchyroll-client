package com.github.blogshot.crtv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.github.blogshot.crtv.AppContainer
import com.github.blogshot.crtv.CrApp
import com.github.blogshot.crtv.core.auth.SessionState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CrApp).container
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { App(container) }
            }
        }
    }
}

@Composable
private fun App(container: AppContainer) {
    val state by container.session.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.session.restore() }

    when (state) {
        SessionState.Unknown -> Centered { Text("Lade …") }
        SessionState.LoggedOut -> LoginScreen(
            viewModel { LoginViewModel(container.authApi, container.session) },
        )
        is SessionState.LoggedIn -> HomePlaceholder(container)
    }
}

@Composable
private fun LoginScreen(vm: LoginViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    Centered {
        when (val s = state) {
            LoginUiState.Loading -> Text("Hole Anmeldecode …")
            is LoginUiState.ShowCode -> {
                Text("Öffne ${s.url} auf deinem Handy und gib diesen Code ein:", style = MaterialTheme.typography.titleLarge)
                Text(s.userCode, style = MaterialTheme.typography.displayLarge)
                Text("Die Anmeldung bleibt danach dauerhaft bestehen.", style = MaterialTheme.typography.bodyLarge)
            }
            is LoginUiState.Error -> {
                Text(s.message, style = MaterialTheme.typography.titleLarge)
                Button(onClick = vm::start) { Text("Erneut versuchen") }
            }
        }
    }
}

/** Platzhalter bis M2: zeigt, dass die Session steht. */
@Composable
private fun HomePlaceholder(container: AppContainer) {
    val scope = rememberCoroutineScope()
    Centered {
        Text("Angemeldet", style = MaterialTheme.typography.displayMedium)
        Text("Startseite folgt in M2.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { scope.launch { container.session.logout() } }) { Text("Abmelden") }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
