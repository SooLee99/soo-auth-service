package io.soo.springboot.storage.db.core

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "verification_code")
class VerificationCodeEntity(
    @Id
    @Column(name = "code_key", length = 220)
    var key: String,

    @Column(nullable = false, length = 200)
    var codeHash: String,

    @Column(nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    var lastIssuedAt: Instant,

    @Version
    var version: Long? = null,
)
