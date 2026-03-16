package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AdminUserActionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface UserStatusAuditLogRepository {
    fun save(targetUserId: Long, actorUserId: Long, actionType: AdminUserActionType, reason: String? = null): UserStatusAuditLog
    fun findByTargetUserId(targetUserId: Long, pageable: Pageable): Page<UserStatusAuditLog>
}
