package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import java.time.Instant

data class User(
    val id: Long = 0L,
    val email: String,
    val emailVerified: Boolean = false,
    val phoneNumber: String?,
    val phoneNumberE164: String? = null,
    val phoneVerified: Boolean = false,
    val name: String?,
    val nickname: String?,
    val gender: Gender = Gender.UNKNOWN,
    val locale: String? = null,
    val birthyear: String? = null,
    val birthday: String? = null,
    val ageRange: String? = null,
    val profileImageUrl: String? = null,
    val thumbnailImageUrl: String? = null,
    val authProvider: AuthProvider,
    val oauthProviderUserId: String? = null,
    val role: Role = Role.USER,
    val userStatus: UserStatus = UserStatus.ACTIVE,
    val oauthConnectedAt: Instant? = null,
    val oauthExtraJson: String? = null,
    val oauthRawJson: String? = null,
    val blocked: Boolean = false,
    val blockedReason: String? = null,
    val blockedAt: Instant? = null,
    val blockedByAdminId: Long? = null,
    val unblockedAt: Instant? = null,
    val unblockedByAdminId: Long? = null,
    val deletedAt: Instant? = null,
    val deletionReason: String? = null,
    val retentionUntil: Instant? = null,
)
