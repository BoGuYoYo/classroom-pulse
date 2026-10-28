package com.classpulse.web.dto

import com.classpulse.domain.QuestionType
import com.classpulse.domain.Role
import jakarta.validation.constraints.*
import java.time.Instant

// ---------- Auth ----------

data class UserDto(
    val id: Long,
    val username: String,
    val name: String,
    val role: Role,
)

data class AuthResponse(
    val token: String,
    val user: UserDto,
)

data class RegisterRequest(
    @field:NotBlank(message = "学号不能为空")
    @field:Pattern(regexp = "^[A-Za-z0-9_-]{4,40}$", message = "学号只能包含字母、数字、下划线或短横线，长度 4-40")
    val studentNo: String,

    @field:NotBlank(message = "姓名不能为空")
    @field:Size(min = 2, max = 30, message = "姓名长度需在 2-30 之间")
    val name: String,

    @field:NotBlank(message = "密码不能为空")
    @field:Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
    val password: String,
)

data class LoginRequest(
    @field:NotBlank(message = "账号不能为空") val username: String,
    @field:NotBlank(message = "密码不能为空") val password: String,
)

// ---------- Course ----------

data class CreateCourseRequest(
    @field:NotBlank(message = "课程名称不能为空")
    @field:Size(max = 120, message = "课程名称不能超过 120 字")
    val name: String,

    @field:NotBlank(message = "课程简介不能为空")
    @field:Size(max = 2000, message = "课程简介不能超过 2000 字")
    val description: String,
)

data class JoinCourseRequest(
    @field:NotBlank(message = "邀请码不能为空")
    @field:Pattern(regexp = "^[A-Za-z0-9]{4,12}$", message = "邀请码格式不正确")
    val inviteCode: String,
)

data class CourseSummaryDto(
    val id: Long,
    val name: String,
    val description: String,
    val inviteCode: String?,
    val teacherName: String,
    val memberCount: Long,
    val createdAt: Instant,
    val joinedAt: Instant?,
)

data class MemberDto(
    val studentId: Long,
    val studentNo: String,
    val name: String,
    val joinedAt: Instant,
)

// ---------- Attendance ----------

data class CreateAttendanceRequest(
    @field:NotBlank(message = "签到标题不能为空")
    @field:Size(max = 160, message = "签到标题不能超过 160 字")
    val title: String,

    @field:Min(value = 1, message = "有效时长至少 1 分钟")
    @field:Max(value = 180, message = "有效时长最多 180 分钟")
    val durationMinutes: Int,
)

data class CheckInRequest(
    @field:NotBlank(message = "签到码不能为空")
    @field:Pattern(regexp = "^\\d{6}$", message = "签到码为 6 位数字")
    val code: String,
)

data class AttendanceDto(
    val id: Long,
    val courseId: Long,
    val title: String,
    val code: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val createdAt: Instant,
    val memberCount: Long,
    val presentCount: Long,
    val active: Boolean,
    val myCheckedIn: Boolean,
    val myCheckedAt: Instant?,
)

data class AttendancePersonDto(
    val studentId: Long,
    val studentNo: String,
    val name: String,
    val checkedAt: Instant?,
)

data class AttendanceDetailDto(
    val attendance: AttendanceDto,
    val present: List<AttendancePersonDto>,
    val absent: List<AttendancePersonDto>,
)

data class CheckInResultDto(
    val attendanceId: Long,
    val title: String,
    val checkedAt: Instant,
    val message: String,
)

// ---------- Questions ----------

data class CreateQuestionRequest(
    @field:NotNull(message = "题型不能为空")
    val type: QuestionType?,

    @field:NotBlank(message = "题干不能为空")
    @field:Size(max = 2000, message = "题干不能超过 2000 字")
    val prompt: String,

    @field:Size(max = 6, message = "选项不能超过 6 个")
    val options: List<String>? = null,

    @field:NotBlank(message = "参考答案不能为空")
    @field:Size(max = 500, message = "参考答案不能超过 500 字")
    val correctAnswer: String,

    @field:NotNull(message = "截止时间不能为空")
    @field:Future(message = "截止时间必须晚于当前时间")
    val deadlineAt: Instant?,
)

data class QuestionOptionDto(
    val key: String,
    val text: String,
)

