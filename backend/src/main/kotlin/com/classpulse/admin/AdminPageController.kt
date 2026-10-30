package com.classpulse.admin

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class AdminPageController(private val authService: AdminAuthService) {
    @GetMapping("/admin", "/admin/")
    fun adminPage(request: HttpServletRequest): String =
        if (authService.findSession(sessionToken(request)) == null) {
            "redirect:/admin/login"
        } else {
            "forward:/admin/index.html"
        }

    @GetMapping("/admin/login", "/admin/login/")
    fun loginPage(request: HttpServletRequest): String =
        if (authService.findSession(sessionToken(request)) == null) {
            "forward:/admin/login.html"
        } else {
            "redirect:/admin/"
        }

    private fun sessionToken(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == AdminSessionInterceptor.ADMIN_COOKIE }?.value
}
