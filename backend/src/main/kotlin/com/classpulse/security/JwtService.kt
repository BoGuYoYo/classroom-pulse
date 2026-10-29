package com.classpulse.security

import com.classpulse.config.AppProperties
import com.classpulse.domain.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(private val properties: AppProperties) {
    private val logger = LoggerFactory.getLogger(JwtService::class.java)
    private val key: SecretKey

    init {
        val configured = properties.jwt.secret.trim()
        val secretBytes = if (configured.isBlank()) {
            check(!properties.jwt.requireConfigured) {
                "生产环境必须配置 JWT_SECRET（至少 32 字节随机值）"
            }
            val generated = ByteArray(48)
            SecureRandom().nextBytes(generated)
            logger.warn("JWT_SECRET 未配置，已生成临时开发密钥；服务重启后既有令牌会失效。生产环境必须设置 JWT_SECRET。")
            generated
        } else {
            decodeSecret(configured)
        }
        require(secretBytes.size >= 32) { "JWT_SECRET 至少需要 32 字节" }
        key = Keys.hmacShaKeyFor(secretBytes)
    }

    private fun decodeSecret(value: String): ByteArray {
        return runCatching {
            val decoded = Base64.getDecoder().decode(value)
            if (decoded.size >= 32) decoded else value.toByteArray(Charsets.UTF_8)
        }.getOrElse { value.toByteArray(Charsets.UTF_8) }
    }

    fun issue(user: User): String {
        val now = Instant.now()
        val expires = now.plus(properties.jwt.expirationHours, ChronoUnit.HOURS)
        return Jwts.builder()
            .subject(requireNotNull(user.id).toString())
            .claim("username", user.username)
            .claim("name", user.name)
            .claim("role", user.role.name)
            .claim("ver", user.authVersion)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expires))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
    }

    fun parse(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
