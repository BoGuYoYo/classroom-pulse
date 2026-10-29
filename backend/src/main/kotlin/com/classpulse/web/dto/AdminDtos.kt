package com.classpulse.web.dto

import com.classpulse.domain.QuestionType
import com.classpulse.domain.Role
import jakarta.validation.constraints.*
import java.time.Instant

data class AdminLoginRequest(
    @field:NotBlank(message = "管理员账号不能为空") val username: String,
    @field:NotBlank(message = "密码不能为空") val password: String,
)

data class AdminUserDto(
    val id: Long,
    val username: String,
    val name: String,
    val createdAt: Instant,
)

data class AdminLoginResponse(
    val admin: AdminUserDto,
    val csrfToken: String,
    val expiresAt: Instant,
)

data class AdminAccountDto(
    val id: Long,
    val username: String,
    val name: String,
    val role: Role,
    val courseCount: Long,
    val createdAt: Instant,
    val version: Long,
)

data class CreateAdminAccountRequest(
    @field:NotBlank(message = "账号不能为空")
    @field:Pattern(regexp = "^[A-Za-z0-9_-]{4,40}$", message = "账号只能包含字母、数字、下划线或短横线，长度 4-40")
    val username: String,

    @field:NotBlank(message = "姓名不能为空")
    @field:Size(min = 2, max = 30, message = "姓名长度需在 2-30 之间")
    val name: String,

    @field:NotBlank(message = "密码不能为空")
    @field:Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
    val password: String,

    @field:NotNull(message = "角色不能为空")
    val role: Role?,
)

data class UpdateAdminAccountRequest(
    @field:NotBlank(message = "姓名不能为空")
    @field:Size(min = 2, max = 30, message = "姓名长度需在 2-30 之间")
    val name: String,

    @field:NotNull(message = "版本不能为空")
    val expectedVersion: Long?,
)

data class AdminPasswordResetResponse(
    val accountId: Long,
    val username: String,
    val temporaryPassword: String,
)

data class AdminAccountDeleteImpactDto(
    val accountId: Long,
    val username: String,
    val role: Role,
    val ownedCourseCount: Long,
    val membershipCount: Long,
    val answerCount: Long,
    val attendanceCount: Long,
    val submissionCount: Long,
)

data class AdminCourseDto(
    val id: Long,
    val name: String,
    val description: String,
    val inviteCode: String,
    val teacherId: Long,
    val teacherName: String,
    val memberCount: Long,
    val questionCount: Long,
    val assignmentCount: Long,
    val archived: Boolean,
    val deleted: Boolean,
    val createdAt: Instant,
    val version: Long,
)

data class CreateAdminCourseRequest(
    @field:NotBlank(message = "课程名称不能为空")
    @field:Size(max = 120, message = "课程名称不能超过 120 字")
    val name: String,

    @field:NotBlank(message = "课程简介不能为空")
    @field:Size(max = 2000, message = "课程简介不能超过 2000 字")
    val description: String,

    @field:NotNull(message = "任课教师不能为空")
    val teacherId: Long?,
)

data class UpdateAdminCourseRequest(
    @field:NotBlank(message = "课程名称不能为空")
    @field:Size(max = 120, message = "课程名称不能超过 120 字")
    val name: String,

    @field:NotBlank(message = "课程简介不能为空")
    @field:Size(max = 2000, message = "课程简介不能超过 2000 字")
    val description: String,

    @field:NotNull(message = "任课教师不能为空")
    val teacherId: Long?,

    @field:NotNull(message = "版本不能为空")
    val expectedVersion: Long?,
)

data class AdminCourseImpactDto(
    val courseId: Long,
    val members: Long,
    val questions: Long,
    val answers: Long,
    val assignments: Long,
    val submissions: Long,
    val attendanceSessions: Long,
    val attendanceRecords: Long,
)

data class AdminMemberDto(
    val studentId: Long,
    val studentNo: String,
    val name: String,
    val joinedAt: Instant,
)

data class AddAdminMemberRequest(
    @field:NotBlank(message = "学生账号不能为空") val studentNo: String,
)

data class AdminQuestionDto(
    val id: Long,
    val type: QuestionType,
    val prompt: String,
    val deadlineAt: Instant,
    val submissionCount: Long,
    val correctCount: Long,
)

data class AdminSubmissionDto(
    val id: Long,
    val studentNo: String,
    val studentName: String,
    val imageUrl: String,
    val note: String?,
    val score: Int?,
    val feedback: String?,
    val submittedAt: Instant,
    val updatedAt: Instant,
)

data class AdminAssignmentDto(
    val id: Long,
    val title: String,
    val description: String,
    val deadlineAt: Instant,
    val submissionCount: Long,
    val submissions: List<AdminSubmissionDto>,
)

data class AdminCourseDetailDto(
    val course: AdminCourseDto,
    val members: List<AdminMemberDto>,
    val questions: List<AdminQuestionDto>,
    val assignments: List<AdminAssignmentDto>,
)

data class AdminStudentDetailDto(
    val account: AdminAccountDto,
    val courses: List<StudentCourseStatsDto>,
)

data class AdminSummaryDto(
    val teachers: Long,
    val students: Long,
    val courses: Long,
    val archivedCourses: Long,
    val deletedCourses: Long,
)