data class AnswerRequest(
    @field:NotBlank(message = "答案不能为空")
    @field:Size(max = 2000, message = "答案不能超过 2000 字")
    val answer: String,
)

data class AnswerResultDto(
    val questionId: Long,
    val answer: String,
    val isCorrect: Boolean,
    val correctAnswer: String,
    val submittedAt: Instant,
)

data class AnswerDetailDto(
    val studentId: Long,
    val studentNo: String,
    val studentName: String,
    val answer: String,
    val isCorrect: Boolean,
    val submittedAt: Instant,
)

data class QuestionDto(
    val id: Long,
    val courseId: Long,
    val type: QuestionType,
    val prompt: String,
    val options: List<QuestionOptionDto>,
    val deadlineAt: Instant,
    val createdAt: Instant,
    val submissionCount: Long?,
    val correctCount: Long?,
    val correctRate: Double?,
    val answered: Boolean,
    val myAnswer: String?,
    val myIsCorrect: Boolean?,
    val correctAnswer: String?,
)

data class OptionDistributionDto(
    val key: String,
    val text: String,
    val count: Long,
)

data class QuestionStatsDto(
    val question: QuestionDto,
    val answerDetails: List<AnswerDetailDto>,
    val optionDistribution: List<OptionDistributionDto>,
    val conclusion: String,
)

// ---------- Assignments ----------

data class CreateAssignmentRequest(
    @field:NotBlank(message = "作业标题不能为空")
    @field:Size(max = 160, message = "作业标题不能超过 160 字")
    val title: String,

    @field:NotBlank(message = "作业说明不能为空")
    @field:Size(max = 4000, message = "作业说明不能超过 4000 字")
    val description: String,

    @field:NotNull(message = "截止时间不能为空")
    @field:Future(message = "截止时间必须晚于当前时间")
    val deadlineAt: Instant?,
)

data class SubmissionDto(
    val id: Long,
    val assignmentId: Long,
    val studentId: Long,
    val studentName: String?,
    val imageUrl: String,
    val originalFilename: String,
    val contentType: String,
    val fileSize: Long,
    val note: String?,
    val submittedAt: Instant,
    val updatedAt: Instant,
)

data class AssignmentDto(
    val id: Long,
    val courseId: Long,
    val title: String,
    val description: String,
    val deadlineAt: Instant,
    val createdAt: Instant,
    val submissionCount: Long,
    val memberCount: Long,
    val active: Boolean,
    val mySubmission: SubmissionDto?,
)

data class StudentSubmissionDto(
    val studentId: Long,
    val studentNo: String,
    val studentName: String,
    val submitted: Boolean,
    val submission: SubmissionDto?,
)

data class AssignmentSubmissionsDto(
    val assignment: AssignmentDto,
    val rows: List<StudentSubmissionDto>,
    val submittedCount: Long,
    val missingCount: Long,
)

// ---------- Statistics ----------

data class AttendanceStatItemDto(
    val id: Long,
    val title: String,
    val startsAt: Instant,
    val presentCount: Long,
    val expectedCount: Long,
    val rate: Double,
)

data class AttendanceStatsDto(
    val items: List<AttendanceStatItemDto>,
    val averageRate: Double,
    val conclusion: String,
)

data class QuestionStatItemDto(
    val id: Long,
    val prompt: String,
    val type: QuestionType,
    val submissionCount: Long,
    val correctCount: Long,
    val correctRate: Double,
)

data class QuestionStatsSummaryDto(
    val items: List<QuestionStatItemDto>,
    val averageCorrectRate: Double,
    val totalSubmissions: Long,
    val conclusion: String,
)

data class AssignmentStatItemDto(
    val id: Long,
    val title: String,
    val submissionCount: Long,
    val expectedCount: Long,
    val rate: Double,
)

data class AssignmentStatsDto(
    val items: List<AssignmentStatItemDto>,
    val averageRate: Double,
    val conclusion: String,
)

data class CourseSummaryStatsDto(
    val overallRate: Double,
    val highlights: List<String>,
)

data class CourseStatsDto(
    val courseId: Long,
    val courseName: String,
    val memberCount: Long,
    val generatedAt: Instant,
    val attendance: AttendanceStatsDto,
    val questions: QuestionStatsSummaryDto,
    val assignments: AssignmentStatsDto,
    val summary: CourseSummaryStatsDto,
)
