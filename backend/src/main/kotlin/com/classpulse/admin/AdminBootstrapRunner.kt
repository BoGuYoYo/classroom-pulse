package com.classpulse.admin

import com.classpulse.config.AppProperties
import com.classpulse.domain.AdminUser
import com.classpulse.repository.AdminUserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@Order(30)
class AdminBootstrapRunner(
    private val properties: AppProperties,
    private val adminUserRepository: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(AdminBootstrapRunner::class.java)

    override fun run(args: ApplicationArguments) {
        val config = properties.admin
        if (config.initialUsername.isBlank() && config.initialPassword.isBlank()) return
        if (config.initialUsername.isBlank() || config.initialPassword.isBlank()) {
            logger.warn("管理员初始化跳过：BOOTSTRAP_ADMIN_USERNAME 与 BOOTSTRAP_ADMIN_PASSWORD 必须同时设置")
            return
        }
        if (config.initialPassword.length < 8) {
            logger.warn("管理员初始化跳过：BOOTSTRAP_ADMIN_PASSWORD 至少 8 位")
            return
        }
        if (adminUserRepository.existsByUsername(config.initialUsername)) return
        adminUserRepository.save(
            AdminUser(
                username = config.initialUsername,
                name = config.initialName,
                passwordHash = passwordEncoder.encode(config.initialPassword),
                createdAt = Instant.now(),
            ),
        )
        logger.info("已初始化管理员账号 {}", config.initialUsername)
    }
}
