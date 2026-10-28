package com.classpulse.web

import com.classpulse.service.AssignmentService
import com.classpulse.web.dto.*
import jakarta.validation.Valid
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/v1")
class AssignmentController(private val assignmentService: AssignmentService) {
    @PostMapping("/courses/{courseId}/assignments")
    fun create(
        @PathVariable courseId: Long,
        @Valid @RequestBody request: CreateAssignmentRequest,
    ): ResponseEntity<ApiResponse<AssignmentDto>> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(assignmentService.create(courseId, request)))

    @GetMapping("/courses/{courseId}/assignments")
    fun list(@PathVariable courseId: Long): ApiResponse<List<AssignmentDto>> =
        ApiResponse.success(assignmentService.list(courseId))

    @PostMapping("/assignments/{assignmentId}/submissions")
    fun submit(
        @PathVariable assignmentId: Long,
        @RequestPart("file") file: MultipartFile,
        @RequestParam("note", required = false) note: String?,
    ): ApiResponse<SubmissionDto> =
        ApiResponse.success(assignmentService.submit(assignmentId, file, note))

    @GetMapping("/assignments/{assignmentId}/submissions")
    fun submissions(@PathVariable assignmentId: Long): ApiResponse<AssignmentSubmissionsDto> =
        ApiResponse.success(assignmentService.submissions(assignmentId))

    @GetMapping("/submissions/{submissionId}/image")
    fun image(@PathVariable submissionId: Long): ResponseEntity<Resource> {
        val stored = assignmentService.image(submissionId)
        val disposition = org.springframework.http.ContentDisposition.inline()
            .filename(stored.filename, StandardCharsets.UTF_8)
            .build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(stored.contentType))
            .cacheControl(CacheControl.noStore())
            .header("Content-Disposition", disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(stored.resource)
    }
}
