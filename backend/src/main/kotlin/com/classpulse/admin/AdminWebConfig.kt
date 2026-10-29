package com.classpulse.admin

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class AdminWebConfig(private val adminSessionInterceptor: AdminSessionInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(adminSessionInterceptor)
            .addPathPatterns("/api/admin/**")
            .excludePathPatterns("/api/admin/auth/login")
    }
}
