package com.classpulse.app.ui.courses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.classpulse.app.ClassPulseApplication
import com.classpulse.app.data.model.CourseSummary
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CoursesUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val courses: List<CourseSummary> = emptyList(),
    val error: String? = null,
)

class CoursesViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ClassPulseApplication).container
    private val repository = container.repository

    private val _state = MutableStateFlow(CoursesUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.courses.isEmpty(), error = null)
            repository.courses()
                .onSuccess { courses -> _state.value = _state.value.copy(loading = false, courses = courses) }
                .onFailure { failure ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = failure.message ?: "课程加载失败",
                    )
                }
        }
    }

    fun createCourse(name: String, description: String, onResult: (String?) -> Unit) {
        if (name.isBlank() || description.isBlank()) {
            onResult("请完整填写课程名称和简介")
            return
        }
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            repository.createCourse(name, description)
                .onSuccess {
                    _messages.emit("课程已创建，邀请码 ${it.inviteCode}")
                    refresh()
                    onResult(null)
                }
                .onFailure { onResult(it.message ?: "创建课程失败") }
            _state.value = _state.value.copy(busy = false)
        }
    }

    fun joinCourse(inviteCode: String, onResult: (String?) -> Unit) {
        if (inviteCode.isBlank()) {
            onResult("请输入课程邀请码")
            return
        }
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            repository.joinCourse(inviteCode)
                .onSuccess {
                    _messages.emit("已加入「${it.name}」")
                    refresh()
                    onResult(null)
                }
                .onFailure { onResult(it.message ?: "加入课程失败") }
            _state.value = _state.value.copy(busy = false)
        }
    }
}
