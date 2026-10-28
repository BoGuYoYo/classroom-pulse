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
}
