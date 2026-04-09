package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "user_entity",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_oauth_provider",
            columnNames = ["authProvider", "oauthProviderUserId"],
        ),
    ],
    indexes = [
        Index(name = "ix_user_email", columnList = "email"),
        Index(name = "ix_user_login_id", columnList = "loginId"),
        Index(name = "ix_user_oauth", columnList = "authProvider, oauthProviderUserId"),
    ],
)
class UserEntity(

    @Column(nullable = false)
    var email: String? = null,

    @Column(length = 50)
    var loginId: String? = null,

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

    // "MM-DD"
    @Column
    var birthday: String? = null,

    // "YYYY"
    @Column
    var birthyear: String? = null,

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
    var role: Role = Role.USER,

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    var userStatus: UserStatus = UserStatus.ACTIVE,

    @Column(nullable = false)
    var blocked: Boolean = false,

    @Column(length = 500)
    var blockedReason: String? = null,

    @Column
    var blockedAt: Instant? = null,

    @Column
    var blockedByAdminId: Long? = null,

    @Column
    var unblockedAt: Instant? = null,

    @Column
    var unblockedByAdminId: Long? = null,

    @Column
    var deletedAt: Instant? = null,

    @Column(length = 500)
    var deletionReason: String? = null,

    @Column
    var retentionUntil: Instant? = null,
) : BaseEntity()
