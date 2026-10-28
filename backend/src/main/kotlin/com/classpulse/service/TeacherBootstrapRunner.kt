package com.classpulse.service

import com.classpulse.config.AppProperties
import com.classpulse.domain.Role
import com.classpulse.domain.User
import com.classpulse.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Optional production bootstrap: create one teacher account from environment variables.
 * It runs after any application data initializers and never overwrites an existing account.
 */
@Component
@Order(20)
class TeacherBootstrapRunner(
    private val properties: AppProperties,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(TeacherBootstrapRunner::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val config = properties.bootstrap
        if (config.teacherUsername.isBlank() && config.teacherPassword.isBlank()) return
        if (config.teacherUsername.isBlank() || config.teacherPassword.isBlank()) {
            logger.warn("教师初始化跳过：BOOTSTRAP_TEACHER_USERNAME 与 BOOTSTRAP_TEACHER_PASSWORD 必须同时设置")
            return
        }
        if (config.teacherPassword.length < 8) {
            logger.warn("教师初始化跳过：BOOTSTRAP_TEACHER_PASSWORD 至少需要 8 位")
            return
        }
        if (userRepository.existsByUsername(config.teacherUsername.trim())) {
            logger.info("教师初始化跳过：账号 {} 已存在", config.teacherUsername.trim())
            return
        }
        userRepository.save(
            User(
                username = config.teacherUsername.trim(),
                name = config.teacherName.trim().ifBlank { "教师" },
                role = Role.TEACHER,
                passwordHash = passwordEncoder.encode(config.teacherPassword),
                createdAt = Instant.now(),
            ),
        )
        logger.info("已根据环境变量创建教师账号 {}", config.teacherUsername.trim())
    }
}

