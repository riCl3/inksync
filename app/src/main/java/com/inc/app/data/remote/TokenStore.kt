package com.inc.app.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight holder for the auth token + user id.
 *
 * Stored in [EncryptedSharedPreferences] (AES-256) so credentials are not
 * readable from a rooted device's data directory. Exposes an in-memory
 * [token] flow so the OkHttp auth interceptor can read it synchronously
 * without blocking on disk.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "auth_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _token = MutableStateFlow<String?>(prefs.getString("access_token", null))
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _userId = MutableStateFlow<String?>(prefs.getString("user_id", null))
    val userId: StateFlow<String?> = _userId.asStateFlow()

    fun setSession(token: String, userId: String) {
        prefs.edit().putString("access_token", token).putString("user_id", userId).apply()
        _token.value = token
        _userId.value = userId
    }

    fun clear() {
        prefs.edit().clear().apply()
        _token.value = null
        _userId.value = null
    }
}
