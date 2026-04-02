package io.soo.springboot.core.domain.admin

import io.soo.springboot.storage.db.core.User

interface AdminUserCommandUseCase {
    fun update(userId: Long, command: UserUpdateCommand, adminUserId: Long): User
    fun softDelete(userId: Long, reason: String?, adminUserId: Long): User
    fun updatePassword(userId: Long, newPassword: String): User
    fun revokeTokens(userId: Long): User
}
