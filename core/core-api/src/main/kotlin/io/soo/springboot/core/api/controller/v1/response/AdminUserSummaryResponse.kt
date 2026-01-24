package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.enums.AuthProvider
import java.time.LocalDateTime

data class AdminUserSummaryResponse(
    val userId: Long,
    val email: String?,
    val nickname: String?,
    val profileImageUrl: String?,
    val thumbnailImageUrl: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val providers: List<AuthProvider>,
    val deviceCount: Int,
    val activeSessionCount: Int,
)
