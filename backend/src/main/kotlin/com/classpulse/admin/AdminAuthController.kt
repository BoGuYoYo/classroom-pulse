package com.classpulse.admin

import com.classpulse.domain.AdminSession
import com.classpulse.web.ApiResponse
import com.classpulse.web.dto.AdminLoginRequest
import com.classpulse.web.dto.AdminLoginResponse
import com.classpulse.web.dto.AdminUserDto
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/auth")
class AdminAuthController(
    private val authService: AdminAuthService,
    private val properties: com.classpulse.config.AppProperties,
) {
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: AdminLoginRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<AdminLoginResponse>> {
        val clientKey = servletRequest.getHeader("X-Forwarded-For")
            ?.substringBefore(',')
            ?.trim()
            ?.ifBlank { servletRequest.remoteAddr }
            ?: servletRequest.remoteAddr
        val result = authService.login(request.username, request.password, clientKey)
        val cookie = ResponseCookie.from(AdminSessionInterceptor.ADMIN_COOKIE, result.token)
            .httpOnly(true)
            .secure(servletRequest.isSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(properties.admin.sessionHours * 3600)
            .build()
        val response = AdminLoginResponse(
            admin = result.admin.toDto(),
            csrfToken = result.csrfToken,
            expiresAt = result.expiresAt,
        )
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(ApiResponse.success(response))
    }

    @GetMapping("/session")
    fun session(
        @RequestAttribute(AdminSessionInterceptor.ADMIN_SESSION_ATTRIBUTE) session: AdminSession,
    ): ApiResponse<AdminLoginResponse> = ApiResponse.success(
        AdminLoginResponse(
            admin = session.admin.toDto(),
            csrfToken = session.csrfToken,
            expiresAt = session.expiresAt,
        ),
    )

    @PostMapping("/logout")
    fun logout(
        @RequestAttribute(AdminSessionInterceptor.ADMIN_SESSION_ATTRIBUTE) session: AdminSession,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Boolean>> {
        authService.logout(request.cookies?.firstOrNull { it.name == AdminSessionInterceptor.ADMIN_COOKIE }?.value)
        val cookie = ResponseCookie.from(AdminSessionInterceptor.ADMIN_COOKIE, "")
            .httpOnly(true)
            .secure(request.isSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build()
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(ApiResponse.success(true))
    }

    private fun com.classpulse.domain.AdminUser.toDto() = AdminUserDto(
        id = requireNotNull(id),
        username = username,
        name = name,
        createdAt = createdAt,
    )
}
