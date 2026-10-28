package com.classpulse.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.classpulse.app.BuildConfig
import com.classpulse.app.data.model.AuthData
import com.classpulse.app.data.model.User
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.sessionDataStore by preferencesDataStore(name = "classpulse_session")

sealed interface SessionStatus {
    data object Loading : SessionStatus
    data object LoggedOut : SessionStatus
    data class LoggedIn(val user: User) : SessionStatus
}

class SessionManager(
    private val context: Context,
    private val gson: Gson,
) {
    private val tokenKey = stringPreferencesKey("token")
    private val userKey = stringPreferencesKey("user")
    private val serverKey = stringPreferencesKey("server_url")

    private val _status = MutableStateFlow<SessionStatus>(SessionStatus.Loading)
    val status: StateFlow<SessionStatus> = _status.asStateFlow()

    @Volatile
    var token: String? = null
        private set

    @Volatile
    var user: User? = null
        private set

    @Volatile
    var serverUrl: String = normalizeServerUrl(BuildConfig.API_BASE_URL)
        private set

    suspend fun restore() {
        val preferences = context.sessionDataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .first()
        serverUrl = normalizeServerUrl(preferences[serverKey] ?: BuildConfig.API_BASE_URL)
        token = preferences[tokenKey]
        user = preferences[userKey]?.let { json ->
            runCatching { gson.fromJson(json, User::class.java) }.getOrNull()
        }
        _status.value = if (token != null && user != null) {
            SessionStatus.LoggedIn(requireNotNull(user))
        } else {
            SessionStatus.LoggedOut
        }
    }

    suspend fun saveAuth(auth: AuthData) {
        token = auth.token
        user = auth.user
        context.sessionDataStore.edit { preferences ->
            preferences[tokenKey] = auth.token
            preferences[userKey] = gson.toJson(auth.user)
            preferences[serverKey] = serverUrl
        }
        _status.value = SessionStatus.LoggedIn(auth.user)
    }

    suspend fun updateUser(updated: User) {
        user = updated
        context.sessionDataStore.edit { it[userKey] = gson.toJson(updated) }
        if (token != null) _status.value = SessionStatus.LoggedIn(updated)
    }

    suspend fun updateServer(rawUrl: String) {
        val normalized = normalizeServerUrl(rawUrl)
        serverUrl = normalized
        context.sessionDataStore.edit { it[serverKey] = normalized }
    }

    suspend fun forceLogout() {
        token = null
        user = null
        context.sessionDataStore.edit { preferences ->
            preferences.remove(tokenKey)
            preferences.remove(userKey)
        }
        _status.value = SessionStatus.LoggedOut
    }

    fun fullUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return serverUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    companion object {
        fun normalizeServerUrl(rawUrl: String): String {
            var value = rawUrl.trim()
            if (value.isBlank()) return BuildConfig.API_BASE_URL
            if (!value.contains("://")) value = "https://$value"
            if (!value.endsWith("/")) value += "/"
            return value
        }

        fun validateServerUrl(rawUrl: String): String? {
            val value = normalizeServerUrl(rawUrl)
            val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return "服务器地址格式不正确"
            if (uri.scheme != "http" && uri.scheme != "https") return "服务器地址必须以 http:// 或 https:// 开头"
            if (uri.host.isNullOrBlank()) return "服务器地址缺少域名或 IP"
            return null
        }
    }
}
