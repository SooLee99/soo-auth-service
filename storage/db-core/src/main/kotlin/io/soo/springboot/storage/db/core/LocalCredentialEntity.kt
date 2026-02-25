package io.soo.springboot.storage.db.core

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
class LocalCredentialEntity(
    @Column(nullable = false)
    var userId: Long,

    @Column(nullable = false)
    var userEmail: String,

    @Column(nullable = false, length = 200)
    var passwordHash: String,

    @Column( nullable = false)
    var passwordUpdatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var failedLoginCount: Int = 0,

    @Column
    var lastFailedAt: LocalDateTime? = null,

    @Column
    var lockUntil: LocalDateTime? = null,

) : BaseEntity() {

    fun isLocked(now: LocalDateTime = LocalDateTime.now()): Boolean {
        val until = lockUntil ?: return false
        return now.isBefore(until)
    }

    fun recordLoginSuccess(now: LocalDateTime = LocalDateTime.now()) {
        failedLoginCount = 0
        lastFailedAt = null
        lockUntil = null
    }

    fun recordLoginFailure(
        now: LocalDateTime = LocalDateTime.now(),
        maxAttempts: Int = 5,
        lockMinutes: Long = 1,
        //extendLockMinutesWhenLocked: Long = 1,
    ) {
        // ✅ 이미 잠금 상태에서 또 시도(=또 실패)하면 lockUntil을 now+1분으로 갱신
        if (isLocked(now)) {
            lastFailedAt = now
            //lockUntil = now.plusMinutes(extendLockMinutesWhenLocked)
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
