package io.soo.springboot.storage.db.core

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table
class VerificationGuardEntity(
    @Id
    @Column(name = "verification_key")
    var key: String,

    @Column
    var lastSentAt: Instant? = null,

    @Column
    var failedAttempts: Int = 0,

    @Column
    var lockedUntil: Instant? = null,

    // 시간창 기준 전송 횟수 제한
    @Column
    var windowStart: Instant? = null,

    @Column
    var windowCount: Int = 0,

    @Version
    var version: Long? = null,
)
