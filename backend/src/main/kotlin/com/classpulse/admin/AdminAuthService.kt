package com.classpulse.admin

import com.classpulse.config.AppProperties
import com.classpulse.domain.AdminUser
import com.classpulse.repository.AdminSessionRepository
import com.classpulse.repository.AdminUserRepository
import com.classpulse.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class AdminLoginResult(
    val admin: AdminUser,
    val token: String,
    val csrfToken: String,
    val expiresAt: Instant,
)

@Service
class AdminAuthService(
    private val adminUserRepository: AdminUserRepository,
    private val adminSessionRepository: AdminSessionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val properties: AppProperties,
) {
    private val random = SecureRandom()
    private val attempts = ConcurrentHashMap<String, MutableList<Long>>()
    private val windowMillis = 10 * 60 * 1000L
    private val maxFailures = 5

    @Transactional
    fun login(username: String, password: String, clientKey: String): AdminLoginResult {
        val key = "$clientKey|${username.lowercase()}"
        assertNotLocked(key)
        val admin = adminUserRepository.findByUsername(username.trim())
        if (admin == null || !passwordEncoder.matches(password, admin.passwordHash)) {
            recordFailure(key)
            throw ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_INVALID_CREDENTIALS", "管理员账号或密码错误")
        }
        attempts.remove(key)
        adminSessionRepository.deleteExpired(Instant.now())
        val token = randomToken(32)
        val csrf = randomToken(24)
        val now = Instant.now()
        val expiresAt = now.plus(properties.admin.sessionHours, ChronoUnit.HOURS)
        adminSessionRepository.save(
            com.classpulse.domain.AdminSession(
                tokenHash = sha256(token),
                csrfToken = csrf,
                admin = admin,
                createdAt = now,
                expiresAt = expiresAt,
                lastSeenAt = now,
            ),
        )
        return AdminLoginResult(admin, token, csrf, expiresAt)
    }

    @Transactional(readOnly = true)
    fun findSession(token: String?): com.classpulse.domain.AdminSession? {
        if (token.isNullOrBlank()) return null
        val session = adminSessionRepository.findByTokenHash(sha256(token)) ?: return null
        if (!session.expiresAt.isAfter(Instant.now())) return null
        return session
    }

    @Transactional
    fun logout(token: String?) {
        if (token.isNullOrBlank()) return
        adminSessionRepository.findByTokenHash(sha256(token))?.let { adminSessionRepository.delete(it) }
    }

    fun requireCsrf(session: com.classpulse.domain.AdminSession, header: String?): Boolean =
        header != null && MessageDigest.isEqual(
            session.csrfToken.toByteArray(Charsets.UTF_8),
            header.toByteArray(Charsets.UTF_8),
        )

    private fun assertNotLocked(key: String) {
        val now = System.currentTimeMillis()
        val recent = attempts.computeIfAbsent(key) { mutableListOf() }
        synchronized(recent) {
            recent.removeIf { it < now - windowMillis }
            if (recent.size >= maxFailures) {
                throw ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "ADMIN_LOGIN_LOCKED",
                    "登录尝试过多，请 10 分钟后再试",
                )
            }
        }
    }

    private fun recordFailure(key: String) {
        val recent = attempts.computeIfAbsent(key) { mutableListOf() }
        synchronized(recent) {
            recent.add(System.currentTimeMillis())
        }
    }

    private fun randomToken(bytes: Int): String {
        val data = ByteArray(bytes)
        random.nextBytes(data)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
