package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.User
import java.time.Instant

data class AdminUserBlockResponse(
    val userId: Long,
    val userStatus: UserStatus,
    val blocked: Boolean,
    val blockedReason: String?,
    val blockedAt: Instant?,
    val blockedByAdminId: Long?,
    val unblockedAt: Instant?,
    val unblockedByAdminId: Long?,
    val deletedAt: Instant?,
    val deletionReason: String?,
    val retentionUntil: Instant?,
) {
    companion object {
        fun from(user: User): AdminUserBlockResponse {
            return AdminUserBlockResponse(
                userId = user.id,
                userStatus = user.userStatus,
                blocked = user.blocked,
                blockedReason = user.blockedReason,
                blockedAt = user.blockedAt,
                blockedByAdminId = user.blockedByAdminId,
                unblockedAt = user.unblockedAt,
                unblockedByAdminId = user.unblockedByAdminId,
                deletedAt = user.deletedAt,
                deletionReason = user.deletionReason,
                retentionUntil = user.retentionUntil,
            )
        }
    }
}
