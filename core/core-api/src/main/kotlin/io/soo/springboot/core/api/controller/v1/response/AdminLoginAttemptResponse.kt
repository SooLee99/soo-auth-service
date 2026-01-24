package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.enums.AuthProvider
import java.time.LocalDateTime

data class AdminLoginAttemptDto(
    val id: Long,
    val success: Boolean,
    val provider: AuthProvider,
    val userId: Long?,
    val providerUserId: String?,
    val deviceId: String?,
    val ip: String?,
    val userAgent: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: LocalDateTime,
)

data class AdminLoginAttemptStatsDto(
    val from: LocalDateTime,
    val to: LocalDateTime,
    val total: Long,
    val success: Long,
    val failure: Long,

    // provider별 시도 횟수 (LOCAL/GOOGLE/NAVER...)
    val byProvider: List<KeyCountDto>,

    // 실패 건에 대한 errorCode 분포 (BAD_CREDENTIALS, DEVICE_BLOCKED...)
    val failureByErrorCode: List<KeyCountDto>,

    // 실패 건 기준 Top N
    val topFailedIps: List<KeyCountDto>,
    val topFailedDeviceIds: List<KeyCountDto>,
)

data class KeyCountDto(
    val key: String,
    val count: Long,
)
