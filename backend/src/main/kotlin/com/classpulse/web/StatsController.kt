package com.classpulse.web

import com.classpulse.service.StatsService
import com.classpulse.web.dto.CourseStatsDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class StatsController(private val statsService: StatsService) {
    @GetMapping("/courses/{courseId}/stats")
    fun stats(@PathVariable courseId: Long): ApiResponse<CourseStatsDto> =
        ApiResponse.success(statsService.courseStats(courseId))
}
