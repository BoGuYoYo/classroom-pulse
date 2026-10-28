package com.classpulse.web

import java.time.Instant

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val code: String? = null,
    val timestamp: Instant = Instant.now(),
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)
        fun error(code: String, message: String): ApiResponse<Unit> = ApiResponse(
            success = false,
            code = code,
            message = message,
        )
    }
}
