package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.DeviceStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_device",
    uniqueConstraints = [UniqueConstraint(name = "uq_user_device", columnNames = ["user_id", "device_id"])],
)
@AttributeOverride(
    name = "entityStatus",
    column = Column(columnDefinition = "VARCHAR", nullable = false),
)
class UserDeviceEntity(

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "device_id", nullable = false, length = 255)
    var deviceId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "device_status", nullable = false, length = 20)
    var deviceStatus: DeviceStatus = DeviceStatus.ACTIVE,

    @Column(name = "blocked_reason", length = 500)
    var blockedReason: String? = null,

    @Column(name = "blocked_at")
    var blockedAt: LocalDateTime? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null,

    @Column(name = "last_ip", length = 100)
    var lastIp: String? = null,

    @Column(name = "last_user_agent", length = 1000)
    var lastUserAgent: String? = null,

) : BaseEntity()
