package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.User
import java.time.Instant

data class AdminUserDetailResponse(
    val userId: Long,
    val email: String,
    val emailVerified: Boolean,
    val phoneNumber: String?,
    val phoneVerified: Boolean,
    val name: String?,
    val nickname: String?,
    val gender: Gender,
    val locale: String?,
    val birthyear: String?,
    val birthday: String?,
    val profileImageUrl: String?,
    val thumbnailImageUrl: String?,
    val authProvider: AuthProvider,
    val oauthProviderUserId: String?,
    val role: Role,
    val userStatus: UserStatus,
    val blocked: Boolean,
    val blockedReason: String?,
    val blockedAt: Instant?,
    val blockedByAdminId: Long?,
    val unblockedAt: Instant?,
    val unblockedByAdminId: Long?,
    val deletedAt: Instant?,
    val deletionReason: String?,
    val retentionUntil: Instant?,
) {
    companion object {
        fun from(user: User): AdminUserDetailResponse {
            return AdminUserDetailResponse(
                userId = user.id,
                email = user.email,
                emailVerified = user.emailVerified,
                phoneNumber = user.phoneNumber,
                phoneVerified = user.phoneVerified,
                name = user.name,
                nickname = user.nickname,
                gender = user.gender,
                locale = user.locale,
                birthyear = user.birthyear,
                birthday = user.birthday,
                profileImageUrl = user.profileImageUrl,
                thumbnailImageUrl = user.thumbnailImageUrl,
                authProvider = user.authProvider,
                oauthProviderUserId = user.oauthProviderUserId,
                role = user.role,
                userStatus = user.userStatus,
                blocked = user.blocked,
                blockedReason = user.blockedReason,
                blockedAt = user.blockedAt,
                blockedByAdminId = user.blockedByAdminId,
                unblockedAt = user.unblockedAt,
                unblockedByAdminId = user.unblockedByAdminId,
                deletedAt = user.deletedAt,
                deletionReason = user.deletionReason,
                retentionUntil = user.retentionUntil,
            )
        }
    }
}
