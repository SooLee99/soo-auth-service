package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import jakarta.persistence.*

@Entity
@Table(
    name = "login_attempt",
    indexes = [
        Index(name = "idx_login_attempt_user_created", columnList = "user_id, created_at"),
    ],
)
@AttributeOverride(
    name = "entityStatus",
    column = Column(columnDefinition = "VARCHAR", nullable = false),
)
class LoginAttemptEntity(

    @Column(name = "success", nullable = false)
    var success: Boolean,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    var provider: AuthProvider,

    @Column(name = "user_id")
    var userId: Long? = null,

    @Column(name = "provider_user_id", length = 255)
    var providerUserId: String? = null,

    @Column(name = "device_id", length = 255)
    var deviceId: String? = null,

    @Column(name = "ip", length = 100)
    var ip: String? = null,

    @Column(name = "user_agent", length = 1000)
    var userAgent: String? = null,

    @Column(name = "error_code", length = 100)
    var errorCode: String? = null,

    @Column(name = "error_message", length = 2000)
    var errorMessage: String? = null,

) : BaseEntity()
