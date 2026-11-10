package com.inc.app.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.authDataStore by preferencesDataStore(name = "auth")

/**
 * Lightweight holder for the auth token + user id.
 *
 * Stored in DataStore (not EncryptedSharedPreferences yet — a documented
 * limitation carried over from Milestone 1; secure storage lands with the
 * auth polish). Exposes an in-memory [token] flow so the OkHttp auth
 * interceptor can read it synchronously without blocking on disk.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val tokenKey = stringPreferencesKey("access_token")
    private val userIdKey = stringPreferencesKey("user_id")

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    init {
        // Hydrate synchronously so interceptors see the token on first request.
        runBlocking {
            val prefs = context.authDataStore.data.first()
            _token.value = prefs[tokenKey]
            _userId.value = prefs[userIdKey]
        }
    }

    suspend fun setSession(token: String, userId: String) {
        context.authDataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[userIdKey] = userId
        }
        _token.value = token
        _userId.value = userId
    }

    suspend fun clear() {
        context.authDataStore.edit { it.clear() }
        _token.value = null
        _userId.value = null
    }
}
