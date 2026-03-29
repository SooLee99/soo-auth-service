package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import java.time.Instant

data class RefreshToken(
    val id: Long = 0L,
    val userId: Long,
    val tokenHash: String,
    val deviceId: String,
    val provider: AuthProvider,
    val expiresAt: Instant,
    val lastAccessedAt: Instant,
    val createdAt: Instant = Instant.now(),
    val usedAt: Instant? = null,
    val revokedAt: Instant? = null,
    val replacedByHash: String? = null,
)
