package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AdminUserActionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class UserStatusAuditLogRepositoryImpl(
    private val jpaRepository: UserStatusAuditLogJpaRepository,
) : UserStatusAuditLogRepository {
    override fun save(
        targetUserId: Long,
        actorUserId: Long,
        actionType: AdminUserActionType,
        reason: String?,
    ): UserStatusAuditLog {
        val entity = UserStatusAuditLogEntity(
            targetUserId = targetUserId,
            actorUserId = actorUserId,
            actionType = actionType,
            reason = reason,
            actionAt = Instant.now(),
        )
        return UserStatusAuditLog.from(jpaRepository.save(entity))
    }

    override fun findByTargetUserId(targetUserId: Long, pageable: Pageable): Page<UserStatusAuditLog> {
        return jpaRepository.findAllByTargetUserId(targetUserId, pageable).map { UserStatusAuditLog.from(it) }
    }
}
