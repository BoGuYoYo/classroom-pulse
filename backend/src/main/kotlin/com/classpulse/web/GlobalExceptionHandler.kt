package com.classpulse.web

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    private fun error(status: HttpStatus, code: String, message: String): ResponseEntity<ApiResponse<Unit>> =
        ResponseEntity.status(status).body(ApiResponse.error(code, message))

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ApiResponse<Unit>> =
        error(ex.status, ex.code, ex.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val message = ex.bindingResult.allErrors.joinToString("；") { error ->
            if (error is FieldError) "${error.field}: ${error.defaultMessage}" else error.defaultMessage ?: "参数错误"
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraint(ex: ConstraintViolationException): ResponseEntity<ApiResponse<Unit>> {
        val message = ex.constraintViolations.joinToString("；") { "${it.propertyPath}: ${it.message}" }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ResponseEntity<ApiResponse<Unit>> =
        error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录状态无效，请重新登录")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleForbidden(ex: AccessDeniedException): ResponseEntity<ApiResponse<Unit>> =
        error(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问该资源")

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleOversized(ex: MaxUploadSizeExceededException): ResponseEntity<ApiResponse<Unit>> =
        error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "图片不能超过 10 MB")

    @ExceptionHandler(MultipartException::class)
    fun handleMultipart(ex: MultipartException): ResponseEntity<ApiResponse<Unit>> =
        error(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART", "上传内容格式不正确")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Unit>> =
        error(HttpStatus.BAD_REQUEST, "INVALID_JSON", "请求内容格式不正确")

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(ex: NoResourceFoundException): ResponseEntity<ApiResponse<Unit>> =
        error(HttpStatus.NOT_FOUND, "NOT_FOUND", "接口不存在")

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleIntegrity(ex: DataIntegrityViolationException): ResponseEntity<ApiResponse<Unit>> {
        logger.warn("数据库约束冲突", ex)
        return error(HttpStatus.CONFLICT, "CONFLICT", "数据已存在或关联不合法")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception): ResponseEntity<ApiResponse<Unit>> {
        logger.error("未处理异常", ex)
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时不可用，请稍后再试")
    }
}
