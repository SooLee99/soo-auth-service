package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.enums.DeviceStatus
import java.time.LocalDateTime

data class AdminUserDevicResponse(
    val id: Long,
    val userId: Long,
    val deviceId: String,
    val status: DeviceStatus,
    val blockedReason: String?,
    val blockedAt: LocalDateTime?,
    val lastLoginAt: LocalDateTime?,
    val lastIp: String?,
    val lastUserAgent: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
