package com.classpulse.web

import com.classpulse.service.CourseService
import com.classpulse.web.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/courses")
class CourseController(private val courseService: CourseService) {
    @PostMapping
    fun create(@Valid @RequestBody request: CreateCourseRequest): ResponseEntity<ApiResponse<CourseSummaryDto>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(courseService.create(request)))

    @PostMapping("/join")
    fun join(@Valid @RequestBody request: JoinCourseRequest): ApiResponse<CourseSummaryDto> =
        ApiResponse.success(courseService.join(request))

    @GetMapping
    fun list(): ApiResponse<List<CourseSummaryDto>> = ApiResponse.success(courseService.list())

    @GetMapping("/{courseId}")
    fun detail(@PathVariable courseId: Long): ApiResponse<CourseSummaryDto> =
        ApiResponse.success(courseService.detail(courseId))

    @GetMapping("/{courseId}/members")
    fun members(@PathVariable courseId: Long): ApiResponse<List<MemberDto>> =
        ApiResponse.success(courseService.members(courseId))

    @PutMapping("/{courseId}")
    fun update(
        @PathVariable courseId: Long,
        @Valid @RequestBody request: UpdateCourseRequest,
    ): ApiResponse<CourseSummaryDto> = ApiResponse.success(courseService.update(courseId, request))

    @PostMapping("/{courseId}/archive")
    fun archive(@PathVariable courseId: Long): ApiResponse<CourseSummaryDto> =
        ApiResponse.success(courseService.setArchived(courseId, true))

    @PostMapping("/{courseId}/unarchive")
    fun unarchive(@PathVariable courseId: Long): ApiResponse<CourseSummaryDto> =
        ApiResponse.success(courseService.setArchived(courseId, false))

    @DeleteMapping("/{courseId}")
    fun delete(@PathVariable courseId: Long): ApiResponse<Boolean> {
        courseService.delete(courseId)
        return ApiResponse.success(true)
    }

    @PostMapping("/{courseId}/invite-code/reset")
    fun resetInviteCode(@PathVariable courseId: Long): ApiResponse<CourseSummaryDto> =
        ApiResponse.success(courseService.resetInviteCode(courseId))

    @DeleteMapping("/{courseId}/members/{studentId}")
    fun removeMember(@PathVariable courseId: Long, @PathVariable studentId: Long): ApiResponse<Boolean> {
        courseService.removeMember(courseId, studentId)
        return ApiResponse.success(true)
    }

    @DeleteMapping("/{courseId}/membership")
    fun leave(@PathVariable courseId: Long): ApiResponse<Boolean> {
        courseService.leave(courseId)
        return ApiResponse.success(true)
    }

    @PostMapping("/{courseId}/members/{studentId}/reset-password")
    fun resetStudentPassword(
        @PathVariable courseId: Long,
        @PathVariable studentId: Long,
    ): ApiResponse<ResetStudentPasswordDto> =
        ApiResponse.success(courseService.resetStudentPassword(courseId, studentId))
}
