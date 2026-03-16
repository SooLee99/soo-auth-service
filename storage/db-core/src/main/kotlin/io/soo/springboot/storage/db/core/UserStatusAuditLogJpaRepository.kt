package io.soo.springboot.storage.db.core

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface UserStatusAuditLogJpaRepository : JpaRepository<UserStatusAuditLogEntity, Long> {
    fun findAllByTargetUserId(targetUserId: Long, pageable: Pageable): Page<UserStatusAuditLogEntity>
}
