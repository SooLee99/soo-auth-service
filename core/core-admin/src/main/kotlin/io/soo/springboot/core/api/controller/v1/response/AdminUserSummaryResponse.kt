package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.User
import java.time.Instant

data class AdminUserSummaryResponse(
    val userId: Long,
    val email: String,
    val name: String?,
    val nickname: String?,
    val role: Role,
    val authProvider: AuthProvider,
    val userStatus: UserStatus,
    val blocked: Boolean,
    val deletedAt: Instant?,
) {
    companion object {
        fun from(user: User): AdminUserSummaryResponse {
            return AdminUserSummaryResponse(
                userId = user.id,
                email = user.email,
                name = user.name,
                nickname = user.nickname,
                role = user.role,
                authProvider = user.authProvider,
                userStatus = user.userStatus,
                blocked = user.blocked,
                deletedAt = user.deletedAt,
            )
        }
    }
}
