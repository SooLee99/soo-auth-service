package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.LoginStatus
import io.soo.springboot.core.enums.LoginType
import java.time.LocalDateTime

data class LoginHistory(
    val id: Long,
    val userId: Long,
    val userEmail: String,
    val loginType: LoginType,
    val status: LoginStatus,
    val ipAddress: String?,
    val userAgent: String?,
    val deviceId: String?,
    val failureReason: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(entity: LoginHistoryEntity): LoginHistory {
            return LoginHistory(
                id = entity.id,
                userId = entity.userId,
                userEmail = entity.userEmail,
                loginType = entity.loginType,
                status = entity.loginStatus,
                ipAddress = entity.ipAddress,
                userAgent = entity.userAgent,
                deviceId = entity.deviceId,
                failureReason = entity.failureReason,
                createdAt = entity.createdAt,
            )
        }
    }
}
