package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_session_map",
    uniqueConstraints = [UniqueConstraint(name = "uq_user_session_map_session_id", columnNames = ["session_id"])],
    indexes = [
        Index(name = "idx_user_session_map_user", columnList = "user_id"),
        Index(name = "idx_user_session_map_user_device", columnList = "user_id, device_id"),
        Index(name = "idx_user_session_map_revoked", columnList = "revoked_at"),
    ],
)
@AttributeOverride(
    name = "entityStatus",
    column = Column(columnDefinition = "VARCHAR", nullable = false),
)
class UserSessionMapEntity(

    @Column(name = "session_id", nullable = false, length = 100)
    var sessionId: String,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "device_id", nullable = false, length = 255)
    var deviceId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    var provider: AuthProvider,

    @Column(name = "last_accessed_at")
    var lastAccessedAt: LocalDateTime? = null,

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,

    @Column(name = "revoked_reason", length = 500)
    var revokedReason: String? = null,

) : BaseEntity()
