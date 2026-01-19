package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_account")
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

    ) : BaseEntity()
