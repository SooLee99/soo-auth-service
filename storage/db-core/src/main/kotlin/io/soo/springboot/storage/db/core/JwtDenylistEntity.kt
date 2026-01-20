package io.soo.springboot.storage.db.core

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "jwt_denylist",
    indexes = [
        Index(name = "idx_jwt_denylist_jti", columnList = "jti"),
        Index(name = "idx_jwt_denylist_expires_at", columnList = "expires_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_jwt_denylist_jti", columnNames = ["jti"]),
    ],
)
@AttributeOverride(
    name = "entityStatus",
    column = Column(name = "status", columnDefinition = "VARCHAR", nullable = false),
)
class JwtDenylistEntity(

    /**
     * ✅ JWT ID (jti)
     * - 토큰 식별자(블랙리스트의 key)
     */
    @Column(name = "jti", nullable = false, length = 120)
    var jti: String,

    /**
     * ✅ 언제 revoke 되었는지
     */
    @Column(name = "revoked_at", nullable = false)
    var revokedAt: Instant = Instant.now(),

    /**
     * ✅ 토큰 만료 시각 (cleanup 기준)
     */
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    /**
     * ✅ revoke 사유(선택)
     */
    @Column(name = "reason", nullable = true, length = 500)
    var reason: String? = null,
) : BaseEntity()
