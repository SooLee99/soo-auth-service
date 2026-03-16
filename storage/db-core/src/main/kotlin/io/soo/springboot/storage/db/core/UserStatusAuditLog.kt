package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AdminUserActionType
import java.time.Instant

data class UserStatusAuditLog(
    val id: Long,
    val targetUserId: Long,
    val actorUserId: Long,
    val actionType: AdminUserActionType,
    val reason: String?,
    val actionAt: Instant,
) {
    companion object {
        fun from(entity: UserStatusAuditLogEntity): UserStatusAuditLog {
            return UserStatusAuditLog(
                id = entity.id,
                targetUserId = entity.targetUserId,
                actorUserId = entity.actorUserId,
                actionType = entity.actionType,
                reason = entity.reason,
                actionAt = entity.actionAt,
            )
        }
    }
}
