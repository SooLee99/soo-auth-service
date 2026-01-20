package io.soo.springboot.storage.db.core

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "local_credential",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_local_credential_user", columnNames = ["user_id"]),
    ],
    indexes = [
        Index(name = "idx_local_credential_user", columnList = "user_id"),
        Index(name = "idx_local_credential_lock_until", columnList = "lock_until"),
    ],
)
@AttributeOverride(
    name = "entityStatus",
    column = Column(columnDefinition = "VARCHAR", nullable = false),
)
class LocalCredentialEntity(

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "password_hash", nullable = false, length = 200)
    var passwordHash: String,

    @Column(name = "password_updated_at", nullable = false)
    var passwordUpdatedAt: LocalDateTime = LocalDateTime.now(),

    // ✅ 로그인 실패 누적 횟수
    @Column(name = "failed_login_count", nullable = false)
    var failedLoginCount: Int = 0,

    // ✅ 마지막 실패 시각
    @Column(name = "last_failed_at")
    var lastFailedAt: LocalDateTime? = null,

    // ✅ 잠금 해제 시각(이 시간 전에는 로그인 불가)
    @Column(name = "lock_until")
    var lockUntil: LocalDateTime? = null,

) : BaseEntity() {

    fun isLocked(now: LocalDateTime = LocalDateTime.now()): Boolean {
        val until = lockUntil ?: return false
        return now.isBefore(until)
    }

    fun recordLoginSuccess(now: LocalDateTime = LocalDateTime.now()) {
        // ✅ 로그인 성공 시 잠금/카운트 초기화
        failedLoginCount = 0
        lastFailedAt = null
        lockUntil = null
    }

    fun recordLoginFailure(
        now: LocalDateTime = LocalDateTime.now(),
        maxAttempts: Int = 5,
        lockMinutes: Long = 5,
        extendLockMinutesWhenLocked: Long = 1, // ✅ 잠금 중 재시도 시 now+1분 갱신
    ) {
        // ✅ 이미 잠금 상태에서 또 시도(=또 실패)하면 lockUntil을 now+1분으로 갱신
        if (isLocked(now)) {
            lastFailedAt = now
            lockUntil = now.plusMinutes(extendLockMinutesWhenLocked)
            return
        }

        failedLoginCount += 1
        lastFailedAt = now

        // ✅ 5회 도달 시 5분 잠금
        if (failedLoginCount >= maxAttempts) {
            lockUntil = now.plusMinutes(lockMinutes)
            // ✅ 잠금 걸리는 순간 카운트 리셋(정책: 리셋)
            failedLoginCount = 0
        }
    }
}
