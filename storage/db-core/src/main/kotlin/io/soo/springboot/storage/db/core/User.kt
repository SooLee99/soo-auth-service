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
) {
    companion object {
        fun createLocal(
            email: String,
            phoneNumber: String?,
            emailVerified: Boolean = false,
            phoneVerified: Boolean = false,
            name: String? = null,
            nickname: String? = null,
            gender: Gender = Gender.UNKNOWN,
            locale: String? = null,
            birthyear: String? = null,
            birthday: String? = null,
            profileImageUrl: String? = null,
            thumbnailImageUrl: String? = null,
            role: Role = Role.USER,
        ): User {
            return User(
                email = email,
                emailVerified = emailVerified,
                phoneNumber = phoneNumber,
                phoneVerified = phoneVerified,
                name = name,
                nickname = nickname,
                gender = gender,
                locale = locale,
                birthyear = birthyear,
                birthday = birthday,
                profileImageUrl = profileImageUrl,
                thumbnailImageUrl = thumbnailImageUrl,
                authProvider = AuthProvider.LOCAL,
                role = role,
            )
        }

        fun createOAuth2(
            provider: AuthProvider,
            providerUserId: String,
            email: String,
            emailVerified: Boolean,
            phoneNumber: String?,
            phoneNumberE164: String?,
            nickname: String?,
            name: String?,
            locale: String?,
            gender: Gender,
            birthday: String?,
            birthyear: String?,
            ageRange: String?,
            profileImageUrl: String?,
            thumbnailImageUrl: String?,
            connectedAt: Instant?,
            extraJson: String?,
            rawJson: String?,
        ): User {
            return User(
                email = email,
                emailVerified = emailVerified,
                phoneNumber = phoneNumber,
                phoneNumberE164 = phoneNumberE164,
                phoneVerified = false,
                nickname = nickname,
                name = name,
                locale = locale,
                gender = gender,
                birthday = birthday,
                birthyear = birthyear,
                ageRange = ageRange,
                profileImageUrl = profileImageUrl,
                thumbnailImageUrl = thumbnailImageUrl,
                authProvider = provider,
                oauthProviderUserId = providerUserId,
                oauthConnectedAt = connectedAt,
                oauthExtraJson = extraJson,
                oauthRawJson = rawJson,
            )
        }
    }

    fun block(byAdminId: Long, at: Instant, reason: String?): User {
        return copy(
            userStatus = UserStatus.BLOCKED,
            blocked = true,
            blockedReason = reason?.trim()?.takeIf { it.isNotBlank() },
            blockedAt = at,
            blockedByAdminId = byAdminId,
            unblockedAt = null,
            unblockedByAdminId = null,
        )
    }

    fun unblock(byAdminId: Long, at: Instant): User {
        return copy(
            userStatus = UserStatus.ACTIVE,
            blocked = false,
            unblockedAt = at,
            unblockedByAdminId = byAdminId,
        )
    }

    fun promoteToAdmin(): User {
        if (role == Role.ADMIN) return this
        return copy(role = Role.ADMIN)
    }

    fun softDelete(
        at: Instant,
        retentionUntil: Instant,
        reason: String?,
        anonymizedEmail: String,
        anonymizedPhone: String,
    ): User {
        return copy(
            email = anonymizedEmail,
            emailVerified = false,
            phoneNumber = anonymizedPhone,
            phoneNumberE164 = null,
            phoneVerified = false,
            name = null,
            nickname = null,
            gender = Gender.UNKNOWN,
            locale = null,
            birthyear = null,
            birthday = null,
            ageRange = null,
            profileImageUrl = null,
            thumbnailImageUrl = null,
            oauthProviderUserId = null,
            oauthConnectedAt = null,
            oauthExtraJson = null,
            oauthRawJson = null,
            userStatus = UserStatus.SOFT_DELETED,
            blocked = false,
            blockedReason = null,
            blockedAt = null,
            blockedByAdminId = null,
            unblockedAt = null,
            unblockedByAdminId = null,
            deletedAt = at,
            deletionReason = reason?.trim()?.takeIf { it.isNotBlank() },
            retentionUntil = retentionUntil,
        )
    }

    fun updateByAdmin(
        email: String?,
        phoneNumber: String?,
        name: String?,
        nickname: String?,
        gender: Gender?,
        locale: String?,
        birthyear: String?,
        birthday: String?,
        profileImageUrl: String?,
        thumbnailImageUrl: String?,
        role: Role?,
        userStatus: UserStatus?,
        blocked: Boolean?,
        blockedReason: String?,
        emailVerified: Boolean?,
        phoneVerified: Boolean?,
        adminUserId: Long,
        now: Instant,
    ): User {
        val targetStatus = userStatus ?: this.userStatus
        val targetBlocked = blocked ?: this.blocked
        val normalizedBlocked = targetBlocked || targetStatus == UserStatus.BLOCKED
        val normalizedStatus = when {
            targetStatus == UserStatus.SOFT_DELETED -> UserStatus.SOFT_DELETED
            normalizedBlocked -> UserStatus.BLOCKED
            else -> UserStatus.ACTIVE
        }

        val nextBlockedAt = when {
            normalizedBlocked && !this.blocked -> now
            normalizedBlocked -> this.blockedAt
            else -> null
        }

        val nextUnblockedAt = when {
            !normalizedBlocked && this.blocked -> now
            !normalizedBlocked -> this.unblockedAt
            else -> null
        }

        return copy(
            email = email?.trim()?.lowercase() ?: this.email,
            emailVerified = emailVerified ?: this.emailVerified,
            phoneNumber = phoneNumber?.trim() ?: this.phoneNumber,
            phoneVerified = phoneVerified ?: this.phoneVerified,
            name = name?.trim() ?: this.name,
            nickname = nickname?.trim() ?: this.nickname,
            gender = gender ?: this.gender,
            locale = locale?.trim() ?: this.locale,
            birthyear = birthyear ?: this.birthyear,
            birthday = birthday ?: this.birthday,
            profileImageUrl = profileImageUrl?.trim() ?: this.profileImageUrl,
            thumbnailImageUrl = thumbnailImageUrl?.trim() ?: this.thumbnailImageUrl,
            role = role ?: this.role,
            userStatus = normalizedStatus,
            blocked = normalizedBlocked,
            blockedReason = if (normalizedBlocked) blockedReason?.trim() ?: this.blockedReason else null,
            blockedAt = nextBlockedAt,
            blockedByAdminId = if (normalizedBlocked) adminUserId else null,
            unblockedAt = nextUnblockedAt,
            unblockedByAdminId = if (!normalizedBlocked && this.blocked) adminUserId else this.unblockedByAdminId,
        )
    }

    fun mergeOAuth2Profile(
        email: String?,
        emailVerified: Boolean?,
        name: String?,
        nickname: String?,
        locale: String?,
        profileImageUrl: String?,
        thumbnailImageUrl: String?,
        gender: Gender?,
        birthday: String?,
        birthyear: String?,
        ageRange: String?,
        phoneNumber: String?,
        phoneNumberE164: String?,
        connectedAt: Instant?,
        extraJson: String?,
        rawJson: String?,
    ): User {
        return copy(
            email = email?.takeIf { it.isNotBlank() } ?: this.email,
            emailVerified = emailVerified ?: this.emailVerified,
            name = name?.takeIf { it.isNotBlank() } ?: this.name,
            nickname = nickname?.takeIf { it.isNotBlank() } ?: this.nickname,
            locale = locale?.takeIf { it.isNotBlank() } ?: this.locale,
            profileImageUrl = profileImageUrl?.takeIf { it.isNotBlank() } ?: this.profileImageUrl,
            thumbnailImageUrl = thumbnailImageUrl?.takeIf { it.isNotBlank() } ?: this.thumbnailImageUrl,
            gender = gender ?: this.gender,
            birthday = birthday?.takeIf { it.isNotBlank() } ?: this.birthday,
            birthyear = birthyear?.takeIf { it.isNotBlank() } ?: this.birthyear,
            ageRange = ageRange?.takeIf { it.isNotBlank() } ?: this.ageRange,
            phoneNumber = phoneNumber?.takeIf { it.isNotBlank() } ?: this.phoneNumber,
            phoneNumberE164 = phoneNumberE164?.takeIf { it.isNotBlank() } ?: this.phoneNumberE164,
            oauthConnectedAt = connectedAt ?: this.oauthConnectedAt,
            oauthExtraJson = extraJson ?: this.oauthExtraJson,
            oauthRawJson = rawJson ?: this.oauthRawJson,
        )
    }
}
