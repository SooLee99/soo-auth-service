package io.soo.springboot.storage.db.core

import java.time.LocalDateTime

data class LocalCredential(
    val id: Long = 0L,
    val userId: Long,
    val userEmail: String,
    val passwordHash: String,

    var failedLoginCount: Int = 0,
    var lastFailedAt: LocalDateTime? = null,
    var lockUntil: LocalDateTime? = null,
) {
    fun isLocked(now: LocalDateTime = LocalDateTime.now()): Boolean {
        val until = lockUntil ?: return false
        return now.isBefore(until)
    }

    fun recordLoginSuccess() {
        failedLoginCount = 0
        lastFailedAt = null
        lockUntil = null
    }

    fun recordLoginFailure(
        now: LocalDateTime = LocalDateTime.now(),
        maxAttempts: Int = 5,
        lockMinutes: Long = 1,
    ) {
        if (isLocked(now)) {
            lastFailedAt = now
            return
        }
        failedLoginCount += 1
        lastFailedAt = now

        // ✅ 5회 도달 시 1분 잠금
        if (failedLoginCount >= maxAttempts) {
            lockUntil = now.plusMinutes(lockMinutes)
            failedLoginCount = 0
        }
    }
}