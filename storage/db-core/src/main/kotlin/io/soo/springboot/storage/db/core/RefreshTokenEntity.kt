package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "refresh_token_entity",
    indexes = [
        Index(name = "ix_refresh_token_user", columnList = "user_id"),
        Index(name = "ix_refresh_token_expires_at", columnList = "expires_at"),
        Index(name = "ix_refresh_token_user_device", columnList = "user_id, device_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "ux_refresh_token_hash", columnNames = ["token_hash"]),
    ],
)
class RefreshTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var expiresAt: Instant,

    @Column
    var revokedAt: Instant? = null,

    @Column
    var usedAt: Instant? = null,

    @Column(nullable = false)
    var userId: Long,

    // ✅ 클라이언트에서 들어온 refreshToken(원문)을 해시해서 저장
    @Column(nullable = false, length = 64)
    var tokenHash: String,

    // ✅ rotate 체인용 (새 토큰의 hash)
    @Column(length = 64)
    var replacedByHash: String? = null,

    // ✅ JWT 세션 대신 "디바이스 세션" 역할
    @Column(nullable = false, length = 255)
    var deviceId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var provider: AuthProvider,

    @Column(nullable = false)
    var lastAccessedAt: Instant = Instant.now(),
)
