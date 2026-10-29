package com.classpulse.service

import com.classpulse.domain.Role
import com.classpulse.domain.User
import com.classpulse.repository.UserRepository
import com.classpulse.security.JwtService
import com.classpulse.security.UserPrincipal
import com.classpulse.web.ApiException
import com.classpulse.web.dto.AuthResponse
import com.classpulse.web.dto.ChangePasswordRequest
import com.classpulse.web.dto.LoginRequest
import com.classpulse.web.dto.RegisterRequest
import com.classpulse.web.dto.UserDto
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val username = request.studentNo.trim()
        if (userRepository.existsByUsername(username)) {
            throw ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "该学号已注册，请直接登录")
        }
        val user = userRepository.save(
            User(
                username = username,
                name = request.name.trim(),
                role = Role.STUDENT,
                passwordHash = passwordEncoder.encode(request.password),
                createdAt = Instant.now(),
            ),
        )
        return AuthResponse(jwtService.issue(user), user.toDto())
    }

    @Transactional(readOnly = true)
    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByUsername(request.username.trim())
        if (user == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误")
        }
        return AuthResponse(jwtService.issue(user), user.toDto())
    }

    @Transactional(readOnly = true)
    fun me(principal: UserPrincipal): UserDto {
        val user = userRepository.findById(principal.id).orElseThrow {
            throw ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "账号不存在")
        }
        return user.toDto()
    }

    @Transactional
    fun changePassword(principal: UserPrincipal, request: ChangePasswordRequest): UserDto {
        val user = userRepository.findById(principal.id).orElseThrow {
            throw ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "账号不存在")
        }
        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "当前密码不正确")
        }
        if (passwordEncoder.matches(request.newPassword, user.passwordHash)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "新密码不能与当前密码相同")
        }
        user.passwordHash = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)
        return user.toDto()
    }

    private fun User.toDto(): UserDto = UserDto(
        id = requireNotNull(id),
        username = username,
        name = name,
        role = role,
    )
}
