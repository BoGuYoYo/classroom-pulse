package com.classpulse.config

import com.classpulse.security.JwtAuthenticationFilter
import com.classpulse.web.ApiResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper,
    private val appProperties: AppProperties,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/admin/**",
                    "/admin",
                    "/admin/**",
                    "/actuator/health",
                    "/actuator/health/**",
                ).permitAll()
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                auth.anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { request, response, _ ->
                    val code = if (request.getAttribute("jwt.error") != null) "TOKEN_INVALID" else "UNAUTHORIZED"
                    val message = if (code == "TOKEN_INVALID") "登录已失效，请重新登录" else "请先登录"
                    writeError(response, HttpStatus.UNAUTHORIZED, code, message)
                }
                exceptions.accessDeniedHandler { _, response, _ ->
                    writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问该资源")
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val origins = appProperties.cors.allowedOrigins
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val configuration = CorsConfiguration()
        if (origins.isNotEmpty()) {
            configuration.allowedOrigins = origins
            configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            configuration.allowedHeaders = listOf("Authorization", "Content-Type")
            configuration.exposedHeaders = listOf("Content-Disposition")
            configuration.allowCredentials = true
            configuration.maxAge = 3600
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    private fun writeError(response: HttpServletResponse, status: HttpStatus, code: String, message: String) {
        response.status = status.value()
        response.contentType = "application/json;charset=UTF-8"
        objectMapper.writeValue(response.writer, ApiResponse.error(code, message))
    }
}
