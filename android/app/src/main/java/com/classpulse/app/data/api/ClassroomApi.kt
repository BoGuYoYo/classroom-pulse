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

    @GET("api/v1/courses/{courseId}/stats")
    suspend fun stats(@Path("courseId") courseId: Long): ApiEnvelope<CourseStats>
}
