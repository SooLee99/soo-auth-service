package io.soo.springboot.core.domain.token

import io.soo.springboot.core.enums.AuthProvider
import java.time.Instant

data class RefreshTokenRecord(
    val token: String,
    val userId: Long,
    val expiresAt: Instant,
    val deviceId: String,
    val provider: AuthProvider
)