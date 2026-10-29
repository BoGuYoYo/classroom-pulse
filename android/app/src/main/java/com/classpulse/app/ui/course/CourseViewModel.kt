package com.classpulse.app.ui.course

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.classpulse.app.ClassPulseApplication
import com.classpulse.app.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CourseUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val course: CourseSummary? = null,
    val members: List<Member> = emptyList(),
    val attendance: List<Attendance> = emptyList(),
    val questions: List<Question> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val stats: CourseStats? = null,
    val statsLoading: Boolean = false,
    val studentStats: StudentCourseStats? = null,
    val studentStatsLoading: Boolean = false,
    val error: String? = null,
    val sheetLoading: Boolean = false,
    val sheetError: String? = null,
    val attendanceDetail: AttendanceDetail? = null,
    val questionStats: QuestionStats? = null,
    val assignmentSubmissions: AssignmentSubmissions? = null,
)

class CourseViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ClassPulseApplication).container
    private val repository = container.repository
    private val sessionManager = container.sessionManager

    private val _state = MutableStateFlow(CourseUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val messages = _messages.asSharedFlow()

    fun load(courseId: Long, quiet: Boolean = false) {
        if (!quiet) {
            _state.update { it.copy(loading = true, error = null) }
        }
        viewModelScope.launch {
            coroutineScope {
                val courseDeferred = async { repository.course(courseId) }
                val attendanceDeferred = async { repository.attendance(courseId) }
                val questionsDeferred = async { repository.questions(courseId) }
                val assignmentsDeferred = async { repository.assignments(courseId) }
                val membersDeferred = if (sessionManager.user?.isTeacher == true) {
                    async { repository.members(courseId) }
                } else {
                    null
                }
                val courseResult = courseDeferred.await()
                val attendanceResult = attendanceDeferred.await()
                val questionsResult = questionsDeferred.await()
                val assignmentsResult = assignmentsDeferred.await()
                val membersResult = membersDeferred?.await()

                val courseFailure = courseResult.exceptionOrNull()
                if (courseFailure != null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = courseFailure.message ?: "课程加载失败",
                        )
                    }
                    return@coroutineScope
                }
                val secondaryFailure = attendanceResult.exceptionOrNull()
                    ?: questionsResult.exceptionOrNull()
                    ?: assignmentsResult.exceptionOrNull()
                    ?: membersResult?.exceptionOrNull()
                _state.update {
                    it.copy(
                        loading = false,
                        error = secondaryFailure?.message,
                        course = courseResult.getOrNull(),
                        attendance = attendanceResult.getOrDefault(it.attendance),
                        questions = questionsResult.getOrDefault(it.questions),
                        assignments = assignmentsResult.getOrDefault(it.assignments),
                        members = membersResult?.getOrDefault(it.members) ?: it.members,
                    )
                }
            }
        }
    }

    fun refresh(courseId: Long) = load(courseId, quiet = true)

    fun loadStats(courseId: Long, force: Boolean = false) {
        if (_state.value.statsLoading) return
        if (_state.value.stats != null && !force) return
        _state.update { it.copy(statsLoading = true) }
        viewModelScope.launch {
            repository.stats(courseId)
                .onSuccess { stats -> _state.update { it.copy(stats = stats, statsLoading = false) } }
                .onFailure { failure ->
                    _state.update { it.copy(statsLoading = false) }
                    _messages.emit(failure.message ?: "统计数据加载失败")
                }
        }
    }

    fun createAttendance(
        courseId: Long,
        title: String,
        minutes: Int,
        onResult: (String?) -> Unit,
    ) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.createAttendance(courseId, title, minutes)
                .onSuccess { created ->
                    val refreshed = repository.attendance(courseId)
                    _state.update {
                        it.copy(
                            busy = false,
                            attendance = refreshed.getOrDefault(it.attendance),
                        )
                    }
                    _messages.emit("签到已发起，签到码 ${created.code}")
                    onResult(null)
                }
                .onFailure {
                    _state.update { state -> state.copy(busy = false) }
                    onResult(it.message ?: "发起签到失败")
                }
        }
    }

    fun checkIn(attendanceId: Long, courseId: Long, code: String, onResult: (String?) -> Unit) {
        if (!Regex("^\\d{6}$").matches(code.trim())) {
            onResult("请输入 6 位数字签到码")
            return
        }
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.checkIn(attendanceId, code)
                .onSuccess { result ->
                    val refreshed = repository.attendance(courseId)
                    _state.update {
                        it.copy(
                            busy = false,
                            attendance = refreshed.getOrDefault(it.attendance),
                        )
                    }
                    _messages.emit(result.message.ifBlank { "签到成功" })
                    onResult(null)
                }
                .onFailure {
                    _state.update { state -> state.copy(busy = false) }
                    onResult(it.message ?: "签到失败")
                }
        }
    }

    fun createQuestion(
        courseId: Long,
        request: CreateQuestionRequest,
        onResult: (String?) -> Unit,
    ) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.createQuestion(courseId, request)
                .onSuccess {
                    val refreshed = repository.questions(courseId)
                    _state.update { state ->
                        state.copy(busy = false, questions = refreshed.getOrDefault(state.questions))
                    }
                    _messages.emit("课堂问题已发布")
                    onResult(null)
                }
                .onFailure {
                    _state.update { state -> state.copy(busy = false) }
                    onResult(it.message ?: "发布问题失败")
                }
        }
    }

    fun answerQuestion(
        questionId: Long,
        courseId: Long,
        answer: String,
        onResult: (String?) -> Unit,
    ) {
        if (answer.isBlank()) {
            onResult("请先作答")
            return
        }
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.answer(questionId, answer)
                .onSuccess { result ->
                    val refreshed = repository.questions(courseId)
                    _state.update { state ->
                        state.copy(busy = false, questions = refreshed.getOrDefault(state.questions))
                    }
                    _messages.emit(
                        if (result.isCorrect) {
                            "回答正确，继续保持"
                        } else {
                            "答案已提交，正确答案：${result.correctAnswer}"
                        },
                    )
                    onResult(null)
                }
                .onFailure {
                    _state.update { state -> state.copy(busy = false) }
                    onResult(it.message ?: "提交答案失败")
                }
        }
    }

    fun createAssignment(
        courseId: Long,
        request: CreateAssignmentRequest,
        onResult: (String?) -> Unit,
    ) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.createAssignment(courseId, request)
                .onSuccess {
                    val refreshed = repository.assignments(courseId)
                    _state.update { state ->
                        state.copy(busy = false, assignments = refreshed.getOrDefault(state.assignments))
                    }
                    _messages.emit("作业已发布")
                    onResult(null)
                }
                .onFailure {
                    _state.update { state -> state.copy(busy = false) }
                    onResult(it.message ?: "发布作业失败")
                }
        }
    }

    fun uploadAssignment(
        assignmentId: Long,
        courseId: Long,
        bytes: ByteArray,
        filename: String,
        contentType: String,
        note: String,
        onResult: (String?) -> Unit,
    ) {
        if (bytes.size > 10 * 1024 * 1024) {
            onResult("图片不能超过 10 MB")
            return
        }
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.submitAssignment(assignmentId, bytes, filename, contentType, note)
                .onSuccess {
                    val refreshed = repository.assignments(courseId)
                    _state.update { state ->
                        state.copy(busy = false, assignments = refreshed.getOrDefault(state.assignments))
                    }
                    _messages.emit(if (it.updatedAt != it.submittedAt) "作业已替换为最新版本" else "作业提交成功")
                    onResult(null)
                }
                .onFailure {
                    _state.update { state -> state.copy(busy = false) }
                    onResult(it.message ?: "作业提交失败")
                }
        }
    }

    fun loadAttendanceDetail(attendanceId: Long) {
        _state.update {
            it.copy(
                sheetLoading = true,
                sheetError = null,
                attendanceDetail = null,
                questionStats = null,
                assignmentSubmissions = null,
            )
        }
        viewModelScope.launch {
            repository.attendanceResults(attendanceId)
                .onSuccess { detail ->
                    _state.update { it.copy(sheetLoading = false, attendanceDetail = detail) }
                }
                .onFailure {
                    _state.update { state ->
                        state.copy(sheetLoading = false, sheetError = it.message ?: "名单加载失败")
                    }
                }
        }
    }

    fun loadQuestionStats(questionId: Long) {
        _state.update {
            it.copy(
                sheetLoading = true,
                sheetError = null,
                attendanceDetail = null,
                questionStats = null,
                assignmentSubmissions = null,
            )
        }
        viewModelScope.launch {
            repository.questionStats(questionId)
                .onSuccess { stats ->
                    _state.update { it.copy(sheetLoading = false, questionStats = stats) }
                }
                .onFailure {
                    _state.update { state ->
                        state.copy(sheetLoading = false, sheetError = it.message ?: "作答统计加载失败")
                    }
                }
        }
    }

    fun loadAssignmentSubmissions(assignmentId: Long) {
        _state.update {
            it.copy(
                sheetLoading = true,
                sheetError = null,
                attendanceDetail = null,
                questionStats = null,
                assignmentSubmissions = null,
            )
        }
        viewModelScope.launch {
            repository.assignmentSubmissions(assignmentId)
                .onSuccess { submissions ->
                    _state.update { it.copy(sheetLoading = false, assignmentSubmissions = submissions) }
                }
                .onFailure {
                    _state.update { state ->
                        state.copy(sheetLoading = false, sheetError = it.message ?: "提交情况加载失败")
                    }
                }
        }
    }

    fun loadStudentStats(courseId: Long, force: Boolean = false) {
        if (_state.value.studentStatsLoading) return
        if (_state.value.studentStats != null && !force) return
        _state.update { it.copy(studentStatsLoading = true) }
        viewModelScope.launch {
            repository.myStats(courseId)
                .onSuccess { stats -> _state.update { it.copy(studentStats = stats, studentStatsLoading = false) } }
                .onFailure { failure ->
                    _state.update { it.copy(studentStatsLoading = false) }
                    _messages.emit(failure.message ?: "个人成绩加载失败")
                }
        }
    }

    fun updateCourse(courseId: Long, name: String, description: String, onResult: (String?) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.updateCourse(courseId, name, description)
                .onSuccess { updated ->
                    _state.update { it.copy(busy = false, course = updated) }
                    _messages.emit("课程信息已更新")
                    onResult(null)
                }
                .onFailure {
                    _state.update { state -> state.copy(busy = false) }
                    onResult(it.message ?: "更新课程失败")
                }
        }
    }

    fun setArchived(courseId: Long, archived: Boolean, onResult: (String?) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = if (archived) repository.archiveCourse(courseId) else repository.unarchiveCourse(courseId)
            result.onSuccess { updated ->
                _state.update { it.copy(busy = false, course = updated) }
                _messages.emit(if (archived) "课程已归档" else "课程已取消归档")
                onResult(null)
            }.onFailure {
                _state.update { state -> state.copy(busy = false) }
                onResult(it.message ?: "课程状态更新失败")
            }
        }
    }

    fun deleteCourse(courseId: Long, onResult: (String?) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.deleteCourse(courseId)
                .onSuccess {
                    _state.update { it.copy(busy = false) }
                    _messages.emit("课程已删除")
                    onResult(null)
                }
                .onFailure {
                    _state.update { state -> state.copy(busy = false) }
                    onResult(it.message ?: "删除课程失败")
                }
        }
    }

    fun resetInviteCode(courseId: Long, onResult: (String?) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.resetInviteCode(courseId)
                .onSuccess { updated ->
                    _state.update { it.copy(busy = false, course = updated) }
                    _messages.emit("邀请码已重置")
                    onResult(null)
                }
                .onFailure {
                    _state.update { state -> state.copy(busy = false) }
                    onResult(it.message ?: "邀请码重置失败")
                }
        }
    }

    fun removeMember(courseId: Long, studentId: Long, onResult: (String?) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.removeMember(courseId, studentId)
                .onSuccess { load(courseId, quiet = true); onResult(null) }
                .onFailure { onResult(it.message ?: "移除学生失败") }
            _state.update { it.copy(busy = false) }
        }
    }

    fun leaveCourse(courseId: Long, onResult: (String?) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.leaveCourse(courseId)
                .onSuccess { _messages.emit("已退出课程"); onResult(null) }
                .onFailure { onResult(it.message ?: "退出课程失败") }
            _state.update { it.copy(busy = false) }
        }
    }

    fun resetStudentPassword(
        courseId: Long,
        studentId: Long,
        onResult: (ResetStudentPassword?, String?) -> Unit,
    ) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.resetStudentPassword(courseId, studentId)
                .onSuccess { onResult(it, null) }
                .onFailure { onResult(null, it.message ?: "重置密码失败") }
            _state.update { it.copy(busy = false) }
        }
    }

    fun updateQuestion(
        courseId: Long,
        questionId: Long,
        request: UpdateQuestionRequest,
        onResult: (String?) -> Unit,
    ) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.updateQuestion(questionId, request)
                .onSuccess {
                    val refreshed = repository.questions(courseId)
                    _state.update { state ->
                        state.copy(busy = false, questions = refreshed.getOrDefault(state.questions))
                    }
                    _messages.emit("题目已更新")
                    onResult(null)
                }
                .onFailure {
                    _state.update { state -> state.copy(busy = false) }
                    onResult(it.message ?: "更新题目失败")
                }
        }
    }

    fun closeQuestion(courseId: Long, questionId: Long, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            repository.closeQuestion(questionId)
                .onSuccess {
                    val refreshed = repository.questions(courseId)
                    _state.update { state -> state.copy(questions = refreshed.getOrDefault(state.questions)) }
                    _messages.emit("题目已提前截止")
                    onResult(null)
                }
                .onFailure { onResult(it.message ?: "截止题目失败") }
        }
    }

    fun deleteQuestion(courseId: Long, questionId: Long, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            repository.deleteQuestion(questionId)
                .onSuccess {
                    val refreshed = repository.questions(courseId)
                    _state.update { state -> state.copy(questions = refreshed.getOrDefault(state.questions)) }
                    _messages.emit("题目已删除")
                    onResult(null)
                }
                .onFailure { onResult(it.message ?: "删除题目失败") }
        }
    }

    fun gradeSubmission(
        assignmentId: Long,
        submissionId: Long,
        score: Int,
        feedback: String?,
        onResult: (String?) -> Unit,
    ) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.gradeSubmission(submissionId, score, feedback)
                .onSuccess {
                    val refreshed = repository.assignmentSubmissions(assignmentId)
                    _state.update { state ->
                        state.copy(
                            busy = false,
                            assignmentSubmissions = refreshed.getOrDefault(state.assignmentSubmissions),
                        )
                    }
                    _messages.emit("评分已保存")
                    onResult(null)
                }
                .onFailure {
                    _state.update { state -> state.copy(busy = false) }
                    onResult(it.message ?: "评分失败")
                }
        }
    }

    fun clearSheet() {
        _state.update {
            it.copy(
                sheetLoading = false,
                sheetError = null,
                attendanceDetail = null,
                questionStats = null,
                assignmentSubmissions = null,
            )
        }
    }
}
