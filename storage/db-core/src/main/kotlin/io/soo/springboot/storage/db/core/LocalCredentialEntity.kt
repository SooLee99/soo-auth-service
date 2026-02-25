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

}
