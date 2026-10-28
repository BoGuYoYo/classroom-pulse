package com.classpulse.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.classpulse.app.ClassPulseApplication
import com.classpulse.app.data.SessionStatus
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ClassPulseApplication).container
    val sessionStatus = container.sessionManager.status

    init {
        viewModelScope.launch {
            container.sessionManager.restore()
            if (container.sessionManager.status.value is SessionStatus.LoggedIn) {
                container.repository.me().onSuccess { user ->
                    if (user != container.sessionManager.user) {
                        container.sessionManager.updateUser(user)
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            container.clearImageCache()
            container.sessionManager.forceLogout()
        }
    }
}
