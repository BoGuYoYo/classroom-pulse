package com.classpulse.app.data.api

import com.classpulse.app.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface ClassroomApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiEnvelope<AuthData>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiEnvelope<AuthData>

    @GET("api/v1/auth/me")
    suspend fun me(): ApiEnvelope<User>

    @PUT("api/v1/auth/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): ApiEnvelope<AuthData>

    @GET("api/v1/courses")
    suspend fun courses(): ApiEnvelope<List<CourseSummary>>

    @POST("api/v1/courses")
    suspend fun createCourse(@Body request: CreateCourseRequest): ApiEnvelope<CourseSummary>

    @POST("api/v1/courses/join")
    suspend fun joinCourse(@Body request: JoinCourseRequest): ApiEnvelope<CourseSummary>

    @GET("api/v1/courses/{courseId}")
    suspend fun course(@Path("courseId") courseId: Long): ApiEnvelope<CourseSummary>

    @GET("api/v1/courses/{courseId}/members")
    suspend fun members(@Path("courseId") courseId: Long): ApiEnvelope<List<Member>>

    @PUT("api/v1/courses/{courseId}")
    suspend fun updateCourse(
        @Path("courseId") courseId: Long,
        @Body request: UpdateCourseRequest,
    ): ApiEnvelope<CourseSummary>

    @POST("api/v1/courses/{courseId}/archive")
    suspend fun archiveCourse(@Path("courseId") courseId: Long): ApiEnvelope<CourseSummary>

    @POST("api/v1/courses/{courseId}/unarchive")
    suspend fun unarchiveCourse(@Path("courseId") courseId: Long): ApiEnvelope<CourseSummary>

    @DELETE("api/v1/courses/{courseId}")
    suspend fun deleteCourse(@Path("courseId") courseId: Long): ApiEnvelope<Boolean>

    @POST("api/v1/courses/{courseId}/invite-code/reset")
    suspend fun resetInviteCode(@Path("courseId") courseId: Long): ApiEnvelope<CourseSummary>

    @DELETE("api/v1/courses/{courseId}/members/{studentId}")
    suspend fun removeMember(
        @Path("courseId") courseId: Long,
        @Path("studentId") studentId: Long,
    ): ApiEnvelope<Boolean>

    @DELETE("api/v1/courses/{courseId}/membership")
    suspend fun leaveCourse(@Path("courseId") courseId: Long): ApiEnvelope<Boolean>

    @POST("api/v1/courses/{courseId}/members/{studentId}/reset-password")
    suspend fun resetStudentPassword(
        @Path("courseId") courseId: Long,
        @Path("studentId") studentId: Long,
    ): ApiEnvelope<ResetStudentPassword>

    @GET("api/v1/courses/{courseId}/attendance")
    suspend fun attendance(@Path("courseId") courseId: Long): ApiEnvelope<List<Attendance>>

    @POST("api/v1/courses/{courseId}/attendance")
    suspend fun createAttendance(
        @Path("courseId") courseId: Long,
        @Body request: CreateAttendanceRequest,
    ): ApiEnvelope<Attendance>

    @POST("api/v1/attendance/{attendanceId}/check-in")
    suspend fun checkIn(
        @Path("attendanceId") attendanceId: Long,
        @Body request: CheckInRequest,
    ): ApiEnvelope<CheckInResult>

    @GET("api/v1/attendance/{attendanceId}/results")
    suspend fun attendanceResults(@Path("attendanceId") attendanceId: Long): ApiEnvelope<AttendanceDetail>

    @GET("api/v1/courses/{courseId}/questions")
    suspend fun questions(@Path("courseId") courseId: Long): ApiEnvelope<List<Question>>

    @POST("api/v1/courses/{courseId}/questions")
    suspend fun createQuestion(
        @Path("courseId") courseId: Long,
        @Body request: CreateQuestionRequest,
    ): ApiEnvelope<Question>

    @PUT("api/v1/questions/{questionId}")
    suspend fun updateQuestion(
        @Path("questionId") questionId: Long,
        @Body request: UpdateQuestionRequest,
    ): ApiEnvelope<Question>

    @POST("api/v1/questions/{questionId}/close")
    suspend fun closeQuestion(@Path("questionId") questionId: Long): ApiEnvelope<Question>

    @DELETE("api/v1/questions/{questionId}")
    suspend fun deleteQuestion(@Path("questionId") questionId: Long): ApiEnvelope<Boolean>

    @POST("api/v1/questions/{questionId}/answers")
    suspend fun answer(
        @Path("questionId") questionId: Long,
        @Body request: AnswerRequest,
    ): ApiEnvelope<AnswerResult>

    @GET("api/v1/questions/{questionId}/stats")
    suspend fun questionStats(@Path("questionId") questionId: Long): ApiEnvelope<QuestionStats>

    @GET("api/v1/courses/{courseId}/assignments")
    suspend fun assignments(@Path("courseId") courseId: Long): ApiEnvelope<List<Assignment>>

    @POST("api/v1/courses/{courseId}/assignments")
    suspend fun createAssignment(
        @Path("courseId") courseId: Long,
        @Body request: CreateAssignmentRequest,
    ): ApiEnvelope<Assignment>

    @Multipart
    @POST("api/v1/assignments/{assignmentId}/submissions")
    suspend fun submitAssignment(
        @Path("assignmentId") assignmentId: Long,
        @Part file: MultipartBody.Part,
        @Part("note") note: RequestBody,
    ): ApiEnvelope<Submission>

    @GET("api/v1/assignments/{assignmentId}/submissions")
    suspend fun assignmentSubmissions(@Path("assignmentId") assignmentId: Long): ApiEnvelope<AssignmentSubmissions>

    @PUT("api/v1/submissions/{submissionId}/grade")
    suspend fun gradeSubmission(
        @Path("submissionId") submissionId: Long,
        @Body request: GradeSubmissionRequest,
    ): ApiEnvelope<Submission>

    @GET("api/v1/courses/{courseId}/stats")
    suspend fun stats(@Path("courseId") courseId: Long): ApiEnvelope<CourseStats>

    @GET("api/v1/courses/{courseId}/my-stats")
    suspend fun myStats(@Path("courseId") courseId: Long): ApiEnvelope<StudentCourseStats>
}
