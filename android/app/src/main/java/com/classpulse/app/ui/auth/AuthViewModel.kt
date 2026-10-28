package com.classpulse.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.classpulse.app.ClassPulseApplication
import com.classpulse.app.data.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val serverUrl: String = "",
)

/**
 * Authentication attempts always finish, including when saving the session
 * throws. Keeping this transition pure makes the "login -> logout -> login"
 * regression directly testable.
 */
internal fun AuthUiState.afterAuthentication(error: String? = null): AuthUiState =
    copy(loading = false, error = error)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ClassPulseApplication).container
    private val repository = container.repository
    private val sessionManager = container.sessionManager

    private val _state = MutableStateFlow(AuthUiState(serverUrl = sessionManager.serverUrl))
    val state = _state.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "请输入账号和密码")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val auth = repository.login(username, password).getOrThrow()
                sessionManager.saveAuth(auth)
                _state.value = _state.value.afterAuthentication()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                runCatching { sessionManager.forceLogout() }
                _state.value = _state.value.afterAuthentication(
                    exception.message ?: "登录失败，请稍后重试",
                )
            }
        }
    }

    fun register(studentNo: String, name: String, password: String) {
        when {
            studentNo.isBlank() || name.isBlank() || password.isBlank() -> {
                _state.value = _state.value.copy(error = "请完整填写学号、姓名和密码")
                return
            }
            password.length < 8 -> {
                _state.value = _state.value.copy(error = "密码至少需要 8 位")
                return
            }
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val auth = repository.register(studentNo, name, password).getOrThrow()
                sessionManager.saveAuth(auth)
                _state.value = _state.value.afterAuthentication()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                runCatching { sessionManager.forceLogout() }
                _state.value = _state.value.afterAuthentication(
                    exception.message ?: "注册失败，请稍后重试",
                )
            }
        }
    }

    fun clearError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    fun updateServer(rawUrl: String, onResult: (String?) -> Unit) {
        val validationError = SessionManager.validateServerUrl(rawUrl)
        if (validationError != null) {
            onResult(validationError)
            return
        }
        viewModelScope.launch {
            sessionManager.updateServer(rawUrl)
            _state.value = _state.value.copy(serverUrl = sessionManager.serverUrl)
            onResult(null)
        }
    }
}
