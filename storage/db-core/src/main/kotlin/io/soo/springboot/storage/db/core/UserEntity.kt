package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.Role
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "user_entity",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_oauth_provider",
            columnNames = ["authProvider", "oauthProviderUserId"]
        )
    ],
    indexes = [
        Index(name = "ix_user_email", columnList = "email"),
        Index(name = "ix_user_oauth", columnList = "authProvider, oauthProviderUserId"),
    ]
)
class UserEntity(

    @Column(nullable = false)
    var email: String? = null,

    @Column(nullable = false)
    var emailVerified: Boolean = false,

    @Column
    var phoneNumber: String? = null,

    @Column
    var phoneNumberE164: String? = null,

    @Column
    var phoneVerified: Boolean = false,

    @Column
    var nickname: String? = null,

    @Column
    var name: String? = null,

    @Column
    var locale: String? = null,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var gender: Gender = Gender.UNKNOWN,

    @Column
    var birthday: String? = null,   // "MM-DD"

    @Column
    var birthyear: String? = null,  // "YYYY"

    @Column
    var ageRange: String? = null,

    @Column
    var profileImageUrl: String? = null,

    @Column
    var thumbnailImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var authProvider: AuthProvider,

    @Column(length = 128)
    var oauthProviderUserId: String? = null,

    @Column
    var oauthConnectedAt: Instant? = null,

    @Lob
    @Column
    var oauthExtraJson: String? = null,

    @Lob
    @Column
    var oauthRawJson: String? = null,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var role: Role = Role.USER
) : BaseEntity()