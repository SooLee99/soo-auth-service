package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.Role
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Entity
class UserEntity(

    @Column(nullable = false)
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
    var locale: String? = null,

    // 동의 기반/선택 정보
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var gender: Gender = Gender.UNKNOWN,

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
    var authProvider: AuthProvider,

    @Column(length = 128)
    var oauthProviderUserId: String? = null,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var role: Role = Role.USER
) : BaseEntity()
