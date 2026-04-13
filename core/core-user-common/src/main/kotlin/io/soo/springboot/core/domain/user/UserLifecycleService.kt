package io.soo.springboot.core.domain.user

import io.soo.springboot.core.domain.token.TokenRevocationService
import io.soo.springboot.core.enums.AdminUserActionType
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.UserRepository
import io.soo.springboot.storage.db.core.UserStatusAuditLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

@Service
class UserLifecycleService(
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val tokenRevocationService: TokenRevocationService,
    private val userStatusAuditLogRepository: UserStatusAuditLogRepository,
) {
    @Transactional
    fun softDelete(userId: Long, reason: String?, actorUserId: Long? = null) {
        val user = userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, mapOf("userId" to userId))
        if (user.userStatus == UserStatus.SOFT_DELETED) return

        val now = Instant.now()
        val retentionUntil = now.atOffset(ZoneOffset.UTC).plusYears(5).toInstant()
        val trimmedReason = reason?.trim()?.takeIf { it.isNotBlank() }
        val anonymizedEmail = buildDeletedEmail(userId, now)
        val anonymizedPhone = buildDeletedPhone(userId, now)

        userRepository.save(
            user.softDelete(
                at = now,
                retentionUntil = retentionUntil,
                reason = trimmedReason,
                anonymizedEmail = anonymizedEmail,
                anonymizedPhone = anonymizedPhone,
            ),
        )

        localCredentialRepository.deleteByUserId(userId)
        tokenRevocationService.revokeAllByUserId(userId)
        userStatusAuditLogRepository.save(
            targetUserId = userId,
            actorUserId = actorUserId ?: userId,
            actionType = AdminUserActionType.SOFT_DELETE,
            reason = trimmedReason,
        )
    }

    private fun buildDeletedEmail(userId: Long, at: Instant): String {
        return "deleted+$userId.${at.epochSecond}@deleted.local"
    }

    private fun buildDeletedPhone(userId: Long, at: Instant): String {
        return "deleted-$userId-${at.epochSecond}"
    }
}
