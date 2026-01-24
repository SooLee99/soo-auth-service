package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.enums.AuthProvider
import java.time.LocalDateTime

data class AdminSessionResponse(
    val id: Long?,
    val sessionId: String,
    val userId: Long,
    val deviceId: String,
    val provider: AuthProvider,
    val createdAt: LocalDateTime,
    val lastAccessedAt: LocalDateTime?,
    val revokedAt: LocalDateTime?,
    val revokedReason: String?,
)
