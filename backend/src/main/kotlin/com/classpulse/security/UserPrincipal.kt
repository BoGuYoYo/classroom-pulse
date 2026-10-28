package com.classpulse.security

import com.classpulse.domain.Role
import com.classpulse.domain.User
import java.security.Principal

data class UserPrincipal(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: Role,
) : Principal {
    override fun getName(): String = username

    companion object {
        fun from(user: User): UserPrincipal = UserPrincipal(
            id = requireNotNull(user.id),
            username = user.username,
            displayName = user.name,
            role = user.role,
        )
    }
}
