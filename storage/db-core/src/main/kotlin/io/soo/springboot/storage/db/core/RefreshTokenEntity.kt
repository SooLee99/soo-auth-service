package io.soo.springboot.storage.db.core

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    indexes = [
        Index(name = "ux_refresh_token_hash", columnList = "token_hash", unique = true),
        Index(name = "ix_refresh_token_user", columnList = "user_id"),
        Index(name = "ix_refresh_token_expires_at", columnList = "expires_at"),
    ]
)
class RefreshTokenEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 64, updatable = false)
    var tokenHash: String,

    @Column(nullable = false, updatable = false)
    var userId: Long,

    @Column(nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    /** rotate로 사용(소모)된 시각 */
    @Column
    var usedAt: Instant? = null,

    /** 명시적 revoke(로그아웃 등) 시각 */
    @Column
    var revokedAt: Instant? = null,

    /** 어떤 토큰으로 교체되었는지(감사용) */
    @Column(length = 64)
    var replacedByHash: String? = null,
)
