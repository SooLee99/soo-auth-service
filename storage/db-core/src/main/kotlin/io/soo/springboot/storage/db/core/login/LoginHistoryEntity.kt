package io.soo.springboot.storage.db.core.login

import io.soo.springboot.core.enums.LoginStatus
import io.soo.springboot.core.enums.LoginType
import io.soo.springboot.storage.db.core.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "login_history",
    indexes = [
        Index(name = "idx_login_history_user_id", columnList = "userId"),
        Index(name = "idx_login_history_created_at", columnList = "createdAt"),
    ],
)
class LoginHistoryEntity(
    @Column(nullable = false)
    var userId: Long,

    @Column(nullable = false, length = 100)
    var userEmail: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var loginType: LoginType,

    @Enumerated(EnumType.STRING)
    @Column(name = "login_status", nullable = false, length = 20)
    var loginStatus: LoginStatus,

    @Column(length = 50)
    var ipAddress: String? = null,

    @Column(length = 500)
    var userAgent: String? = null,

    @Column(length = 100)
    var deviceId: String? = null,

    @Column(length = 200)
    var failureReason: String? = null,

) : BaseEntity()
