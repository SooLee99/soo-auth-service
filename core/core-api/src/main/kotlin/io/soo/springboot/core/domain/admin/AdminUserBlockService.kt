package io.soo.springboot.core.domain.admin

import io.soo.springboot.core.enums.AdminUserActionType
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import io.soo.springboot.storage.db.core.UserStatusAuditLog
import io.soo.springboot.storage.db.core.UserStatusAuditLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AdminUserBlockService(
    private val userRepository: UserRepository,
    private val userStatusAuditLogRepository: UserStatusAuditLogRepository,
) {

    @Transactional
    fun blockUser(targetUserId: Long, adminUserId: Long, reason: String?): User {
        val user = userRepository.findByIdIncludingDeleted(targetUserId)
            ?: throw CoreException(ErrorType.NOT_FOUND, mapOf("userId" to targetUserId))
        if (user.userStatus == UserStatus.SOFT_DELETED) {
            throw CoreException(ErrorType.CONFLICT, mapOf("reason" to "SOFT_DELETED_USER_CANNOT_BE_BLOCKED"))
        }

        val now = Instant.now()
        val trimmedReason = reason?.trim()?.takeIf { it.isNotBlank() }
        val updated = user.copy(
            userStatus = UserStatus.BLOCKED,
            blocked = true,
            blockedReason = trimmedReason,
            blockedAt = now,
            blockedByAdminId = adminUserId,
            unblockedAt = null,
            unblockedByAdminId = null,
        )

        val saved = userRepository.save(updated)
        userStatusAuditLogRepository.save(
            targetUserId = targetUserId,
            actorUserId = adminUserId,
            actionType = AdminUserActionType.BLOCK,
            reason = trimmedReason,
        )
        return saved
    }

    @Transactional
    fun unblockUser(targetUserId: Long, adminUserId: Long): User {
        val user = userRepository.findByIdIncludingDeleted(targetUserId)
            ?: throw CoreException(ErrorType.NOT_FOUND, mapOf("userId" to targetUserId))
        if (user.userStatus == UserStatus.SOFT_DELETED) {
            throw CoreException(ErrorType.CONFLICT, mapOf("reason" to "SOFT_DELETED_USER_CANNOT_BE_UNBLOCKED"))
        }

        val now = Instant.now()
        val updated = user.copy(
            userStatus = UserStatus.ACTIVE,
            blocked = false,
            unblockedAt = now,
            unblockedByAdminId = adminUserId,
        )

        val saved = userRepository.save(updated)
        userStatusAuditLogRepository.save(
            targetUserId = targetUserId,
            actorUserId = adminUserId,
            actionType = AdminUserActionType.UNBLOCK,
            reason = null,
        )
        return saved
    }

    @Transactional(readOnly = true)
    fun findBlockedUsers(pageable: Pageable): Page<User> {
        return userRepository.findBlockedUsers(pageable)
    }

    @Transactional(readOnly = true)
    fun findSoftDeletedUsers(pageable: Pageable): Page<User> {
        return userRepository.findSoftDeletedUsers(pageable)
    }

    @Transactional(readOnly = true)
    fun findStatusAuditLogs(targetUserId: Long, pageable: Pageable): Page<UserStatusAuditLog> {
        return userStatusAuditLogRepository.findByTargetUserId(targetUserId, pageable)
    }
}