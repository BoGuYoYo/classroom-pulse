package com.classpulse.web

import com.classpulse.security.UserPrincipal
import com.classpulse.service.AuthService
import com.classpulse.web.dto.AuthResponse
import com.classpulse.web.dto.ChangePasswordRequest
import com.classpulse.web.dto.LoginRequest
import com.classpulse.web.dto.RegisterRequest
import com.classpulse.web.dto.UserDto
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<ApiResponse<AuthResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authService.register(request)))

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ApiResponse<AuthResponse> =
        ApiResponse.success(authService.login(request))

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: UserPrincipal): ApiResponse<UserDto> =
        ApiResponse.success(authService.me(principal))

    @PutMapping("/password")
    fun changePassword(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: ChangePasswordRequest,
    ): ApiResponse<AuthResponse> = ApiResponse.success(authService.changePassword(principal, request))
}
