package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.enums.AdminUserActionType
import io.soo.springboot.storage.db.core.UserStatusAuditLog
import java.time.Instant

data class UserStatusAuditLogResponse(
    val id: Long,
    val targetUserId: Long,
    val actorUserId: Long,
    val actionType: AdminUserActionType,
    val reason: String?,
    val actionAt: Instant,
) {
    companion object {
        fun from(log: UserStatusAuditLog): UserStatusAuditLogResponse {
            return UserStatusAuditLogResponse(
                id = log.id,
                targetUserId = log.targetUserId,
                actorUserId = log.actorUserId,
                actionType = log.actionType,
                reason = log.reason,
                actionAt = log.actionAt,
            )
        }
    }
}
