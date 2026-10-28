package com.classpulse.web

import com.classpulse.service.AttendanceService
import com.classpulse.web.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class AttendanceController(private val attendanceService: AttendanceService) {
    @PostMapping("/courses/{courseId}/attendance")
    fun create(
        @PathVariable courseId: Long,
        @Valid @RequestBody request: CreateAttendanceRequest,
    ): ResponseEntity<ApiResponse<AttendanceDto>> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(attendanceService.create(courseId, request)))

    @GetMapping("/courses/{courseId}/attendance")
    fun list(@PathVariable courseId: Long): ApiResponse<List<AttendanceDto>> =
        ApiResponse.success(attendanceService.list(courseId))

    @GetMapping("/attendance/{sessionId}/results")
    fun results(@PathVariable sessionId: Long): ApiResponse<AttendanceDetailDto> =
        ApiResponse.success(attendanceService.results(sessionId))

    @PostMapping("/attendance/{sessionId}/check-in")
    fun checkIn(
        @PathVariable sessionId: Long,
        @Valid @RequestBody request: CheckInRequest,
    ): ApiResponse<CheckInResultDto> =
        ApiResponse.success(attendanceService.checkIn(sessionId, request))
}
