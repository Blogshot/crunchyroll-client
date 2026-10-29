package com.github.blogshot.crtv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.blogshot.crtv.core.api.ClientCredentials
import com.github.blogshot.crtv.core.api.DefaultClientCredentialsProvider
import com.github.blogshot.crtv.core.api.MissingClientCredentialsException
import com.github.blogshot.crtv.core.auth.AuthApi
import com.github.blogshot.crtv.core.auth.DevicePoll
import com.github.blogshot.crtv.core.auth.LoginResult
import com.github.blogshot.crtv.core.auth.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data class Form(val busy: Boolean = false, val message: String? = null) : LoginUiState
    /** Weder gespeichert noch auf crunchyroll.com gefunden: einmal von Hand eingeben. */
    data class NeedClientId(val message: String? = null) : LoginUiState
    data object CodeLoading : LoginUiState
    data class ShowCode(val userCode: String, val url: String) : LoginUiState
}

/**
 * Interaktiver Login. Standard ist E-Mail und Passwort; das Passwort wird nur für den einen Token-Request benutzt
 * und nie gespeichert. Alternativ Gerätecode, falls die Client-Kennung das unterstützt.
 */
class LoginViewModel(
    private val auth: AuthApi,
    private val session: SessionManager,
    private val clientCredentials: DefaultClientCredentialsProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Form())
    val state: StateFlow<LoginUiState> = _state
    private var job: Job? = null

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) {
            _state.value = LoginUiState.Form(message = "Bitte E-Mail und Passwort eingeben.")
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = LoginUiState.Form(busy = true)
            _state.value = when (val r = auth.passwordLogin(email.trim(), password)) {
                is LoginResult.Success -> {
                    session.onLoggedIn(r.tokens)
                    LoginUiState.Form()
                }
                LoginResult.WrongCredentials -> LoginUiState.Form(message = "E-Mail oder Passwort stimmt nicht.")
                LoginResult.MissingClientId -> LoginUiState.NeedClientId()
                is LoginResult.Failed -> LoginUiState.Form(message = "Crunchyroll ist gerade nicht erreichbar.")
            }
        }
    }

    fun saveClientId(id: String, secret: String) {
        if (id.isBlank()) {
            _state.value = LoginUiState.NeedClientId("Die Client-ID darf nicht leer sein.")
            return
        }
        viewModelScope.launch {
            clientCredentials.setManually(ClientCredentials(id.trim(), secret.trim()))
            _state.value = LoginUiState.Form(message = "Client-ID gespeichert. Bitte erneut anmelden.")
        }
    }

    fun backToForm() {
        job?.cancel()
        _state.value = LoginUiState.Form()
    }

    /** Gerätecode-Login: Code anzeigen, im Intervall pollen, abgelaufene Codes automatisch ersetzen. */
    fun startDeviceCode() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = LoginUiState.CodeLoading
            val code = try {
                auth.requestDeviceCode()
            } catch (e: MissingClientCredentialsException) {
                _state.value = LoginUiState.NeedClientId()
                return@launch
            } catch (e: Exception) {
                _state.value = LoginUiState.Form(message = "Anmeldung per Code ist gerade nicht möglich.")
                return@launch
            }
            _state.value = LoginUiState.ShowCode(code.userCode, code.verificationUrl)
            val deadline = System.currentTimeMillis() + code.expiresInSec * 1000
            var failures = 0
            while (System.currentTimeMillis() < deadline) {
                delay(code.intervalSec * 1000 * (1 + failures.coerceAtMost(4)))
                when (val poll = auth.pollDeviceCode(code.deviceCode)) {
                    DevicePoll.Pending -> failures = 0
                    is DevicePoll.Failed -> failures++
                    DevicePoll.Expired -> break
                    is DevicePoll.Approved -> {
                        session.onLoggedIn(poll.tokens)
                        return@launch
                    }
                }
            }
            startDeviceCode()
        }
    }
}
