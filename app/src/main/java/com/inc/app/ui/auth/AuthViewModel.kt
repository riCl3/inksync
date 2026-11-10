package com.inc.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inc.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isRegister: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmail(v: String) { _state.value = _state.value.copy(email = v) }
    fun onPassword(v: String) { _state.value = _state.value.copy(password = v) }
    fun toggleMode() { _state.value = _state.value.copy(isRegister = !_state.value.isRegister, error = null) }

    fun submit() {
        val s = _state.value
        if (s.email.isBlank() || s.password.length < 8) {
            _state.value = s.copy(error = "Enter a valid email and password (8+ chars).")
            return
        }
        _state.value = s.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val res = if (s.isRegister) authRepository.register(s.email, s.password)
            else authRepository.login(s.email, s.password)
            res.onSuccess {
                _state.value = _state.value.copy(isLoading = false, loggedIn = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Auth failed")
            }
        }
    }
}
