package io.soo.springboot.core.api.controller.v1.response

import java.time.LocalDateTime

data class AdminUserDetailResponse(
    val userId: Long?,
    val email: String?,
    val nickname: String?,
    val profileImageUrl: String?,
    val thumbnailImageUrl: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val identities: List<AdminOAuthIdentityResponse>,
    val devices: List<AdminUserDevicResponse>,
    val sessions: List<AdminSessionResponse>,
    val lastLoginAttempts: List<AdminLoginAttemptDto>,
)
