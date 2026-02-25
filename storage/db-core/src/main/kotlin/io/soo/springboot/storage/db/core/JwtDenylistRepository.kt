package io.soo.springboot.storage.db.core

import java.time.Instant

interface JwtDenylistRepository {
    fun save(jti: String, expiresAt: Instant)
    fun existsByJtiAndExpiresAtAfter(jti: String, now: Instant): Boolean
    fun findByJti(jti: String): JwtDenylistEntry?
    fun deleteExpired(before: Instant): Int
}

data class JwtDenylistEntry(
    val jti: String,
    val expiresAt: Instant,
)