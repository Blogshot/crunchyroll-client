package com.github.blogshot.crtv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.blogshot.crtv.core.auth.AuthApi
import com.github.blogshot.crtv.core.auth.DevicePoll
import com.github.blogshot.crtv.core.auth.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Loading : LoginUiState
    data class ShowCode(val userCode: String, val url: String) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

/** Gerätecode-Login: Code anzeigen, im Intervall pollen, abgelaufene Codes automatisch ersetzen. */
class LoginViewModel(private val auth: AuthApi, private val session: SessionManager) : ViewModel() {
    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val state: StateFlow<LoginUiState> = _state
    private var job: Job? = null

    init { start() }

    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = LoginUiState.Loading
            val code = runCatching { auth.requestDeviceCode() }.getOrElse {
                _state.value = LoginUiState.Error("Crunchyroll ist gerade nicht erreichbar.")
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
            start()
        }
    }
}
