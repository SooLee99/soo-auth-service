package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AdminUserActionType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "user_status_audit_log",
    indexes = [
        Index(name = "ix_user_status_audit_log_user_id", columnList = "targetUserId"),
        Index(name = "ix_user_status_audit_log_created_at", columnList = "actionAt"),
    ],
)
class UserStatusAuditLogEntity(
    @Column(nullable = false)
    var targetUserId: Long,

    @Column(nullable = false)
    var actorUserId: Long,

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    var actionType: AdminUserActionType,

    @Column(length = 500)
    var reason: String? = null,

    @Column(nullable = false)
    var actionAt: Instant = Instant.now(),
) : BaseEntity()
