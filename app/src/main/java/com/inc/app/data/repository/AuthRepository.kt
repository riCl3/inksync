package com.inc.app.data.repository

import com.inc.app.data.remote.IncSyncApi
import com.inc.app.data.remote.TokenStore
import com.inc.app.data.remote.LoginIn
import com.inc.app.data.remote.RegisterIn
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthState {
    object LoggedOut : AuthState()
    data class LoggedIn(val userId: String, val email: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: IncSyncApi,
    private val tokenStore: TokenStore,
) {
    private val _state = MutableStateFlow<AuthState>(
        if (tokenStore.userId.value != null) AuthState.LoggedIn(tokenStore.userId.value!!, "") else AuthState.LoggedOut
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val res = api.login(LoginIn(email, password))
        tokenStore.setSession(res.accessToken, res.user.id)
        _state.value = AuthState.LoggedIn(res.user.id, res.user.email)
    }

    suspend fun register(email: String, password: String): Result<Unit> = runCatching {
        val res = api.register(RegisterIn(email, password))
        tokenStore.setSession(res.accessToken, res.user.id)
        _state.value = AuthState.LoggedIn(res.user.id, res.user.email)
    }

    suspend fun logout() {
        tokenStore.clear()
        _state.value = AuthState.LoggedOut
    }

    fun currentUserId(): String? = tokenStore.userId.value
}
