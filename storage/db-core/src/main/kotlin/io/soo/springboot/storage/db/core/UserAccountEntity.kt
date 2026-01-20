package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDateTime

@Entity
@Table(name = "user_account")
@AttributeOverride(
    name = "entityStatus",
    column = Column(columnDefinition = "VARCHAR", nullable = false),
)
class UserAccountEntity(

    @Column
    var email: String? = null,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    @Column
    var nickname: String? = null,

    @Column
    var name: String? = null,

    @Column(name = "given_name")
    var givenName: String? = null,

    @Column(name = "family_name")
    var familyName: String? = null,

    @Column
    var locale: String? = null,

    // 동의 기반/선택 정보
    @Column
    var gender: String? = null,

    @Column(name = "age_range")
    var ageRange: String? = null,

    @Column
    var birthday: String? = null,   // "MM-DD"

    @Column
    var birthyear: String? = null,  // "YYYY"

    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    @Column(name = "profile_image_url")
    var profileImageUrl: String? = null,

    @Column(name = "thumbnail_image_url")
    var thumbnailImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "last_login_provider", length = 30)
    var lastLoginProvider: AuthProvider? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    @Column(name = "suspended_at")
    var suspendedAt: LocalDateTime? = null,

    @Column(name = "suspended_until")
    var suspendedUntil: LocalDateTime? = null,

    @Column(name = "suspended_reason", length = 500)
    var suspendedReason: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: Role = Role.USER

    ) : BaseEntity()
