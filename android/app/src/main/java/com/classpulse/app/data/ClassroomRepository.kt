package com.classpulse.app.data

import com.classpulse.app.data.api.ApiProvider
import com.classpulse.app.data.model.*
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class ApiFailure(
    val code: String,
    override val message: String,
) : Exception(message)

class ClassroomRepository(
    private val apiProvider: ApiProvider,
    private val sessionManager: SessionManager,
    private val gson: Gson,
) {
    suspend fun login(username: String, password: String): Result<AuthData> =
        call { apiProvider.api.login(LoginRequest(username.trim(), password)) }

    suspend fun register(studentNo: String, name: String, password: String): Result<AuthData> =
        call { apiProvider.api.register(RegisterRequest(studentNo.trim(), name.trim(), password)) }

    suspend fun me(): Result<User> = call { apiProvider.api.me() }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<AuthData> =
        call { apiProvider.api.changePassword(ChangePasswordRequest(currentPassword, newPassword)) }

    suspend fun courses(): Result<List<CourseSummary>> = call { apiProvider.api.courses() }

    suspend fun createCourse(name: String, description: String): Result<CourseSummary> =
        call { apiProvider.api.createCourse(CreateCourseRequest(name.trim(), description.trim())) }

    suspend fun joinCourse(inviteCode: String): Result<CourseSummary> =
        call { apiProvider.api.joinCourse(JoinCourseRequest(inviteCode.trim().uppercase())) }

    suspend fun course(courseId: Long): Result<CourseSummary> = call { apiProvider.api.course(courseId) }

    suspend fun members(courseId: Long): Result<List<Member>> = call { apiProvider.api.members(courseId) }

    suspend fun updateCourse(courseId: Long, name: String, description: String): Result<CourseSummary> =
        call { apiProvider.api.updateCourse(courseId, UpdateCourseRequest(name.trim(), description.trim())) }

    suspend fun archiveCourse(courseId: Long): Result<CourseSummary> = call { apiProvider.api.archiveCourse(courseId) }

    suspend fun unarchiveCourse(courseId: Long): Result<CourseSummary> = call { apiProvider.api.unarchiveCourse(courseId) }

    suspend fun deleteCourse(courseId: Long): Result<Boolean> = call { apiProvider.api.deleteCourse(courseId) }

    suspend fun resetInviteCode(courseId: Long): Result<CourseSummary> = call { apiProvider.api.resetInviteCode(courseId) }

    suspend fun removeMember(courseId: Long, studentId: Long): Result<Boolean> =
        call { apiProvider.api.removeMember(courseId, studentId) }

    suspend fun leaveCourse(courseId: Long): Result<Boolean> = call { apiProvider.api.leaveCourse(courseId) }

    suspend fun resetStudentPassword(courseId: Long, studentId: Long): Result<ResetStudentPassword> =
        call { apiProvider.api.resetStudentPassword(courseId, studentId) }

    suspend fun attendance(courseId: Long): Result<List<Attendance>> =
        call { apiProvider.api.attendance(courseId) }

    suspend fun createAttendance(courseId: Long, title: String, minutes: Int): Result<Attendance> =
        call { apiProvider.api.createAttendance(courseId, CreateAttendanceRequest(title.trim(), minutes)) }

    suspend fun checkIn(attendanceId: Long, code: String): Result<CheckInResult> =
        call { apiProvider.api.checkIn(attendanceId, CheckInRequest(code.trim())) }

    suspend fun attendanceResults(attendanceId: Long): Result<AttendanceDetail> =
        call { apiProvider.api.attendanceResults(attendanceId) }

    suspend fun questions(courseId: Long): Result<List<Question>> =
        call { apiProvider.api.questions(courseId) }

    suspend fun createQuestion(courseId: Long, request: CreateQuestionRequest): Result<Question> =
        call { apiProvider.api.createQuestion(courseId, request) }

    suspend fun updateQuestion(questionId: Long, request: UpdateQuestionRequest): Result<Question> =
        call { apiProvider.api.updateQuestion(questionId, request) }

    suspend fun closeQuestion(questionId: Long): Result<Question> = call { apiProvider.api.closeQuestion(questionId) }

    suspend fun deleteQuestion(questionId: Long): Result<Boolean> = call { apiProvider.api.deleteQuestion(questionId) }

    suspend fun answer(questionId: Long, answer: String): Result<AnswerResult> =
        call { apiProvider.api.answer(questionId, AnswerRequest(answer.trim())) }

    suspend fun questionStats(questionId: Long): Result<QuestionStats> =
        call { apiProvider.api.questionStats(questionId) }

    suspend fun assignments(courseId: Long): Result<List<Assignment>> =
        call { apiProvider.api.assignments(courseId) }

    suspend fun createAssignment(courseId: Long, request: CreateAssignmentRequest): Result<Assignment> =
        call { apiProvider.api.createAssignment(courseId, request) }

    suspend fun submitAssignment(
        assignmentId: Long,
        bytes: ByteArray,
        filename: String,
        contentType: String,
        note: String,
    ): Result<Submission> {
        val mime = contentType.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()!!
        val filePart = MultipartBody.Part.createFormData(
            "file",
            filename,
            bytes.toRequestBody(mime),
        )
        val notePart = note.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())
        return call { apiProvider.api.submitAssignment(assignmentId, filePart, notePart) }
    }

    suspend fun assignmentSubmissions(assignmentId: Long): Result<AssignmentSubmissions> =
        call { apiProvider.api.assignmentSubmissions(assignmentId) }

    suspend fun gradeSubmission(
        submissionId: Long,
        score: Int,
        feedback: String?,
        expectedVersion: Long,
    ): Result<Submission> =
        call { apiProvider.api.gradeSubmission(submissionId, GradeSubmissionRequest(score, feedback, expectedVersion)) }

    suspend fun stats(courseId: Long): Result<CourseStats> = call { apiProvider.api.stats(courseId) }

    suspend fun myStats(courseId: Long): Result<StudentCourseStats> = call { apiProvider.api.myStats(courseId) }

    fun imageUrl(path: String): String = sessionManager.fullUrl(path)

    private suspend fun <T> call(block: suspend () -> ApiEnvelope<T>): Result<T> {
        return try {
            val envelope = block()
            if (envelope.success && envelope.data != null) {
                Result.success(envelope.data)
            } else {
                val code = envelope.code ?: "REQUEST_FAILED"
                val message = envelope.message ?: "请求失败，请稍后重试"
                handleAuthFailure(code)
                Result.failure(ApiFailure(code, message))
            }
        } catch (exception: HttpException) {
            val raw = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
            val error = raw?.let {
                runCatching { gson.fromJson(it, ErrorEnvelope::class.java) }.getOrNull()
            }
            val code = error?.code ?: "HTTP_${exception.code()}"
            val message = error?.message ?: when (exception.code()) {
                401 -> "登录已失效，请重新登录"
                403 -> "无权执行该操作"
                404 -> "请求的资源不存在"
                413 -> "上传文件过大"
                else -> "服务器返回 ${exception.code()}，请稍后重试"
            }
            handleAuthFailure(code)
            Result.failure(ApiFailure(code, message))
        } catch (exception: SocketTimeoutException) {
            Result.failure(ApiFailure("TIMEOUT", "连接服务器超时，请检查网络后重试"))
        } catch (exception: IOException) {
            Result.failure(ApiFailure("NETWORK_ERROR", "无法连接服务器，请检查网络或服务器地址"))
        } catch (exception: Exception) {
            Result.failure(ApiFailure("UNKNOWN_ERROR", exception.message ?: "发生未知错误"))
        }
    }

    private suspend fun handleAuthFailure(code: String) {
        if (sessionManager.token != null && (code == "UNAUTHORIZED" || code == "TOKEN_INVALID")) {
            sessionManager.forceLogout()
        }
    }
}
