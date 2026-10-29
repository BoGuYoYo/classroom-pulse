package com.classpulse.admin

import com.classpulse.domain.Role
import com.classpulse.web.ApiResponse
import com.classpulse.web.dto.*
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
class AdminController(private val adminService: AdminService) {
    @GetMapping("/summary")
    fun summary(): ApiResponse<AdminSummaryDto> = ApiResponse.success(adminService.summary())

    @GetMapping("/accounts")
    fun accounts(
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) role: Role?,
    ): ApiResponse<List<AdminAccountDto>> = ApiResponse.success(adminService.accounts(query, role))

    @PostMapping("/accounts")
    fun createAccount(@Valid @RequestBody request: CreateAdminAccountRequest): ApiResponse<AdminAccountDto> =
        ApiResponse.success(adminService.createAccount(request))

    @PutMapping("/accounts/{accountId}")
    fun updateAccount(
        @PathVariable accountId: Long,
        @Valid @RequestBody request: UpdateAdminAccountRequest,
    ): ApiResponse<AdminAccountDto> = ApiResponse.success(adminService.updateAccount(accountId, request))

    @PostMapping("/accounts/{accountId}/reset-password")
    fun resetPassword(@PathVariable accountId: Long): ApiResponse<AdminPasswordResetResponse> =
        ApiResponse.success(adminService.resetAccountPassword(accountId))

    @GetMapping("/accounts/{accountId}/impact")
    fun accountImpact(@PathVariable accountId: Long): ApiResponse<AdminAccountDeleteImpactDto> =
        ApiResponse.success(adminService.accountImpact(accountId))

    @DeleteMapping("/accounts/{accountId}")
    fun deleteAccount(
        @PathVariable accountId: Long,
        @RequestParam expectedVersion: Long,
        @RequestParam(defaultValue = "false") confirm: Boolean,
    ): ApiResponse<Boolean> {
        adminService.deleteAccount(accountId, expectedVersion, confirm)
        return ApiResponse.success(true)
    }

    @GetMapping("/accounts/{accountId}/student-detail")
    fun studentDetail(@PathVariable accountId: Long): ApiResponse<AdminStudentDetailDto> =
        ApiResponse.success(adminService.studentDetail(accountId))

    @GetMapping("/courses")
    fun courses(
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) status: String?,
    ): ApiResponse<List<AdminCourseDto>> = ApiResponse.success(adminService.courses(query, status))

    @PostMapping("/courses")
    fun createCourse(@Valid @RequestBody request: CreateAdminCourseRequest): ApiResponse<AdminCourseDto> =
        ApiResponse.success(adminService.createCourse(request))

    @PutMapping("/courses/{courseId}")
    fun updateCourse(
        @PathVariable courseId: Long,
        @Valid @RequestBody request: UpdateAdminCourseRequest,
    ): ApiResponse<AdminCourseDto> = ApiResponse.success(adminService.updateCourse(courseId, request))

    @PostMapping("/courses/{courseId}/archive")
    fun archive(
        @PathVariable courseId: Long,
        @RequestParam expectedVersion: Long,
    ): ApiResponse<AdminCourseDto> =
        ApiResponse.success(adminService.setArchived(courseId, true, expectedVersion))

    @PostMapping("/courses/{courseId}/unarchive")
    fun unarchive(
        @PathVariable courseId: Long,
        @RequestParam expectedVersion: Long,
    ): ApiResponse<AdminCourseDto> =
        ApiResponse.success(adminService.setArchived(courseId, false, expectedVersion))

    @PostMapping("/courses/{courseId}/invite-code/reset")
    fun resetInvite(
        @PathVariable courseId: Long,
        @RequestParam expectedVersion: Long,
    ): ApiResponse<AdminCourseDto> =
        ApiResponse.success(adminService.resetInviteCode(courseId, expectedVersion))

    @DeleteMapping("/courses/{courseId}")
    fun softDelete(
        @PathVariable courseId: Long,
        @RequestParam expectedVersion: Long,
    ): ApiResponse<AdminCourseDto> =
        ApiResponse.success(adminService.softDeleteCourse(courseId, expectedVersion))

    @GetMapping("/courses/{courseId}/impact")
    fun courseImpact(@PathVariable courseId: Long): ApiResponse<AdminCourseImpactDto> =
        ApiResponse.success(adminService.courseImpact(courseId))

    @DeleteMapping("/courses/{courseId}/permanent")
    fun permanentDelete(
        @PathVariable courseId: Long,
        @RequestParam expectedVersion: Long,
        @RequestParam confirmName: String,
    ): ApiResponse<Boolean> {
        adminService.permanentDeleteCourse(courseId, expectedVersion, confirmName)
        return ApiResponse.success(true)
    }

    @GetMapping("/courses/{courseId}/detail")
    fun courseDetail(@PathVariable courseId: Long): ApiResponse<AdminCourseDetailDto> =
        ApiResponse.success(adminService.courseDetail(courseId))

    @GetMapping("/courses/{courseId}/members")
    fun members(@PathVariable courseId: Long): ApiResponse<List<AdminMemberDto>> =
        ApiResponse.success(adminService.members(courseId))

    @PostMapping("/courses/{courseId}/members")
    fun addMember(
        @PathVariable courseId: Long,
        @Valid @RequestBody request: AddAdminMemberRequest,
    ): ApiResponse<List<AdminMemberDto>> = ApiResponse.success(adminService.addMember(courseId, request))

    @DeleteMapping("/courses/{courseId}/members/{studentId}")
    fun removeMember(@PathVariable courseId: Long, @PathVariable studentId: Long): ApiResponse<Boolean> {
        adminService.removeMember(courseId, studentId)
        return ApiResponse.success(true)
    }
}
