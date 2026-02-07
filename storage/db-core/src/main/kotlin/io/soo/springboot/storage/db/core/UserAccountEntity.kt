package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant
import java.time.LocalDateTime

@Entity
class UserAccountEntity(

    @Column
    var email: String? = null,

    @Column(nullable = false)
    var emailVerified: Boolean = false,

    @Column
    var phoneNumber: String? = null,

    @Column
    var phoneVerified: Boolean = false,

    @Column
    var nickname: String? = null,

    @Column
    var name: String? = null,

    @Column
    var givenName: String? = null,

    @Column
    var familyName: String? = null,

    @Column
    var locale: String? = null,

    // 동의 기반/선택 정보
    @Column
    var gender: String? = null,

    @Column
    var ageRange: String? = null,

    @Column
    var birthday: String? = null,   // "MM-DD"

    @Column
    var birthyear: String? = null,  // "YYYY"

    @Column
    var profileImageUrl: String? = null,

    @Column
    var thumbnailImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var lastLoginProvider: AuthProvider? = null,

    @Column
    var lastLoginAt: Instant? = null,

    @Column
    var suspendedAt: LocalDateTime? = null,

    @Column
    var suspendedUntil: LocalDateTime? = null,

    @Column(length = 500)
    var suspendedReason: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: Role = Role.USER
) : BaseEntity()
