package io.soo.springboot.storage.db.core

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    indexes = [
        Index(name = "ix_jwt_denylist_expires_at", columnList = "expires_at"),
        Index(name = "ux_jwt_denylist_jti", columnList = "jti", unique = true),
    ]
)
class JwtDenylistEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 128, updatable = false)
    var jti: String,

    @Column(nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)
