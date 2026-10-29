package com.classpulse.admin

import com.classpulse.web.ApiResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AdminSessionInterceptor(
    private val authService: AdminAuthService,
    private val objectMapper: ObjectMapper,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method.equals("OPTIONS", ignoreCase = true)) return true
        val token = request.cookies?.firstOrNull { it.name == ADMIN_COOKIE }?.value
        val session = authService.findSession(token)
        if (session == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员登录已失效，请重新登录")
            return false
        }
        val unsafe = request.method !in setOf("GET", "HEAD", "OPTIONS")
        if (unsafe && !authService.requireCsrf(session, request.getHeader("X-CSRF-Token"))) {
            writeError(response, HttpStatus.FORBIDDEN, "ADMIN_CSRF_INVALID", "页面安全校验已失效，请刷新后重试")
            return false
        }
        request.setAttribute(ADMIN_SESSION_ATTRIBUTE, session)
        return true
    }

    private fun writeError(response: HttpServletResponse, status: HttpStatus, code: String, message: String) {
        response.status = status.value()
        response.contentType = "application/json;charset=UTF-8"
        objectMapper.writeValue(response.writer, ApiResponse.error(code, message))
    }

    companion object {
        const val ADMIN_COOKIE = "CP_ADMIN_SESSION"
        const val ADMIN_SESSION_ATTRIBUTE = "adminSession"
    }
}
