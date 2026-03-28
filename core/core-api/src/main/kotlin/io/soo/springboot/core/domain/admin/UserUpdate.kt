package io.soo.springboot.core.domain.admin

import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.User
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class UserUpdate {
    fun merge(
        current: User,
        command: UserUpdateCmd,
        adminUserId: Long,
        now: Instant,
    ): User {
        val targetStatus = command.userStatus ?: current.userStatus
        val targetBlocked = command.blocked ?: current.blocked
        val normalizedBlocked = targetBlocked || targetStatus == UserStatus.BLOCKED
        val normalizedStatus = when {
            targetStatus == UserStatus.SOFT_DELETED -> UserStatus.SOFT_DELETED
            normalizedBlocked -> UserStatus.BLOCKED
            else -> UserStatus.ACTIVE
        }

        val blockedAt = when {
            normalizedBlocked && !current.blocked -> now
            normalizedBlocked -> current.blockedAt
            else -> null
        }

        val unblockedAt = when {
            !normalizedBlocked && current.blocked -> now
            !normalizedBlocked -> current.unblockedAt
            else -> null
        }

        return current.copy(
            email = command.email?.trim()?.lowercase() ?: current.email,
            emailVerified = command.emailVerified ?: current.emailVerified,
            phoneNumber = command.phoneNumber?.trim() ?: current.phoneNumber,
            phoneVerified = command.phoneVerified ?: current.phoneVerified,
            name = command.name?.trim() ?: current.name,
            nickname = command.nickname?.trim() ?: current.nickname,
            gender = command.gender ?: current.gender,
            locale = command.locale?.trim() ?: current.locale,
            birthyear = command.birthyear ?: current.birthyear,
            birthday = command.birthday ?: current.birthday,
            profileImageUrl = command.profileImageUrl?.trim() ?: current.profileImageUrl,
            thumbnailImageUrl = command.thumbnailImageUrl?.trim() ?: current.thumbnailImageUrl,
            role = command.role ?: current.role,
            userStatus = normalizedStatus,
            blocked = normalizedBlocked,
            blockedReason = if (normalizedBlocked) command.blockedReason?.trim() ?: current.blockedReason else null,
            blockedAt = blockedAt,
            blockedByAdminId = if (normalizedBlocked) adminUserId else null,
            unblockedAt = unblockedAt,
            unblockedByAdminId = if (!normalizedBlocked && current.blocked) adminUserId else current.unblockedByAdminId,
        )
    }
}
