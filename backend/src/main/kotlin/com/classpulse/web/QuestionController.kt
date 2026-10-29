package com.classpulse.web

import com.classpulse.service.QuestionService
import com.classpulse.web.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class QuestionController(private val questionService: QuestionService) {
    @PostMapping("/courses/{courseId}/questions")
    fun create(
        @PathVariable courseId: Long,
        @Valid @RequestBody request: CreateQuestionRequest,
    ): ResponseEntity<ApiResponse<QuestionDto>> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(questionService.create(courseId, request)))

    @GetMapping("/courses/{courseId}/questions")
    fun list(@PathVariable courseId: Long): ApiResponse<List<QuestionDto>> =
        ApiResponse.success(questionService.list(courseId))

    @PostMapping("/questions/{questionId}/answers")
    fun answer(
        @PathVariable questionId: Long,
        @Valid @RequestBody request: AnswerRequest,
    ): ApiResponse<AnswerResultDto> =
        ApiResponse.success(questionService.submit(questionId, request))

    @GetMapping("/questions/{questionId}/stats")
    fun stats(@PathVariable questionId: Long): ApiResponse<QuestionStatsDto> =
        ApiResponse.success(questionService.stats(questionId))

    @PutMapping("/questions/{questionId}")
    fun update(
        @PathVariable questionId: Long,
        @Valid @RequestBody request: UpdateQuestionRequest,
    ): ApiResponse<QuestionDto> = ApiResponse.success(questionService.update(questionId, request))

    @PostMapping("/questions/{questionId}/close")
    fun close(@PathVariable questionId: Long): ApiResponse<QuestionDto> =
        ApiResponse.success(questionService.close(questionId))

    @DeleteMapping("/questions/{questionId}")
    fun delete(@PathVariable questionId: Long): ApiResponse<Boolean> {
        questionService.delete(questionId)
        return ApiResponse.success(true)
    }
}
