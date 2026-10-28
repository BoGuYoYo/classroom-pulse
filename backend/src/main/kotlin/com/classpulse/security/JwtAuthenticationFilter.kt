package com.classpulse.security

import com.classpulse.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authorization = request.getHeader("Authorization")
        if (authorization != null && authorization.startsWith("Bearer ", ignoreCase = true) &&
            SecurityContextHolder.getContext().authentication == null
        ) {
            val token = authorization.substringAfter(' ').trim()
            runCatching {
                val claims = jwtService.parse(token)
                val userId = claims.subject.toLong()
                val user = userRepository.findById(userId).orElse(null)
                if (user != null) {
                    val principal = UserPrincipal.from(user)
                    val authentication = UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        listOf(SimpleGrantedAuthority("ROLE_${user.role.name}")),
                    )
                    SecurityContextHolder.getContext().authentication = authentication
                }
            }.onFailure {
                request.setAttribute("jwt.error", "INVALID_TOKEN")
            }
        }
        filterChain.doFilter(request, response)
    }
}
